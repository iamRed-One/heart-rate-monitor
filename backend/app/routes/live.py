"""Cookie-authenticated viewer; one-use pairing and write-only companion token."""
from datetime import datetime
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Header, Request, Response
from pydantic import BaseModel, ConfigDict, Field

from app.routes.auth import current_user, require_origin
from app.routes.auth import DB
from app.routes.imports import mutation_user
from app.services.auth import AuthError
from app.services.live import live_sessions
from app.models.tables import recording_sessions, heart_rate_readings
from sqlalchemy.dialects.postgresql import insert
import sqlalchemy as sa
from hashlib import sha256

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


class StartRequest(BaseModel):
    model_config = ConfigDict(extra='forbid')
    save_recording: bool = False


@router.post('/sessions', status_code=201, dependencies=[Depends(require_origin)])
def create(session: DB, body: StartRequest = StartRequest(), user=Depends(mutation_user)):
    result = live_sessions.create(user['id'], save_recording=body.save_recording)
    if body.save_recording:
        try:
            session.execute(recording_sessions.insert().values(
                id=UUID(result['id']), user_id=user['id'], source_type='recorded',
                label='Live recording ' + datetime.now().strftime('%Y-%m-%d %H:%M'),
                original_filename='live-heart-rate', file_digest=sha256(('live:'+result['id']).encode()).hexdigest()))
            session.commit()
        except Exception:
            with live_sessions.lock:
                live_sessions.sessions.pop(result['id'], None)
            raise
    return result


@router.post('/pair')
def pair(body: PairRequest, request: Request, session: DB):
    result = live_sessions.pair(body.code, body.device_id, body.device_label, request.client.host if request.client else 'unknown')
    state = live_sessions.sessions[result['session_id']]
    if state['save_recording']:
        session.execute(recording_sessions.update().where(recording_sessions.c.id == UUID(result['session_id'])).values(device_label=body.device_label))
        session.commit()
    return result


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
def receive(session_id: UUID, body: LiveReading, session: DB, authorization: Annotated[str | None, Header()] = None):
    if not authorization or not authorization.startswith('Bearer '):
        raise AuthError(401, 'live_token_required', 'A paired companion token is required.')
    def save(state, sample):
        exists = session.scalar(sa.select(recording_sessions.c.id).where(
            recording_sessions.c.id == session_id, recording_sessions.c.user_id == UUID(state['owner'])).with_for_update())
        if exists is None:
            raise AuthError(410, 'recording_deleted', 'Recording was deleted. Start a new one.')
        session.execute(insert(heart_rate_readings).values(recording_id=session_id, measured_at=sample['timestamp'], bpm=sample['heart_rate'])
            .on_conflict_do_nothing(constraint='uq_reading_recording_time'))
        session.commit()
    return live_sessions.receive(session_id, authorization[7:], body.model_dump(), writer=save)


def bearer(authorization):
    if not authorization or not authorization.startswith('Bearer '):
        raise AuthError(401, 'live_token_required', 'A paired companion token is required.')
    return authorization[7:]


@router.get('/sessions/{session_id}/control')
def control(session_id: UUID, authorization: Annotated[str | None, Header()] = None):
    return live_sessions.control(session_id, bearer(authorization))


@router.post('/sessions/{session_id}/finish')
def finish(session_id: UUID, authorization: Annotated[str | None, Header()] = None):
    return live_sessions.control(session_id, bearer(authorization), stop=True)
