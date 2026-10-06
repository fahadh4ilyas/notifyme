package com.fahadh.notifyme

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM end-to-end encryption for mirrored notification payloads. */
object AesGcm {
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128

    /** Generates a fresh 256-bit key and returns it base64url-encoded. */
    fun generateSecret(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return base64Url(bytes)
    }

    fun keyFromSecret(secret: String): ByteArray = base64Decode(secret)

    fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return iv + cipher.doFinal(plaintext)
    }

    fun decrypt(key: ByteArray, data: ByteArray): ByteArray? = try {
        val iv = data.copyOfRange(0, IV_LENGTH)
        val ciphertext = data.copyOfRange(IV_LENGTH, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.doFinal(ciphertext)
    } catch (_: Exception) {
        null
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun base64Decode(s: String): ByteArray =
        Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
