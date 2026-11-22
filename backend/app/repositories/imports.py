"""Owner-scoped preview/recording operations in a single request transaction."""

import sqlalchemy as sa
from sqlalchemy.dialects.postgresql import insert

from app.models.tables import heart_rate_readings as readings
from app.models.tables import import_previews as previews
from app.models.tables import recording_sessions as recordings
from app.models.tables import users


class ImportRepository:
    def __init__(self, session):
        self.session = session

    def lock_owner(self, owner):
        self.session.execute(
            sa.select(users.c.id).where(users.c.id == owner).with_for_update()
        ).first()

    def active_previews(self, owner, now):
        return self.session.scalar(
            sa.select(sa.func.count())
            .select_from(previews)
            .where(
                previews.c.user_id == owner,
                previews.c.status == "pending",
                previews.c.expires_at > now,
            )
        )

    def add_preview(self, values):
        self.session.execute(previews.insert().values(**values))

    def preview(self, owner, preview_id):
        return (
            self.session.execute(
                sa.select(previews)
                .where(previews.c.id == preview_id, previews.c.user_id == owner)
                .with_for_update()
            )
            .mappings()
            .first()
        )

    def discard(self, owner, preview_id):
        return self.session.execute(
            previews.delete()
            .where(previews.c.id == preview_id, previews.c.user_id == owner)
            .returning(previews.c.id)
        ).scalar_one_or_none()

    def add_recording(self, values):
        return self.session.execute(
            insert(recordings)
            .values(**values)
            .on_conflict_do_nothing(constraint="uq_recording_owner_digest")
            .returning(recordings.c.id)
        ).scalar_one_or_none()

    def existing_recording_id(self, owner, file_digest):
        return self.session.scalar(
            sa.select(recordings.c.id).where(
                recordings.c.user_id == owner, recordings.c.file_digest == file_digest
            )
        )

    def add_readings(self, values):
        self.session.execute(readings.insert(), values)

    def consume(self, preview_id, recording_id, now):
        self.session.execute(
            previews.update()
            .where(previews.c.id == preview_id)
            .values(
                status="consumed",
                recording_id=recording_id,
                consumed_at=now,
                validated_payload=sa.null(),
            )
        )

    def recording_query(self, owner):
        stats = (
            sa.select(
                readings.c.recording_id,
                sa.func.count().label("count"),
                sa.func.min(readings.c.measured_at).label("start_at"),
                sa.func.max(readings.c.measured_at).label("end_at"),
                sa.func.min(readings.c.bpm).label("minimum_bpm"),
                sa.func.max(readings.c.bpm).label("maximum_bpm"),
                sa.func.round(sa.func.avg(readings.c.bpm), 1).label("average_bpm"),
            )
            .join(recordings, recordings.c.id == readings.c.recording_id)
            .where(recordings.c.user_id == owner)
            .group_by(readings.c.recording_id)
            .subquery()
        )
        return (
            sa.select(
                recordings.c.id,
                recordings.c.label,
                recordings.c.source_type,
                recordings.c.device_label,
                recordings.c.original_filename,
                recordings.c.imported_at,
                sa.func.coalesce(stats.c.count, 0).label("count"),
                stats.c.start_at,
                stats.c.end_at,
                stats.c.minimum_bpm,
                stats.c.maximum_bpm,
                stats.c.average_bpm,
            )
            .outerjoin(stats, stats.c.recording_id == recordings.c.id)
            .where(recordings.c.user_id == owner)
        )

    def recording(self, owner, recording_id):
        return (
            self.session.execute(
                self.recording_query(owner).where(recordings.c.id == recording_id)
            )
            .mappings()
            .first()
        )

    def list_recordings(self, owner, limit, before=None):
        query = self.recording_query(owner)
        if before:
            query = query.where(
                sa.tuple_(recordings.c.imported_at, recordings.c.id)
                < sa.tuple_(*before)
            )
        return (
            self.session.execute(
                query.order_by(
                    recordings.c.imported_at.desc(), recordings.c.id.desc()
                ).limit(limit + 1)
            )
            .mappings()
            .all()
        )


def cleanup_expired(session, now):
    from datetime import timedelta

    session.execute(
        previews.delete().where(
            sa.or_(
                sa.and_(previews.c.status == "pending", previews.c.expires_at <= now),
                sa.and_(
                    previews.c.status == "consumed",
                    previews.c.consumed_at <= now - timedelta(hours=24),
                ),
            )
        )
    )
