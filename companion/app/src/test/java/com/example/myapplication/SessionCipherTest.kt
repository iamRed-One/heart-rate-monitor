package com.example.myapplication

import com.example.myapplication.heart.SessionCipher
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator

class SessionCipherTest {
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test fun sessionRoundTripUsesDifferentCiphertextEachTime() {
        val key = key()
        val secret = "sample-session-cookie".toByteArray()
        val first = SessionCipher.encrypt(secret, key)
        val second = SessionCipher.encrypt(secret, key)
        assertArrayEquals(secret, SessionCipher.decrypt(first, key))
        assertArrayEquals(secret, SessionCipher.decrypt(second, key))
        assertFalse(first.contentEquals(second))
        assertFalse(first.toString(Charsets.ISO_8859_1).contains("sample-session-cookie"))
    }

    @Test(expected = AEADBadTagException::class) fun alteredSessionIsRejected() {
        val key = key()
        val encrypted = SessionCipher.encrypt("sample-session-cookie".toByteArray(), key)
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        SessionCipher.decrypt(encrypted, key)
    }

    @Test(expected = AEADBadTagException::class) fun anotherKeyCannotRestoreTheSession() {
        SessionCipher.decrypt(SessionCipher.encrypt("sample-session-cookie".toByteArray(), key()), key())
    }

    @Test(expected = IllegalArgumentException::class) fun truncatedSessionIsRejected() {
        SessionCipher.decrypt(byteArrayOf(1, 2, 3), key())
    }
}
