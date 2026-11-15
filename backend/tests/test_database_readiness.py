"""Readiness failure handling and connection lifecycle checks without a real DB."""

import os
import unittest
from unittest.mock import MagicMock, patch

from fastapi import Response
from psycopg import OperationalError
from pydantic import ValidationError

from app.config import DatabaseSettings, PROJECT_ROOT
from app.database import check_database
from app.main import health, readiness


def settings() -> DatabaseSettings:
    return DatabaseSettings(_env_file=None, postgres_password="test-only:@secret")


class DatabaseReadinessTests(unittest.TestCase):
    def test_env_file_is_at_repository_root_not_backend(self):
        self.assertTrue((PROJECT_ROOT / "compose.yaml").is_file())
        self.assertEqual(
            DatabaseSettings.model_config["env_file"], PROJECT_ROOT / ".env"
        )

    def test_health_does_not_connect_to_database(self):
        with patch("app.main.check_database") as check:
            self.assertEqual(health().status, "OK")
            check.assert_not_called()

    def test_ready_reports_connected_and_is_not_cached(self):
        response = Response()
        with (
            patch("app.main.DatabaseSettings", return_value=settings()),
            patch("app.main.check_database", return_value=True),
        ):
            result = readiness(response)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(result.model_dump(), {"status": "OK", "database": "connected"})
        self.assertEqual(response.headers["Cache-Control"], "no-store")

    def test_database_failure_is_503_without_error_details(self):
        response = Response()
        with (
            patch("app.main.DatabaseSettings", return_value=settings()),
            patch(
                "app.main.check_database",
                side_effect=OperationalError("private detail"),
            ),
            self.assertLogs("app.main", level="WARNING") as logs,
        ):
            result = readiness(response)
        self.assertEqual(response.status_code, 503)
        self.assertEqual(result.database, "unavailable")
        self.assertNotIn("private detail", repr(result))
        self.assertNotIn("private detail", str(logs.output))

    def test_missing_password_is_503_and_health_still_works(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(ValidationError) as invalid:
                DatabaseSettings(_env_file=None)
        response = Response()
        with (
            patch("app.main.DatabaseSettings", side_effect=invalid.exception),
            patch("app.main.check_database") as check,
            self.assertLogs("app.main", level="WARNING"),
        ):
            self.assertEqual(readiness(response).database, "unavailable")
        self.assertEqual(response.status_code, 503)
        check.assert_not_called()
        self.assertEqual(health().status, "OK")

    def test_query_uses_keyword_credentials_and_closes_connection(self):
        connection_context = MagicMock()
        connection = connection_context.__enter__.return_value
        connection.execute.return_value.fetchone.return_value = (1, "0001_initial")
        with patch(
            "app.database.psycopg.connect", return_value=connection_context
        ) as connect:
            self.assertTrue(check_database(settings()))
        self.assertEqual(connect.call_args.kwargs["password"], "test-only:@secret")
        self.assertEqual(connect.call_args.kwargs["connect_timeout"], 3)
        connection.execute.assert_called_once_with(
            "SELECT 1, (SELECT version_num FROM alembic_version)"
        )
        connection_context.__exit__.assert_called_once()

    def test_invalid_port_is_rejected_without_exposing_secret(self):
        with self.assertRaises(ValidationError) as error:
            DatabaseSettings(
                _env_file=None, postgres_password="test-only-secret", postgres_port=0
            )
        self.assertNotIn("test-only-secret", str(error.exception))

    def test_unexpected_migration_revision_is_not_ready(self):
        connection_context = MagicMock()
        connection_context.__enter__.return_value.execute.return_value.fetchone.return_value = (
            1,
            "old_revision",
        )
        with patch("app.database.psycopg.connect", return_value=connection_context):
            self.assertFalse(check_database(settings()))


if __name__ == "__main__":
    unittest.main()
