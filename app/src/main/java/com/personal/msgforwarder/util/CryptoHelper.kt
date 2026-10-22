package com.personal.msgforwarder.util

import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM authenticated encryption and decryption helper.
 *
 * - Uses SHA-256 to deterministically derive a 256-bit AES key from any passphrase.
 * - Uses a cryptographically secure random 12-byte IV for every encryption call.
 * - Authenticated with 128-bit GCM tag (AEAD) to prevent tampering.
 * - Output format: "ENC:<Base64(IV + Ciphertext + Tag)>"
 */
object CryptoHelper {

    private const val TAG = "CryptoHelper"
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12
    const val PREFIX = "ENC:"

    /**
     * Derives a 256-bit (32 bytes) AES key from the user-provided key using SHA-256.
     */
    private fun deriveKey(passphrase: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(passphrase.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts plaintext using AES-256-GCM.
     * Returns "ENC:<Base64>" string.
     */
    fun encrypt(plainText: String, passphrase: String): String {
        if (plainText.isEmpty() || passphrase.isBlank()) return plainText

        return try {
            val secretKey = deriveKey(passphrase)
            val iv = ByteArray(IV_LENGTH_BYTES).apply {
                SecureRandom().nextBytes(this)
            }

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed: ${e.message}", e)
            plainText
        }
    }

    /**
     * Decrypts an AES-256-GCM encrypted string back to plaintext.
     * Returns null if decryption fails (e.g. wrong key, corrupted data).
     */
    fun decrypt(cipherText: String, passphrase: String): String? {
        if (cipherText.isBlank() || passphrase.isBlank()) return null

        return try {
            val rawBase64 = if (cipherText.startsWith(PREFIX)) {
                cipherText.removePrefix(PREFIX)
            } else {
                cipherText
            }

            val combined = Base64.decode(rawBase64, Base64.NO_WRAP)
            if (combined.size <= IV_LENGTH_BYTES) return null

            val iv = ByteArray(IV_LENGTH_BYTES)
            val cipherBytes = ByteArray(combined.size - IV_LENGTH_BYTES)
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES)
            System.arraycopy(combined, IV_LENGTH_BYTES, cipherBytes, 0, cipherBytes.size)

            val secretKey = deriveKey(passphrase)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            val plainBytes = cipher.doFinal(cipherBytes)

            String(plainBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed: ${e.message}", e)
            null
        }
    }
}
