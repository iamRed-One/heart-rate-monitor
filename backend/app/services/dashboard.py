"""Shared filters, scoped cursors and bounded chart responses."""

import base64
import hashlib
import json
from datetime import UTC, datetime, time, timedelta

from app.models.tables import recording_sessions
from app.repositories.dashboard import DashboardRepository
from app.services.imports import ImportProblem, not_found
import sqlalchemy as sa


def validate_filters(filters):
    if (
        filters.start_date
        and filters.end_date
        and filters.start_date > filters.end_date
    ):
        raise ImportProblem(
            422, "invalid_date_range", "Start date must be on or before end date."
        )
    start_at = (
        datetime.combine(filters.start_date, time.min, UTC)
        if filters.start_date
        else None
    )
    try:
        end_before = (
            datetime.combine(filters.end_date + timedelta(days=1), time.min, UTC)
            if filters.end_date
            else None
        )
    except OverflowError:
        raise ImportProblem(
            422, "invalid_date_range", "End date must be earlier than 9999-12-31."
        ) from None
    return start_at, end_before


def repository(session, owner, filters):
    start_at, end_before = validate_filters(filters)
    if filters.recording_id is not None:
        exists = session.scalar(
            sa.select(recording_sessions.c.id).where(
                recording_sessions.c.id == filters.recording_id,
                recording_sessions.c.user_id == owner,
            )
        )
        if exists is None:
            not_found()
    return DashboardRepository(session, owner, filters, start_at, end_before)


def source_type(stats):
    if stats["count"] == 0:
        return "none"
    return "mixed" if stats["source_count"] > 1 else stats["single_source"]


def cursor_scope(owner, filters):
    return hashlib.sha256((str(owner) + filters.model_dump_json()).encode()).hexdigest()


def read_cursor(value, scope):
    try:
        data = json.loads(
            base64.b64decode(value.encode(), altchars=b"-_", validate=True)
        )
        if not isinstance(data, list) or len(data) != 3 or data[0] != scope:
            raise ValueError
        stamp = datetime.fromisoformat(data[1])
        row_id = data[2]
        if (
            stamp.tzinfo is None
            or type(row_id) is not int
            or not 1 <= row_id <= 2**63 - 1
        ):
            raise ValueError
        return stamp, row_id
    except (ValueError, TypeError, KeyError, UnicodeError):
        raise ImportProblem(
            422,
            "invalid_cursor",
            "Invalid cursor or cursor does not match these filters.",
        ) from None


def get_readings(session, owner, filters, limit, cursor):
    repo = repository(session, owner, filters)
    scope = cursor_scope(owner, filters)
    after = read_cursor(cursor, scope) if cursor is not None else None
    rows = repo.page(limit + 1, after)
    next_cursor = None
    if len(rows) > limit:
        last = rows[limit - 1]
        next_cursor = base64.urlsafe_b64encode(
            json.dumps([scope, last["timestamp"].isoformat(), last["id"]]).encode()
        ).decode()
    return {"items": rows[:limit], "next_cursor": next_cursor}


def get_summary(session, owner, filters):
    repo = repository(session, owner, filters)
    stats = repo.stats()
    newest = repo.page(1, descending=True)
    return {
        **{
            key: stats[key]
            for key in (
                "count",
                "minimum_bpm",
                "maximum_bpm",
                "average_bpm",
                "start_at",
                "end_at",
            )
        },
        "source_type": source_type(stats),
        "latest_reading": newest[0] if newest else None,
    }


def get_chart(session, owner, filters):
    repo = repository(session, owner, filters)
    stats = repo.stats()
    result = {
        "mode": "exact",
        "count": stats["count"],
        "source_type": source_type(stats),
        "bucket_width_seconds": None,
        "points": [],
        "buckets": [],
    }
    if stats["count"] <= 1000:
        result["points"] = repo.page(1000)
        return result
    span_us = (stats["end_at"] - stats["start_at"]) // timedelta(microseconds=1)
    width_us = max(1, (span_us + 500) // 500)
    buckets = []
    for row in repo.buckets(stats["start_at"], width_us):
        start = stats["start_at"] + timedelta(microseconds=row["bucket"] * width_us)
        buckets.append(
            {key: value for key, value in row.items() if key != "bucket"}
            | {"start_at": start, "end_at": start + timedelta(microseconds=width_us)}
        )
    return {
        **result,
        "mode": "aggregated",
        "bucket_width_seconds": width_us / 1_000_000,
        "buckets": buckets,
    }
