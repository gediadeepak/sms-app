package com.gediadeepak.smsapp

import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

object FirebaseVerification {
    suspend fun check() {
        try { withTimeout(15_000) { FirebaseAppCheck.getInstance().getAppCheckToken(false).await() } }
        catch(e:TimeoutCancellationException) { throw IllegalStateException("Device verification timed out. Check the internet connection and retry.") }
        catch(e:CancellationException) { throw e }
        catch(e:Exception) { throw IllegalStateException(AppCheckSetup.verificationError,e) }
    }
}
