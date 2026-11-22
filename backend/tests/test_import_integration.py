"""Opt-in import tests use only the explicitly isolated *_test database."""

import asyncio
import os
import unittest
from concurrent.futures import ThreadPoolExecutor
from datetime import UTC, datetime, timedelta
from pathlib import Path
from unittest.mock import patch

import sqlalchemy as sa
from alembic import command
from alembic.config import Config
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.config import DatabaseSettings
from app.main import app
from app.maintenance import cleanup
from app.models.tables import (
    heart_rate_readings,
    import_previews,
    metadata,
    recording_sessions,
)
from app.persistence import database_url, get_session
from app.repositories.imports import ImportRepository
from app.upload_limits import MAX_REQUEST_BYTES, UploadLimitMiddleware

SAMPLE = b"timestamp,heart_rate\n2026-11-01T09:00:00+01:00,72\n2026-11-01T09:01:00+01:00,75\n2026-11-01T09:02:00+01:00,78\n"


@unittest.skipUnless(
    os.getenv("HR_RUN_DB_TESTS") == "1", "Set HR_RUN_DB_TESTS=1 for isolated DB tests"
)
class ImportIntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        settings = DatabaseSettings()
        target = settings.model_copy(
            update={"postgres_db": settings.postgres_db + "_test"}
        )
        assert target.postgres_db.endswith("_test")
        cls.engine = sa.create_engine(database_url(target))
        with cls.engine.connect() as conn:
            assert (
                conn.scalar(sa.text("SELECT current_database()")) == target.postgres_db
            )
        config = Config(str(Path(__file__).resolve().parents[1] / "alembic.ini"))
        config.attributes["database_url"] = database_url(target)
        command.upgrade(config, "head")

    @classmethod
    def tearDownClass(cls):
        cls.engine.dispose()

    def setUp(self):
        # TestClient lifespan must never run maintenance on the app database.
        self.cleanup_patch = patch("app.main.run_cleanup", return_value=None)
        self.cleanup_patch.start()
        with self.engine.begin() as conn:
            for table in reversed(metadata.sorted_tables):
                conn.execute(table.delete())

        def session():
            with Session(self.engine) as value:
                yield value

        app.dependency_overrides[get_session] = session
        self.client = TestClient(app)
        self.origin = {"Origin": "http://127.0.0.1:8000"}
        self.credentials = {
            "email": "importer@example.com",
            "password": "Synthetic password 123!",
        }
        self.client.post(
            "/api/auth/register", json=self.credentials, headers=self.origin
        )
        response = self.client.post(
            "/api/auth/login", json=self.credentials, headers=self.origin
        )
        self.headers = {**self.origin, "X-CSRF-Token": response.json()["csrf_token"]}

    def tearDown(self):
        self.client.close()
        app.dependency_overrides.clear()
        self.cleanup_patch.stop()

    def preview(self, content=SAMPLE, **overrides):
        fields = {"label": "Synthetic demo", "source_type": "synthetic", **overrides}
        return self.client.post(
            "/api/imports/preview",
            files={"file": ("demo.csv", content, "text/csv")},
            data=fields,
            headers=self.headers,
        )

    def confirm(self, preview_id):
        return self.client.post(
            f"/api/imports/{preview_id}/confirm", headers=self.headers
        )

    def count(self, table):
        with self.engine.connect() as conn:
            return conn.scalar(sa.select(sa.func.count()).select_from(table))

    def test_preview_confirm_retries_and_history(self):
        preview = self.preview()
        self.assertEqual(preview.status_code, 200, preview.text)
        self.assertEqual(preview.json()["count"], 3)
        self.assertEqual(self.count(recording_sessions), 0)
        preview_id = preview.json()["preview_id"]
        result = self.confirm(preview_id)
        self.assertEqual(result.status_code, 201, result.text)
        recording = result.json()["recording"]
        self.assertEqual(
            (
                recording["count"],
                recording["minimum_bpm"],
                recording["maximum_bpm"],
                recording["average_bpm"],
            ),
            (3, 72, 78, 75.0),
        )
        self.assertEqual(self.confirm(preview_id).status_code, 200)
        self.assertEqual(self.count(heart_rate_readings), 3)
        history = self.client.get("/api/recordings").json()
        self.assertEqual(history["items"][0]["id"], recording["id"])
        self.assertEqual(
            self.client.get("/api/recordings/" + recording["id"]).status_code, 200
        )
        with self.engine.connect() as conn:
            self.assertIsNone(
                conn.scalar(sa.select(import_previews.c.validated_payload))
            )

    def test_invalid_file_and_metadata_save_nothing(self):
        result = self.preview(b"timestamp,heart_rate\ninvalid,zero")
        self.assertEqual(result.status_code, 422)
        self.assertEqual(result.json()["total_errors"], 2)
        self.assertEqual(self.preview(label="  ").status_code, 422)
        self.assertEqual(self.preview(source_type="unverified").status_code, 422)
        self.assertEqual(self.count(import_previews), 0)
        self.assertEqual(self.count(recording_sessions), 0)

    def test_csrf_session_and_foreign_owner(self):
        result = self.client.post(
            "/api/imports/preview",
            files={"file": ("demo.csv", SAMPLE)},
            data={"label": "x", "source_type": "synthetic"},
            headers=self.origin,
        )
        self.assertEqual(result.status_code, 403)
        preview_id = self.preview().json()["preview_id"]
        other = {
            "email": "other-importer@example.com",
            "password": "Another password 123!",
        }
        with TestClient(app) as client:
            client.post("/api/auth/register", json=other, headers=self.origin)
            login = client.post(
                "/api/auth/login", json=other, headers=self.origin
            ).json()
            headers = {**self.origin, "X-CSRF-Token": login["csrf_token"]}
            self.assertEqual(
                client.post(
                    f"/api/imports/{preview_id}/confirm", headers=headers
                ).status_code,
                404,
            )
            self.assertEqual(
                client.delete(
                    f"/api/imports/{preview_id}", headers=headers
                ).status_code,
                404,
            )
            record = self.confirm(preview_id).json()["recording"]
            self.assertEqual(
                client.get("/api/recordings/" + record["id"]).status_code, 404
            )
            self.assertEqual(client.get("/api/recordings").json()["items"], [])

    def test_expiry_cap_discard_and_cleanup(self):
        ids = [self.preview().json()["preview_id"] for _ in range(3)]
        self.assertEqual(self.preview().status_code, 429)
        self.assertEqual(
            self.client.delete(
                "/api/imports/" + ids[0], headers=self.headers
            ).status_code,
            204,
        )
        self.assertEqual(self.count(import_previews), 2)
        with self.engine.begin() as conn:
            conn.execute(
                import_previews.update().values(
                    created_at=datetime.now(UTC) - timedelta(hours=2),
                    expires_at=datetime.now(UTC) - timedelta(hours=1),
                )
            )
        self.assertEqual(self.confirm(ids[1]).status_code, 410)
        with Session(self.engine) as session:
            cleanup(session, datetime.now(UTC))
            session.commit()
        self.assertEqual(self.confirm(ids[1]).status_code, 404)

    def test_rollback_preserves_preview_on_failure(self):
        preview_id = self.preview().json()["preview_id"]
        with patch.object(
            ImportRepository,
            "add_readings",
            side_effect=sa.exc.OperationalError(
                "forced", None, Exception("private failure")
            ),
        ):
            response = self.confirm(preview_id)
        self.assertEqual(response.status_code, 503)
        self.assertNotIn("private failure", response.text)
        self.assertEqual(self.count(recording_sessions), 0)
        self.assertEqual(self.count(heart_rate_readings), 0)
        self.assertEqual(self.confirm(preview_id).status_code, 201)

    def test_duplicate_files_preserve_metadata_and_concurrent_confirm(self):
        ids = [
            self.preview().json()["preview_id"],
            self.preview(label="Different label", source_type="recorded").json()[
                "preview_id"
            ],
        ]
        cookies = dict(self.client.cookies)

        def confirm(preview_id):
            with TestClient(app) as client:
                client.cookies.update(cookies)
                return client.post(
                    f"/api/imports/{preview_id}/confirm", headers=self.headers
                )

        with ThreadPoolExecutor(max_workers=2) as pool:
            responses = list(pool.map(confirm, ids))
        self.assertEqual(
            sorted(response.status_code for response in responses), [200, 201]
        )
        self.assertEqual(
            responses[0].json()["recording"]["id"],
            responses[1].json()["recording"]["id"],
        )
        self.assertEqual(
            responses[0].json()["recording"]["source_type"],
            responses[1].json()["recording"]["source_type"],
        )
        self.assertEqual(self.count(recording_sessions), 1)
        self.assertEqual(self.count(heart_rate_readings), 3)

    def test_cursor_validation(self):
        self.assertEqual(
            self.client.get("/api/recordings?cursor=e30=").status_code, 422
        )
        self.assertEqual(self.client.get("/api/recordings?cursor=bad").status_code, 422)


class UploadLimitTests(unittest.TestCase):
    def test_chunked_body_is_rejected_before_parser(self):
        async def run():
            messages = iter(
                [
                    {
                        "type": "http.request",
                        "body": b"x" * MAX_REQUEST_BYTES,
                        "more_body": True,
                    },
                    {"type": "http.request", "body": b"x", "more_body": False},
                ]
            )
            sent = []

            async def receive():
                return next(messages)

            async def send(message):
                sent.append(message)

            async def unused_app(scope, receive, send):
                self.fail("Multipart parser must not run for oversized requests")

            await UploadLimitMiddleware(unused_app)(
                {"type": "http", "path": "/api/imports/preview", "headers": []},
                receive,
                send,
            )
            self.assertEqual(sent[0]["status"], 413)

        asyncio.run(run())
