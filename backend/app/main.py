"""Application entry point and process health endpoints."""

import logging

from fastapi import FastAPI, Response
from pydantic import BaseModel, ValidationError
from psycopg import Error as DatabaseError

from app.config import DatabaseSettings
from app.database import check_database


logger = logging.getLogger(__name__)


class StatusResponse(BaseModel):
    status: str


class ReadinessResponse(BaseModel):
    status: str
    database: str


app = FastAPI(
    title="Wearable Heart-Rate Data Viewer API",
    description="Recorded heart-rate data viewer. Initial API foundation.",
    version="0.1.0",
)


@app.get("/", response_model=StatusResponse, tags=["status"])
@app.get("/health", response_model=StatusResponse, tags=["status"])
def health() -> StatusResponse:
    """Confirm the API process responds; this does not check the database."""
    return StatusResponse(status="OK")


@app.get(
    "/ready",
    response_model=ReadinessResponse,
    responses={503: {"model": ReadinessResponse}},
    tags=["status"],
)
def readiness(response: Response) -> ReadinessResponse:
    """Check database connectivity; migration readiness will follow later."""
    response.headers["Cache-Control"] = "no-store"
    try:
        connected = check_database(DatabaseSettings())
    except (DatabaseError, ValidationError, OSError):
        # Exception details can contain credentials or database internals.
        logger.warning("Database readiness check failed")
        connected = False

    if not connected:
        response.status_code = 503
        return ReadinessResponse(status="unavailable", database="unavailable")
    return ReadinessResponse(status="OK", database="connected")
