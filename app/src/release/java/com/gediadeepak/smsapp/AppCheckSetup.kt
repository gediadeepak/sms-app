package com.gediadeepak.smsapp

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

internal object AppCheckSetup {
    const val isDevelopment = false
    const val verificationError = "This phone could not be verified by Play Integrity. Install Sandesh through its configured release channel. The release signing SHA-256 and App Check Play Integrity settings must match the distributed build. Cloud backup and AI require verification."
    fun debugToken(context: android.content.Context): String? = null
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
