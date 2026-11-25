package com.gediadeepak.smsapp.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.gediadeepak.smsapp.core.*
import com.gediadeepak.smsapp.data.PreferenceStore
import com.gediadeepak.smsapp.data.SandeshDatabase
import com.gediadeepak.smsapp.telephony.SmsAccess
import com.gediadeepak.smsapp.audio.HeadsetReadout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

data class BackupPreview(val messageCount: Int, val createdAt: Long)
data class RestoreResult(val inserted: Int, val skipped: Int)

/** Backups contain SMS history and app preferences/flags, never authentication tokens or send jobs. */
class SmsBackupRepository(private val context: Context) {
    private val resolver get() = context.contentResolver
    private val database = SandeshDatabase.get(context)
    private val preferences = PreferenceStore(context)
    private val types = setOf(Telephony.Sms.MESSAGE_TYPE_INBOX, Telephony.Sms.MESSAGE_TYPE_SENT, Telephony.Sms.MESSAGE_TYPE_FAILED,
        Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.MESSAGE_TYPE_QUEUED)

    fun snapshot(): ByteArray {
        requireAccess()
        val messages = JSONArray()
        val addresses = mutableMapOf<String, String>()
        var estimatedBytes = 0L
        val columns = arrayOf("thread_id", "address", "body", "date", "date_sent", "type", "read", "status", "sub_id")
        resolver.query(Telephony.Sms.CONTENT_URI, columns, null, null, "date ASC, _id ASC")?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(5) !in types) continue
                val address = c.getString(1) ?: continue
                val body = c.getString(2).orEmpty()
                estimatedBytes += body.toByteArray(Charsets.UTF_8).size + address.length * 4 + 250
                require(estimatedBytes <= BackupCipher.MAX_PLAIN_BYTES && messages.length() < 100_000) { "This inbox exceeds the current 16 MiB / 100,000-message backup limit." }
                addresses[c.getString(0)] = address
                messages.put(JSONObject().put("address", address).put("body", body).put("date", c.getLong(3)).put("dateSent", c.getLong(4))
                    .put("type", c.getInt(5)).put("read", c.getInt(6) != 0).put("status", if (c.isNull(7)) -1 else c.getInt(7))
                    .put("originalSubscriptionId", if (c.isNull(8)) -1 else c.getInt(8)))
            }
        } ?: error("Could not read the SMS inbox.")
        val metadata = JSONArray()
        database.metadata().forEach { (id, flags) -> addresses[id]?.let { address -> metadata.put(JSONObject()
            .put("address", address).put("starred", flags.starred).put("archived", flags.archived).put("alias", flags.alias).put("spam", flags.spam).put("pinned",flags.pinned).put("vendor",flags.vendor)) } }
        val p = preferences.load()
        val blockedSenders = database.blockedSenders().values.toList()
        require(blockedSenders.size <= 10_000) { "This backup exceeds the 10,000 blocked-sender limit." }
        val root = JSONObject().put("format", FORMAT).put("version", 1).put("createdAt", System.currentTimeMillis())
            .put("messageCount", messages.length()).put("messages", messages).put("threads", metadata)
            .put("blockedSenders", JSONArray(blockedSenders))
            .put("preferences", JSONObject().put("personal", p.personalLabel).put("work", p.workLabel).put("theme", p.theme.name).put("suggestions", p.suggestions).put("undoSend",p.undoSend).put("groupBusinesses",p.groupBusinesses))
        return root.toString().toByteArray(Charsets.UTF_8).also { require(it.size <= BackupCipher.MAX_PLAIN_BYTES) { "This backup exceeds the 16 MiB limit." } }
    }

    fun validate(plain: ByteArray): BackupPreview {
        val root = parse(plain)
        return BackupPreview(root.getJSONArray("messages").length(), root.getLong("createdAt"))
    }

    fun restore(plain: ByteArray): RestoreResult {
        // Parse and validate ALL records before the first provider write.
        val root = parse(plain)
        requireAccess()
        HeadsetReadout.stop(context)
        val threads = mutableMapOf<String, Long>()
        val existingThreads = mutableSetOf<Long>()
        resolver.query(Telephony.Sms.CONTENT_URI, arrayOf("thread_id"), null, null, null)?.use { c -> while (c.moveToNext()) existingThreads.add(c.getLong(0)) }
        fun thread(address: String) = threads.getOrPut(address) { Telephony.Threads.getOrCreateThreadId(context, address) }
        var inserted = 0
        var skipped = 0
        val rows = root.getJSONArray("messages")
        try {
            repeat(rows.length()) { index ->
                requireAccess()
                val item = rows.getJSONObject(index)
                val address = item.getString("address")
                val id = thread(address)
                val originalType = item.getInt("type")
                // Restoring queued/outbox records as FAILED prevents Android from sending them.
                val type = if (originalType == Telephony.Sms.MESSAGE_TYPE_INBOX || originalType == Telephony.Sms.MESSAGE_TYPE_SENT) originalType else Telephony.Sms.MESSAGE_TYPE_FAILED
                val date = item.getLong("date")
                val body = item.getString("body")
                val typeClause = if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) "type=1" else "type IN (2,4,5,6)"
                val exists = resolver.query(Telephony.Sms.CONTENT_URI, arrayOf("_id"), "thread_id=? AND date=? AND body=? AND $typeClause",
                    arrayOf(id.toString(), date.toString(), body), null)?.use { it.moveToFirst() } ?: error("Could not check existing messages.")
                if (exists) skipped++ else {
                    resolver.insert(Telephony.Sms.CONTENT_URI, ContentValues().apply {
                        put("thread_id", id); put("address", address); put("body", body); put("date", date); put("date_sent", item.getLong("dateSent"))
                        put("type", type); put("read", if (item.getBoolean("read")) 1 else 0); put("seen", 1)
                        put("status", if (type == Telephony.Sms.MESSAGE_TYPE_FAILED) Telephony.Sms.STATUS_FAILED else item.getInt("status"))
                        // Subscription IDs are not portable between phones. Never guess a current SIM.
                        put("sub_id", -1)
                    }) ?: error("Android rejected a restored message.")
                    inserted++
                }
            }
            val metadata = root.getJSONArray("threads")
            repeat(metadata.length()) { index ->
                val m = metadata.getJSONObject(index)
                val threadId = thread(m.getString("address"))
                database.updateMetadata(threadId.toString()) { existing -> existing.copy(
                    starred = existing.starred || m.getBoolean("starred"), archived = if (threadId in existingThreads) existing.archived else m.getBoolean("archived"),
                    alias = existing.alias.ifBlank { m.getString("alias") }, spam = existing.spam || m.optBoolean("spam", false), pinned = existing.pinned || m.optBoolean("pinned",false), vendor = existing.vendor.ifBlank { m.optString("vendor","").take(40) }) }
            }
            root.optJSONArray("blockedSenders")?.let { senders -> repeat(senders.length()) { database.setSenderBlocked(senders.getString(it), true) } }
            val p = root.getJSONObject("preferences")
            preferences.save(preferences.load().copy(personalLabel = p.getString("personal"), workLabel = p.getString("work"),
                theme = ThemeMode.valueOf(p.getString("theme")), suggestions = p.getBoolean("suggestions"), defaultSubscriptionId = null, aiEnabled = false,
                undoSend = p.optBoolean("undoSend",true), groupBusinesses = p.optBoolean("groupBusinesses",false)))
        } catch (e: Exception) {
            throw IllegalStateException("Restore stopped after $inserted new messages ($skipped already present). Existing messages were kept. You can retry this backup without duplicating restored messages.", e)
        }
        return RestoreResult(inserted, skipped)
    }

    fun readEncrypted(uri: Uri): ByteArray = resolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            total += size
            require(total <= BackupCipher.MAX_ENCRYPTED_BYTES) { "That backup file is too large." }
            output.write(buffer, 0, size)
        }
        output.toByteArray()
    } ?: error("Unable to read the selected backup.")

    fun writeEncrypted(uri: Uri, bytes: ByteArray) { resolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() } ?: error("Unable to write the selected backup file.") }

    private fun parse(plain: ByteArray): JSONObject {
        require(plain.size <= BackupCipher.MAX_PLAIN_BYTES) { "Backup is too large." }
        val root = JSONObject(plain.toString(Charsets.UTF_8))
        require(root.getString("format") == FORMAT && root.getInt("version") == 1) { "Unsupported backup format." }
        require(root.getLong("createdAt") >= 0) { "Invalid backup date." }
        val messages = root.getJSONArray("messages")
        require(messages.length() <= 100_000 && root.getInt("messageCount") == messages.length()) { "Invalid backup message count." }
        repeat(messages.length()) { index ->
            val m = messages.getJSONObject(index)
            require(m.getString("address").length in 1..512 && m.getString("body").length <= 100_000 && m.getInt("type") in types) { "Invalid message in backup." }
            require(m.getLong("date") >= 0 && m.getLong("dateSent") >= 0) { "Invalid message date." }
            m.getBoolean("read"); m.getInt("status")
        }
        val metadata = root.getJSONArray("threads")
        require(metadata.length() <= 100_000) { "Too many conversation records." }
        repeat(metadata.length()) { index ->
            val item = metadata.getJSONObject(index)
            require(item.getString("address").length in 1..512 && item.getString("alias").length <= 60) { "Invalid conversation metadata." }
            item.getBoolean("starred"); item.getBoolean("archived")
            if (item.has("spam")) item.getBoolean("spam")
            if (item.has("pinned")) item.getBoolean("pinned")
            if (item.has("vendor")) require(item.getString("vendor").length <= 40) { "Invalid business group." }
        }
        if (root.has("blockedSenders")) {
            val senders = root.getJSONArray("blockedSenders")
            require(senders.length() <= 10_000) { "Too many blocked senders." }
            repeat(senders.length()) { val sender = senders.getString(it); require(sender.length in 1..512 && sender.isNotBlank()) { "Invalid blocked sender." } }
        }
        val p = root.getJSONObject("preferences")
        require(p.getString("personal").length in 1..18 && p.getString("work").length in 1..18) { "Invalid SIM labels." }
        ThemeMode.valueOf(p.getString("theme")); p.getBoolean("suggestions")
        return root
    }
    private fun requireAccess() { check(SmsAccess.ready(context)) { "Make Sandesh the default SMS app and grant SMS permissions before backing up or restoring." } }
    companion object { private const val FORMAT = "sandesh.sms.backup" }
}
