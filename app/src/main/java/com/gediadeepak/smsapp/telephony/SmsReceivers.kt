package com.gediadeepak.smsapp.telephony

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import com.gediadeepak.smsapp.data.SandeshDatabase
import kotlinx.coroutines.*

class IncomingSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { DeviceSmsRepository(context).receive(intent) }
            catch (_: Exception) { SmsNotifications.failure(context, "An incoming SMS could not be saved. Open Sandesh to check SMS access.") }
            finally { pending.finish() }
        }
    }
}

class SmsResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SmsTransport.ACTION_SENT && intent.action != SmsTransport.ACTION_DELIVERED) return
        val id = intent.getStringExtra("sms_id")?.takeIf { it.toLongOrNull() != null } ?: return
        val part = intent.getIntExtra("part", -1).takeIf { it >= 0 } ?: return
        val result = resultCode
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = SandeshDatabase.get(context)
                val delivery = if (intent.action == SmsTransport.ACTION_SENT) {
                    db.recordPart(id, part, sent = if (result == Activity.RESULT_OK) 1 else -1, error = if (result == Activity.RESULT_OK) null else result)
                } else {
                    val pdu = intent.getByteArrayExtra("pdu") ?: return@launch
                    val format = intent.getStringExtra("format") ?: "3gpp"
                    val status = SmsMessage.createFromPdu(pdu, format)?.status ?: return@launch
                    when {
                        status in 0..31 -> db.recordPart(id, part, sent = 1, delivered = 1)
                        status >= 64 -> db.recordPart(id, part, sent = 1, delivered = -1)
                        else -> return@launch // An interim report is not a delivery confirmation.
                    }
                }
                SmsTransport.updateProvider(context, id, delivery)
                if (delivery == com.gediadeepak.smsapp.core.Delivery.FAILED) SmsNotifications.failure(context, "SMS sending failed. Some parts may have sent; check the message before trying again.")
            } catch (_: Exception) { SmsNotifications.failure(context, "A send result could not be recorded. Check the message status in Sandesh before retrying.") }
            finally { pending.finish() }
        }
    }
}

/** Preserve the raw MMS notification; attachment transport/decoding is a separate milestone. */
class IncomingMmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val data = intent.getByteArrayExtra("data") ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { SandeshDatabase.get(context).saveMms(data, DeviceSmsRepository.subscriptionFrom(intent)); SmsNotifications.mms(context) }
            catch (_: Exception) { SmsNotifications.failure(context, "An MMS notification could not be stored. This build supports SMS only.") }
            finally { pending.finish() }
        }
    }
}
