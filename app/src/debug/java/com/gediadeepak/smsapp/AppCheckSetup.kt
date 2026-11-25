package com.gediadeepak.smsapp

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal object AppCheckSetup {
    const val isDevelopment = true
    const val verificationError = "This phone is not verified. Each development installation has its own App Check token. Open Device verification help, register this phone's token in Firebase Security → App Check → Apps → Manage debug tokens, then retry. Signing in or copying the APK does not register another phone."
    fun debugToken(context: android.content.Context): String? {
        val app = com.google.firebase.FirebaseApp.getInstance()
        // Read-only diagnostics for the pinned Firebase debug SDK; never replaces or exports a token automatically.
        return context.getSharedPreferences("com.google.firebase.appcheck.debug.store." + app.persistenceKey, android.content.Context.MODE_PRIVATE)
            .getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null)
            ?.takeIf { runCatching { java.util.UUID.fromString(it) }.isSuccess }
    }
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
