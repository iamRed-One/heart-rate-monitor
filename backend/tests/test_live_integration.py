"""Durable live collection against the isolated test database."""
import os
import unittest
from datetime import UTC, datetime, timedelta
from uuid import uuid4
import sqlalchemy as sa
import test_import_integration as fixtures
from app.models.tables import heart_rate_readings
from app.services.live import live_sessions

@unittest.skipUnless(os.getenv('HR_RUN_DB_TESTS') == '1', 'Requires isolated test DB')
class PersistentLiveTests(unittest.TestCase):
    setUpClass = classmethod(fixtures.ImportIntegrationTests.setUpClass.__func__)
    tearDownClass = classmethod(fixtures.ImportIntegrationTests.tearDownClass.__func__)
    setUp = fixtures.ImportIntegrationTests.setUp
    tearDown = fixtures.ImportIntegrationTests.tearDown

    def test_saved_samples_retry_stop_and_deleted_recording(self):
        live_sessions.sessions.clear()
        start=self.client.post('/api/live/sessions', json={'save_recording':True}, headers=self.headers)
        self.assertEqual(start.status_code,201,start.text)
        session=start.json()['id']
        pair=self.client.post('/api/live/pair',json={'code':start.json()['pairing_code'],'device_id':'test-watch','device_label':'ET488'})
        self.assertEqual(pair.status_code,200,pair.text)
        token={'Authorization':'Bearer '+pair.json()['upload_token']}
        sample={'sample_id':str(uuid4()),'timestamp':(datetime.now(UTC)-timedelta(minutes=2)).isoformat(),'heart_rate':72}
        url=f'/api/live/sessions/{session}/readings'
        self.assertEqual(self.client.post(url,json=sample,headers=token).status_code,200)
        self.assertTrue(self.client.post(url,json=sample,headers=token).json()['duplicate'])
        with self.engine.connect() as conn:
            self.assertEqual(conn.scalar(sa.select(sa.func.count()).select_from(heart_rate_readings)),1)
        state=self.client.get(f'/api/live/sessions/{session}').json()
        self.assertEqual(state['persistence'],'postgresql')
        self.assertEqual(state['status'],'stale')
        self.assertEqual(self.client.delete(f'/api/live/sessions/{session}',headers=self.headers).status_code,204)
        self.assertFalse(self.client.get(f'/api/live/sessions/{session}/control',headers=token).json()['active'])
        self.assertIsNone(self.client.get('/api/live/current').json())
        sample.update(sample_id=str(uuid4()),timestamp=(datetime.now(UTC)+timedelta(seconds=1)).isoformat())
        self.assertEqual(self.client.post(url,json=sample,headers=token).status_code,409)
        self.assertEqual(self.client.delete(f'/api/recordings/{session}',headers=self.headers).status_code,204)
        sample.update(sample_id=str(uuid4()),timestamp=(datetime.now(UTC)-timedelta(minutes=1)).isoformat())
        self.assertEqual(self.client.post(url,json=sample,headers=token).status_code,410)
