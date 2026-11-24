package com.gediadeepak.smsapp.audio

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.gediadeepak.smsapp.telephony.SmsNotifications
import com.gediadeepak.smsapp.telephony.SmsNotificationStatus
import java.util.UUID

data class HeadsetOutput(val key: String, val label: String)

/** A Bluetooth speaker can also be A2DP, so the user explicitly selects a trusted audio output. */
object HeadsetReadout {
    const val READ = "com.sandesh.app.READ_SMS_ALOUD"
    const val ANNOUNCE = "com.sandesh.app.ANNOUNCE_SMS_SENDER"
    const val STOP = "com.sandesh.app.STOP_SMS_READOUT"
    const val TEST = "com.sandesh.app.TEST_HEADSET_READOUT"
    private val session = UUID.randomUUID().toString()
    private fun prefs(context: Context) = context.getSharedPreferences("sandesh_headset", Context.MODE_PRIVATE)
    fun announceEnabled(context: Context) = prefs(context).getBoolean("announce_sender", false)
    fun setAnnounce(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean("announce_sender", enabled).apply(); if (!enabled) stop(context) }
    fun selectedName(context: Context): String = prefs(context).getString("output_name", "No headset chosen").orEmpty()
    fun selectedKey(context: Context): String? = prefs(context).getString("output_key", null)
    fun choose(context: Context, output: HeadsetOutput) { stop(context); prefs(context).edit().putString("output_key", output.key).putString("output_name", output.label).apply() }
    fun forget(context: Context) { stop(context); prefs(context).edit().clear().apply() }
    fun outputs(context: Context): List<HeadsetOutput> = devices(context).map { HeadsetOutput(key(it), it.productName.toString().ifBlank { "Audio device ${it.id}" }) }
    fun selected(context: Context): AudioDeviceInfo? {
        val saved = prefs(context).getString("output_key", null) ?: return null
        return devices(context).firstOrNull { key(it) == saved }
    }
    private fun devices(context: Context): List<AudioDeviceInfo> {
        if (Build.VERSION.SDK_INT < 28) return emptyList()
        return context.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
            it.type in setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_HEARING_AID) || (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }
    private fun key(device: AudioDeviceInfo): String {
        val address = if (Build.VERSION.SDK_INT >= 28) device.address else ""
        // Unidentified outputs are trusted only for this process/session, not guessed after process death.
        return "${device.type}|${address.takeIf { it.isNotBlank() } ?: "$session:${device.id}"}"
    }
    fun problem(context: Context): String? = when {
        Build.VERSION.SDK_INT < 28 -> "Headset readout requires Android 9 or newer."
        SmsNotifications.status(context) != SmsNotificationStatus.ENABLED -> "Enable message notifications before using headset readout."
        selected(context) == null -> "Connect and choose your headset in Settings → Headset readout."
        else -> null
    }
    fun start(context: Context, smsId: String, body: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        if (smsId.toLongOrNull()?.let { it >= 0 } != true || (!body && !announceEnabled(context)) || problem(context) != null) return false
        return try {
            ContextCompat.startForegroundService(context, Intent(context, HeadsetReadoutService::class.java)
                .setAction(if (body) HeadsetReadout.READ else HeadsetReadout.ANNOUNCE).putExtra("sms_id", smsId))
            true
        } catch (_: RuntimeException) { false } // Android/OEM background restrictions: retain the normal SMS alert.
    }
    fun stop(context: Context) { if (Build.VERSION.SDK_INT >= 28) context.stopService(Intent(context, HeadsetReadoutService::class.java)) }
    fun test(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        if (problem(context) != null) return false
        return try { ContextCompat.startForegroundService(context, Intent(context, HeadsetReadoutService::class.java).setAction(HeadsetReadout.TEST)); true }
        catch (_: RuntimeException) { false }
    }
}
