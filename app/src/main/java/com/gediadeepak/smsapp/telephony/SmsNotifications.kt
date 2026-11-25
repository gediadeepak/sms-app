package com.gediadeepak.smsapp.telephony

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
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
import com.gediadeepak.smsapp.audio.HeadsetReadout
import com.gediadeepak.smsapp.audio.ReadSmsActionReceiver

enum class SmsNotificationStatus { ENABLED, PERMISSION_REQUIRED, APP_BLOCKED, CHANNEL_BLOCKED }

object SmsNotifications {
    private const val CHANNEL = "sandesh_sms"
    private const val INCOMING_TAG = "sandesh.incoming"
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

    private fun post(context: Context, id: Int, title: String, text: String, intent: Intent, readSmsId: String? = null,
        tag: String? = null, silent: Boolean = false, localOnly: Boolean = false, configure: (NotificationCompat.Builder) -> Unit = {}): Boolean {
        ensureChannel(context)
        if (status(context) != SmsNotificationStatus.ENABLED) return false
        val reviewFlags = if (intent.action == WatchNotifications.REVIEW) PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_ONE_SHOT else PendingIntent.FLAG_UPDATE_CURRENT
        val pending = PendingIntent.getActivity(context, id, intent, reviewFlags or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text).setContentIntent(pending).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setLocalOnly(localOnly).setSilent(silent)
            .setPublicVersion(NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Sandesh").setContentText("Open Sandesh to view this alert.").build())
        if (readSmsId != null && HeadsetReadout.selected(context) != null) {
            val action = Intent(context, ReadSmsActionReceiver::class.java).setAction(HeadsetReadout.READ)
                .setData(Uri.parse("sandesh://read/$readSmsId")).putExtra("sms_id", readSmsId)
            val read = PendingIntent.getBroadcast(context, 0, action, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(NotificationCompat.Action.Builder(android.R.drawable.ic_media_play, "Read message", read).setAuthenticationRequired(true).build())
        }
        configure(builder)
        return try { manager(context).notify(tag, id, builder.build()); true }
        catch (_: SecurityException) { false }
    }

    fun incoming(context: Context, thread: String, number: String, smsId: String? = null, silent: Boolean = false): Boolean {
        // Upgrades used untagged notifications. Remove the old card before posting its replacement.
        manager(context).cancel(thread.hashCode())
        val options = WatchSettings(context).load()
        val message = if (smsId != null && (options.actions || options.previews))
            runCatching { WatchNotifications.message(context, thread, smsId) }.getOrNull() else null
        return post(context, thread.hashCode(), "New SMS", "Open Sandesh to read your message.",
            Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("sandesh://thread/$thread")).putExtra("thread_id", thread),
            smsId, tag = "$INCOMING_TAG.$thread", silent = silent) { builder ->
            if (message != null) runCatching { WatchNotifications.extend(context, builder, message, options) }
        }
    }

    /** Revoke old preview/action cards immediately when either watch opt-in changes. */
    fun clearWatchNotifications(context: Context) {
        manager(context).activeNotifications.filter { it.tag?.startsWith("$INCOMING_TAG.") == true || it.tag == INCOMING_TAG || it.tag?.startsWith("sandesh.watch-draft.") == true }
            .forEach { manager(context).cancel(it.tag, it.id) }
    }

    internal fun watchDraft(context: Context, message: WatchMessage, body: String) = post(context, message.thread.hashCode(),
        "Watch reply ready to review", "Open Sandesh, review your reply, choose a SIM and tap Send. Nothing has been sent.",
        WatchNotifications.reviewIntent(context, message, body), tag = "sandesh.watch-draft.${message.thread}", localOnly = true)

    fun watchTest(context: Context) = post(context, -105, "Sandesh watch test", "If this alert appears on your watch, notification mirroring is working. No SMS was sent.",
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
    fun watchOpen(context: Context, thread: String) = post(context, thread.hashCode(), "Open your watch conversation", "Tap to open this conversation in Sandesh. Nothing was sent.",
        Intent(context, MainActivity::class.java).setAction(WatchNotifications.OPEN).setData(Uri.parse("sandesh://watch-open/$thread")).putExtra("thread_id", thread),
        tag = "sandesh.watch-open.$thread", localOnly = true)
    fun readoutForeground(context: Context, body: Boolean, testing: Boolean): Notification {
        val stop = PendingIntent.getBroadcast(context, -104, Intent(context, ReadSmsActionReceiver::class.java).setAction(HeadsetReadout.STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(context, -104, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification).setOngoing(true).setSilent(true)
            .setContentTitle(if (testing) "Testing headset readout" else if (body) "Reading SMS through your headset" else "Announcing SMS sender")
            .setContentText("Tap Stop to end playback.").setContentIntent(open).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stop).build()
    }
    fun cancel(context: Context, thread: String) {
        manager(context).cancel(thread.hashCode())
        manager(context).cancel(INCOMING_TAG, thread.hashCode())
        manager(context).cancel("$INCOMING_TAG.$thread", thread.hashCode())
    }
    fun failure(context: Context, message: String) = post(context, -100, "Sandesh needs attention", message, Intent(context, MainActivity::class.java))
    fun mms(context: Context) = post(context, -101, "MMS received — attachments unavailable", "This SMS-only build cannot display MMS. Open Sandesh for details.", Intent(context, MainActivity::class.java))
    fun draft(context: Context, number: String, body: String) = post(context, -102, "Review your SMS reply", "Choose a SIM and tap Send in Sandesh.",
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_SENDTO).setData(Uri.fromParts("smsto", number, null)).putExtra("sms_body", body))
    fun test(context: Context) = post(context, -103, "Sandesh test notification", "This is a test alert, not an SMS. New message alerts use this same Messages channel.",
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
}
