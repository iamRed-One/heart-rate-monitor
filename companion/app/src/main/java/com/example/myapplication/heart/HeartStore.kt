package com.example.myapplication.heart

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import org.json.JSONObject
import java.util.UUID

data class HeartSample(val id: String, val recordingId: String, val timestamp: String, val bpm: Int)

/** Local recovery/outbox survives network loss. Account IDs scope every query. */
class HeartStore(context: Context): SQLiteOpenHelper(context, "heart-collection.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE samples(id TEXT PRIMARY KEY, owner TEXT NOT NULL, recording TEXT NOT NULL, timestamp TEXT NOT NULL, bpm INTEGER NOT NULL CHECK(bpm BETWEEN 1 AND 300), uploaded INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX samples_owner_time ON samples(owner,timestamp)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun add(owner: String, recording: String, timestamp: String, bpm: Int) {
        writableDatabase.insertOrThrow("samples", null, ContentValues().apply {
            put("id", UUID.randomUUID().toString()); put("owner", owner); put("recording", recording); put("timestamp", timestamp); put("bpm", bpm)
        })
    }
    fun rows(owner: String, recording: String? = null, pending: Boolean = false, limit: Int = 300): List<HeartSample> {
        val clauses = mutableListOf("owner=?"); val args = mutableListOf(owner)
        if (recording != null) { clauses += "recording=?"; args += recording }
        if (pending) clauses += "uploaded=0"
        return readableDatabase.query("samples", null, clauses.joinToString(" AND "), args.toTypedArray(), null, null, "timestamp ASC, rowid ASC", limit.toString()).use { c ->
            buildList { while(c.moveToNext()) add(HeartSample(c.getString(c.getColumnIndexOrThrow("id")), c.getString(c.getColumnIndexOrThrow("recording")), c.getString(c.getColumnIndexOrThrow("timestamp")), c.getInt(c.getColumnIndexOrThrow("bpm")))) }
        }
    }
    fun mark(id: String) { writableDatabase.update("samples", ContentValues().apply { put("uploaded",1) }, "id=?", arrayOf(id)) }
    fun count(owner: String, pending: Boolean = false): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM samples WHERE owner=?" + if(pending) " AND uploaded=0" else "", arrayOf(owner)).use { it.moveToFirst(); it.getInt(0) }
    fun csv(owner: String): ByteArray = ("timestamp,heart_rate\n" + rows(owner, limit=100000).joinToString("\n") { "${it.timestamp},${it.bpm}" } + "\n").toByteArray()
}
