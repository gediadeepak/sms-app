package com.gediadeepak.smsapp.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.gediadeepak.smsapp.core.WatchReplyRules
import com.gediadeepak.smsapp.core.validNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Non-exported; only the per-message notification PendingIntent can invoke this component. */
class WatchActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WatchNotifications.READ && intent.action != WatchNotifications.REPLY) return
        val target = intent.data ?: return
        if (target.scheme != "sandesh" || target.host != "watch-action") return
        val path = target.pathSegments
        if (path.size != 3 || path[0] != if (intent.action == WatchNotifications.READ) "read" else "reply") return
        val reply = if (intent.action == WatchNotifications.REPLY) WatchReplyRules.reply(RemoteInput.getResultsFromIntent(intent)?.getCharSequence(WatchNotifications.INPUT)) else null
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!WatchSettings(context).load().actions) return@launch
                val message = WatchNotifications.message(context, path[1], path[2]) ?: return@launch
                if (intent.action == WatchNotifications.READ) DeviceSmsRepository(context).markReadThrough(message.thread, message.id, message.date)
                else if (validNumber(message.number)) {
                    if (reply == null) SmsNotifications.failure(context, "The watch reply was empty or exceeded 1,600 characters. Nothing was sent; compose it in Sandesh.")
                    else SmsNotifications.watchDraft(context, message, reply)
                }
            } catch (_: Exception) {
                SmsNotifications.failure(context, "The watch action could not be completed. Open Sandesh to review. Nothing was sent.")
            } finally { pending.finish() }
        }
    }
}
