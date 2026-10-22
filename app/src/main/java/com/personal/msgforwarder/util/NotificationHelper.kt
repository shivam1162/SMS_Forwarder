package com.personal.msgforwarder.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Helper for creating notification channels and showing backup notifications.
 */
object NotificationHelper {

    const val CHANNEL_STATUS = "backup_status"
    private var notificationId = 1000

    /**
     * Creates notification channels. Call once on app start.
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)

            val statusChannel = NotificationChannel(
                CHANNEL_STATUS,
                "Backup Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status notifications for SMS Google Sheets backup"
            }

            manager.createNotificationChannel(statusChannel)
        }
    }

    /**
     * Shows a notification when a message is backed up.
     */
    fun showBackupNotification(context: Context, sender: String, count: Int = 1) {
        val title = if (count == 1) "SMS Backed Up" else "$count Messages Backed Up"
        val message = "Backed up to Google Sheet 'msg_backup' from $sender"

        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId++, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted — silently ignore
        }
    }
}
