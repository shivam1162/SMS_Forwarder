package com.personal.msgforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.personal.msgforwarder.data.MessageData
import com.personal.msgforwarder.data.PreferencesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Manifest-registered BroadcastReceiver for SMS_RECEIVED.
 *
 * Battery efficiency & Reliability:
 * - No persistent background service needed — Android OS delivers SMS events directly.
 * - When an SMS arrives, it uses goAsync() to hold the wake lock for a few seconds.
 * - Checks live activation state from Firebase Realtime Database.
 * - If active, pushes the SMS to Firebase immediately and updates heartbeat.
 * - Completes within ~1 second and allows the device to go back to sleep.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = PreferencesHelper(context)
        val code = prefs.pairingCode

        if (code.isNullOrBlank()) {
            Log.d(TAG, "No pairing code configured, ignoring SMS")
            return
        }

        // Extract SMS messages from the intent
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            Log.d(TAG, "No SMS messages found in intent")
            return
        }

        // Group multi-part SMS by originating address
        val groupedMessages = mutableMapOf<String, StringBuilder>()
        for (msg in messages) {
            val sender = msg.displayOriginatingAddress ?: "Unknown"
            groupedMessages.getOrPut(sender) { StringBuilder() }.append(msg.messageBody ?: "")
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fetch live activation state from Firebase with a 5-second timeout
                val database = FirebaseDatabase.getInstance()
                val channelRef = database.getReference("channels").child(code)

                val activeSnapshot = withTimeoutOrNull(5000L) {
                    channelRef.child("active").get().await()
                }

                val isLiveActive = activeSnapshot?.getValue(Boolean::class.java) ?: prefs.isActive
                Log.d(TAG, "SMS received. Live active status in Firebase: $isLiveActive (local cached: ${prefs.isActive})")

                // Update local cache
                prefs.isActive = isLiveActive

                if (isLiveActive) {
                    val timestamp = System.currentTimeMillis()

                    for ((sender, bodyBuilder) in groupedMessages) {
                        val body = bodyBuilder.toString()
                        Log.d(TAG, "Forwarding SMS from $sender: ${body.take(30)}...")

                        val message = MessageData(
                            sender = sender,
                            body = body,
                            timestamp = timestamp
                        )

                        // Push message to Firebase
                        channelRef.child("messages").push().setValue(message).await()
                    }

                    // Also update heartbeat to show Mom's phone is currently active & online
                    channelRef.child("heartbeat").child("lastSeen").setValue(timestamp)
                    Log.d(TAG, "Successfully forwarded SMS to Firebase channel: $code")
                } else {
                    Log.d(TAG, "Forwarding is inactive, discarded SMS.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing/forwarding SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
