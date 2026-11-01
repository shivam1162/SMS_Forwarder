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
import com.personal.msgforwarder.data.PreferencesHelper
import com.personal.msgforwarder.data.google.GoogleSheetsManager

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
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Backup Status Channel (Low priority, quiet)
            val statusChannel = NotificationChannel(
                CHANNEL_STATUS,
                "Backup Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows notifications when messages are backed up"
                setShowBadge(false)
            }
            nm.createNotificationChannel(statusChannel)

            // Auth Recovery Channel (High priority, alert user to re-authenticate)
            val authChannel = NotificationChannel(
                CHANNEL_AUTH,
                "Action Required",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when Google re-authentication is required"
                setShowBadge(true)
                enableVibration(true)
            }
            nm.createNotificationChannel(authChannel)
        }
    }

    /**
     * Shows a high-priority actionable notification when Google OAuth needs user re-consent.
     * Tapping the notification opens MainActivity so the user can re-authenticate.
     */
    fun showAuthRecoveryNotification(context: Context, recoveryIntent: Intent?) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            2001,
            recoveryIntent ?: tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_AUTH)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("SMS Backup: Action Required")
            .setContentText("Tap to re-authorize Google Drive access for SMS backup")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Google Drive access needs re-authorization. Tap to restore automatic SMS backups.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(2001, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    /**
     * Shows a notification when a message is backed up.
     */
    fun showBackupNotification(context: Context, sender: String, count: Int = 1) {
        val sheetName = GoogleSheetsManager.resolveSpreadsheetName(PreferencesHelper(context).googleAccountEmail)
        val title = if (count == 1) "SMS Backed Up" else "$count Messages Backed Up"
        val message = "Backed up to Google Sheet '$sheetName' from $sender"

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
            e.printStackTrace()
        }
    }
}
