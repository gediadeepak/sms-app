package com.gediadeepak.smsapp

import android.app.Application
import com.google.firebase.FirebaseApp

class SandeshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // SMS stays usable in builds without Firebase configuration.
        if (FirebaseApp.initializeApp(this) != null) AppCheckSetup.install()
    }
}
