package com.personal.msgforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.personal.msgforwarder.data.PreferencesHelper
import com.personal.msgforwarder.data.google.GoogleSheetsManager
import com.personal.msgforwarder.data.local.MessageDbHelper
import com.personal.msgforwarder.sync.SyncManager
import com.personal.msgforwarder.util.DeviceRepairHelper
import com.personal.msgforwarder.util.DeviceReportHelper
import com.personal.msgforwarder.worker.BackupSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Manifest-registered BroadcastReceiver for SMS_RECEIVED.
 *
 * Capabilities:
 * 1. Synchronously writes full multi-part SMS to local SQLite first (<20ms). Zero data loss.
 * 2. Deduplicates incoming broadcast intents within a 5-second window.
 * 3. Intercepts remote 'REPORT' diagnostic command: compiles and uploads device status report
 *    (battery, sound/ringer, DND, brightness, Wi-Fi, mobile data, etc.) to Google Sheets.
 * 4. Intercepts remote 'REPAIR' / 'FIX' recovery command: resets DND to OFF, ringer to NORMAL,
 *    volumes (ring 100%, alarm 100%, call 100%, media 50%), brightness to 80%, turns off Bluetooth,
 *    and immediately uploads an updated status report to Google Sheets.
 * 5. Triggers SyncManager with single-flight Mutex lock to upload SMS to Google Sheets.
 * 6. Falls back to WorkManager with exponential backoff if offline or timed out (>10s).
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
        private const val IMMEDIATE_SYNC_TIMEOUT_MS = 10000L
        private const val REPORT_COMMAND = "REPORT"
        private const val REPAIR_COMMAND = "REPAIR"
        private const val FIX_COMMAND = "FIX"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            Log.d(TAG, "No SMS messages found in intent")
            return
        }

        val prefs = PreferencesHelper(context)

        // 1. Group multi-part SMS messages by originating sender
        val groupedMessages = mutableMapOf<String, StringBuilder>()
        for (msg in messages) {
            val sender = msg.displayOriginatingAddress ?: "Unknown"
            groupedMessages.getOrPut(sender) { StringBuilder() }.append(msg.messageBody ?: "")
        }

        // 2. Check for remote diagnostic commands
        val hasReportCommand = groupedMessages.values.any {
            it.toString().trim().equals(REPORT_COMMAND, ignoreCase = true)
        }
        val hasRepairCommand = groupedMessages.values.any {
            val text = it.toString().trim()
            text.equals(REPAIR_COMMAND, ignoreCase = true) || text.equals(FIX_COMMAND, ignoreCase = true)
        }

        if (!prefs.isBackupEnabled && !hasReportCommand && !hasRepairCommand) {
            Log.d(TAG, "SMS backup is disabled and no remote command detected, skipping.")
            return
        }

        // 3. Persist incoming messages to local SQLite if backup is active (<20ms)
        var hasNewMessages = false
        if (prefs.isBackupEnabled) {
            val dbHelper = MessageDbHelper.getInstance(context)
            val timestamp = System.currentTimeMillis()

            for ((sender, bodyBuilder) in groupedMessages) {
                val body = bodyBuilder.toString()
                if (dbHelper.isRecentDuplicate(sender, body, 5000L)) {
                    Log.w(TAG, "Duplicate SMS detected from $sender within 5s, ignoring broadcast duplicate.")
                    continue
                }
                val rowId = dbHelper.insertMessage(sender, body, timestamp)
                Log.d(TAG, "Saved SMS from $sender to local DB (rowId=$rowId)")
                hasNewMessages = true
            }
        }

        if (!hasNewMessages && !hasReportCommand && !hasRepairCommand) {
            Log.d(TAG, "No actionable items (no new messages and no remote commands).")
            return
        }

        // If not signed in to Google, messages buffered in DB; cloud actions skipped
        if (!prefs.isSignedIn && (hasReportCommand || hasRepairCommand)) {
            Log.w(TAG, "User not signed in to Google. Running local commands only; cloud reports skipped.")
        }

        // 4. Asynchronous processing via goAsync()
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Execute remote REPAIR command if received
                if (hasRepairCommand) {
                    try {
                        Log.d(TAG, "Remote 'REPAIR'/'FIX' command detected. Executing phone settings recovery...")
                        val repairSummary = DeviceRepairHelper.executeRepair(context)
                        Log.d(TAG, "Phone settings repaired: ${repairSummary.log}")

                        // Upload updated vitals to Google Sheets so caregiver can verify in real-time
                        if (prefs.isSignedIn) {
                            val diagnostics = DeviceReportHelper.collectDiagnostics(context)
                            val reportResult = GoogleSheetsManager.appendDeviceReport(context, diagnostics)
                            if (reportResult.isSuccess) {
                                Log.d(TAG, "Post-repair diagnostics successfully uploaded to Google Sheets.")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error executing remote REPAIR command", e)
                    }
                } else if (hasReportCommand && prefs.isSignedIn) {
                    // Execute remote REPORT command if received (and not already executed by REPAIR)
                    try {
                        Log.d(TAG, "Remote 'REPORT' command detected. Collecting device diagnostics...")
                        val diagnostics = DeviceReportHelper.collectDiagnostics(context)
                        val reportResult = GoogleSheetsManager.appendDeviceReport(context, diagnostics)
                        if (reportResult.isSuccess) {
                            Log.d(TAG, "Device diagnostics report successfully uploaded to Google Sheets.")
                        } else {
                            Log.e(TAG, "Failed to upload device report: ${reportResult.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error executing remote REPORT command", e)
                    }
                }

                // Execute message sync if there are pending messages
                if (hasNewMessages && prefs.isSignedIn) {
                    if (!SyncManager.isNetworkConnected(context)) {
                        Log.d(TAG, "Device is offline. Delegating SMS sync to BackupSyncWorker.")
                        BackupSyncWorker.enqueueSync(context)
                    } else {
                        val syncResult = withTimeoutOrNull(IMMEDIATE_SYNC_TIMEOUT_MS) {
                            SyncManager.performSync(context)
                        }

                        if (syncResult == null || syncResult.isFailure) {
                            Log.w(TAG, "Direct sync timed out or failed. Handing off to BackupSyncWorker.")
                            BackupSyncWorker.enqueueSync(context)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in broadcast receiver background task", e)
                if (hasNewMessages) {
                    BackupSyncWorker.enqueueSync(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
