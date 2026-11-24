package com.gediadeepak.smsapp.audio

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Private immutable notification actions; arbitrary external broadcasts cannot make private SMS speak. */
class ReadSmsActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == HeadsetReadout.STOP) { HeadsetReadout.stop(context); return }
        if (intent.action != HeadsetReadout.READ) return
        if (context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            Toast.makeText(context, "Unlock your phone before reading a message aloud.", Toast.LENGTH_LONG).show(); return
        }
        val id = intent.getStringExtra("sms_id") ?: return
        if (!HeadsetReadout.start(context, id, true)) Toast.makeText(context, HeadsetReadout.problem(context) ?: "Unable to start readout. Open Sandesh and try again.", Toast.LENGTH_LONG).show()
    }
}
