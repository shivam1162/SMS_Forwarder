package com.personal.msgforwarder.data

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Manages FCM token lifecycle.
 * On app start, fetches the current FCM token and writes it to Firebase
 * under channels/<code>/devices/<role> so the partner device can send pushes.
 */
object FcmTokenManager {

    private const val TAG = "FcmTokenManager"

    /**
     * Fetches the current FCM token and registers it in Firebase.
     * Should be called on app start and whenever the pairing code or role changes.
     */
    fun registerToken(context: Context) {
        val prefs = PreferencesHelper(context)
        val code = prefs.pairingCode ?: return
        val role = prefs.role ?: return

        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            Log.d(TAG, "FCM token: $token")
            prefs.fcmToken = token
            FirebaseHelper.writeDeviceToken(code, role, token)
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to get FCM token", e)
        }
    }

    /**
     * Called when a new token is generated (from FcmService.onNewToken).
     * Updates both local storage and Firebase.
     */
    fun onNewToken(context: Context, token: String) {
        val prefs = PreferencesHelper(context)
        prefs.fcmToken = token

        val code = prefs.pairingCode ?: return
        val role = prefs.role ?: return
        FirebaseHelper.writeDeviceToken(code, role, token)
    }
}
