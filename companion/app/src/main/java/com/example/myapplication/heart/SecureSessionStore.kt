package com.example.myapplication.heart

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import okhttp3.Cookie
import okhttp3.HttpUrl
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal fun restoredSessionCookies(origin: HttpUrl, saved: List<String>, now: Long): List<Cookie> =
    saved.mapNotNull { Cookie.parse(origin, it) }.filter { it.matches(origin) && it.expiresAt > now }

internal object SessionCipher {
    private val purpose = "HeaRt account session v1".toByteArray(Charsets.UTF_8)

    fun encrypt(plaintext: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(purpose)
        require(cipher.iv.size == 12)
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(plaintext)
    }

    fun decrypt(bytes: ByteArray, key: SecretKey): ByteArray {
        require(bytes.size in 29..8192 && bytes[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(purpose)
        return cipher.doFinal(bytes.copyOfRange(13, bytes.size))
    }
}

/** The session cookie is encrypted; the key stays in Android Keystore, outside backups. */
internal class SecureSessionStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "heart-account-session.bin"))
    private val alias = "heart-account-session-v1"

    private fun key(create: Boolean): SecretKey {
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keys.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "Session key unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    @Synchronized fun read(): JSONObject? {
        if (!file.baseFile.exists()) return null
        return try {
            val bytes = file.openRead().use { it.readBytes() }
            JSONObject(SessionCipher.decrypt(bytes, key(false)).toString(Charsets.UTF_8))
        } catch (_: Exception) { clear(); null }
    }

    @Synchronized fun write(value: JSONObject) {
        val encrypted = SessionCipher.encrypt(value.toString().toByteArray(Charsets.UTF_8), key(true))
        require(encrypted.size <= 8192)
        val output = file.startWrite()
        try { output.write(encrypted); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }

    @Synchronized fun clear() { file.delete() }
}
