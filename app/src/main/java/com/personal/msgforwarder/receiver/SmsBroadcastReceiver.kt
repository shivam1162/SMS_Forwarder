package com.personal.msgforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

/**
 * Manifest-registered BroadcastReceiver for SMS_RECEIVED.
 *
 * Captures incoming SMS messages (handling multi-part messages),
 * writes them immediately to local SQLite database (for offline safety),
 * and syncs them to Google Sheets.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
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

        // Group multi-part SMS messages by originating sender
        val groupedMessages = mutableMapOf<String, StringBuilder>()
        for (msg in messages) {
            val sender = msg.displayOriginatingAddress ?: "Unknown"
            groupedMessages.getOrPut(sender) { StringBuilder() }.append(msg.messageBody ?: "")
        }

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

        // If not signed in to Google yet, keep in local DB for when user signs in
        if (!prefs.isSignedIn) {
            Log.d(TAG, "User not signed in to Google, message queued locally.")
            return
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Try immediate upload to Google Sheets
                val syncResult = GoogleSheetsManager.appendMessages(context, insertedMessages)
                if (syncResult.isSuccess) {
                    val syncedIds = insertedMessages.map { it.id }
                    dbHelper.deleteBatch(syncedIds)
                    prefs.totalSyncedCount += syncedIds.size
                    Log.d(TAG, "Successfully synced ${syncedIds.size} SMS to Google Sheets immediately and deleted from local storage")
                } else {
                    Log.w(TAG, "Immediate sync failed: ${syncResult.exceptionOrNull()?.message}. Enqueueing WorkManager.")
                    BackupSyncWorker.enqueueSync(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing SMS to Google Sheets, queueing with WorkManager", e)
                BackupSyncWorker.enqueueSync(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
