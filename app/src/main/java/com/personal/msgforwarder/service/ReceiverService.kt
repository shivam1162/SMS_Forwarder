package com.personal.msgforwarder.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.firebase.database.ChildEventListener
import com.personal.msgforwarder.MainActivity
import com.personal.msgforwarder.data.FirebaseHelper
import com.personal.msgforwarder.data.PreferencesHelper
import com.personal.msgforwarder.util.NotificationHelper

/**
 * Foreground service running on Receiver's phone ONLY while activation is active.
 * Ensures your phone receives forwarded SMS notifications even when the app is in background or closed.
 */
class ReceiverService : Service() {

    companion object {
        private const val TAG = "ReceiverService"
        private const val FOREGROUND_NOTIFICATION_ID = 2001
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        fun start(context: Context) {
            val intent = Intent(context, ReceiverService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ReceiverService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var messageListener: ChildEventListener? = null
    private var pairingCode: String? = null
    private var isFirstLoad = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startForegroundWithNotification()
                startListening()
            }
            else -> {
                startForegroundWithNotification()
                startListening()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("SMS Forwarder Active")
            .setContentText("Listening for forwarded messages from Mom's phone...")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                FOREGROUND_NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun startListening() {
        val prefs = PreferencesHelper(this)
        val code = prefs.pairingCode ?: return

        if (messageListener != null && pairingCode == code) {
            return // already listening
        }

        stopListening()
        pairingCode = code
        val serviceStartTime = System.currentTimeMillis() - 5000 // only show notifs for new msgs

        // Purge messages older than 30 minutes from Firebase
        FirebaseHelper.purgeOldMessages(code)

        messageListener = FirebaseHelper.listenForMessages(code) { rawMessage ->
            val message = rawMessage.decrypted(code)
            Log.d(TAG, "New message received from: ${message.sender}")
            if (message.timestamp >= serviceStartTime) {
                NotificationHelper.showMessageNotification(this, message.sender, message.body)
            }
        }
    }

    private fun stopListening() {
        val code = pairingCode
        val listener = messageListener
        if (code != null && listener != null) {
            FirebaseHelper.removeMessagesListener(code, listener)
        }
        messageListener = null
    }

    private fun stopForegroundService() {
        stopListening()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
    }
}
