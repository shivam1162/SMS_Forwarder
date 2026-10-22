package com.personal.msgforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.personal.msgforwarder.data.PreferencesHelper
import com.personal.msgforwarder.data.local.MessageDbHelper
import com.personal.msgforwarder.worker.BackupSyncWorker

/**
 * Manifest-registered receiver for BOOT_COMPLETED.
 * When phone boots up, checks if there are any unsynced SMS messages in the database
 * and enqueues BackupSyncWorker to upload them to Google Sheets.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = PreferencesHelper(context)
        if (!prefs.isSignedIn || !prefs.isBackupEnabled) {
            Log.d(TAG, "Boot completed, backup not active or not signed in.")
            return
        }

        val pendingCount = MessageDbHelper.getInstance(context).getPendingCount()
        if (pendingCount > 0) {
            Log.d(TAG, "Boot completed: Found $pendingCount pending message(s), triggering sync.")
            BackupSyncWorker.enqueueSync(context)
        } else {
            Log.d(TAG, "Boot completed: No pending messages to sync.")
        }
    }
}
