package com.personal.msgforwarder.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.personal.msgforwarder.MainActivity

/**
 * Helper for creating notification channels and showing backup notifications.
 */
object NotificationHelper {

    const val CHANNEL_STATUS = "backup_status"
    const val CHANNEL_AUTH = "google_auth"
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

            val authChannel = NotificationChannel(
                CHANNEL_AUTH,
                "Google Account Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when Google account requires re-authentication"
            }

            manager.createNotificationChannel(statusChannel)
            manager.createNotificationChannel(authChannel)
        }
    }

    /**
     * Shows a high-priority actionable notification when Google requires re-authentication.
     */
    fun showAuthRecoveryNotification(context: Context, recoveryIntent: Intent?) {
        val targetIntent = recoveryIntent ?: Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1001,
            targetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_AUTH)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Google Sign-In Required")
            .setContentText("Tap to re-authenticate Google account so SMS backup can continue.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Google requires you to re-authenticate or confirm permissions. Tap here to resume automatic SMS backup."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(2001, notification)
        } catch (e: SecurityException) {
            // Silently ignore
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
