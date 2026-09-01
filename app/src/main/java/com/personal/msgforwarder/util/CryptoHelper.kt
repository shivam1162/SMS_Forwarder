package com.personal.msgforwarder.util

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Provides AES-256-GCM End-to-End Encryption (E2EE) using the shared pairing code.
 *
 * Security:
 * - Key derivation: PBKDF2WithHmacSHA256 (10,000 iterations)
 * - Cipher: AES-256-GCM (Authenticated Encryption with Associated Data)
 * - Nonce/IV: 12-byte unique cryptographically secure random bytes per message
 * - Tag length: 128-bit authentication tag
 *
 * Result: All SMS text and sender phone numbers stored in Firebase are unreadable ciphertext.
 * Only devices possessing the pairing code can decrypt them.
 */
object CryptoHelper {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256

    // Salt to prevent rainbow table attacks on the 6-digit pairing code
    private val SALT = "msgforwarder_e2ee_salt_2026".toByteArray(Charsets.UTF_8)

    private fun deriveKey(pairingCode: String): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pairingCode.toCharArray(), SALT, ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, ALGORITHM)
    }

    /**
     * Encrypts the message (sender + body) into a Base64 string containing [IV + Ciphertext + Tag].
     */
    fun encrypt(sender: String, body: String, pairingCode: String): String {
        val jsonPayload = JSONObject().apply {
            put("s", sender)
            put("b", body)
        }.toString()

        val key = deriveKey(pairingCode)
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec)

        val cipherText = cipher.doFinal(jsonPayload.toByteArray(Charsets.UTF_8))

        // Combine IV + Ciphertext
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts the Base64 ciphertext into a Pair(sender, body).
     * Returns null if decryption or authentication tag fails (wrong pairing code / tampered data).
     */
    fun decrypt(encryptedBase64: String, pairingCode: String): Pair<String, String>? {
        if (encryptedBase64.isBlank()) return null

        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size <= IV_LENGTH) return null

            val iv = ByteArray(IV_LENGTH)
            val cipherText = ByteArray(combined.size - IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH)
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.size)

            val key = deriveKey(pairingCode)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec)

            val decryptedBytes = cipher.doFinal(cipherText)
            val jsonString = String(decryptedBytes, Charsets.UTF_8)
            val json = JSONObject(jsonString)

            val sender = json.optString("s", "Unknown")
            val body = json.optString("b", "")
            Pair(sender, body)
        } catch (e: Exception) {
            null
        }
    }
}
