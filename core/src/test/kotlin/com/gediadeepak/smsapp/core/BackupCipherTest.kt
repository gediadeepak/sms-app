package com.gediadeepak.smsapp.core

import org.junit.Assert.*
import org.junit.Test

class BackupCipherTest {
    private val password get() = "test-only long backup passphrase ☀".toCharArray()
    @Test fun `encrypted backups round trip unicode and use fresh nonces`() {
        val plain = "Private SMS body — café ☕\n+44 7700 900123".toByteArray()
        val first = BackupCipher.encrypt(plain, password)
        val second = BackupCipher.encrypt(plain, password)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(plain, BackupCipher.decrypt(first, password))
        assertFalse(first.toString(Charsets.ISO_8859_1).contains("Private SMS body"))
    }
    @Test fun `wrong password and modified ciphertext never return plaintext`() {
        val sealed = BackupCipher.encrypt("do not restore unauthenticated data".toByteArray(), password)
        assertThrows(Exception::class.java) { BackupCipher.decrypt(sealed, "wrong backup password".toCharArray()) }
        val modified = sealed.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { BackupCipher.decrypt(modified, password) }
    }
    @Test fun `truncated and foreign files are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { BackupCipher.decrypt(byteArrayOf(1, 2, 3), password) }
        assertThrows(IllegalArgumentException::class.java) { BackupCipher.decrypt(ByteArray(200), password) }
        assertThrows(IllegalArgumentException::class.java) { BackupCipher.encrypt(byteArrayOf(1), "short".toCharArray()) }
    }
}
