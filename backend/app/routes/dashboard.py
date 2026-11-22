"""Private dashboard queries and owner-only recording management."""

from datetime import date
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Query, Response

from app.repositories.dashboard import delete_recording, rename_recording
from app.routes.auth import DB, current_user, require_origin
from app.routes.imports import mutation_user
from app.schemas.dashboard import (
    ChartResponse,
    ReadingFilters,
    ReadingsResponse,
    RenameRequest,
    SummaryResponse,
)
from app.schemas.imports import RecordingResponse
from app.services import dashboard
from app.services.imports import get_recording, not_found

router = APIRouter(tags=["dashboard"])


def reading_filters(
    recording_id: UUID | None = None,
    start_date: date | None = None,
    end_date: date | None = None,
) -> ReadingFilters:
    result = ReadingFilters(
        recording_id=recording_id, start_date=start_date, end_date=end_date
    )
    dashboard.validate_filters(result)
    return result


Filters = Annotated[ReadingFilters, Depends(reading_filters)]


def snapshot(session):
    # Authentication already used a read-only transaction. Start a stable snapshot
    # so stats and points cannot disagree during concurrent imports/deletion.
    session.commit()
    session.connection(execution_options={"isolation_level": "REPEATABLE READ"})


@router.get("/api/readings", response_model=ReadingsResponse)
def readings(
    session: DB,
    response: Response,
    filters: Filters,
    limit: Annotated[int, Query(ge=1, le=100)] = 100,
    cursor: Annotated[str | None, Query(max_length=1024)] = None,
    user=Depends(current_user),
):
    snapshot(session)
    response.headers["Cache-Control"] = "no-store"
    return dashboard.get_readings(session, user["id"], filters, limit, cursor)


@router.get("/api/summary", response_model=SummaryResponse)
def summary(
    session: DB, response: Response, filters: Filters, user=Depends(current_user)
):
    snapshot(session)
    response.headers["Cache-Control"] = "no-store"
    return dashboard.get_summary(session, user["id"], filters)


@router.get("/api/chart", response_model=ChartResponse)
def chart(
    session: DB, response: Response, filters: Filters, user=Depends(current_user)
):
    snapshot(session)
    response.headers["Cache-Control"] = "no-store"
    return dashboard.get_chart(session, user["id"], filters)


@router.patch(
    "/api/recordings/{recording_id}",
    response_model=RecordingResponse,
    dependencies=[Depends(require_origin)],
)
def rename(
    recording_id: UUID,
    body: RenameRequest,
    session: DB,
    response: Response,
    user=Depends(mutation_user),
):
    if rename_recording(session, user["id"], recording_id, body.label) is None:
        not_found()
    result = get_recording(session, user["id"], recording_id)
    session.commit()
    response.headers["Cache-Control"] = "no-store"
    return result


@router.delete(
    "/api/recordings/{recording_id}",
    status_code=204,
    dependencies=[Depends(require_origin)],
)
def delete(
    recording_id: UUID, session: DB, response: Response, user=Depends(mutation_user)
):
    if delete_recording(session, user["id"], recording_id) is None:
        not_found()
    session.commit()
    response.headers["Cache-Control"] = "no-store"
