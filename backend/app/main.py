"""Application entry point and process health endpoints."""

import logging
import asyncio
from contextlib import asynccontextmanager, suppress

from fastapi import FastAPI, Request, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ValidationError
from psycopg import Error as DatabaseError
from sqlalchemy.exc import SQLAlchemyError

from app.config import DatabaseSettings
from app.database import check_database
from app.routes.auth import router as auth_router
from app.services.auth import AuthError
from app.routes.imports import router as imports_router
from app.services.imports import ImportProblem
from app.parsers.csv import CsvValidationError
from app.upload_limits import UploadLimitMiddleware
from app.maintenance import run_cleanup
from app.routes.dashboard import router as dashboard_router
from app.routes.live import router as live_router


logger = logging.getLogger(__name__)


class StatusResponse(BaseModel):
    status: str


class ReadinessResponse(BaseModel):
    status: str
    database: str


@asynccontextmanager
async def lifespan(app):
    async def cleanup_loop():
        while True:
            try:
                await asyncio.to_thread(run_cleanup)
            except (SQLAlchemyError, ValidationError, OSError):
                logger.warning("Scheduled cleanup could not access the database")
            await asyncio.sleep(300)

    task = asyncio.create_task(cleanup_loop())
    try:
        yield
    finally:
        task.cancel()
        with suppress(asyncio.CancelledError):
            await task


app = FastAPI(
    title="Wearable Heart-Rate Data Viewer API",
    description="Recorded heart-rate data viewer. Initial API foundation.",
    version="0.1.0",
    lifespan=lifespan,
)
app.include_router(live_router)
app.include_router(auth_router)
app.include_router(imports_router)
app.include_router(dashboard_router)
app.add_middleware(UploadLimitMiddleware)


@app.exception_handler(ImportProblem)
async def import_error_handler(request: Request, error: ImportProblem):
    return JSONResponse(
        status_code=error.status,
        content={"code": error.code, "message": error.message},
        headers={"Cache-Control": "no-store"},
    )


@app.exception_handler(CsvValidationError)
async def csv_error_handler(request: Request, error: CsvValidationError):
    return JSONResponse(
        status_code=error.status,
        content={
            "code": "csv_validation_failed",
            "message": "Correct the file and upload it again.",
            "total_errors": error.total_errors,
            "errors": error.errors,
        },
        headers={"Cache-Control": "no-store"},
    )


@app.exception_handler(AuthError)
async def auth_error_handler(request: Request, error: AuthError):
    headers = {"Cache-Control": "no-store"}
    if error.retry_after:
        headers["Retry-After"] = str(error.retry_after)
    return JSONResponse(
        status_code=error.status,
        content={"code": error.code, "message": error.message},
        headers=headers,
    )


@app.exception_handler(RequestValidationError)
async def validation_error_handler(request: Request, error: RequestValidationError):
    # FastAPI's default includes input values; never echo a submitted password.
    errors = [
        {"field": list(item["loc"]), "type": item["type"], "message": item["msg"]}
        for item in error.errors()
    ]
    return JSONResponse(
        status_code=422,
        content={"code": "validation_failed", "errors": errors},
        headers={"Cache-Control": "no-store"},
    )


@app.exception_handler(SQLAlchemyError)
async def database_error_handler(request: Request, error: SQLAlchemyError):
    logger.warning("Database operation failed")
    return JSONResponse(
        status_code=503,
        content={
            "code": "database_unavailable",
            "message": "Database unavailable. Try again later.",
        },
        headers={"Cache-Control": "no-store"},
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
    """Check database connectivity and the expected schema revision."""
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
