package com.gediadeepak.smsapp.telephony

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.net.toUri
import com.gediadeepak.smsapp.core.*
import com.gediadeepak.smsapp.data.SandeshDatabase

class SmsTransport(private val context: Context) {
    @Suppress("DEPRECATION")
    fun send(number: String, text: String, subscriptionId: Int): Uri {
        check(SmsAccess.ready(context)) { "Make Sandesh the default SMS app and allow SMS permissions first." }
        require(SmsAccess.subscriptions(context).any { it.subscriptionId == subscriptionId }) { "That SIM is no longer active. Select a sending SIM again." }
        require(validNumber(number)) { "This sender cannot receive SMS replies. Check the destination number." }
        require(text.isNotBlank() && text.length <= 1600) { "Enter a message of up to 1600 characters." }
        val manager = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
            else SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        val pieces = manager.divideMessage(text)
        require(pieces.isNotEmpty()) { "Unable to split this SMS." }
        // Check again immediately before committing the send. Never fall back to another SIM.
        require(SmsAccess.subscriptions(context).any { it.subscriptionId == subscriptionId }) { "Selected SIM was removed. Choose a SIM again." }
        val thread = Telephony.Threads.getOrCreateThreadId(context, number)
        val uri = context.contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, ContentValues().apply {
            put("address", number); put("body", text); put("thread_id", thread); put("date", System.currentTimeMillis())
            put("read", 1); put("seen", 1); put("sub_id", subscriptionId)
            put("type", Telephony.Sms.MESSAGE_TYPE_OUTBOX); put("status", Telephony.Sms.STATUS_PENDING)
        }) ?: error("Could not save the outgoing SMS. Nothing was sent.")
        val id = ContentUris.parseId(uri).toString()
        val database = SandeshDatabase.get(context)
        val sent = ArrayList<PendingIntent>()
        val delivered = ArrayList<PendingIntent>()
        try {
            database.prepareParts(id, pieces.size)
            repeat(pieces.size) { part -> sent.add(callback(id, part, ACTION_SENT)); delivered.add(callback(id, part, ACTION_DELIVERED)) }
            manager.sendMultipartTextMessage(number, null, pieces, sent, delivered)
        } catch (e: Exception) {
            // An outbox row already exists: record the failed attempt, never blindly retry.
            repeat(pieces.size) { runCatching { database.recordPart(id, it, sent = -1) } }
            updateProvider(context, id, Delivery.FAILED)
            SmsNotifications.failure(context, "SMS could not be sent. Open Sandesh to review the failed message.")
        }
        return uri
    }
    private fun callback(id: String, part: Int, action: String): PendingIntent {
        val intent = Intent(context, SmsResultReceiver::class.java).setAction(action)
            .setData("sandesh://sms-result/$id/$part/${if (action == ACTION_SENT) "sent" else "delivery"}".toUri())
            .putExtra("sms_id", id).putExtra("part", part)
        // Delivery reports need the framework to supply the PDU. Component and URI are explicit.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (action == ACTION_DELIVERED) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }
    companion object {
        const val ACTION_SENT = "com.gediadeepak.smsapp.SMS_SENT"
        const val ACTION_DELIVERED = "com.gediadeepak.smsapp.SMS_DELIVERED"
        fun updateProvider(context: Context, id: String, delivery: Delivery) {
            if (!SmsAccess.isDefault(context)) return
            val values = ContentValues()
            when (delivery) {
                Delivery.FAILED -> { values.put("type", Telephony.Sms.MESSAGE_TYPE_FAILED); values.put("status", Telephony.Sms.STATUS_FAILED) }
                Delivery.SENT -> { values.put("type", Telephony.Sms.MESSAGE_TYPE_SENT); values.put("status", Telephony.Sms.STATUS_PENDING) }
                Delivery.DELIVERED -> { values.put("type", Telephony.Sms.MESSAGE_TYPE_SENT); values.put("status", Telephony.Sms.STATUS_COMPLETE) }
                Delivery.UNDELIVERED -> { values.put("type", Telephony.Sms.MESSAGE_TYPE_SENT); values.put("status", Telephony.Sms.STATUS_FAILED) }
                else -> return
            }
            runCatching { context.contentResolver.update(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id.toLong()), values, null, null) }
        }
    }
}
