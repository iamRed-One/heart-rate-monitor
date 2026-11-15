"""Validate previews and commit confirmed files atomically and idempotently."""

import base64
import hashlib
import json
from datetime import UTC, datetime, timedelta
from pathlib import PureWindowsPath
from uuid import UUID, uuid4

from app.parsers.csv import CanonicalCsvParser, ImportParser
from app.repositories.imports import ImportRepository
from app.schemas.imports import RecordingResponse


class ImportProblem(Exception):
    def __init__(self, status, code, message):
        self.status, self.code, self.message = status, code, message


def not_found():
    raise ImportProblem(404, "not_found", "The requested item was not found.")


def create_preview(
    session,
    owner,
    content,
    metadata,
    filename,
    now=None,
    parser: ImportParser | None = None,
):
    now = now or datetime.now(UTC)
    rows = (parser or CanonicalCsvParser()).parse(content, now)
    repo = ImportRepository(session)
    repo.lock_owner(owner)
    if repo.active_previews(owner, now) >= 3:
        raise ImportProblem(
            429,
            "preview_limit",
            "Discard unused previews or wait for expiry. Maximum three active previews.",
        )
    preview_id, expires = uuid4(), now + timedelta(minutes=15)
    payload = [
        {"timestamp": row.timestamp.isoformat(), "heart_rate": row.heart_rate}
        for row in rows
    ]
    # WindowsPath handles both slash styles; never use input as a disk path.
    filename = (
        PureWindowsPath(filename or "upload.csv").name.replace("\x00", "")[:255]
        or "upload.csv"
    )
    repo.add_preview(
        {
            "id": preview_id,
            "user_id": owner,
            "file_digest": hashlib.sha256(content).hexdigest(),
            **metadata.model_dump(),
            "original_filename": filename,
            "validated_payload": payload,
            "created_at": now,
            "expires_at": expires,
        }
    )
    return {
        "preview_id": preview_id,
        "expires_at": expires,
        "label": metadata.label,
        "source_type": metadata.source_type,
        "count": len(rows),
        "start_at": rows[0].timestamp,
        "end_at": rows[-1].timestamp,
        "sample_rows": payload[:10],
    }


def confirm_preview(session, owner, preview_id, now=None):
    now = now or datetime.now(UTC)
    repo = ImportRepository(session)
    preview = repo.preview(owner, preview_id)
    if preview is None:
        not_found()
    if preview["status"] == "consumed":
        if preview["recording_id"] is None:
            raise ImportProblem(
                410,
                "preview_consumed",
                "The recording was deleted. Upload the file again.",
            )
        return {
            "outcome": "existing_recording",
            "recording": get_recording(session, owner, preview["recording_id"]),
        }
    if preview["expires_at"] <= now:
        raise ImportProblem(
            410, "preview_expired", "Preview expired. Upload the file again."
        )
    recording_id = repo.add_recording(
        {
            "id": uuid4(),
            "user_id": owner,
            **{
                key: preview[key]
                for key in (
                    "label",
                    "source_type",
                    "device_label",
                    "original_filename",
                    "file_digest",
                )
            },
        }
    )
    created = recording_id is not None
    if created:
        repo.add_readings(
            [
                {
                    "recording_id": recording_id,
                    "measured_at": datetime.fromisoformat(row["timestamp"]),
                    "bpm": row["heart_rate"],
                }
                for row in preview["validated_payload"]
            ]
        )
    else:
        recording_id = repo.existing_recording_id(owner, preview["file_digest"])
    repo.consume(preview_id, recording_id, now)
    return {
        "outcome": "created" if created else "existing_recording",
        "recording": get_recording(session, owner, recording_id),
    }


def get_recording(session, owner, recording_id):
    row = ImportRepository(session).recording(owner, recording_id)
    if row is None:
        not_found()
    return RecordingResponse(**row)


def list_recordings(session, owner, limit, cursor):
    before = None
    if cursor:
        try:
            values = json.loads(base64.urlsafe_b64decode(cursor.encode()))
            stamp = datetime.fromisoformat(values[0])
            if stamp.tzinfo is None:
                raise ValueError
            before = stamp, UUID(values[1])
        except (
            ValueError,
            TypeError,
            IndexError,
            KeyError,
            UnicodeError,
            json.JSONDecodeError,
        ):
            raise ImportProblem(
                422, "invalid_cursor", "Invalid recording cursor."
            ) from None
    rows = ImportRepository(session).list_recordings(owner, limit, before)
    next_cursor = None
    if len(rows) > limit:
        last = rows[limit - 1]
        next_cursor = base64.urlsafe_b64encode(
            json.dumps([last["imported_at"].isoformat(), str(last["id"])]).encode()
        ).decode()
    return {
        "items": [RecordingResponse(**row) for row in rows[:limit]],
        "next_cursor": next_cursor,
    }
