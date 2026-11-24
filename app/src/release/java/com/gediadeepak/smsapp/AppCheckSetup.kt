package com.gediadeepak.smsapp

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

internal object AppCheckSetup {
    const val verificationError = "This installation could not be verified for AI. Update Sandesh from its trusted distribution source or contact support."
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
