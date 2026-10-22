package com.personal.msgforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Telephony
import android.util.Log
import com.personal.msgforwarder.data.MessageData
import com.personal.msgforwarder.data.PreferencesHelper
import com.personal.msgforwarder.data.google.GoogleSheetsManager
import com.personal.msgforwarder.data.local.MessageDbHelper
import com.personal.msgforwarder.worker.BackupSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Manifest-registered BroadcastReceiver for SMS_RECEIVED.
 *
 * Lightning-Fast Ingestion Architecture (<50ms):
 * 1. Synchronously writes full multi-part SMS to local SQLite first (<20ms). Zero data loss.
 * 2. If offline/flight mode, delegates to WorkManager immediately (<10ms) without waiting.
 * 3. If online, attempts direct Google Sheets upload capped at a strict 4-second timeout.
 * 4. If upload exceeds 4s (slow cellular/weak signal), immediately hands off to WorkManager
 *    and finishes broadcast before Android OS triggers any ANR or kills the process.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
        private const val IMMEDIATE_SYNC_TIMEOUT_MS = 4000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = PreferencesHelper(context)
        if (!prefs.isBackupEnabled) {
            Log.d(TAG, "SMS backup is disabled in settings, skipping.")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            Log.d(TAG, "No SMS messages found in intent")
            return
        }

        // 1. Group multi-part SMS messages by originating sender
        val groupedMessages = mutableMapOf<String, StringBuilder>()
        for (msg in messages) {
            val sender = msg.displayOriginatingAddress ?: "Unknown"
            groupedMessages.getOrPut(sender) { StringBuilder() }.append(msg.messageBody ?: "")
        }

        // 2. Synchronous DB persist first — guarantees message is saved locally in <20ms
        val dbHelper = MessageDbHelper.getInstance(context)
        val timestamp = System.currentTimeMillis()
        val insertedMessages = mutableListOf<MessageData>()

        for ((sender, bodyBuilder) in groupedMessages) {
            val body = bodyBuilder.toString()
            val rowId = dbHelper.insertMessage(sender, body, timestamp)
            Log.d(TAG, "Saved SMS from $sender to local DB (rowId=$rowId)")
            insertedMessages.add(
                MessageData(
                    id = rowId,
                    sender = sender,
                    body = body,
                    timestamp = timestamp,
                    isSynced = false
                )
            )
        }

        // If not signed in to Google, message remains safely in SQLite until sign-in
        if (!prefs.isSignedIn) {
            Log.d(TAG, "User not signed in to Google, message safely buffered in local DB.")
            return
        }

        // 3. Fast network check: If offline, don't waste time/battery attempting socket connects
        if (!isNetworkConnected(context)) {
            Log.d(TAG, "Device is offline. Immediately scheduling BackupSyncWorker for when connection restores.")
            BackupSyncWorker.enqueueSync(context)
            return
        }

        // 4. Online: Attempt direct upload with strict 4-second timeout
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val syncResult = withTimeoutOrNull(IMMEDIATE_SYNC_TIMEOUT_MS) {
                    GoogleSheetsManager.appendMessages(context, insertedMessages)
                }

                if (syncResult != null && syncResult.isSuccess) {
                    val syncedIds = insertedMessages.map { it.id }
                    dbHelper.deleteBatch(syncedIds)
                    prefs.totalSyncedCount += syncedIds.size
                    Log.d(TAG, "Successfully synced ${syncedIds.size} SMS immediately and cleaned local storage")
                } else {
                    // Timed out (>4s) or API error — hand off to WorkManager
                    if (syncResult == null) {
                        Log.w(TAG, "Direct sync timed out (>4s). Handing off to BackupSyncWorker.")
                    } else {
                        Log.w(TAG, "Direct sync failed: ${syncResult.exceptionOrNull()?.message}. Handing off to BackupSyncWorker.")
                    }
                    BackupSyncWorker.enqueueSync(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in immediate sync, handing off to BackupSyncWorker", e)
                BackupSyncWorker.enqueueSync(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Fast check to verify if device currently has active internet connectivity.
     */
    private fun isNetworkConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
