package com.gediadeepak.smsapp

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal object AppCheckSetup {
    const val verificationError = "Device verification is not ready. Register this development build’s App Check debug token in the Sandesh Firebase project, then try again."
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
