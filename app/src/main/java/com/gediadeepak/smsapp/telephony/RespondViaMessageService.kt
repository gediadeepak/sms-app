package com.gediadeepak.smsapp.telephony

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager

/** Telecom quick-reply opens an explicit review, so an ambiguous dual-SIM line is never guessed. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            val number = intent.data?.schemeSpecificPart?.substringBefore('?').orEmpty()
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
            if (number.isNotBlank()) SmsNotifications.draft(this, number, text)
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
