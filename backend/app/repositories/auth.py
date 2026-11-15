"""Database operations for accounts, sessions and persistent rate limits."""

from datetime import timedelta

import sqlalchemy as sa
from sqlalchemy.dialects.postgresql import insert

from app.models.tables import account_sessions, auth_rate_limits, users


class AuthRepository:
    def __init__(self, session):
        self.session = session

    def find_user(self, email):
        return (
            self.session.execute(sa.select(users).where(users.c.email == email))
            .mappings()
            .first()
        )

    def add_user(self, values):
        return (
            self.session.execute(
                insert(users)
                .values(**values)
                .on_conflict_do_nothing(index_elements=[users.c.email])
                .returning(users)
            )
            .mappings()
            .first()
        )

    def rehash_password(self, user_id, password_hash):
        self.session.execute(
            users.update()
            .where(users.c.id == user_id)
            .values(password_hash=password_hash)
        )

    def add_session(self, values):
        self.session.execute(account_sessions.insert().values(**values))

    def find_session(self, digest, now):
        query = (
            sa.select(
                users.c.id,
                users.c.email,
                users.c.created_at,
                account_sessions.c.csrf_digest,
            )
            .join(account_sessions, account_sessions.c.user_id == users.c.id)
            .where(
                account_sessions.c.token_digest == digest,
                account_sessions.c.expires_at > now,
            )
        )
        return self.session.execute(query).mappings().first()

    def delete_session(self, digest):
        self.session.execute(
            account_sessions.delete().where(account_sessions.c.token_digest == digest)
        )

    def consume_rate_limit(self, key, now, limit, seconds):
        # Serialise only this account/IP counter, including its first insertion.
        lock_id = int.from_bytes(bytes.fromhex(key)[:8], "big", signed=True)
        self.session.execute(sa.select(sa.func.pg_advisory_xact_lock(lock_id)))
        row = (
            self.session.execute(
                sa.select(auth_rate_limits).where(auth_rate_limits.c.key == key)
            )
            .mappings()
            .first()
        )
        if row is None or row["window_start"] + timedelta(seconds=seconds) <= now:
            self.session.execute(
                insert(auth_rate_limits)
                .values(key=key, attempts=1, window_start=now)
                .on_conflict_do_update(
                    index_elements=[auth_rate_limits.c.key],
                    set_={"attempts": 1, "window_start": now},
                )
            )
            return 0
        if row["attempts"] >= limit:
            return max(
                1,
                int(
                    (
                        row["window_start"] + timedelta(seconds=seconds) - now
                    ).total_seconds()
                ),
            )
        self.session.execute(
            auth_rate_limits.update()
            .where(auth_rate_limits.c.key == key)
            .values(attempts=row["attempts"] + 1)
        )
        return 0

    def reset_rate_limit(self, key):
        self.session.execute(
            auth_rate_limits.delete().where(auth_rate_limits.c.key == key)
        )
