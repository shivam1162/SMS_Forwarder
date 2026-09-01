package com.personal.msgforwarder.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Helper for creating notification channels and showing notifications.
 */
object NotificationHelper {

    const val CHANNEL_MESSAGES = "forwarded_messages"
    const val CHANNEL_STATUS = "forwarding_status"

    private var notificationId = 1000

    /**
     * Creates notification channels. Call once on app start.
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)

            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Forwarded Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming forwarded SMS messages"
            }

            val statusChannel = NotificationChannel(
                CHANNEL_STATUS,
                "Forwarding Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status notifications for SMS forwarding"
            }

            manager.createNotificationChannel(messagesChannel)
            manager.createNotificationChannel(statusChannel)
        }
    }

    /**
     * Shows a notification for a forwarded SMS message (on receiver phone).
     */
    fun showMessageNotification(context: Context, sender: String, body: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("SMS from $sender")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId++, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted — silently ignore
        }
    }

    /**
     * Shows a low-priority status notification (on sender phone).
     */
    fun showStatusNotification(context: Context, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("SMS Forwarder")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(0, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted — silently ignore
        }
    }
}
