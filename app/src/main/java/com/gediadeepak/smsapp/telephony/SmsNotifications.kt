package com.gediadeepak.smsapp.telephony

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.gediadeepak.smsapp.MainActivity
import com.gediadeepak.smsapp.R

object SmsNotifications {
    private const val CHANNEL = "sandesh_sms"
    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
    private fun post(context: Context, id: Int, title: String, text: String, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 && !SmsAccess.allowed(context, Manifest.permission.POST_NOTIFICATIONS)) return
        val manager = manager(context)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH))
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text).setContentIntent(pending).setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        try { manager.notify(id, notification) } catch (_: SecurityException) { /* Inbox remains available without notifications. */ }
    }
    fun incoming(context: Context, thread: String, number: String) = post(context, thread.hashCode(), "New SMS", "Open Sandesh to read your message.",
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("sandesh://thread/$thread")).putExtra("thread_id", thread))
    fun cancel(context: Context, thread: String) = manager(context).cancel(thread.hashCode())
    fun failure(context: Context, message: String) = post(context, -100, "Sandesh needs attention", message, Intent(context, MainActivity::class.java))
    fun mms(context: Context) = post(context, -101, "MMS received — attachments unavailable", "This SMS-only build cannot display MMS. Open Sandesh for details.", Intent(context, MainActivity::class.java))
    fun draft(context: Context, number: String, body: String) = post(context, -102, "Review your SMS reply", "Choose a SIM and tap Send in Sandesh.",
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_SENDTO).setData(Uri.fromParts("smsto", number, null)).putExtra("sms_body", body))
}
