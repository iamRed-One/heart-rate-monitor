"""Live pairing boundaries, expiry, ownership and honest freshness states."""
import unittest
from datetime import UTC, datetime, timedelta
from uuid import uuid4

from fastapi.testclient import TestClient
from app.main import app
from app.routes.auth import current_user
from app.services.auth import AuthError, digest
from app.services.live import LiveSessions, live_sessions


class LiveSessionTests(unittest.TestCase):
    def setUp(self):
        self.store = LiveSessions()
        self.now = datetime(2026, 11, 17, 9, tzinfo=UTC)
        self.created = self.store.create('owner', self.now)

    def pair(self):
        return self.store.pair(self.created['pairing_code'], 'watch', 'ET488', 'local', self.now)

    def sample(self, **changes):
        return dict(sample_id=uuid4(), timestamp=self.now, heart_rate=72, **changes)

    def test_pairing_is_one_use_and_scoped(self):
        pair = self.pair()
        with self.assertRaises(AuthError): self.pair()
        with self.assertRaises(AuthError): self.store.view(self.created['id'], 'other', self.now)
        with self.assertRaises(AuthError): self.store.receive(pair['session_id'], 'wrong', self.sample(), self.now)
        state = self.store.view(self.created['id'], 'owner', self.now)
        self.assertEqual(state['status'], 'connected')
        self.assertNotIn('token_digest', state)
        self.assertNotIn('pairing_code', state)

    def test_duplicate_freshness_timestamp_validation_and_stop(self):
        pair = self.pair()
        sample = self.sample()
        self.assertTrue(self.store.receive(pair['session_id'], pair['upload_token'], sample, self.now)['accepted'])
        self.assertTrue(self.store.receive(pair['session_id'], pair['upload_token'], sample, self.now)['duplicate'])
        self.assertEqual(self.store.view(pair['session_id'], 'owner', self.now)['status'], 'receiving')
        self.assertEqual(self.store.view(pair['session_id'], 'owner', self.now + timedelta(seconds=11))['status'], 'stale')
        for timestamp in [self.now - timedelta(minutes=1), self.now + timedelta(minutes=1), self.now.replace(tzinfo=None)]:
            bad = dict(sample_id=uuid4(), timestamp=timestamp, heart_rate=70)
            with self.assertRaises(AuthError): self.store.receive(pair['session_id'], pair['upload_token'], bad, self.now + timedelta(seconds=1))
        self.store.stop(pair['session_id'], 'owner', self.now)
        with self.assertRaises(AuthError): self.store.receive(pair['session_id'], pair['upload_token'], sample, self.now)

    def test_expiry_rate_limit_and_bounded_retention(self):
        with self.assertRaises(AuthError): self.store.pair(self.created['pairing_code'], 'w', 'ET488', 'expired', self.now + timedelta(minutes=11))
        for _ in range(12):
            with self.assertRaises(AuthError): self.store.pair('WRONG0000000', 'w', 'ET488', 'bad', self.now)
        with self.assertRaises(AuthError) as failure: self.store.pair(self.created['pairing_code'], 'w', 'ET488', 'bad', self.now)
        self.assertEqual(failure.exception.status, 429)
        pair = self.pair()
        for i in range(310):
            stamp = self.now + timedelta(seconds=i)
            self.store.receive(pair['session_id'], pair['upload_token'], dict(sample_id=uuid4(), timestamp=stamp, heart_rate=70), stamp)
        self.assertEqual(len(self.store.view(pair['session_id'], 'owner', stamp)['samples']), 300)
        with self.assertRaises(AuthError): self.store.view(pair['session_id'], 'owner', self.now + timedelta(minutes=31))


class LiveRouteTests(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(app)
        live_sessions.sessions.clear()
        live_sessions.attempts.clear()
        app.dependency_overrides[current_user] = lambda: {'id': 'route-owner', 'csrf_digest': digest('route-csrf')}
        self.headers = {'Origin': 'http://127.0.0.1:5173', 'X-CSRF-Token': 'route-csrf'}

    def tearDown(self):
        app.dependency_overrides.clear()
        live_sessions.sessions.clear()
        live_sessions.attempts.clear()

    def test_mutation_guards_pairing_ingest_validation_and_owner(self):
        self.assertEqual(self.client.post('/api/live/sessions').status_code, 403)
        self.assertEqual(self.client.post('/api/live/sessions', headers={'Origin': self.headers['Origin']}).status_code, 403)
        created = self.client.post('/api/live/sessions', headers=self.headers)
        self.assertEqual(created.status_code, 201)
        self.assertEqual(created.headers['cache-control'], 'no-store')
        session = created.json()
        pair = self.client.post('/api/live/pair', json={'code': session['pairing_code'], 'device_id': 'ET488', 'device_label': 'Watch'})
        self.assertEqual(pair.status_code, 200)
        url = '/api/live/sessions/' + session['id']
        sample = {'sample_id': str(uuid4()), 'timestamp': datetime.now(UTC).isoformat(), 'heart_rate': 72}
        self.assertEqual(self.client.post(url + '/readings', json=sample).status_code, 401)
        phone = {'Authorization': 'Bearer ' + pair.json()['upload_token']}
        self.assertEqual(self.client.post(url + '/readings', json=sample, headers=phone).status_code, 200)
        for bpm in [0, 301, 72.5, True, '72']:
            invalid = dict(sample, sample_id=str(uuid4()), heart_rate=bpm)
            self.assertEqual(self.client.post(url + '/readings', json=invalid, headers=phone).status_code, 422)
        self.assertEqual(self.client.get(url).json()['samples'][0]['heart_rate'], 72)
        app.dependency_overrides[current_user] = lambda: {'id': 'someone-else'}
        self.assertEqual(self.client.get(url).status_code, 404)
        app.dependency_overrides[current_user] = lambda: {'id': 'route-owner', 'csrf_digest': digest('route-csrf')}
        self.assertEqual(self.client.delete(url, headers=self.headers).status_code, 204)
        self.assertEqual(self.client.post(url + '/readings', json=sample, headers=phone).status_code, 401)
