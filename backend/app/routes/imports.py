"""Authenticated CSV previews, confirmation and basic recording history."""

from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, File, Form, Header, Query, Response, UploadFile
from pydantic import ValidationError

from app.parsers.csv import MAX_FILE_BYTES
from app.repositories.auth import AuthRepository
from app.repositories.imports import ImportRepository
from app.routes.auth import DB, current_user, require_origin
from app.schemas.imports import (
    ConfirmResponse,
    ImportMetadata,
    PreviewResponse,
    RecordingListResponse,
    RecordingResponse,
)
from app.services.auth import enforce_limit, verify_csrf
from app.services import imports

router = APIRouter(tags=["recordings and imports"])


def mutation_user(
    user=Depends(current_user),
    csrf: Annotated[str | None, Header(alias="X-CSRF-Token")] = None,
):
    verify_csrf(user, csrf)
    return user


@router.post(
    "/api/imports/preview",
    response_model=PreviewResponse,
    dependencies=[Depends(require_origin)],
)
def preview(
    session: DB,
    response: Response,
    file: Annotated[UploadFile, File()],
    label: Annotated[str, Form(max_length=100)],
    source_type: Annotated[str, Form(max_length=16)],
    device_label: Annotated[str | None, Form(max_length=100)] = None,
    user=Depends(mutation_user),
):
    from datetime import UTC, datetime

    # Commit only the small throttle transaction, independently of CSV validation.
    try:
        enforce_limit(
            AuthRepository(session),
            "preview:" + str(user["id"]),
            datetime.now(UTC),
            10,
            600,
        )
    finally:
        session.commit()
    try:
        content = file.file.read(MAX_FILE_BYTES + 1)
    finally:
        file.file.close()
    try:
        metadata = ImportMetadata(
            label=label, source_type=source_type, device_label=device_label
        )
    except ValidationError:
        raise imports.ImportProblem(
            422,
            "invalid_metadata",
            "Provide a nonblank label (1–100 characters), source recorded/synthetic and optional device label (up to 100 characters).",
        ) from None
    result = imports.create_preview(
        session, user["id"], content, metadata, file.filename
    )
    session.commit()
    response.headers["Cache-Control"] = "no-store"
    return result


@router.post(
    "/api/imports/{preview_id}/confirm",
    response_model=ConfirmResponse,
    status_code=201,
    dependencies=[Depends(require_origin)],
)
def confirm(
    preview_id: UUID, session: DB, response: Response, user=Depends(mutation_user)
):
    result = imports.confirm_preview(session, user["id"], preview_id)
    session.commit()
    response.status_code = 201 if result["outcome"] == "created" else 200
    response.headers["Cache-Control"] = "no-store"
    return result


@router.delete(
    "/api/imports/{preview_id}", status_code=204, dependencies=[Depends(require_origin)]
)
def discard(preview_id: UUID, session: DB, user=Depends(mutation_user)):
    if ImportRepository(session).discard(user["id"], preview_id) is None:
        imports.not_found()
    session.commit()


@router.get("/api/recordings/{recording_id}", response_model=RecordingResponse)
def recording(
    recording_id: UUID, session: DB, response: Response, user=Depends(current_user)
):
    response.headers["Cache-Control"] = "no-store"
    return imports.get_recording(session, user["id"], recording_id)


@router.get("/api/recordings", response_model=RecordingListResponse)
def history(
    session: DB,
    response: Response,
    limit: Annotated[int, Query(ge=1, le=100)] = 20,
    cursor: Annotated[str | None, Query(max_length=512)] = None,
    user=Depends(current_user),
):
    response.headers["Cache-Control"] = "no-store"
    return imports.list_recordings(session, user["id"], limit, cursor)
