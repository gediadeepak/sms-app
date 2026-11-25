package com.gediadeepak.smsapp.backup

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import androidx.work.*
import com.gediadeepak.smsapp.account.FirebaseAccounts
import com.gediadeepak.smsapp.core.BackupCipher
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray

class AutomaticBackups(private val context:Context) {
    val prefs=context.getSharedPreferences("sandesh_auto_backup",Context.MODE_PRIVATE)
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        return (store.getKey("sandesh-auto-backup",null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("sandesh-auto-backup",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun enable(password:CharArray,mode:String,folder:String,weekly:Boolean) {
        try {
            require(password.size>=12){"Use a backup password of at least 12 characters."}
            require(mode=="file" || mode=="cloud")
            val uid=if(mode=="cloud") FirebaseAccounts(context).profile().let {require(it.uid!=null && it.verified){"Sign in with a verified account first."};it.uid!!} else ""
            if(mode=="file") require(DocumentFile.fromTreeUri(context,Uri.parse(folder))?.canWrite()==true){"Select a writable backup folder."}
            val buffer=StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));val plain=ByteArray(buffer.remaining()).also {buffer.get(it)}
            val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
            val wrapped=try {cipher.doFinal(plain)} finally {plain.fill(0);if(buffer.hasArray())buffer.array().fill(0)}
            disable()
            check(prefs.edit().putBoolean("enabled",true).putString("config",UUID.randomUUID().toString()).putString("device",prefs.getString("device",null) ?: UUID.randomUUID().toString()).putString("mode",mode).putString("folder",folder).putString("uid",uid)
                .putString("password",Base64.encodeToString(wrapped,Base64.NO_WRAP)).putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).putBoolean("weekly",weekly).putString("files","[]").putString("error","").commit())
            val request=PeriodicWorkRequestBuilder<BackupWorker>(if(weekly)7 else 1,TimeUnit.DAYS)
                .setInitialDelay(if(weekly)7 else 1,TimeUnit.DAYS).setConstraints(constraints(mode)).build()
            try {WorkManager.getInstance(context).enqueueUniquePeriodicWork("sandesh-auto-backup",ExistingPeriodicWorkPolicy.UPDATE,request).result.get(15,TimeUnit.SECONDS)}
            catch(e:Exception){disable();throw IllegalStateException("Could not schedule backups. Try again.",e)}
        } finally {password.fill('\u0000')}
    }
    fun disable() {
        prefs.edit().putBoolean("enabled",false).remove("password").remove("iv").remove("config").commit()
        WorkManager.getInstance(context).cancelUniqueWork("sandesh-auto-backup")
        WorkManager.getInstance(context).cancelUniqueWork("sandesh-backup-now")
    }
    fun runNow() {
        check(prefs.getBoolean("enabled",false)){"Enable an automatic backup destination first."}
        WorkManager.getInstance(context).enqueueUniqueWork("sandesh-backup-now",ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<BackupWorker>().setConstraints(constraints(prefs.getString("mode","file")!!)).build())
    }
    private fun constraints(mode:String)=Constraints.Builder().setRequiresStorageNotLow(true).apply {if(mode=="cloud")setRequiredNetworkType(NetworkType.CONNECTED)}.build()
    fun password():CharArray {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(prefs.getString("iv",null),Base64.NO_WRAP)))
        val bytes=cipher.doFinal(Base64.decode(prefs.getString("password",null),Base64.NO_WRAP))
        return try {val chars=StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes));CharArray(chars.remaining()).also {chars.get(it);if(chars.hasArray())chars.array().fill('\u0000')}} finally {bytes.fill(0)}
    }
    companion object {
        fun completed(context:Context,kind:String) {context.getSharedPreferences("sandesh_backup_status",Context.MODE_PRIVATE).edit().putLong(kind,System.currentTimeMillis()).apply()}
    }
}

class BackupWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result=withContext(Dispatchers.IO) {
        // Serialize manual Run now and periodic jobs on this device.
        lock.lock()
        try {
            val store=AutomaticBackups(applicationContext);val prefs=store.prefs
            val config=prefs.getString("config",null)
            if(!prefs.getBoolean("enabled",false) || config==null) return@withContext Result.success()
            try {
                withTimeout(120_000) {
                    val mode=prefs.getString("mode","file")!!;val accounts=FirebaseAccounts(applicationContext)
                    if(mode=="cloud") check(accounts.profile().uid==prefs.getString("uid",null)){"Automatic backup paused: sign back in to the configured account."}
                    val password=store.password()
                    val encrypted=try {
                        val plain=SmsBackupRepository(applicationContext).snapshot()
                        try {BackupCipher.encrypt(plain,password)} finally {plain.fill(0)}
                    } finally {password.fill('\u0000')}
                    try {
                        ensureActive()
                        check(prefs.getBoolean("enabled",false) && prefs.getString("config",null)==config){"Backup settings changed."}
                        if(mode=="cloud") {
                            val device=prefs.getString("device",null) ?: error("Re-enable automatic backups to register this phone.")
                            FirebaseBackupStore(accounts).upload(encrypted,com.gediadeepak.smsapp.core.CloudBackupPolicy.automaticSource(device),android.os.Build.MODEL.take(60)+" · "+device.take(6))
                            AutomaticBackups.completed(applicationContext,"cloud")
                        }
                        else {
                            val tree=DocumentFile.fromTreeUri(applicationContext,Uri.parse(prefs.getString("folder",null))) ?: error("Backup folder is unavailable. Select it again.")
                            val file=tree.createFile("application/octet-stream","Sandesh-auto-"+System.currentTimeMillis()+".sandesh") ?: error("Could not create backup file.")
                            try {SmsBackupRepository(applicationContext).writeEncrypted(file.uri,encrypted);ensureActive();check(prefs.getString("config",null)==config){"Backup settings changed."}} catch(e:Exception){file.delete();throw e}
                            val history=JSONArray(prefs.getString("files","[]"));history.put(file.uri.toString())
                            while(history.length()>7) {val old=Uri.parse(history.getString(0));runCatching {DocumentFile.fromSingleUri(applicationContext,old)?.delete()};history.remove(0)}
                            prefs.edit().putString("files",history.toString()).apply();AutomaticBackups.completed(applicationContext,"file")
                        }
                        if(prefs.getString("config",null)==config) prefs.edit().putLong("last_success",System.currentTimeMillis()).putString("error","").apply()
                    } finally {encrypted.fill(0)}
                }
                Result.success()
            } catch(e:TimeoutCancellationException) {
                prefs.edit().putString("error","Automatic backup timed out. Check the connection and run it again.").apply();Result.success()
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                prefs.edit().putString("error",(e.message ?: "Automatic backup failed. Check storage, account and device verification.").take(500)).apply()
                if(e !is IllegalStateException && e !is IllegalArgumentException && runAttemptCount<2) Result.retry() else Result.success()
            }
        } finally {lock.unlock()}
    }
    companion object {private val lock=kotlinx.coroutines.sync.Mutex()}
}
