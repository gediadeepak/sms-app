package com.gediadeepak.smsapp.telephony

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gediadeepak.smsapp.MainActivity
import com.gediadeepak.smsapp.R

enum class SmsNotificationStatus { ENABLED, PERMISSION_REQUIRED, APP_BLOCKED, CHANNEL_BLOCKED }

object SmsNotifications {
    private const val CHANNEL = "sandesh_sms"
    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming SMS and messaging alerts"
                setShowBadge(true)
            }
            // The same ID preserves the user's existing channel settings, including blocks.
            manager(context).createNotificationChannel(channel)
        }
    }

    fun status(context: Context): SmsNotificationStatus {
        if (Build.VERSION.SDK_INT >= 33 && !SmsAccess.allowed(context, Manifest.permission.POST_NOTIFICATIONS)) return SmsNotificationStatus.PERMISSION_REQUIRED
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return SmsNotificationStatus.APP_BLOCKED
        if (Build.VERSION.SDK_INT >= 26 && manager(context).getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return SmsNotificationStatus.CHANNEL_BLOCKED
        return SmsNotificationStatus.ENABLED
    }

    fun settingsIntent(context: Context): Intent = if (Build.VERSION.SDK_INT >= 26) {
        if (status(context) == SmsNotificationStatus.CHANNEL_BLOCKED || status(context) == SmsNotificationStatus.ENABLED) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL)
        } else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    private fun post(context: Context, id: Int, title: String, text: String, intent: Intent): Boolean {
        ensureChannel(context)
        if (status(context) != SmsNotificationStatus.ENABLED) return false
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text).setContentIntent(pending).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        return try { manager(context).notify(id, notification); true }
        catch (_: SecurityException) { false }
    }

    fun incoming(context: Context, thread: String, number: String) = post(context, thread.hashCode(), "New SMS", "Open Sandesh to read your message.",
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("sandesh://thread/$thread")).putExtra("thread_id", thread))
    fun cancel(context: Context, thread: String) = manager(context).cancel(thread.hashCode())
    fun failure(context: Context, message: String) = post(context, -100, "Sandesh needs attention", message, Intent(context, MainActivity::class.java))
    fun mms(context: Context) = post(context, -101, "MMS received — attachments unavailable", "This SMS-only build cannot display MMS. Open Sandesh for details.", Intent(context, MainActivity::class.java))
    fun draft(context: Context, number: String, body: String) = post(context, -102, "Review your SMS reply", "Choose a SIM and tap Send in Sandesh.",
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_SENDTO).setData(Uri.fromParts("smsto", number, null)).putExtra("sms_body", body))
    fun test(context: Context) = post(context, -103, "Sandesh test notification", "This is a test alert, not an SMS. New message alerts use this same Messages channel.",
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
}
