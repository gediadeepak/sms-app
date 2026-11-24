package com.gediadeepak.smsapp.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter

/** Portable password-encrypted backups. No Android-keystore dependency that would prevent recovery on another phone. */
object BackupCipher {
    const val MAX_PLAIN_BYTES = 16 * 1024 * 1024
    const val MAX_ENCRYPTED_BYTES = MAX_PLAIN_BYTES + 1024 * 1024
    private const val ITERATIONS = 600_000
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private val magic = "SANDESH-BACKUP".toByteArray(StandardCharsets.US_ASCII)
    private val headerSize = magic.size + 1 + 4 + SALT_SIZE + IV_SIZE

    fun encrypt(payload: ByteArray, password: CharArray): ByteArray {
        require(password.size >= 12) { "Use a backup password of at least 12 characters." }
        require(payload.size <= MAX_PLAIN_BYTES) { "This backup exceeds the 16 MiB limit." }
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val header = ByteBuffer.allocate(headerSize).put(magic).put(1.toByte()).putInt(ITERATIONS).put(salt).put(iv).array()
        val compressed = ByteArrayOutputStream().also { output -> GZIPOutputStream(output).use { it.write(payload) } }.toByteArray()
        val key = derive(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(header)
            header + cipher.doFinal(compressed)
        } finally { key.fill(0); compressed.fill(0) }
    }

    fun decrypt(encoded: ByteArray, password: CharArray): ByteArray {
        require(encoded.size in (headerSize + 16)..MAX_ENCRYPTED_BYTES) { "The backup file is invalid or too large." }
        val header = encoded.copyOfRange(0, headerSize)
        val buffer = ByteBuffer.wrap(header)
        val marker = ByteArray(magic.size).also { buffer.get(it) }
        require(marker.contentEquals(magic) && buffer.get().toInt() == 1 && buffer.int == ITERATIONS) { "This is not a supported Sandesh backup." }
        val salt = ByteArray(SALT_SIZE).also { buffer.get(it) }
        val iv = ByteArray(IV_SIZE).also { buffer.get(it) }
        val key = derive(password, salt)
        val compressed = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(header)
            // Authenticate the complete file before exposing plaintext to any restore operation.
            cipher.doFinal(encoded, headerSize, encoded.size - headerSize)
        } finally { key.fill(0) }
        return try {
            GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
                val output = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_PLAIN_BYTES) { "The expanded backup exceeds the 16 MiB limit." }
                    output.write(chunk, 0, read)
                }
                output.toByteArray()
            }
        } finally { compressed.fill(0) }
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password))
        val bytes = ByteArray(encoded.remaining()).also { encoded.get(it) }
        return try {
            // BC's lightweight generator also works on API 24/25, where the platform
            // PBKDF2WithHmacSHA256 SecretKeyFactory is not guaranteed to be available.
            val generator = PKCS5S2ParametersGenerator(SHA256Digest())
            generator.init(bytes, salt, ITERATIONS)
            (generator.generateDerivedParameters(256) as KeyParameter).key.clone()
        } finally { bytes.fill(0); if (encoded.hasArray()) encoded.array().fill(0) }
    }
}
