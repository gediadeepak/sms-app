package com.gediadeepak.smsapp.telephony

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import com.gediadeepak.smsapp.core.*
import com.gediadeepak.smsapp.data.SandeshDatabase
import com.gediadeepak.smsapp.data.PendingSms
import com.gediadeepak.smsapp.audio.HeadsetReadout

class DeviceSmsRepository(private val context: Context) {
    private val resolver get() = context.contentResolver
    private val database = SandeshDatabase.get(context)

    fun load(subscriptions: List<DeviceSim>): List<Conversation> {
        check(SmsAccess.ready(context)) { "Make Sandesh your default SMS app and allow the required permissions." }
        val meta = database.metadata()
        val blockedSenders = database.blockedSenders()
        val deliveries = database.allDeliveries()
        val groups = linkedMapOf<String, MutableList<Message>>()
        val addresses = mutableMapOf<String, String>()
        val unread = mutableMapOf<String, Int>()
        val columns = arrayOf("_id", "thread_id", "address", "body", "date", "type", "read", "sub_id", "status")
        resolver.query(Telephony.Sms.CONTENT_URI, columns, "type<>?", arrayOf(Telephony.Sms.MESSAGE_TYPE_DRAFT.toString()), "date ASC, _id ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val thread = cursor.getString(1)
                val address = cursor.getString(2) ?: continue
                val type = cursor.getInt(5)
                val subscriptionId = if (cursor.isNull(7)) null else cursor.getInt(7).takeIf { it >= 0 }
                val line = subscriptions.firstOrNull { it.subscriptionId == subscriptionId }?.line ?: SimLine.UNKNOWN
                val outgoing = type != Telephony.Sms.MESSAGE_TYPE_INBOX
                val delivery = if (!outgoing) Delivery.RECEIVED else deliveries[id] ?: when {
                    type == Telephony.Sms.MESSAGE_TYPE_FAILED -> Delivery.FAILED
                    type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || type == Telephony.Sms.MESSAGE_TYPE_QUEUED -> Delivery.SENDING
                    !cursor.isNull(8) && cursor.getInt(8) == Telephony.Sms.STATUS_COMPLETE -> Delivery.DELIVERED
                    !cursor.isNull(8) && cursor.getInt(8) >= Telephony.Sms.STATUS_FAILED -> Delivery.UNDELIVERED
                    type == Telephony.Sms.MESSAGE_TYPE_SENT -> Delivery.SENT
                    else -> Delivery.UNKNOWN
                }
                groups.getOrPut(thread) { mutableListOf() }.add(Message(id, cursor.getString(3).orEmpty(), outgoing, cursor.getLong(4), line, delivery, subscriptionId))
                addresses[thread] = address
                if (!outgoing && cursor.getInt(6) == 0) unread[thread] = (unread[thread] ?: 0) + 1
            }
        } ?: error("Android did not return an SMS inbox. Try allowing SMS access again.")
        return groups.map { (thread, messages) ->
            val metadata = meta[thread]
            Conversation(thread, metadata?.alias?.takeIf { it.isNotBlank() } ?: addresses.getValue(thread), addresses.getValue(thread),
                messages.last().line, messages, unread[thread] ?: 0, metadata?.starred ?: false, metadata?.archived ?: false,
                subscriptionId = messages.last().subscriptionId, spam = metadata?.spam == true,
                blocked = spamSenderKey(addresses.getValue(thread)) in blockedSenders)
        }.sortedByDescending { it.messages.last().timestamp }
    }
    fun threadId(number: String): String {
        check(SmsAccess.isDefault(context)) { "SMS access is not ready." }
        return Telephony.Threads.getOrCreateThreadId(context, number).toString()
    }
    fun markRead(thread: String) {
        if (!SmsAccess.ready(context)) return
        resolver.update(Telephony.Sms.CONTENT_URI, ContentValues().apply { put("read", 1); put("seen", 1) }, "thread_id=? AND read=0", arrayOf(thread))
        SmsNotifications.cancel(context, thread)
    }
    /** A delayed watch action must never mark a newer, unseen SMS read. */
    fun markReadThrough(thread: String, id: String, date: Long): Unit = synchronized(incomingLock) {
        if (!SmsAccess.ready(context)) return
        resolver.update(Telephony.Sms.Inbox.CONTENT_URI, ContentValues().apply { put("read", 1); put("seen", 1) },
            "thread_id=? AND read=0 AND _id<=? AND date<=?", arrayOf(thread, id, date.toString()))
        val latest = resolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf("_id", "address"), "thread_id=? AND read=0", arrayOf(thread), "date DESC, _id DESC")?.use {
            if (it.moveToFirst()) it.getString(0) to it.getString(1).orEmpty() else null
        }
        if (latest == null) SmsNotifications.cancel(context, thread)
        else SmsNotifications.incoming(context, thread, latest.second, latest.first, silent = true)
    }
    fun updateMetadata(thread: String, edit: (com.gediadeepak.smsapp.data.ThreadMetadata) -> com.gediadeepak.smsapp.data.ThreadMetadata) = database.updateMetadata(thread, edit)

    fun receive(intent: android.content.Intent): String? {
        if (!SmsAccess.isDefault(context)) return null
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.toList().orEmpty()
        if (parts.isEmpty()) return null
        val first = parts.first()
        val number = first.displayOriginatingAddress ?: first.originatingAddress ?: return null
        val text = parts.joinToString("") { it.displayMessageBody ?: it.messageBody.orEmpty() }
        val stamp = first.timestampMillis
        val subscriptionId = subscriptionFrom(intent)
        val rawKey = "$number\u0000$stamp\u0000$subscriptionId\u0000$text"
        val key = java.security.MessageDigest.getInstance("SHA-256").digest(rawKey.toByteArray()).joinToString("") { "%02x".format(it) }
        database.retainIncoming(PendingSms(key, number, text, stamp, subscriptionId))
        if (!SmsAccess.ready(context)) SmsNotifications.failure(context, "An incoming SMS is saved locally. Restore SMS and SIM permissions to show it in your inbox.")
        drainPendingIncoming(announceKey = key)
        return if (SmsAccess.ready(context)) threadId(number) else null
    }
    fun drainPendingIncoming(announceKey: String? = null): Unit = synchronized(incomingLock) {
        if (!SmsAccess.ready(context)) return
        database.pendingIncoming().forEach { receipt ->
        val number = receipt.address
        val stamp = receipt.timestamp
        val text = receipt.text
        val subscriptionId = receipt.subscriptionId
        val thread = threadId(number)
        // Some OEMs redeliver the same broadcast; do not insert another copy.
        val exists = resolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf("_id"),
            "address=? AND date=? AND body=? AND sub_id=?", arrayOf(number, stamp.toString(), text, (subscriptionId ?: -1).toString()), null)?.use { it.moveToFirst() } ?: false
        if (!exists) {
            val saved = resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, ContentValues().apply {
                put("address", number); put("body", text); put("date", stamp); put("date_sent", stamp)
                put("thread_id", thread.toLong()); put("read", 0); put("seen", 0)
                put("type", Telephony.Sms.MESSAGE_TYPE_INBOX); put("sub_id", subscriptionId ?: -1)
            }) ?: error("Unable to save the received SMS.")
            val unwanted = database.isSenderBlocked(number) || database.threadMetadata(thread)?.spam == true
            database.updateMetadata(thread) { it.copy(archived = false) }
            if (unwanted) SmsNotifications.cancel(context, thread) else {
                val id = ContentUris.parseId(saved).toString()
                SmsNotifications.incoming(context, thread, number, id)
                if (receipt.key == announceKey) HeadsetReadout.start(context, id, false)
            }
        }
        database.acknowledgeIncoming(receipt.key)
        }
    }
    companion object {
        private val incomingLock = Any()
        fun subscriptionFrom(intent: android.content.Intent): Int? {
            val modern = if (android.os.Build.VERSION.SDK_INT >= 26) intent.getIntExtra(android.telephony.SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1) else -1
            if (modern >= 0) return modern
            // The telephony SMS broadcast documents "subscription" as a long; OEMs also use int.
            val value = intent.extras?.get("subscription") as? Number
            return value?.toInt()?.takeIf { it >= 0 }
        }
    }
}
