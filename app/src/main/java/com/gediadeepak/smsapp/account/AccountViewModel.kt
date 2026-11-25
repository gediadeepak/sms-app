package com.gediadeepak.smsapp.account

import android.app.Activity
import android.app.Application
import android.net.Uri
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gediadeepak.smsapp.backup.*
import com.gediadeepak.smsapp.core.BackupCipher
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AccountState(
    val profile: AccountProfile = AccountProfile(false, false), val busy: String? = null,
    val notice: String? = null, val error: String? = null, val exportRequest: Long? = null,
    val restorePasswordRequested: Boolean = false, val preview: BackupPreview? = null,
    val inboxChanged: Int = 0, val verificationSentAt: Long = 0,
    val restoring: Boolean = false,
    val cloudSources: List<CloudBackupSource> = emptyList(),
)

class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val accounts = FirebaseAccounts(application.applicationContext)
    private val backups = SmsBackupRepository(application.applicationContext)
    private val cloud = FirebaseBackupStore(accounts)
    private val mutableState = MutableStateFlow(AccountState())
    val state = mutableState.asStateFlow()
    private val stopObserving = accounts.observe { profile -> mutableState.update { it.copy(profile = profile,cloudSources=if(it.profile.uid==profile.uid) it.cloudSources else emptyList()) } }
    private var pendingExport: ByteArray? = null
    private var pendingRestore: ByteArray? = null
    private var restoreFile: Uri? = null
    private var cloudSource = "latest"
    fun cloudSources() = run("Loading cloud backup sources…") {
        val uid=accounts.profile().uid
        val sources=cloud.sources()
        check(accounts.profile().uid==uid) { "Account changed. Reload backup sources." }
        mutableState.update {it.copy(cloudSources=sources,notice=if(sources.isEmpty()) "No completed cloud backups exist for this account." else null)}
    }
    fun chooseCloudSource(source:String) { require(FirebaseBackupStore.validSource(source));cloudSource=source }

    private fun run(label: String, timeout: Boolean = true, action: suspend () -> Unit) {
        if (state.value.busy != null) return
        mutableState.update { it.copy(busy = label, error = null, notice = null) }
        viewModelScope.launch {
            try { if (timeout) withTimeout(120_000) { action() } else action() }
            catch (_: GetCredentialCancellationException) { /* The user cancelled Google sign-in. */ }
            catch (_: TimeoutCancellationException) { mutableState.update { it.copy(error = "The service did not confirm completion. Check your connection and try again.") } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.update { it.copy(error = friendlyError(e)) } }
            finally { mutableState.update { it.copy(busy = null, profile = accounts.profile()) } }
        }
    }
    fun clearMessage() { mutableState.update { it.copy(error = null, notice = null) } }
    fun register(name: String, email: String, password: String) = run("Creating your account…") {
        require(password.length >= 8) { "Choose an account password with at least 8 characters." }
        val sent = accounts.register(name, email, password)
        mutableState.update { it.copy(notice = if (sent) "Account created. Check your email to verify it before using cloud backup." else "Account created, but the verification email could not be sent. Use Resend verification below.", verificationSentAt = if (sent) System.currentTimeMillis() else 0) }
    }
    fun login(email: String, password: String) = run("Signing in…") { accounts.login(email, password) }
    fun google(activity: Activity) = run("Waiting for Google sign-in…", timeout = false) { accounts.google(activity) }
    fun forgot(email: String) = run("Requesting a reset email…") {
        accounts.resetPassword(email)
        mutableState.update { it.copy(notice = "If this email has an account, a password-reset link has been requested. Check your inbox and spam folder.") }
    }
    fun verifyEmail() = run("Sending verification email…") {
        check(System.currentTimeMillis() - state.value.verificationSentAt >= 60_000) { "Please wait a minute before requesting another verification email." }
        accounts.verifyEmail(); mutableState.update { it.copy(notice = "Verification email sent.", verificationSentAt = System.currentTimeMillis()) }
    }
    fun refreshAccount() = run("Refreshing your account…") { accounts.refresh() }
    fun signOut() = run("Signing out…") { val auto=AutomaticBackups(getApplication());if(auto.prefs.getString("mode",null)=="cloud") auto.disable(); clearRestore(); accounts.signOut() }

    private suspend fun encryptedSnapshot(password: CharArray): ByteArray = withContext(Dispatchers.Default) {
        val plain = withContext(Dispatchers.IO) { backups.snapshot() }
        try { BackupCipher.encrypt(plain, password) } finally { plain.fill(0) }
    }
    fun export(password: CharArray) {
        if (state.value.busy != null) { password.fill('\u0000'); return }
        run("Encrypting your file backup…") {
            try {
                pendingExport?.fill(0)
                pendingExport = encryptedSnapshot(password)
                mutableState.update { it.copy(exportRequest = System.nanoTime()) }
            } finally { password.fill('\u0000') }
        }
    }
    fun exportRequestHandled() { mutableState.update { it.copy(exportRequest = null) } }
    fun saveExport(uri: Uri?) {
        if (uri == null) { pendingExport?.fill(0); pendingExport = null; return }
        run("Saving encrypted backup…") {
            val bytes = pendingExport ?: error("Backup export was interrupted. Please create it again.")
            try { withContext(Dispatchers.IO) { backups.writeEncrypted(uri, bytes); AutomaticBackups.completed(getApplication(),"file") }; mutableState.update { it.copy(notice = "Encrypted backup saved. Keep its password somewhere safe; it cannot be reset.") } }
            finally { bytes.fill(0); pendingExport = null }
        }
    }
    fun backupCloud(password: CharArray) {
        if (state.value.busy != null) { password.fill('\u0000'); return }
        run("Encrypting and uploading backup…") {
            try {
                val bytes = encryptedSnapshot(password)
                try {
                    val cleaned = cloud.upload(bytes)
                    AutomaticBackups.completed(getApplication(),"cloud")
                    mutableState.update { it.copy(notice = if (cleaned) "Encrypted cloud backup saved to your account." else "Cloud backup saved. The older encrypted snapshot could not be cleaned up.") }
                } finally { bytes.fill(0) }
            } finally { password.fill('\u0000') }
        }
    }
    fun selectRestoreFile(uri: Uri?) {
        if (uri == null || state.value.busy != null) return
        clearRestore(); restoreFile = uri
        mutableState.update { it.copy(restorePasswordRequested = true) }
    }
    fun restorePasswordHandled() { mutableState.update { it.copy(restorePasswordRequested = false) } }
    fun inspectRestore(password: CharArray, fromCloud: Boolean) {
        if (state.value.busy != null) { password.fill('\u0000'); return }
        run("Verifying encrypted backup…") {
            try {
                val encoded = if (fromCloud) cloud.downloadLatest(cloudSource) else withContext(Dispatchers.IO) { backups.readEncrypted(restoreFile ?: error("Choose a backup file first.")) }
                val plain = try { withContext(Dispatchers.Default) { BackupCipher.decrypt(encoded, password) } } finally { encoded.fill(0) }
                try {
                    val preview = withContext(Dispatchers.Default) { backups.validate(plain) }
                    pendingRestore?.fill(0); pendingRestore = plain
                    mutableState.update { it.copy(preview = preview) }
                } catch (e: Exception) { plain.fill(0); throw e }
            } finally { password.fill('\u0000') }
        }
    }
    fun confirmRestore() = run("Restoring messages. Keep Sandesh open…", timeout = false) {
        val plain = pendingRestore ?: error("Restore preview expired. Choose the backup again.")
        mutableState.update { it.copy(restoring = true) }
        try {
            val result = withContext(Dispatchers.IO) { backups.restore(plain) }
            mutableState.update { it.copy(notice = "Restored ${result.inserted} messages; skipped ${result.skipped} already present. Choose your default SIM again in Settings.") }
        } finally { clearRestore(); mutableState.update { it.copy(inboxChanged = it.inboxChanged + 1, restoring = false) } }
    }
    fun clearRestore() { pendingRestore?.fill(0); pendingRestore = null; restoreFile = null; mutableState.update { it.copy(preview = null, restorePasswordRequested = false) } }
    override fun onCleared() { stopObserving(); pendingExport?.fill(0); pendingRestore?.fill(0); super.onCleared() }

    private fun friendlyError(error: Exception): String = when (error) {
        is javax.crypto.AEADBadTagException -> "The backup password is incorrect, or the file is damaged. Your inbox has not changed."
        is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" -> "Unable to sign in. Check your email and password."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "This email already has an account. Sign in or reset its password."
            "ERROR_OPERATION_NOT_ALLOWED" -> "This sign-in method is not enabled for Sandesh yet."
            "ERROR_INVALID_EMAIL" -> "Enter a valid email address."
            "ERROR_WEAK_PASSWORD" -> "Choose a stronger account password."
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> "Use the sign-in method originally used for this email."
            "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please wait and try again."
            else -> "Account service could not complete the request. Check your connection and try again."
        }
        is FirebaseFirestoreException -> if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) "Cloud backup access was denied after verification. Refresh your account and check that the Sandesh Firestore rules allow this verified account. File backup still works without Firebase." else "Cloud backup could not complete. Check your connection and try again."
        is IllegalArgumentException, is IllegalStateException -> error.message ?: "The operation could not be completed."
        else -> "The operation could not be completed. Check your connection or selected backup file and try again."
    }
}
