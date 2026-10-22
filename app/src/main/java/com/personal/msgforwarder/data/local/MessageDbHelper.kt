package com.personal.msgforwarder.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.personal.msgforwarder.data.MessageData

/**
 * SQLite helper for local message storage and offline queueing.
 * Guarantees zero message loss when offline or during transient network errors.
 */
class MessageDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "msg_backup.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_MESSAGES = "messages"
        const val COLUMN_ID = "id"
        const val COLUMN_SENDER = "sender"
        const val COLUMN_BODY = "body"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_IS_SYNCED = "is_synced"
        const val COLUMN_SYNCED_AT = "synced_at"

        @Volatile
        private var instance: MessageDbHelper? = null

        fun getInstance(context: Context): MessageDbHelper {
            return instance ?: synchronized(this) {
                instance ?: MessageDbHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_MESSAGES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SENDER TEXT NOT NULL,
                $COLUMN_BODY TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_IS_SYNCED INTEGER NOT NULL DEFAULT 0,
                $COLUMN_SYNCED_AT INTEGER
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MESSAGES")
        onCreate(db)
    }

    fun insertMessage(sender: String, body: String, timestamp: Long): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SENDER, sender)
            put(COLUMN_BODY, body)
            put(COLUMN_TIMESTAMP, timestamp)
            put(COLUMN_IS_SYNCED, 0)
        }
        return db.insert(TABLE_MESSAGES, null, values)
    }

    fun deleteMessage(id: Long) {
        val db = writableDatabase
        db.delete(TABLE_MESSAGES, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun deleteBatch(ids: List<Long>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in ids) {
                db.delete(TABLE_MESSAGES, "$COLUMN_ID = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun markSynced(id: Long, syncedAt: Long = System.currentTimeMillis()) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_IS_SYNCED, 1)
            put(COLUMN_SYNCED_AT, syncedAt)
        }
        db.update(TABLE_MESSAGES, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun markBatchSynced(ids: List<Long>, syncedAt: Long = System.currentTimeMillis()) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put(COLUMN_IS_SYNCED, 1)
                put(COLUMN_SYNCED_AT, syncedAt)
            }
            for (id in ids) {
                db.update(TABLE_MESSAGES, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getPendingMessages(): List<MessageData> {
        val list = mutableListOf<MessageData>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MESSAGES,
            null,
            "$COLUMN_IS_SYNCED = 0",
            null,
            null,
            null,
            "$COLUMN_TIMESTAMP ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    MessageData(
                        id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                        sender = it.getString(it.getColumnIndexOrThrow(COLUMN_SENDER)),
                        body = it.getString(it.getColumnIndexOrThrow(COLUMN_BODY)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                        isSynced = false,
                        syncedAt = null
                    )
                )
            }
        }
        return list
    }

    fun getRecentMessages(limit: Int = 50): List<MessageData> {
        val list = mutableListOf<MessageData>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MESSAGES,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_TIMESTAMP DESC",
            limit.toString()
        )
        cursor.use {
            while (it.moveToNext()) {
                val syncedVal = it.getInt(it.getColumnIndexOrThrow(COLUMN_IS_SYNCED)) == 1
                val syncedAtCol = it.getColumnIndexOrThrow(COLUMN_SYNCED_AT)
                val syncedAt = if (it.isNull(syncedAtCol)) null else it.getLong(syncedAtCol)
                list.add(
                    MessageData(
                        id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                        sender = it.getString(it.getColumnIndexOrThrow(COLUMN_SENDER)),
                        body = it.getString(it.getColumnIndexOrThrow(COLUMN_BODY)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                        isSynced = syncedVal,
                        syncedAt = syncedAt
                    )
                )
            }
        }
        return list
    }

    fun getPendingCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_MESSAGES WHERE $COLUMN_IS_SYNCED = 0", null)
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun getSyncedCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_MESSAGES WHERE $COLUMN_IS_SYNCED = 1", null)
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun clearAll() {
        writableDatabase.delete(TABLE_MESSAGES, null, null)
    }
}
