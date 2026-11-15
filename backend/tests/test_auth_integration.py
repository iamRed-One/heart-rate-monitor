"""Opt-in tests against a separate *_test PostgreSQL database, never app data.

Run from backend: HR_RUN_DB_TESTS=1 uv run python -m unittest discover -s tests -v
"""

import os
import unittest
from datetime import UTC, datetime, timedelta
from pathlib import Path

import sqlalchemy as sa
from alembic import command
from alembic.config import Config
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.config import DatabaseSettings
from app.main import app
from app.models.tables import account_sessions, metadata, users
from app.persistence import database_url, get_session
from app.services.auth import digest


@unittest.skipUnless(
    os.getenv("HR_RUN_DB_TESTS") == "1",
    "Set HR_RUN_DB_TESTS=1 for isolated PostgreSQL tests",
)
class AuthIntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        settings = DatabaseSettings()
        cls.settings = settings.model_copy(
            update={"postgres_db": settings.postgres_db + "_test"}
        )
        if not cls.settings.postgres_db.endswith("_test"):
            raise RuntimeError("Tests must target a separate *_test database")
        cls.engine = sa.create_engine(database_url(cls.settings))
        with cls.engine.connect() as conn:
            if (
                conn.scalar(sa.text("SELECT current_database()"))
                != cls.settings.postgres_db
            ):
                raise RuntimeError("Unexpected test database")
        config = Config(str(Path(__file__).resolve().parents[1] / "alembic.ini"))
        config.attributes["database_url"] = database_url(cls.settings)
        command.upgrade(config, "head")

    @classmethod
    def tearDownClass(cls):
        cls.engine.dispose()

    def setUp(self):
        # Only this explicitly named test database is reset.
        with self.engine.begin() as conn:
            for table in reversed(metadata.sorted_tables):
                conn.execute(table.delete())

        def test_session():
            with Session(self.engine) as session:
                yield session

        app.dependency_overrides[get_session] = test_session
        self.client = TestClient(app)
        self.origin = {"Origin": "http://127.0.0.1:8000"}
        self.credentials = {
            "email": "person@example.com",
            "password": "Test-only password 123!",
        }

    def tearDown(self):
        self.client.close()
        app.dependency_overrides.clear()

    def register(self):
        return self.client.post(
            "/api/auth/register", json=self.credentials, headers=self.origin
        )

    def login(self, credentials=None):
        return self.client.post(
            "/api/auth/login", json=credentials or self.credentials, headers=self.origin
        )

    def test_register_normalises_and_never_returns_password(self):
        self.credentials["email"] = "  PERSON@example.com  "
        result = self.register()
        self.assertEqual(result.status_code, 201)
        self.assertEqual(result.json()["email"], "person@example.com")
        self.assertNotIn("password", result.text)
        self.assertEqual(self.client.get("/api/auth/me").status_code, 401)
        self.assertEqual(self.register().status_code, 409)
        with self.engine.connect() as conn:
            stored = conn.scalar(sa.select(users.c.password_hash))
        self.assertTrue(stored.startswith("$argon2id$"))
        self.assertNotEqual(stored, self.credentials["password"])

    def test_login_me_csrf_logout_and_revocation(self):
        self.register()
        result = self.login()
        self.assertEqual(result.status_code, 200)
        self.assertIn("httponly", result.headers["set-cookie"].lower())
        self.assertIn("samesite=lax", result.headers["set-cookie"].lower())
        token = self.client.cookies["hr_session"]
        csrf = result.json()["csrf_token"]
        self.assertEqual(self.client.get("/api/auth/me").json()["csrf_token"], csrf)
        self.assertEqual(
            self.client.post("/api/auth/logout", headers=self.origin).status_code, 403
        )
        self.assertEqual(
            self.client.post(
                "/api/auth/logout", headers={**self.origin, "X-CSRF-Token": csrf}
            ).status_code,
            204,
        )
        self.client.cookies.set("hr_session", token)
        self.assertEqual(self.client.get("/api/auth/me").status_code, 401)
        with self.engine.connect() as conn:
            self.assertEqual(
                conn.scalar(sa.select(sa.func.count()).select_from(account_sessions)), 0
            )

    def test_failed_login_is_generic_and_rate_limited(self):
        self.register()
        wrong = {**self.credentials, "password": "Wrong password 123!"}
        for _ in range(5):
            response = self.login(wrong)
            self.assertEqual(response.status_code, 401)
            self.assertEqual(response.json()["message"], "Invalid email or password.")
        response = self.login()
        self.assertEqual(response.status_code, 429)
        self.assertGreater(int(response.headers["retry-after"]), 0)

    def test_origin_and_validation_do_not_expose_secrets(self):
        self.assertEqual(
            self.client.post("/api/auth/register", json=self.credentials).status_code,
            403,
        )
        self.assertEqual(
            self.client.post(
                "/api/auth/login",
                json=self.credentials,
                headers={"Origin": "https://other.example"},
            ).status_code,
            403,
        )
        result = self.client.post(
            "/api/auth/register",
            json={**self.credentials, "password": "short"},
            headers=self.origin,
        )
        self.assertEqual(result.status_code, 422)
        self.assertNotIn('"input"', result.text)
        self.assertNotIn('"short"', result.text)

    def test_expired_session_and_rotation(self):
        self.register()
        self.login()
        old_token = self.client.cookies["hr_session"]
        self.login()
        new_token = self.client.cookies["hr_session"]
        self.assertNotEqual(old_token, new_token)
        with self.engine.begin() as conn:
            self.assertIsNone(
                conn.scalar(
                    sa.select(account_sessions.c.token_digest).where(
                        account_sessions.c.token_digest == digest(old_token)
                    )
                )
            )
            conn.execute(
                account_sessions.update().values(
                    created_at=datetime.now(UTC) - timedelta(days=2),
                    expires_at=datetime.now(UTC) - timedelta(days=1),
                )
            )
        self.assertEqual(self.client.get("/api/auth/me").status_code, 401)

    def test_two_accounts_are_isolated(self):
        a = self.register().json()
        self.login()
        client_b = TestClient(app)
        try:
            credentials_b = {**self.credentials, "email": "other@example.com"}
            b = client_b.post(
                "/api/auth/register", json=credentials_b, headers=self.origin
            ).json()
            client_b.post("/api/auth/login", json=credentials_b, headers=self.origin)
            self.assertEqual(
                self.client.get("/api/auth/me").json()["user"]["id"], a["id"]
            )
            self.assertEqual(client_b.get("/api/auth/me").json()["user"]["id"], b["id"])
            wrong_csrf = client_b.get("/api/auth/me").json()["csrf_token"]
            self.assertEqual(
                self.client.post(
                    "/api/auth/logout",
                    headers={**self.origin, "X-CSRF-Token": wrong_csrf},
                ).status_code,
                403,
            )
        finally:
            client_b.close()

    def test_database_constraints_reject_bad_readings(self):
        from uuid import uuid4

        from app.models.tables import heart_rate_readings, recording_sessions

        user_id = self.register().json()["id"]
        recording_id = uuid4()
        with self.engine.begin() as conn:
            conn.execute(
                recording_sessions.insert().values(
                    id=recording_id,
                    user_id=user_id,
                    label="Synthetic",
                    source_type="synthetic",
                    original_filename="sample.csv",
                    file_digest="a" * 64,
                )
            )
        with self.assertRaises(sa.exc.IntegrityError), self.engine.begin() as conn:
            conn.execute(
                heart_rate_readings.insert().values(
                    recording_id=recording_id, measured_at=datetime.now(UTC), bpm=0
                )
            )
