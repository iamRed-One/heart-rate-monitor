"""Dashboard correctness and failure cases against an isolated test database."""

import base64
import hashlib
import json
import os
import unittest
from datetime import UTC, datetime, timedelta
from pathlib import Path
from unittest.mock import patch
from uuid import UUID, uuid4

import sqlalchemy as sa
from alembic import command
from alembic.config import Config
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.config import DatabaseSettings
from app.main import app
from app.models.tables import heart_rate_readings as readings
from app.models.tables import (
    import_previews,
    metadata,
    recording_sessions as recordings,
)
from app.persistence import database_url, get_session
from app.repositories.dashboard import DashboardRepository, delete_recording

START = datetime(2026, 11, 1, tzinfo=UTC)


@unittest.skipUnless(os.getenv("HR_RUN_DB_TESTS") == "1", "Set HR_RUN_DB_TESTS=1")
class DashboardIntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        settings = DatabaseSettings()
        target = settings.model_copy(
            update={"postgres_db": settings.postgres_db + "_test"}
        )
        if not target.postgres_db.endswith("_test"):
            raise RuntimeError("Refusing to reset application data")
        cls.engine = sa.create_engine(database_url(target))
        with cls.engine.connect() as conn:
            if conn.scalar(sa.text("SELECT current_database()")) != target.postgres_db:
                raise RuntimeError("Unexpected database")
        config = Config(str(Path(__file__).resolve().parents[1] / "alembic.ini"))
        config.attributes["database_url"] = database_url(target)
        command.upgrade(config, "head")

    @classmethod
    def tearDownClass(cls):
        cls.engine.dispose()

    def setUp(self):
        self.cleanup_patch = patch("app.main.run_cleanup", return_value=None)
        self.cleanup_patch.start()
        with self.engine.begin() as conn:
            for table in reversed(metadata.sorted_tables):
                conn.execute(table.delete())

        def test_session():
            with Session(self.engine) as session:
                yield session

        app.dependency_overrides[get_session] = test_session
        self.client = TestClient(app)
        self.origin = {"Origin": "http://127.0.0.1:8000"}
        credentials = {
            "email": "dashboard@example.com",
            "password": "Synthetic password 123!",
        }
        self.client.post("/api/auth/register", json=credentials, headers=self.origin)
        login = self.client.post(
            "/api/auth/login", json=credentials, headers=self.origin
        ).json()
        self.owner = UUID(login["user"]["id"])
        self.headers = {**self.origin, "X-CSRF-Token": login["csrf_token"]}

    def tearDown(self):
        self.client.close()
        app.dependency_overrides.clear()
        self.cleanup_patch.stop()

    def seed(self, values, owner=None, source="synthetic"):
        recording_id = uuid4()
        with self.engine.begin() as conn:
            conn.execute(
                recordings.insert().values(
                    id=recording_id,
                    user_id=owner or self.owner,
                    label="Synthetic dashboard fixture",
                    source_type=source,
                    original_filename="fixture.csv",
                    file_digest=hashlib.sha256(str(recording_id).encode()).hexdigest(),
                )
            )
            if values:
                conn.execute(
                    readings.insert(),
                    [
                        {"recording_id": recording_id, "measured_at": stamp, "bpm": bpm}
                        for stamp, bpm in values
                    ],
                )
        return str(recording_id)

    def test_exact_outputs_and_empty_range(self):
        record = self.seed(
            [
                (START, 72),
                (START + timedelta(minutes=1), 75),
                (START + timedelta(minutes=2), 78),
            ]
        )
        query = {"recording_id": record}
        summary = self.client.get("/api/summary", params=query)
        self.assertEqual(summary.status_code, 200, summary.text)
        body = summary.json()
        self.assertEqual(
            (
                body["count"],
                body["minimum_bpm"],
                body["maximum_bpm"],
                body["average_bpm"],
            ),
            (3, 72, 78, 75.0),
        )
        self.assertEqual(body["latest_reading"]["heart_rate"], 78)
        self.assertEqual(body["source_type"], "synthetic")
        points = self.client.get("/api/chart", params=query).json()
        self.assertEqual(points["mode"], "exact")
        self.assertEqual([p["heart_rate"] for p in points["points"]], [72, 75, 78])
        self.assertEqual(points["buckets"], [])
        empty = {**query, "start_date": "2026-11-02"}
        body = self.client.get("/api/summary", params=empty).json()
        self.assertEqual(body["count"], 0)
        for key in (
            "minimum_bpm",
            "maximum_bpm",
            "average_bpm",
            "latest_reading",
            "start_at",
            "end_at",
        ):
            self.assertIsNone(body[key])
        self.assertEqual(body["source_type"], "none")
        self.assertEqual(
            self.client.get("/api/readings", params=empty).json(),
            {"items": [], "next_cursor": None},
        )
        self.assertEqual(
            self.client.get("/api/chart", params=empty).json()["points"], []
        )

    def test_utc_date_boundaries(self):
        record = self.seed(
            [
                (START - timedelta(microseconds=1), 60),
                (START, 72),
                (START + timedelta(days=1) - timedelta(microseconds=1), 78),
                (START + timedelta(days=1), 90),
            ]
        )
        params = {
            "recording_id": record,
            "start_date": "2026-11-01",
            "end_date": "2026-11-01",
        }
        self.assertEqual(
            [
                r["heart_rate"]
                for r in self.client.get("/api/readings", params=params).json()["items"]
            ],
            [72, 78],
        )
        self.assertEqual(
            self.client.get("/api/summary", params=params).json()["count"], 2
        )
        self.assertEqual(
            self.client.get("/api/chart", params=params).json()["count"], 2
        )
        self.assertEqual(
            self.client.get(
                "/api/summary",
                params={"recording_id": record, "end_date": "2026-11-01"},
            ).json()["count"],
            3,
        )

    def test_keyset_pagination_ties_and_scope_validation(self):
        values = [(START + timedelta(seconds=i), 72) for i in range(101)]
        record = self.seed(values)
        self.seed(values)
        first = self.client.get("/api/readings").json()
        self.assertEqual(len(first["items"]), 100)
        seen = [r["id"] for r in first["items"]]
        cursor = first["next_cursor"]
        while cursor:
            page = self.client.get("/api/readings", params={"cursor": cursor}).json()
            seen.extend(r["id"] for r in page["items"])
            cursor = page["next_cursor"]
        self.assertEqual(len(seen), 202)
        self.assertEqual(len(set(seen)), 202)
        self.assertEqual(
            self.client.get(
                "/api/readings",
                params={"recording_id": record, "cursor": first["next_cursor"]},
            ).status_code,
            422,
        )
        self.assertEqual(
            self.client.get(
                "/api/readings",
                params={"start_date": "2026-11-01", "cursor": first["next_cursor"]},
            ).status_code,
            422,
        )
        for value in (
            "bad",
            "",
            base64.urlsafe_b64encode(json.dumps({}).encode()).decode(),
        ):
            self.assertEqual(
                self.client.get("/api/readings", params={"cursor": value}).status_code,
                422,
            )

    def test_aggregation_is_bounded_and_preserves_total(self):
        record = self.seed(
            [(START + timedelta(seconds=i), 60 + i % 100) for i in range(1001)]
        )
        params = {"recording_id": record}
        chart = self.client.get("/api/chart", params=params)
        self.assertEqual(chart.status_code, 200, chart.text)
        data = chart.json()
        self.assertEqual(data["mode"], "aggregated")
        self.assertEqual(data["count"], 1001)
        self.assertEqual(data["points"], [])
        self.assertLessEqual(len(data["buckets"]), 500)
        self.assertEqual(sum(b["count"] for b in data["buckets"]), 1001)
        self.assertTrue(
            all(
                b["minimum_bpm"] <= b["average_bpm"] <= b["maximum_bpm"]
                for b in data["buckets"]
            )
        )
        summary = self.client.get("/api/summary", params=params).json()
        self.assertEqual(summary["count"], 1001)
        self.assertEqual(summary["minimum_bpm"], 60)
        self.assertEqual(summary["maximum_bpm"], 159)

    def test_mixed_sources_and_owner_isolation(self):
        self.seed([(START, 72)])
        self.seed([(START + timedelta(seconds=1), 80)], source="recorded")
        self.assertEqual(self.client.get("/api/summary").json()["source_type"], "mixed")
        self.assertEqual(self.client.get("/api/chart").json()["source_type"], "mixed")
        other = {
            "email": "foreign@example.com",
            "password": "Other synthetic password!",
        }
        other_id = self.client.post(
            "/api/auth/register", json=other, headers=self.origin
        ).json()["id"]
        foreign = self.seed([(START, 200)], owner=UUID(other_id))
        self.assertEqual(self.client.get("/api/summary").json()["count"], 2)
        for path in ("/api/readings", "/api/summary", "/api/chart"):
            self.assertEqual(
                self.client.get(path, params={"recording_id": foreign}).status_code, 404
            )
        self.assertEqual(
            self.client.patch(
                "/api/recordings/" + foreign,
                json={"label": "forbidden"},
                headers=self.headers,
            ).status_code,
            404,
        )
        self.assertEqual(
            self.client.delete(
                "/api/recordings/" + foreign, headers=self.headers
            ).status_code,
            404,
        )

    def test_rename_validation_csrf_and_delete_cascade(self):
        record = self.seed([(START, 72)])
        path = "/api/recordings/" + record
        self.assertEqual(
            self.client.patch(
                path, json={"label": "new"}, headers=self.origin
            ).status_code,
            403,
        )
        self.assertEqual(self.client.delete(path, headers=self.origin).status_code, 403)
        for payload in (
            {"label": " "},
            {"label": "x" * 101},
            {"label": "new", "user_id": str(uuid4())},
        ):
            self.assertEqual(
                self.client.patch(path, json=payload, headers=self.headers).status_code,
                422,
            )
        result = self.client.patch(
            path, json={"label": "  Renamed fixture  "}, headers=self.headers
        )
        self.assertEqual(result.status_code, 200, result.text)
        self.assertEqual(result.json()["label"], "Renamed fixture")
        preview_id = uuid4()
        with self.engine.begin() as conn:
            conn.execute(
                import_previews.insert().values(
                    id=preview_id,
                    user_id=self.owner,
                    recording_id=UUID(record),
                    file_digest="f" * 64,
                    label="Consumed fixture",
                    source_type="synthetic",
                    original_filename="fixture.csv",
                    status="consumed",
                    validated_payload=sa.null(),
                    created_at=START,
                    expires_at=START + timedelta(minutes=15),
                    consumed_at=START,
                )
            )
        self.assertEqual(
            self.client.delete(path, headers=self.headers).status_code, 204
        )
        self.assertEqual(self.client.get(path).status_code, 404)
        self.assertEqual(
            self.client.delete(path, headers=self.headers).status_code, 404
        )
        self.assertEqual(self.client.get("/api/summary").json()["count"], 0)
        with self.engine.connect() as conn:
            self.assertEqual(
                conn.scalar(sa.select(sa.func.count()).select_from(readings)), 0
            )
            self.assertIsNone(
                conn.scalar(
                    sa.select(import_previews.c.recording_id).where(
                        import_previews.c.id == preview_id
                    )
                )
            )
        self.assertEqual(
            self.client.post(
                f"/api/imports/{preview_id}/confirm", headers=self.headers
            ).status_code,
            410,
        )

    def test_rename_and_delete_roll_back_on_database_failure(self):
        record = self.seed([(START, 72)])
        path = "/api/recordings/" + record
        with patch(
            "app.routes.dashboard.get_recording",
            side_effect=sa.exc.OperationalError("forced", None, Exception("private")),
        ):
            self.assertEqual(
                self.client.patch(
                    path, json={"label": "Should roll back"}, headers=self.headers
                ).status_code,
                503,
            )
        self.assertEqual(
            self.client.get(path).json()["label"], "Synthetic dashboard fixture"
        )

        def fail_after_delete(session, owner, recording_id):
            delete_recording(session, owner, recording_id)
            raise sa.exc.OperationalError("forced", None, Exception("private"))

        with patch(
            "app.routes.dashboard.delete_recording", side_effect=fail_after_delete
        ):
            self.assertEqual(
                self.client.delete(path, headers=self.headers).status_code, 503
            )
        self.assertEqual(self.client.get(path).json()["count"], 1)

    def test_invalid_parameters_and_missing_session(self):
        for path in ("/api/readings", "/api/summary", "/api/chart"):
            for params in (
                {"recording_id": "bad"},
                {"start_date": "invalid"},
                {"start_date": "2026-11-02", "end_date": "2026-11-01"},
                {"end_date": "9999-12-31"},
            ):
                self.assertEqual(self.client.get(path, params=params).status_code, 422)
            self.assertEqual(
                self.client.get(
                    path, params={"recording_id": str(uuid4())}
                ).status_code,
                404,
            )
            with TestClient(app) as anonymous:
                self.assertEqual(anonymous.get(path).status_code, 401)
        self.assertEqual(self.client.get("/api/readings?limit=0").status_code, 422)
        self.assertEqual(self.client.get("/api/readings?limit=101").status_code, 422)

    def test_chart_uses_consistent_snapshot_during_concurrent_insert(self):
        record = self.seed([(START + timedelta(seconds=i), 72) for i in range(1000)])
        original = DashboardRepository.stats

        def stats_then_insert(repo):
            result = original(repo)
            with self.engine.begin() as conn:
                conn.execute(
                    readings.insert().values(
                        recording_id=UUID(record),
                        measured_at=START + timedelta(seconds=1001),
                        bpm=200,
                    )
                )
            return result

        with patch.object(
            DashboardRepository, "stats", autospec=True, side_effect=stats_then_insert
        ):
            result = self.client.get("/api/chart", params={"recording_id": record})
        self.assertEqual(result.status_code, 200, result.text)
        self.assertEqual(result.json()["mode"], "exact")
        self.assertEqual(len(result.json()["points"]), 1000)
        self.assertEqual(result.json()["count"], 1000)
        self.assertEqual(
            self.client.get("/api/summary", params={"recording_id": record}).json()[
                "count"
            ],
            1001,
        )
