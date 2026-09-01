package com.personal.msgforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.personal.msgforwarder.data.FirebaseHelper
import com.personal.msgforwarder.data.PreferencesHelper

/**
 * Manifest-registered BroadcastReceiver for SMS_RECEIVED.
 *
 * Battery efficiency core:
 * - No background service needed — Android OS delivers SMS events directly.
 * - Checks a LOCAL flag (SharedPreferences) — no network call if inactive.
 * - Only does one Firebase write per SMS when active.
 * - Goes back to sleep immediately after.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = PreferencesHelper(context)

        // Quick local check — no network, no battery cost
        if (!prefs.isActive) {
            Log.d(TAG, "Forwarding inactive, ignoring SMS")
            return
        }

        val code = prefs.pairingCode
        if (code == null) {
            Log.d(TAG, "No pairing code set, ignoring SMS")
            return
        }

        // Extract SMS messages from the intent
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Group message parts by sender (multi-part SMS)
        val grouped = mutableMapOf<String, StringBuilder>()
        for (msg in messages) {
            val sender = msg.displayOriginatingAddress ?: "Unknown"
            grouped.getOrPut(sender) { StringBuilder() }.append(msg.messageBody ?: "")
        }

        // Push each complete message to Firebase
        for ((sender, bodyBuilder) in grouped) {
            val body = bodyBuilder.toString()
            val timestamp = System.currentTimeMillis()

            Log.d(TAG, "Forwarding SMS from $sender: ${body.take(50)}...")

            FirebaseHelper.pushMessage(
                code = code,
                sender = sender,
                body = body,
                timestamp = timestamp
            )
        }
    }
}
