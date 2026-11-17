package com.example.myapplication

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.core.Metric
import com.example.myapplication.core.Provenance
import com.example.myapplication.core.Quality
import com.example.myapplication.core.RecordId
import com.example.myapplication.core.Source
import com.example.myapplication.db.AppDatabase
import com.example.myapplication.db.RecordDao
import com.example.myapplication.db.ScalarSampleEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** M1 acceptance: records can be inserted, queried, and de-duplicated. */
@RunWith(AndroidJUnit4::class)
class RecordDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: RecordDao

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        dao = db.recordDao()
    }

    @After fun tearDown() = db.close()

    private fun prov(ts: Long) = Provenance(
        participantId = "p1", deviceId = "ring-1", deviceModel = "Q520", firmware = "1.0",
        tsUtc = "2026-10-22T10:00:00.000Z", tzOffsetMin = 60, deviceTsRaw = ts,
        source = Source.HISTORY, quality = Quality.OK,
        ingestedAt = "2026-10-22T10:00:01.000Z", appVersion = "1.0", sdkVersion = "yc-x",
    )

    private fun hr(ts: Long, bpm: Double) =
        ScalarSampleEntity(RecordId.of("ring-1", Metric.HEART_RATE, ts), prov(ts), Metric.HEART_RATE, bpm, "bpm")

    @Test fun insert_query_dedup() = runBlocking {
        val ts = 1_700_000_000_000L
        val rowid = dao.insertScalar(hr(ts, 72.0))
        assertTrue("first insert should persist", rowid >= 0)

        // Re-insert the SAME datum (same deterministic recordId) — must be ignored, no duplicate.
        val again = dao.insertScalar(hr(ts, 72.0))
        assertEquals("re-insert of same record is ignored", -1L, again)
        assertEquals("exactly one HR record", 1, dao.scalarCount("ring-1", Metric.HEART_RATE))

        // A different timestamp is a genuinely new record.
        dao.insertScalar(hr(ts + 1000, 73.0))
        assertEquals(2, dao.scalarCount("ring-1", Metric.HEART_RATE))

        // Query-back returns what we stored, in time order.
        val rows = dao.scalars("ring-1", Metric.HEART_RATE)
        assertEquals(2, rows.size)
        assertNotEquals(rows[0].recordId, rows[1].recordId)
        assertEquals(72.0, rows[0].value, 0.0001)
    }

    @Test fun batchInsert_dedupsWithinBatch() = runBlocking {
        val ts = 1_700_000_100_000L
        val dupBatch = listOf(hr(ts, 60.0), hr(ts, 60.0), hr(ts + 1000, 61.0))
        dao.insertScalars(dupBatch)
        assertEquals("two unique of three", 2, dao.scalarCount("ring-1", Metric.HEART_RATE))
    }
}
