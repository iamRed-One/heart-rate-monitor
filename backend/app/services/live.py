"""Bounded, ephemeral single-process sessions for a local companion showcase.

These are not recordings. A server restart closes them; no historic import is
ever treated as live, and neither pairing codes nor upload tokens are logged.
"""
from collections import deque
from datetime import UTC, datetime, timedelta
from hashlib import sha256
from secrets import compare_digest, token_hex, token_urlsafe
from threading import RLock
from uuid import uuid4

from app.services.auth import AuthError


class LiveSessions:
    def __init__(self):
        self.lock = RLock()
        self.sessions = {}
        self.attempts = {}

    def cleanup(self, now):
        self.sessions = {key: value for key, value in self.sessions.items() if value['expires_at'] > now}
        self.attempts = {key: value for key, value in self.attempts.items() if value[0] > now}

    def create(self, owner, now=None):
        now = now or datetime.now(UTC)
        with self.lock:
            self.cleanup(now)
            if len(self.sessions) >= 100:
                raise AuthError(429, 'live_capacity', 'Live showcase is busy. Try again later.')
            self.sessions = {key: value for key, value in self.sessions.items() if value['owner'] != str(owner)}
            session_id, code = str(uuid4()), token_hex(6).upper()
            session = dict(id=session_id, owner=str(owner), code_digest=sha256(code.encode()).hexdigest(),
                           pair_by=now + timedelta(minutes=10), expires_at=now + timedelta(minutes=30),
                           token_digest=None, device_label=None, device_id=None,
                           samples=deque(maxlen=300), last_received_at=None)
            self.sessions[session_id] = session
            return dict(id=session_id, pairing_code=code, pair_by=session['pair_by'], expires_at=session['expires_at'])

    def pair(self, code, device_id, device_label, address, now=None):
        now = now or datetime.now(UTC)
        with self.lock:
            self.cleanup(now)
            until, count = self.attempts.get(address, (now + timedelta(minutes=1), 0))
            if count >= 12 or (address not in self.attempts and len(self.attempts) >= 1024):
                raise AuthError(429, 'pairing_limited', 'Too many pairing attempts. Wait a minute.')
            self.attempts[address] = (until, count + 1)
            digest = sha256(code.strip().upper().encode()).hexdigest()
            session = next((s for s in self.sessions.values() if compare_digest(s['code_digest'], digest)
                            and s['pair_by'] > now and not s['token_digest']), None)
            if session is None:
                raise AuthError(401, 'pairing_failed', 'Pairing code is invalid, used or expired. Start a new showcase.')
            token = token_urlsafe(32)
            session.update(token_digest=sha256(token.encode()).hexdigest(), device_id=device_id, device_label=device_label)
            return dict(session_id=session['id'], upload_token=token, expires_at=session['expires_at'])

    def owned(self, session_id, owner, now):
        self.cleanup(now)
        session = self.sessions.get(str(session_id))
        if session is None or session['owner'] != str(owner):
            raise AuthError(404, 'live_not_found', 'Showcase ended or is unavailable. Start a new one.')
        return session

    def view(self, session_id, owner, now=None):
        now = now or datetime.now(UTC)
        with self.lock:
            session = self.owned(session_id, owner, now)
            last = session['last_received_at']
            status = 'waiting' if not session['token_digest'] else 'connected' if last is None else 'receiving' if (now-last).total_seconds() <= 10 else 'stale'
            return dict(id=session['id'], status=status, device_label=session['device_label'],
                        expires_at=session['expires_at'], last_received_at=last, samples=list(session['samples']),
                        source_type='recorded', persistence='ephemeral')

    def current(self, owner):
        now = datetime.now(UTC)
        with self.lock:
            self.cleanup(now)
            session = next((s for s in self.sessions.values() if s['owner'] == str(owner)), None)
            return self.view(session['id'], owner, now) if session else None

    def receive(self, session_id, token, sample, now=None):
        now = now or datetime.now(UTC)
        with self.lock:
            self.cleanup(now)
            session = self.sessions.get(str(session_id))
            digest = sha256(token.encode()).hexdigest()
            if session is None or not session['token_digest'] or not compare_digest(session['token_digest'], digest):
                raise AuthError(401, 'live_token_invalid', 'Showcase token is invalid or expired.')
            stamp = sample['timestamp']
            if stamp.tzinfo is None or not -30 <= (stamp-now).total_seconds() <= 10:
                raise AuthError(422, 'live_timestamp_invalid', 'Live readings must have a timezone and be captured now.')
            if any(str(s['sample_id']) == str(sample['sample_id']) for s in session['samples']):
                return {'accepted': False, 'duplicate': True}
            if session['last_received_at'] and (now-session['last_received_at']).total_seconds() < .1:
                raise AuthError(429, 'live_sample_limit', 'Send at most ten readings per second.')
            if session['samples'] and stamp < session['samples'][-1]['timestamp']:
                raise AuthError(422, 'live_out_of_order', 'Send readings in capture order.')
            session['samples'].append(dict(sample, received_at=now))
            session['last_received_at'] = now
            return {'accepted': True, 'duplicate': False}

    def stop(self, session_id, owner, now=None):
        with self.lock:
            self.owned(session_id, owner, now or datetime.now(UTC))
            del self.sessions[str(session_id)]


live_sessions = LiveSessions()
