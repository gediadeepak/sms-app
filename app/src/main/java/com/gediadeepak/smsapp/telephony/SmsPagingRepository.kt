package com.gediadeepak.smsapp.telephony

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.os.CancellationSignal
import android.provider.Telephony
import com.gediadeepak.smsapp.core.*
import com.gediadeepak.smsapp.data.ContactsRepository
import com.gediadeepak.smsapp.data.SandeshDatabase

/** Only requested previews/history pages cross the provider boundary. Backups use their own full export. */
class SmsPagingRepository(private val context: Context, private val contacts: ContactsRepository) {
    private val resolver: ContentResolver get() = context.contentResolver
    private val db get() = SandeshDatabase.get(context)
    private val columns = arrayOf("_id", "thread_id", "address", "body", "date", "type", "read", "sub_id", "status")

    private fun query(projection: Array<String>, selection: String, args: Array<String>, signal: CancellationSignal, limit: Int): Cursor {
        require(limit > 0)
        // AOSP's SMS provider ignores URI/bundle limit hints. Bound the selection itself;
        // never put user input or a LIMIT fragment into the sort-order argument.
        val bounded = "_id IN (SELECT _id FROM sms WHERE ($selection) ORDER BY ${SmsPaging.ORDER} LIMIT $limit)"
        return resolver.query(Telephony.Sms.CONTENT_URI, projection, bounded, args, SmsPaging.ORDER, signal)
            ?: error("Android did not return an SMS page.")
    }

    private fun message(c: Cursor, sims: List<DeviceSim>): Message {
        val id = c.getString(0)
        val type = c.getInt(5)
        val outgoing = type != Telephony.Sms.MESSAGE_TYPE_INBOX
        val subscription = if (c.isNull(7)) null else c.getInt(7).takeIf { it >= 0 }
        val delivery = if (!outgoing) Delivery.RECEIVED else db.delivery(id) ?: when {
            type == Telephony.Sms.MESSAGE_TYPE_FAILED -> Delivery.FAILED
            type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || type == Telephony.Sms.MESSAGE_TYPE_QUEUED -> Delivery.SENDING
            !c.isNull(8) && c.getInt(8) == Telephony.Sms.STATUS_COMPLETE -> Delivery.DELIVERED
            !c.isNull(8) && c.getInt(8) >= Telephony.Sms.STATUS_FAILED -> Delivery.UNDELIVERED
            type == Telephony.Sms.MESSAGE_TYPE_SENT -> Delivery.SENT
            else -> Delivery.UNKNOWN
        }
        return Message(id, c.getString(3).orEmpty(), outgoing, c.getLong(4), sims.find { it.subscriptionId == subscription }?.line ?: SimLine.UNKNOWN, delivery, subscription)
    }

