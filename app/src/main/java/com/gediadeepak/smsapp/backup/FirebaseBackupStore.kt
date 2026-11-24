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
class FirebaseBackupStore(private val accounts: FirebaseAccounts) {
    private val firestore by lazy { FirebaseFirestore.getInstance(accounts.app ?: error("Cloud backup is not configured.")).apply {
        firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
    } }
    private val maxChunk = 400_000
    suspend fun upload(bytes: ByteArray): Boolean {
        require(bytes.size <= BackupCipher.MAX_ENCRYPTED_BYTES) { "Cloud backup is too large." }
        val uid = accounts.verifiedUid()
        val root = firestore.collection("sandesh_accounts").document(uid)
        val pointer = root.collection("state").document("latest")
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
        firestore.batch().update(record, "complete", true).set(pointer, mapOf("backupId" to id, "updatedAt" to FieldValue.serverTimestamp())).commit().await()
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
    suspend fun downloadLatest(): ByteArray {
        val uid = accounts.verifiedUid()
        val root = firestore.collection("sandesh_accounts").document(uid)
        val id = root.collection("state").document("latest").get(Source.SERVER).await().getString("backupId") ?: error("No cloud backup exists for this account yet.")
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
    companion object { private const val MAX_CHUNKS = 50 }
}
