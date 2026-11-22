"""PostgreSQL dashboard queries; ownership is part of every query."""

import sqlalchemy as sa

from app.models.tables import heart_rate_readings as readings
from app.models.tables import recording_sessions as recordings


class DashboardRepository:
    def __init__(self, session, owner, filters, start_at, end_before):
        self.session = session
        self.conditions = [recordings.c.user_id == owner]
        if filters.recording_id is not None:
            self.conditions.append(readings.c.recording_id == filters.recording_id)
        if start_at is not None:
            self.conditions.append(readings.c.measured_at >= start_at)
        if end_before is not None:
            self.conditions.append(readings.c.measured_at < end_before)
        self.join = readings.join(
            recordings, readings.c.recording_id == recordings.c.id
        )

    def query(self, *columns):
        return sa.select(*columns).select_from(self.join).where(*self.conditions)

    def stats(self):
        return (
            self.session.execute(
                self.query(
                    sa.func.count().label("count"),
                    sa.func.min(readings.c.bpm).label("minimum_bpm"),
                    sa.func.max(readings.c.bpm).label("maximum_bpm"),
                    sa.func.round(sa.func.avg(readings.c.bpm), 1).label("average_bpm"),
                    sa.func.min(readings.c.measured_at).label("start_at"),
                    sa.func.max(readings.c.measured_at).label("end_at"),
                    sa.func.count(sa.distinct(recordings.c.source_type)).label(
                        "source_count"
                    ),
                    sa.func.min(recordings.c.source_type).label("single_source"),
                )
            )
            .mappings()
            .one()
        )

    def page(self, limit, after=None, descending=False):
        query = self.query(
            readings.c.id,
            readings.c.recording_id,
            readings.c.measured_at.label("timestamp"),
            readings.c.bpm.label("heart_rate"),
            recordings.c.source_type,
        )
        if after is not None:
            query = query.where(
                sa.tuple_(readings.c.measured_at, readings.c.id) > sa.tuple_(*after)
            )
        ordering = (readings.c.measured_at, readings.c.id)
        if descending:
            ordering = tuple(column.desc() for column in ordering)
        return (
            self.session.execute(query.order_by(*ordering).limit(limit))
            .mappings()
            .all()
        )

    def buckets(self, start_at, width_us):
        # Numeric epoch arithmetic avoids rounding timestamp instants to floats.
        elapsed_us = (
            sa.extract(
                "epoch",
                readings.c.measured_at
                - sa.literal(start_at, type_=sa.DateTime(timezone=True)),
            )
            * 1_000_000
        )
        bucket = sa.cast(
            sa.func.floor(elapsed_us / sa.cast(sa.literal(width_us), sa.Numeric)),
            sa.BigInteger,
        )
        return (
            self.session.execute(
                self.query(
                    bucket.label("bucket"),
                    sa.func.count().label("count"),
                    sa.func.min(readings.c.measured_at).label("first_reading_at"),
                    sa.func.max(readings.c.measured_at).label("last_reading_at"),
                    sa.func.min(readings.c.bpm).label("minimum_bpm"),
                    sa.func.max(readings.c.bpm).label("maximum_bpm"),
                    sa.func.round(sa.func.avg(readings.c.bpm), 1).label("average_bpm"),
                    sa.func.count(sa.distinct(readings.c.recording_id)).label(
                        "recording_count"
                    ),
                )
                .group_by(bucket)
                .order_by(bucket)
            )
            .mappings()
            .all()
        )


def rename_recording(session, owner, recording_id, label):
    return session.execute(
        recordings.update()
        .where(recordings.c.id == recording_id, recordings.c.user_id == owner)
        .values(label=label)
        .returning(recordings.c.id)
    ).scalar_one_or_none()


def delete_recording(session, owner, recording_id):
    return session.execute(
        recordings.delete()
        .where(recordings.c.id == recording_id, recordings.c.user_id == owner)
        .returning(recordings.c.id)
    ).scalar_one_or_none()
