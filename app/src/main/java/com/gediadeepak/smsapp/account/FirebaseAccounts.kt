package com.gediadeepak.smsapp.account

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

data class AccountProfile(val configured: Boolean, val googleConfigured: Boolean, val uid: String? = null,
    val email: String? = null, val displayName: String? = null, val verified: Boolean = false)

class FirebaseAccounts(private val context: Context) {
    val app = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
    private val auth = app?.let { FirebaseAuth.getInstance(it) }
    private val clientId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        .takeIf { it != 0 }?.let { context.getString(it) }?.takeIf { it.isNotBlank() }
    private fun required() = auth ?: error("Online accounts are not available in this build yet. SMS and file backups still work.")
    fun profile(): AccountProfile = AccountProfile(auth != null, !clientId.isNullOrBlank(), auth?.currentUser?.uid,
        auth?.currentUser?.email, auth?.currentUser?.displayName, auth?.currentUser?.isEmailVerified == true)
    fun observe(onChanged: (AccountProfile) -> Unit): () -> Unit {
        val listener = FirebaseAuth.AuthStateListener { onChanged(profile()) }
        auth?.addAuthStateListener(listener)
        onChanged(profile())
        return { auth?.removeAuthStateListener(listener) }
    }
    suspend fun register(name: String, email: String, password: String): Boolean {
        val user = required().createUserWithEmailAndPassword(email.trim(), password).await().user ?: error("Unable to create this account.")
        if (name.isNotBlank()) runCatching { user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim().take(60)).build()).await() }
        return runCatching { user.sendEmailVerification().await() }.isSuccess
    }
    suspend fun login(email: String, password: String) { required().signInWithEmailAndPassword(email.trim(), password).await() }
    suspend fun resetPassword(email: String) { required().sendPasswordResetEmail(email.trim()).await() }
    suspend fun verifyEmail() { val user = required().currentUser ?: error("Sign in first."); user.sendEmailVerification().await() }
    suspend fun refresh() { auth?.currentUser?.reload()?.await(); auth?.currentUser?.getIdToken(true)?.await() }
    suspend fun signOut() { auth?.signOut(); runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) } }
    suspend fun google(activity: Activity) {
        val serverId = clientId ?: error("Google sign-in is not available for this build yet. You can use email sign-in.")
        val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val option = GetGoogleIdOption.Builder().setServerClientId(serverId).setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false).setNonce(nonce).build()
        val result = CredentialManager.create(activity).getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val credential = result.credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) { "Google could not return a sign-in credential." }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        required().signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }
    suspend fun verifiedUid(): String {
        refresh()
        val user = required().currentUser ?: error("Sign in to use cloud backup.")
        check(user.isEmailVerified) { "Verify your email before using cloud backup, then tap Refresh account." }
        return user.uid
    }
}
