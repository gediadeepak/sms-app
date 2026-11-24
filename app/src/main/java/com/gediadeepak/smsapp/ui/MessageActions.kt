package com.gediadeepak.smsapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast

internal fun copyMessage(context: Context, text: String) {
    try {
        val clip = ClipData.newPlainText("Sandesh message", text)
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) { Toast.makeText(context, "The clipboard is unavailable on this device.", Toast.LENGTH_SHORT).show() }
}

internal fun clipboardText(context: Context): String? = context.getSystemService(ClipboardManager::class.java).primaryClip
    ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()

internal fun shareMessage(context: Context, text: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(intent, "Share message"))
    } catch (_: Exception) { Toast.makeText(context, "No app is available to share this message.", Toast.LENGTH_SHORT).show() }
}
