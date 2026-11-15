"""Explicit cleanup command, also invoked by the app's periodic task."""

from datetime import UTC, datetime, timedelta

from sqlalchemy.orm import Session

from app.models.tables import account_sessions, auth_rate_limits
from app.persistence import get_engine
from app.repositories.imports import cleanup_expired


def cleanup(session, now):
    cleanup_expired(session, now)
    session.execute(
        account_sessions.delete().where(account_sessions.c.expires_at <= now)
    )
    session.execute(
        auth_rate_limits.delete().where(
            auth_rate_limits.c.window_start <= now - timedelta(days=1)
        )
    )


def run_cleanup():
    with Session(get_engine()) as session:
        cleanup(session, datetime.now(UTC))
        session.commit()


if __name__ == "__main__":
    run_cleanup()
