package com.gediadeepak.smsapp.telephony

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.gediadeepak.smsapp.MainActivity
import com.gediadeepak.smsapp.R
import com.gediadeepak.smsapp.core.validNumber
import com.gediadeepak.smsapp.data.ContactsRepository
import com.gediadeepak.smsapp.data.SandeshDatabase

internal data class WatchMessage(val id: String, val thread: String, val number: String, val body: String, val date: Long)

/** Phone-side Android Wear 1.x notification extensions; no watch APK or SMS transport. */
object WatchNotifications {
    const val REPLY = "com.sandesh.app.WATCH_DRAFT"
    const val READ = "com.sandesh.app.WATCH_READ"
    const val REVIEW = "com.sandesh.app.REVIEW_WATCH_DRAFT"
    const val INPUT = "watch_reply"

    internal fun message(context: Context, thread: String, id: String): WatchMessage? {
        if (!SmsAccess.ready(context) || thread.toLongOrNull() == null || id.toLongOrNull() == null) return null
        return context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf("_id", "thread_id", "address", "body", "date"), "_id=? AND thread_id=?", arrayOf(id, thread), null)?.use { c ->
            if (!c.moveToFirst()) null else WatchMessage(c.getString(0), c.getString(1), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getLong(4))
        }?.takeUnless { row ->
            val db = SandeshDatabase.get(context)
            db.isSenderBlocked(row.number) || db.threadMetadata(row.thread)?.spam == true
        }
    }

    internal fun extend(context: Context, builder: NotificationCompat.Builder, message: WatchMessage, options: WatchOptions) {
        if (options.previews) {
            val sender = ContactsRepository(context).name(message.number)
                ?: SandeshDatabase.get(context).threadMetadata(message.thread)?.alias?.takeIf { it.isNotBlank() }
                ?: message.number
            builder.setContentTitle(sender).setContentText(message.body.take(500))
                .setStyle(NotificationCompat.BigTextStyle().bigText(message.body.take(1600)))
        }
        if (!options.actions) return
        val wearable = NotificationCompat.WearableExtender()
            .setDismissalId("sandesh-thread-${message.thread}")
            .addAction(NotificationCompat.Action.Builder(android.R.drawable.ic_menu_view, "Mark read", action(context, message, READ, false)).build())
        if (validNumber(message.number)) {
            val input = RemoteInput.Builder(INPUT).setLabel("Draft reply · send from phone").setAllowFreeFormInput(true).build()
            wearable.addAction(NotificationCompat.Action.Builder(android.R.drawable.ic_menu_edit, "Draft reply", action(context, message, REPLY, true))
                .addRemoteInput(input).setAllowGeneratedReplies(false).setShowsUserInterface(false).build())
        }
        builder.extend(wearable)
    }

    private fun action(context: Context, message: WatchMessage, kind: String, mutable: Boolean): PendingIntent {
        // The original URI binds the target; fill-in extras carry only RemoteInput text.
        val intent = Intent(context, WatchActionReceiver::class.java).setAction(kind)
            .setData(Uri.parse("sandesh://watch-action/${if (kind == READ) "read" else "reply"}/${message.thread}/${message.id}"))
        val flags = PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_ONE_SHOT or
            if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    internal fun reviewIntent(context: Context, message: WatchMessage, body: String): Intent =
        Intent(context, MainActivity::class.java).setAction(REVIEW)
            .setData(Uri.parse("sandesh://watch-review/${message.thread}/${message.id}"))
            .putExtra("thread_id", message.thread).putExtra("sms_body", body)
}
