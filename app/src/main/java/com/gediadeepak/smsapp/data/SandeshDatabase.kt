package com.gediadeepak.smsapp.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.gediadeepak.smsapp.core.*

data class ThreadMetadata(val starred: Boolean = false, val archived: Boolean = false, val alias: String = "", val spam: Boolean = false, val pinned: Boolean = false, val vendor: String = "")
data class PendingSms(val key: String, val address: String, val text: String, val timestamp: Long, val subscriptionId: Int?)

/** App metadata only: Android's SMS provider remains the source of truth for messages. */
class SandeshDatabase private constructor(context: Context) : SQLiteOpenHelper(context, "sandesh.db", null, 4) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE thread_metadata (thread_id TEXT PRIMARY KEY, starred INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0, alias TEXT NOT NULL DEFAULT '', spam INTEGER NOT NULL DEFAULT 0, pinned INTEGER NOT NULL DEFAULT 0, vendor TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE send_parts (sms_id TEXT NOT NULL, part INTEGER NOT NULL, sent INTEGER NOT NULL DEFAULT 0, delivered INTEGER NOT NULL DEFAULT 0, error INTEGER, PRIMARY KEY(sms_id, part))")
        db.execSQL("CREATE TABLE pending_mms (id INTEGER PRIMARY KEY AUTOINCREMENT, received_at INTEGER NOT NULL, subscription_id INTEGER, pdu BLOB NOT NULL)")
        createPendingTable(db)
        createBlockedTable(db)
    }
    private fun createPendingTable(db: SQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS pending_sms (receipt_key TEXT PRIMARY KEY, address TEXT NOT NULL, body TEXT NOT NULL, stamp INTEGER NOT NULL, subscription_id INTEGER)") }
    private fun createBlockedTable(db: SQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS blocked_senders (sender_key TEXT PRIMARY KEY, address TEXT NOT NULL, added_at INTEGER NOT NULL)") }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 4) { db.execSQL("ALTER TABLE thread_metadata ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0"); db.execSQL("ALTER TABLE thread_metadata ADD COLUMN vendor TEXT NOT NULL DEFAULT ''") }
        if (oldVersion < 2) createPendingTable(db)
        if (oldVersion < 3) { db.execSQL("ALTER TABLE thread_metadata ADD COLUMN spam INTEGER NOT NULL DEFAULT 0"); createBlockedTable(db) }
    }
    fun blockedSenders(): Map<String, String> = buildMap {
        readableDatabase.query("blocked_senders", arrayOf("sender_key", "address"), null, null, null, null, "added_at DESC").use { c -> while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
    }
    fun isSenderBlocked(address: String): Boolean = readableDatabase.query("blocked_senders", arrayOf("sender_key"), "sender_key=?", arrayOf(spamSenderKey(address)), null, null, null).use { it.moveToFirst() }
    @Synchronized fun setSenderBlocked(address: String, blocked: Boolean) {
        require(address.trim().isNotEmpty() && address.length <= 512) { "Invalid sender." }
        val key = spamSenderKey(address)
        if (blocked) writableDatabase.insertWithOnConflict("blocked_senders", null, ContentValues().apply {
            put("sender_key", key); put("address", address.trim()); put("added_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_IGNORE)
        else writableDatabase.delete("blocked_senders", "sender_key=?", arrayOf(key))
    }
    @Synchronized fun changeBlockedSender(address: String, blocked: Boolean, threadIds: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            setSenderBlocked(address, blocked)
            threadIds.forEach { id -> updateMetadata(id) { it.copy(spam = false, archived = false) } }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun retainIncoming(sms: PendingSms) { writableDatabase.insertWithOnConflict("pending_sms", null, ContentValues().apply {
        put("receipt_key", sms.key); put("address", sms.address); put("body", sms.text); put("stamp", sms.timestamp); put("subscription_id", sms.subscriptionId)
    }, SQLiteDatabase.CONFLICT_IGNORE) }
    fun pendingIncoming(): List<PendingSms> = buildList {
        readableDatabase.query("pending_sms", null, null, null, null, null, "stamp ASC").use { c -> while (c.moveToNext()) add(PendingSms(c.getString(0), c.getString(1), c.getString(2), c.getLong(3), if (c.isNull(4)) null else c.getInt(4))) }
    }
    fun acknowledgeIncoming(key: String) { writableDatabase.delete("pending_sms", "receipt_key=?", arrayOf(key)) }
    fun metadata(): Map<String, ThreadMetadata> = buildMap {
        readableDatabase.query("thread_metadata", arrayOf("thread_id","starred","archived","alias","spam","pinned","vendor"), null, null, null, null, null).use { c ->
            while (c.moveToNext()) put(c.getString(0), ThreadMetadata(c.getInt(1) != 0, c.getInt(2) != 0, c.getString(3), c.getInt(4) != 0, c.getInt(5) != 0, c.getString(6)))
        }
    }
    fun threadMetadata(id: String?): ThreadMetadata? {
        if (id == null) return null
        return readableDatabase.query("thread_metadata", arrayOf("starred", "archived", "alias", "spam", "pinned", "vendor"), "thread_id=?", arrayOf(id), null, null, null).use { c ->
            if (c.moveToFirst()) ThreadMetadata(c.getInt(0) != 0, c.getInt(1) != 0, c.getString(2), c.getInt(3) != 0, c.getInt(4) != 0, c.getString(5)) else null
        }
    }
    @Synchronized fun updateMetadata(id: String, transform: (ThreadMetadata) -> ThreadMetadata) {
        val changed = transform(threadMetadata(id) ?: ThreadMetadata())
        writableDatabase.insertWithOnConflict("thread_metadata", null, ContentValues().apply {
            put("thread_id", id); put("starred", if (changed.starred) 1 else 0); put("archived", if (changed.archived) 1 else 0); put("alias", changed.alias); put("spam", if (changed.spam) 1 else 0); put("pinned", if (changed.pinned) 1 else 0); put("vendor", changed.vendor.take(40))
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun prepareParts(smsId: String, count: Int) {
        writableDatabase.beginTransaction()
        try { repeat(count) { part -> writableDatabase.insertOrThrow("send_parts", null, ContentValues().apply { put("sms_id", smsId); put("part", part) }) }; writableDatabase.setTransactionSuccessful() }
        finally { writableDatabase.endTransaction() }
    }
    @Synchronized fun recordPart(id: String, part: Int, sent: Int? = null, delivered: Int? = null, error: Int? = null): Delivery {
        // Duplicate broadcasts are idempotent. A failed part cannot become successful later.
        val old = parts(id).getOrNull(part) ?: return Delivery.UNKNOWN
        val values = ContentValues().apply {
            if (sent != null) put("sent", if (old.sent < 0 || sent < 0) -1 else maxOf(old.sent, sent))
            if (delivered != null) put("delivered", if (old.delivered < 0 || delivered < 0) -1 else maxOf(old.delivered, delivered))
            if (error != null) put("error", error)
        }
        if (values.size() > 0) writableDatabase.update("send_parts", values, "sms_id=? AND part=?", arrayOf(id, part.toString()))
        return aggregateDelivery(parts(id))
    }
    fun parts(id: String): List<PartResult> = buildList {
        readableDatabase.query("send_parts", arrayOf("sent", "delivered"), "sms_id=?", arrayOf(id), null, null, "part ASC").use { c -> while(c.moveToNext()) add(PartResult(c.getInt(0), c.getInt(1))) }
    }
    fun delivery(id: String): Delivery? = parts(id).takeIf { it.isNotEmpty() }?.let(::aggregateDelivery)
    fun allDeliveries(): Map<String, Delivery> {
        val results = mutableMapOf<String, MutableList<PartResult>>()
        readableDatabase.query("send_parts", arrayOf("sms_id", "sent", "delivered"), null, null, null, null, "part ASC").use { c ->
            while (c.moveToNext()) results.getOrPut(c.getString(0)) { mutableListOf() }.add(PartResult(c.getInt(1), c.getInt(2)))
        }
        return results.mapValues { aggregateDelivery(it.value) }
    }
    fun pendingMmsCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM pending_mms", null).use { it.moveToFirst(); it.getInt(0) }
    fun saveMms(pdu: ByteArray, subscriptionId: Int?) { writableDatabase.insertOrThrow("pending_mms", null, ContentValues().apply { put("received_at", System.currentTimeMillis()); put("subscription_id", subscriptionId); put("pdu", pdu) }) }
    companion object {
        @Volatile private var instance: SandeshDatabase? = null
        fun get(context: Context): SandeshDatabase = instance ?: synchronized(this) { instance ?: SandeshDatabase(context.applicationContext).also { instance = it } }
    }
}
