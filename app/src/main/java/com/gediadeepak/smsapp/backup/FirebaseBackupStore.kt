package com.gediadeepak.smsapp.backup

import com.gediadeepak.smsapp.account.FirebaseAccounts
import com.gediadeepak.smsapp.core.BackupCipher
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID

/** Encrypted chunks in a private Firestore namespace; no Cloud Storage/billing-plan upgrade is performed. */
data class CloudBackupSource(val id:String,val label:String,val updatedAt:Long)

class FirebaseBackupStore(private val accounts: FirebaseAccounts) {
    private val firestore by lazy {
        val app=accounts.app ?: error("Cloud backup is not configured.")
        synchronized(clients) {
            clients.getOrPut(app.name) {
                FirebaseFirestore.getInstance(app).apply {
                    firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
                }
            }
        }
    }
    private val maxChunk = 400_000
    suspend fun sources():List<CloudBackupSource> {
        val uid=accounts.verifiedUid()
        com.gediadeepak.smsapp.FirebaseVerification.check()
        return firestore.collection("sandesh_accounts").document(uid).collection("state")
            .orderBy("updatedAt",com.google.firebase.firestore.Query.Direction.DESCENDING).limit(50)
            .get(Source.SERVER).await().documents.filter {validSource(it.id) && it.getString("backupId")!=null}.map {
                CloudBackupSource(it.id,it.getString("label")?.take(80) ?: if(it.id=="latest") "Manual cloud backup" else "Automatic phone backup",it.getTimestamp("updatedAt")?.toDate()?.time ?: 0)
            }
    }
    suspend fun upload(bytes: ByteArray, sourceId:String="latest", label:String="Manual cloud backup"): Boolean {
        require(validSource(sourceId)) { "Invalid backup source." }
        require(bytes.size <= BackupCipher.MAX_ENCRYPTED_BYTES) { "Cloud backup is too large." }
        val uid = accounts.verifiedUid()
        com.gediadeepak.smsapp.FirebaseVerification.check()
        val root = firestore.collection("sandesh_accounts").document(uid)
        val pointer = root.collection("state").document(sourceId)
        val oldId = pointer.get(Source.SERVER).await().getString("backupId")
        val id = UUID.randomUUID().toString()
        val record = root.collection("backups").document(id)
        val chunks = (bytes.size + maxChunk - 1) / maxChunk
        val digest = sha256(bytes)
        record.set(mapOf("complete" to false, "bytes" to bytes.size, "chunks" to chunks, "sha256" to digest, "createdAt" to FieldValue.serverTimestamp())).await()
        // Each acknowledged batch stays below Firestore's 10 MiB request limit.
        for (first in 0 until chunks step 16) {
            check(accounts.profile().uid == uid) { "Account changed during backup. Sign in and try again." }
            val batch = firestore.batch()
            for (index in first until minOf(first + 16, chunks)) {
                val part = bytes.copyOfRange(index * maxChunk, minOf((index + 1) * maxChunk, bytes.size))
                batch.set(record.collection("chunks").document(index.toString()), mapOf("payload" to Blob.fromBytes(part)))
            }
            batch.commit().await()
        }
        check(accounts.profile().uid == uid) { "Account changed during backup." }
        // Publish only after every chunk has been acknowledged. A failed upload keeps the previous snapshot usable.
        firestore.batch().update(record, "complete", true).set(pointer, mapOf("backupId" to id, "updatedAt" to FieldValue.serverTimestamp(), "label" to label.take(80))).commit().await()
        if (oldId != null && oldId != id && oldId.matches(Regex("[0-9a-f-]{36}"))) {
            return runCatching {
                val old = root.collection("backups").document(oldId)
                val count = old.get(Source.SERVER).await().getLong("chunks")?.toInt() ?: 0
                if (count in 0..MAX_CHUNKS) {
                    for (first in 0 until count step 100) {
                        val batch = firestore.batch()
                        for (part in first until minOf(first + 100, count)) batch.delete(old.collection("chunks").document(part.toString()))
                        batch.commit().await()
                    }
                    old.delete().await()
                }
            }.isSuccess
        }
        return true
    }
    suspend fun downloadLatest(sourceId:String="latest"): ByteArray {
        require(validSource(sourceId)) { "Invalid backup source." }
        val uid = accounts.verifiedUid()
        com.gediadeepak.smsapp.FirebaseVerification.check()
        val root = firestore.collection("sandesh_accounts").document(uid)
        val id = root.collection("state").document(sourceId).get(Source.SERVER).await().getString("backupId") ?: error("No cloud backup exists for this source yet.")
        require(id.matches(Regex("[0-9a-f-]{36}"))) { "Cloud backup metadata is invalid." }
        val record = root.collection("backups").document(id)
        val metadata = record.get(Source.SERVER).await()
        check(metadata.getBoolean("complete") == true) { "That backup is incomplete. Your inbox has not changed." }
        val count = metadata.getLong("chunks")?.toInt() ?: 0
        val size = metadata.getLong("bytes") ?: 0
        require(count in 1..MAX_CHUNKS && size in 1..BackupCipher.MAX_ENCRYPTED_BYTES.toLong()) { "Cloud backup size is invalid." }
        val output = ByteArrayOutputStream()
        repeat(count) { index ->
            check(accounts.profile().uid == uid) { "Account changed during restore." }
            val bytes = record.collection("chunks").document(index.toString()).get(Source.SERVER).await().getBlob("payload")?.toBytes() ?: error("A backup chunk is missing.")
            require(bytes.size <= maxChunk && output.size().toLong() + bytes.size <= size) { "Cloud backup data is invalid." }
            output.write(bytes)
        }
        return output.toByteArray().also { require(it.size.toLong() == size && sha256(it) == metadata.getString("sha256")) { "Cloud backup integrity check failed. Your inbox has not changed." } }
    }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    companion object {
        // Manual pages and background workers share one configured client. Settings cannot change after use.
        private val clients=mutableMapOf<String,FirebaseFirestore>()
        private const val MAX_CHUNKS = 50
        fun validSource(id:String)=com.gediadeepak.smsapp.core.CloudBackupPolicy.validSource(id)
    }
}
