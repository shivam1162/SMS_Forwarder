package com.personal.msgforwarder.data

/**
 * Simple data class representing a forwarded SMS message.
 */
data class MessageData(
    val sender: String = "",
    val body: String = "",
    val timestamp: Long = 0L
)
