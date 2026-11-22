"""PostgreSQL schema metadata. Schema changes are applied only through Alembic."""

import sqlalchemy as sa
from sqlalchemy.dialects.postgresql import JSONB, UUID

metadata = sa.MetaData()


def uuid_pk():
    return sa.Column("id", UUID(as_uuid=True), primary_key=True)


def timestamp(name, **kwargs):
    return sa.Column(name, sa.DateTime(timezone=True), **kwargs)


users = sa.Table(
    "users",
    metadata,
    uuid_pk(),
    sa.Column("email", sa.String(254), nullable=False, unique=True),
    sa.Column("password_hash", sa.Text, nullable=False),
    timestamp("created_at", nullable=False, server_default=sa.func.now()),
    sa.CheckConstraint("email = lower(btrim(email))", name="ck_users_email_normalised"),
)

account_sessions = sa.Table(
    "account_sessions",
    metadata,
    sa.Column("token_digest", sa.String(64), primary_key=True),
    sa.Column(
        "user_id",
        UUID(as_uuid=True),
        sa.ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    ),
    sa.Column("csrf_digest", sa.String(64), nullable=False),
    timestamp("created_at", nullable=False, server_default=sa.func.now()),
    timestamp("expires_at", nullable=False, index=True),
    sa.CheckConstraint("expires_at > created_at", name="ck_sessions_expiry"),
)

recording_sessions = sa.Table(
    "recording_sessions",
    metadata,
    uuid_pk(),
    sa.Column(
        "user_id",
        UUID(as_uuid=True),
        sa.ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
    ),
    sa.Column("label", sa.String(100), nullable=False),
    sa.Column("source_type", sa.String(16), nullable=False),
    sa.Column("device_label", sa.String(100)),
    sa.Column("original_filename", sa.String(255), nullable=False),
    sa.Column("file_digest", sa.String(64), nullable=False),
    timestamp("imported_at", nullable=False, server_default=sa.func.now()),
    sa.UniqueConstraint("user_id", "file_digest", name="uq_recording_owner_digest"),
    sa.CheckConstraint(
        "length(btrim(label)) BETWEEN 1 AND 100", name="ck_recording_label"
    ),
    sa.CheckConstraint(
        "source_type IN ('recorded','synthetic')", name="ck_recording_source"
    ),
    sa.CheckConstraint("file_digest ~ '^[0-9a-f]{64}$'", name="ck_recording_digest"),
)
sa.Index(
    "ix_recordings_owner_imported",
    recording_sessions.c.user_id,
    recording_sessions.c.imported_at.desc(),
    recording_sessions.c.id,
)

heart_rate_readings = sa.Table(
    "heart_rate_readings",
    metadata,
    sa.Column("id", sa.BigInteger, sa.Identity(), primary_key=True),
    sa.Column(
        "recording_id",
        UUID(as_uuid=True),
        sa.ForeignKey("recording_sessions.id", ondelete="CASCADE"),
        nullable=False,
    ),
    timestamp("measured_at", nullable=False),
    sa.Column("bpm", sa.SmallInteger, nullable=False),
    sa.UniqueConstraint(
        "recording_id", "measured_at", name="uq_reading_recording_time"
    ),
    sa.CheckConstraint("bpm BETWEEN 1 AND 300", name="ck_reading_bpm"),
)
sa.Index(
    "ix_readings_recording_time",
    heart_rate_readings.c.recording_id,
    heart_rate_readings.c.measured_at,
    heart_rate_readings.c.id,
)

import_previews = sa.Table(
    "import_previews",
    metadata,
    uuid_pk(),
    sa.Column(
        "user_id",
        UUID(as_uuid=True),
        sa.ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
    ),
    sa.Column(
        "recording_id",
        UUID(as_uuid=True),
        sa.ForeignKey("recording_sessions.id", ondelete="SET NULL"),
        index=True,
    ),
    sa.Column("file_digest", sa.String(64), nullable=False),
    sa.Column("label", sa.String(100), nullable=False),
    sa.Column("source_type", sa.String(16), nullable=False),
    sa.Column("device_label", sa.String(100)),
    sa.Column("original_filename", sa.String(255), nullable=False),
    sa.Column("validated_payload", JSONB),
    sa.Column("status", sa.String(16), nullable=False, server_default="pending"),
    timestamp("created_at", nullable=False, server_default=sa.func.now()),
    timestamp("expires_at", nullable=False),
    timestamp("consumed_at"),
    sa.CheckConstraint(
        "length(btrim(label)) BETWEEN 1 AND 100", name="ck_preview_label"
    ),
    sa.CheckConstraint(
        "source_type IN ('recorded','synthetic')", name="ck_preview_source"
    ),
    sa.CheckConstraint("file_digest ~ '^[0-9a-f]{64}$'", name="ck_preview_digest"),
    sa.CheckConstraint("expires_at > created_at", name="ck_preview_expiry"),
    sa.CheckConstraint(
        "(status = 'pending' AND validated_payload IS NOT NULL AND recording_id IS NULL AND consumed_at IS NULL) OR (status = 'consumed' AND validated_payload IS NULL AND consumed_at IS NOT NULL)",
        name="ck_preview_lifecycle",
    ),
)
sa.Index(
    "ix_previews_owner_expiry", import_previews.c.user_id, import_previews.c.expires_at
)

# Persistent counters coordinate login limits across API processes.
auth_rate_limits = sa.Table(
    "auth_rate_limits",
    metadata,
    sa.Column("key", sa.String(64), primary_key=True),
    sa.Column("attempts", sa.Integer, nullable=False),
    timestamp("window_start", nullable=False),
    sa.CheckConstraint("attempts >= 0", name="ck_auth_attempts"),
    sa.Index("ix_auth_rate_window", "window_start"),
)
