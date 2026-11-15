"""Bounded PostgreSQL connectivity probe; no application schema is created."""

import psycopg

from app.config import DatabaseSettings


def check_database(settings: DatabaseSettings) -> bool:
    """Verify authentication and a read-only query, closing each connection."""
    with psycopg.connect(
        host=settings.postgres_host,
        port=settings.postgres_port,
        user=settings.postgres_user,
        password=settings.postgres_password.get_secret_value(),
        dbname=settings.postgres_db,
        connect_timeout=3,
        options="-c statement_timeout=3000 -c default_transaction_read_only=on",
    ) as connection:
        row = connection.execute("SELECT 1").fetchone()
    return row == (1,)
