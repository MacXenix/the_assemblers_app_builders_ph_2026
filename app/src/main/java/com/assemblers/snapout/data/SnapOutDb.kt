package com.assemblers.snapout.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FeedSession(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long,
    val app: String,
    val swipes: Int,
    val distanceMeters: Double,
    val peakScore: Int,
    val intervened: Boolean,
    val taps: Int = 0,
    val avgDwellMs: Long = 0,
    val late: Boolean = false,
    val dark: Boolean = false,
    val lying: Boolean = false,
)

data class Intervention(
    val id: Long,
    val at: Long,
    val app: String,
    val score: Int,
    val source: String,
    val ttftMs: Long?,
    val text: String,
    val outcome: String?,
)

/** 100% local history. No sync, no network. */
class SnapOutDb(context: Context) : SQLiteOpenHelper(context, "snapout.db", null, 2) {

    private val _version = MutableStateFlow(0)
    /** Bumped on every write so the UI can re-query. */
    val version: StateFlow<Int> = _version.asStateFlow()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE feed_session(id INTEGER PRIMARY KEY AUTOINCREMENT, started_at INTEGER, ended_at INTEGER, " +
                "app TEXT, swipes INTEGER, distance_m REAL, peak_score INTEGER, intervened INTEGER)",
        )
        addV2Columns(db)
        db.execSQL(
            "CREATE TABLE intervention(id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER, app TEXT, score INTEGER, " +
                "source TEXT, ttft_ms INTEGER, text TEXT, outcome TEXT)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) addV2Columns(db)
    }

    private fun addV2Columns(db: SQLiteDatabase) {
        listOf("taps INTEGER", "dwell_ms INTEGER", "late INTEGER", "dark INTEGER", "lying INTEGER").forEach {
            db.execSQL("ALTER TABLE feed_session ADD COLUMN $it DEFAULT 0")
        }
    }

    fun insertSession(s: FeedSession) {
        writableDatabase.insert("feed_session", null, ContentValues().apply {
            put("started_at", s.startedAt); put("ended_at", s.endedAt); put("app", s.app)
            put("swipes", s.swipes); put("distance_m", s.distanceMeters); put("peak_score", s.peakScore)
            put("intervened", if (s.intervened) 1 else 0)
            put("taps", s.taps); put("dwell_ms", s.avgDwellMs)
            put("late", if (s.late) 1 else 0); put("dark", if (s.dark) 1 else 0); put("lying", if (s.lying) 1 else 0)
        })
        _version.value++
    }

    fun insertIntervention(i: Intervention): Long {
        val id = writableDatabase.insert("intervention", null, ContentValues().apply {
            put("at", i.at); put("app", i.app); put("score", i.score); put("source", i.source)
            i.ttftMs?.let { put("ttft_ms", it) }; put("text", i.text); put("outcome", i.outcome)
        })
        _version.value++
        return id
    }

    fun setOutcome(id: Long, outcome: String) {
        writableDatabase.update("intervention", ContentValues().apply { put("outcome", outcome) }, "id=?", arrayOf(id.toString()))
        _version.value++
    }

    fun updateMessage(id: Long, text: String, source: String, ttftMs: Long?) {
        writableDatabase.update("intervention", ContentValues().apply {
            put("text", text); put("source", source); ttftMs?.let { put("ttft_ms", it) }
        }, "id=?", arrayOf(id.toString()))
        _version.value++
    }

    fun insertSample(sessions: List<FeedSession>, interventions: List<Intervention>) {
        sessions.forEach { insertSession(it) }
        interventions.forEach { insertIntervention(it) }
    }

    fun sessions(limit: Int = 100): List<FeedSession> = readableDatabase.rawQuery(
        "SELECT id, started_at, ended_at, app, swipes, distance_m, peak_score, intervened, taps, dwell_ms, late, dark, lying " +
            "FROM feed_session ORDER BY started_at DESC LIMIT $limit",
        null,
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                FeedSession(
                    c.getLong(0), c.getLong(1), c.getLong(2), c.getString(3), c.getInt(4), c.getDouble(5), c.getInt(6), c.getInt(7) == 1,
                    c.getInt(8), c.getLong(9), c.getInt(10) == 1, c.getInt(11) == 1, c.getInt(12) == 1,
                ),
            )
        }
    }

    fun interventions(limit: Int = 100): List<Intervention> = readableDatabase.rawQuery(
        "SELECT id, at, app, score, source, ttft_ms, text, outcome FROM intervention ORDER BY at DESC LIMIT $limit",
        null,
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                Intervention(
                    c.getLong(0), c.getLong(1), c.getString(2), c.getInt(3), c.getString(4),
                    if (c.isNull(5)) null else c.getLong(5), c.getString(6), c.getString(7),
                ),
            )
        }
    }

    fun interventionsSince(since: Long): Int = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM intervention WHERE at >= ?", arrayOf(since.toString()),
    ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun recentTexts(n: Int): List<String> = interventions(n * 3).map { it.text }.filter { it.isNotBlank() }.take(n).reversed()

    fun rowCounts(): Pair<Int, Int> = sessions(100000).size to interventions(100000).size

    fun purge() {
        writableDatabase.execSQL("DELETE FROM feed_session")
        writableDatabase.execSQL("DELETE FROM intervention")
        _version.value++
    }
}
