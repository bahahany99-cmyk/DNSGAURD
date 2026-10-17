package com.dnsguardian.app.security

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.dnsguardian.app.Constants
import org.json.JSONObject
import java.util.UUID

/**
 * Append-only, tamper-evident security event store (SQLite).
 *
 * - No DELETE / UPDATE / CLEAR API is exposed (requirement: no in-app history deletion).
 * - Each record carries prevHash + hash forming a SHA-256 chain; verifyChain() detects
 *   gaps or tampering after the last verified point. It cannot detect a full DB wipe
 *   (acknowledged out-of-scope limitation).
 */
class EventStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, Constants.DB_NAME, null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${Constants.TABLE_EVENTS} (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                tsMillis INTEGER NOT NULL,
                elapsedRealtimeMs INTEGER NOT NULL,
                source TEXT,
                component TEXT,
                responseAttempted TEXT,
                responseVerified INTEGER NOT NULL DEFAULT 0,
                prevHash TEXT NOT NULL,
                hash TEXT NOT NULL,
                diagnostic TEXT
            )
            """
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema stable at v1.
    }

    private fun lastHash(): String {
        val db = readableDatabase
        db.query(
            Constants.TABLE_EVENTS,
            arrayOf("hash"),
            null, null, null, null,
            "tsMillis DESC", "1"
        ).use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        // zero hash
        return "0".repeat(64)
    }

    fun addEvent(
        type: String,
        tsMillis: Long,
        elapsedRealtimeMs: Long,
        source: String? = null,
        component: String? = null,
        responseAttempted: String? = null,
        responseVerified: Boolean = false,
        diagnostic: String? = null,
        id: String? = null
    ): Map<String, Any?> {
        val prevHash = lastHash()
        val record = linkedMapOf<String, Any?>(
            "id" to (id ?: UUID.randomUUID().toString()),
            "type" to type,
            "tsMillis" to tsMillis,
            "elapsedRealtimeMs" to elapsedRealtimeMs,
            "source" to source,
            "component" to component,
            "responseAttempted" to responseAttempted,
            // Store and hash the same integer representation read back from SQLite.
            "responseVerified" to if (responseVerified) 1 else 0,
            "prevHash" to prevHash,
            "diagnostic" to diagnostic
        )
        // Canonical JSON (stable key order, no hash field) -> SHA-256
        val canonical = canonicalJson(record)
        val hash = Crypto.sha256("$prevHash:$canonical")
        record["hash"] = hash

        val cv = ContentValues().apply {
            put("id", record["id"] as String)
            put("type", record["type"] as String)
            put("tsMillis", record["tsMillis"] as Long)
            put("elapsedRealtimeMs", record["elapsedRealtimeMs"] as Long)
            put("source", record["source"] as? String)
            put("component", record["component"] as? String)
            put("responseAttempted", record["responseAttempted"] as? String)
            put("responseVerified", if (responseVerified) 1 else 0)
            put("prevHash", prevHash)
            put("hash", hash)
            put("diagnostic", record["diagnostic"] as? String)
        }
        writableDatabase.insert(Constants.TABLE_EVENTS, null, cv)
        return record
    }

    fun getAll(): List<Map<String, Any?>> {
        val out = mutableListOf<Map<String, Any?>>()
        readableDatabase.query(
            Constants.TABLE_EVENTS, null, null, null, null, null, "tsMillis DESC"
        ).use { c ->
            while (c.moveToNext()) {
                out.add(cursorToMap(c))
            }
        }
        return out
    }

    fun getByType(type: String): List<Map<String, Any?>> = getAll().filter { it["type"] == type }

    /** Returns -1 if intact, otherwise the index (in ascending ts order) of the first bad record. */
    fun verifyChain(): Int {
        val rows = getAll().reversed() // ascending by ts
        var prev = "0".repeat(64)
        var index = 0
        for (r in rows) {
            val record = linkedMapOf<String, Any?>(
                "id" to r["id"],
                "type" to r["type"],
                "tsMillis" to r["tsMillis"],
                "elapsedRealtimeMs" to r["elapsedRealtimeMs"],
                "source" to r["source"],
                "component" to r["component"],
                "responseAttempted" to r["responseAttempted"],
                "responseVerified" to r["responseVerified"],
                "prevHash" to r["prevHash"],
                "diagnostic" to r["diagnostic"]
            )
            val storedHash = r["hash"] as? String ?: return index
            val expected = Crypto.sha256("$prev:${canonicalJson(record)}")
            if (expected != storedHash) {
                // Compatibility for native events written before responseVerified
                // was hashed using SQLite's integer representation.
                val legacyRecord = record.toMutableMap().apply {
                    val value = this["responseVerified"] as? Number
                    this["responseVerified"] = value?.toInt() == 1
                }
                val legacyExpected = Crypto.sha256("$prev:${canonicalJson(legacyRecord)}")
                if (legacyExpected != storedHash) return index
            }
            prev = storedHash
            index++
        }
        return -1
    }

    fun count(): Int {
        readableDatabase.query(
            Constants.TABLE_EVENTS, arrayOf("COUNT(*) as c"),
            null, null, null, null, null
        ).use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return 0
    }

    private fun cursorToMap(c: android.database.Cursor): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        for (i in 0 until c.columnCount) {
            map[c.getColumnName(i)] = when (c.getType(i)) {
                android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                android.database.Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                android.database.Cursor.FIELD_TYPE_NULL -> null
                else -> c.getString(i)
            }
        }
        return map
    }

    private fun canonicalJson(record: Map<String, Any?>): String {
        val sorted = record.filterKeys { it != "hash" }.toSortedMap()
        val obj = JSONObject()
        for ((k, v) in sorted) {
            when (v) {
                is Boolean -> obj.put(k, v)
                is Number -> obj.put(k, v)
                null -> obj.put(k, JSONObject.NULL)
                else -> obj.put(k, v.toString())
            }
        }
        return obj.toString()
    }
}
