"""Cookie-authenticated viewer; one-use pairing and write-only companion token."""
from datetime import datetime
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Header, Request, Response
from pydantic import BaseModel, ConfigDict, Field

from app.routes.auth import current_user, require_origin
from app.routes.imports import mutation_user
from app.services.auth import AuthError
from app.services.live import live_sessions

def no_cache(response: Response):
    response.headers['Cache-Control'] = 'no-store'


router = APIRouter(prefix='/api/live', tags=['live showcase'], dependencies=[Depends(no_cache)])


class PairRequest(BaseModel):
    model_config = ConfigDict(extra='forbid', str_strip_whitespace=True)
    code: str = Field(min_length=12, max_length=12)
    device_id: str = Field(min_length=1, max_length=100)
    device_label: str = Field(min_length=1, max_length=100)


class LiveReading(BaseModel):
    model_config = ConfigDict(extra='forbid')
    sample_id: UUID
    timestamp: datetime
    heart_rate: int = Field(strict=True, ge=1, le=300)


@router.post('/sessions', status_code=201, dependencies=[Depends(require_origin)])
def create(user=Depends(mutation_user)):
    return live_sessions.create(user['id'])


@router.post('/pair')
def pair(body: PairRequest, request: Request):
    return live_sessions.pair(body.code, body.device_id, body.device_label, request.client.host if request.client else 'unknown')


@router.get('/sessions/{session_id}')
def view(session_id: UUID, user=Depends(current_user)):
    return live_sessions.view(session_id, user['id'])


@router.get('/current')
def current(user=Depends(current_user)):
    return live_sessions.current(user['id'])


@router.delete('/sessions/{session_id}', status_code=204, dependencies=[Depends(require_origin)])
def stop(session_id: UUID, user=Depends(mutation_user)):
    live_sessions.stop(session_id, user['id'])


@router.post('/sessions/{session_id}/readings')
def receive(session_id: UUID, body: LiveReading, authorization: Annotated[str | None, Header()] = None):
    if not authorization or not authorization.startswith('Bearer '):
        raise AuthError(401, 'live_token_required', 'A paired companion token is required.')
    return live_sessions.receive(session_id, authorization[7:], body.model_dump())
