package com.personal.msgforwarder.data

/**
 * Message data model representing an SMS message.
 */
data class MessageData(
    val id: Long = 0L,
    val sender: String = "",
    val body: String = "",
    val timestamp: Long = 0L,
    val isSynced: Boolean = false,
    val syncedAt: Long? = null
)