    private fun unread(thread: String, signal: CancellationSignal): Int =
        resolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf("COUNT(*) AS unread_count"), "thread_id=? AND read=0", arrayOf(thread), null, signal)?.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        } ?: error("Android did not return the unread count.")

    private fun conversation(c: Cursor, sims: List<DeviceSim>, signal: CancellationSignal): Conversation {
        val thread = c.getString(1)
        val number = c.getString(2).orEmpty()
        val meta = db.threadMetadata(thread)
        val last = message(c, sims)
        return Conversation(thread, meta?.alias?.takeIf { it.isNotBlank() } ?: number, number, last.line,
            listOf(last), unread(thread, signal), meta?.starred == true, meta?.archived == true,
            subscriptionId = last.subscriptionId, spam = meta?.spam == true, blocked = db.isSenderBlocked(number),
            contactName = contacts.name(number), historyReady = false, hasOlderMessages = true)
    }

    fun inbox(sims: List<DeviceSim>, folder: Folder, line: SimLine?, search: String, after: SmsPageKey?, limit: Int, signal: CancellationSignal): SmsPage<Conversation> {
        check(SmsAccess.ready(context)) { "SMS access is not ready." }
        val base = "type<>3 AND ${SmsPaging.LATEST}" +
            (if (folder == Folder.UNREAD) " AND thread_id IN (SELECT thread_id FROM sms WHERE type=1 AND read=0)" else "")
        val needle = search.trim()
        // Search older bodies in the provider, retaining IDs only. Never restrict search to loaded messages.
        val textMatches = if (needle.isEmpty()) emptySet() else buildSet {
            val literal = needle.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            resolver.query(Telephony.Sms.CONTENT_URI, arrayOf("DISTINCT thread_id"), "type<>3 AND (body LIKE ? ESCAPE '\\' OR address LIKE ? ESCAPE '\\')",
                arrayOf("%$literal%", "%$literal%"), null, signal)?.use { c -> while (c.moveToNext()) { signal.throwIfCanceled(); add(c.getString(0)) } }
                ?: error("Android did not return SMS search results.")
        }
        val meta = db.metadata()
        val blocked = db.blockedSenders().keys
        val result = mutableListOf<Conversation>()
        var scanKey = after
        while (true) {
          val selection = base + if (scanKey != null) " AND ${SmsPaging.BEFORE}" else ""
          val args = scanKey?.let(SmsPaging::before) ?: emptyArray()
          var scanned = 0
          query(columns, selection, args, signal, 100).use { c ->
            while (c.moveToNext()) {
                signal.throwIfCanceled()
                scanned++
                scanKey = SmsPageKey(c.getLong(4), c.getLong(0))
                val thread = c.getString(1)
                val number = c.getString(2).orEmpty()
                val flags = meta[thread]
                val unwanted = flags?.spam == true || spamSenderKey(number) in blocked
                if (if (folder == Folder.SPAM) !unwanted else unwanted) continue
                if (folder != Folder.SPAM && when (folder) {
                    Folder.ARCHIVED -> flags?.archived != true
                    Folder.STARRED -> flags?.archived == true || flags?.starred != true
                    else -> flags?.archived == true
                }) continue
                val sub = if (c.isNull(7)) null else c.getInt(7).takeIf { it >= 0 }
                if (line != null && sims.find { it.subscriptionId == sub }?.line != line) continue
                val name = contacts.name(number)
                if (needle.isNotEmpty() && thread !in textMatches && !number.contains(needle, true) &&
                    flags?.alias?.contains(needle, true) != true && name?.contains(needle, true) != true) continue
                val item = conversation(c, sims, signal).copy(contactName = name)
                if (folder == Folder.UNREAD && item.unread == 0) continue
                if (result.size == limit) return SmsPage(result, SmsPaging.key(result.last().messages.last()))
                result += item
            }
          }
          if (scanned < 100) return SmsPage(result, null)
        }
    }

    fun thread(thread: String, sims: List<DeviceSim>, signal: CancellationSignal): Conversation? {
        require(thread.toLongOrNull() != null)
        return query(columns, "thread_id=? AND type<>3", arrayOf(thread), signal, 1).use { c ->
            if (c.moveToFirst()) conversation(c, sims, signal) else null
        }
    }

    fun senderThreads(address: String, signal: CancellationSignal): List<String> {
        val key = spamSenderKey(address)
        return resolver.query(Telephony.Sms.CONTENT_URI, arrayOf("DISTINCT thread_id", "address"), "type<>3", null, null, signal)?.use { c ->
            buildList { while (c.moveToNext()) { signal.throwIfCanceled(); if (spamSenderKey(c.getString(1).orEmpty()) == key) add(c.getString(0)) } }.distinct()
        } ?: error("Android did not return sender threads.")
    }

    fun history(thread: String, sims: List<DeviceSim>, before: SmsPageKey?, limit: Int, signal: CancellationSignal): SmsPage<Message> {
        check(SmsAccess.ready(context))
        require(thread.toLongOrNull() != null)
        val selection = "thread_id=? AND type<>3" + if (before != null) " AND ${SmsPaging.BEFORE}" else ""
        val args = arrayOf(thread) + (before?.let(SmsPaging::before) ?: emptyArray())
        val result = mutableListOf<Message>()
        query(columns, selection, args, signal, limit + 1).use { c ->
            while (c.moveToNext()) {
                signal.throwIfCanceled()
                if (result.size == limit) return SmsPage(result.asReversed().toList(), SmsPaging.key(result.last()))
                result += message(c, sims)
            }
        }
        return SmsPage(result.asReversed().toList(), null)
    }
}
