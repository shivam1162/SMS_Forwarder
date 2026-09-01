package com.personal.msgforwarder.data

import com.personal.msgforwarder.util.CryptoHelper

/**
 * Message data model stored in Firebase Realtime Database.
 * With E2EE enabled:
 * - 'encrypted' holds the AES-256-GCM ciphertext payload (Base64).
 * - 'sender' and 'body' can hold decrypted or placeholder values.
 */
data class MessageData(
    val sender: String = "",
    val body: String = "",
    val timestamp: Long = 0L,
    val encrypted: String = ""
) {
    /**
     * Returns a decrypted copy of MessageData using the shared pairing code.
     * If unencrypted or decryption fails, falls back gracefully.
     */
    fun decrypted(pairingCode: String): MessageData {
        if (encrypted.isNotBlank()) {
            val decryptedPair = CryptoHelper.decrypt(encrypted, pairingCode)
            if (decryptedPair != null) {
                return copy(
                    sender = decryptedPair.first,
                    body = decryptedPair.second
                )
            }
        }
        return this
    }
}
