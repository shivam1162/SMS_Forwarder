package com.personal.msgforwarder.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.personal.msgforwarder.data.FcmTokenManager
import com.personal.msgforwarder.data.PreferencesHelper
import com.personal.msgforwarder.receiver.BootReceiver
import com.personal.msgforwarder.util.NotificationHelper

/**
 * Firebase Cloud Messaging service.
 *
 * Handles incoming FCM messages:
 * - type=activate  → saves isActive=true, starts heartbeat worker
 * - type=deactivate → saves isActive=false, stops heartbeat
 * - type=message   → shows notification with forwarded SMS (receiver phone)
 *
 * Also handles token refresh via onNewToken().
 */
class FcmService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FcmService"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val type = data["type"] ?: return

        Log.d(TAG, "FCM message received: type=$type")

        when (type) {
            "activate" -> {
                val prefs = PreferencesHelper(this)
                prefs.isActive = true
                Log.d(TAG, "Forwarding activated via FCM")

                // Schedule heartbeat worker
                BootReceiver.scheduleHeartbeat(this)
            }

            "deactivate" -> {
                val prefs = PreferencesHelper(this)
                prefs.isActive = false
                Log.d(TAG, "Forwarding deactivated via FCM")

                // Cancel heartbeat worker
                BootReceiver.cancelHeartbeat(this)
            }

            "message" -> {
                val sender = data["sender"] ?: "Unknown"
                val body = data["body"] ?: ""
                Log.d(TAG, "Forwarded message received from $sender")

                // Show notification on receiver phone
                NotificationHelper.showMessageNotification(this, sender, body)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")

        // Update token in local storage and Firebase
        FcmTokenManager.onNewToken(this, token)
    }
}
