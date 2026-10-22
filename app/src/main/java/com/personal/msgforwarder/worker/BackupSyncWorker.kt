package com.personal.msgforwarder.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.personal.msgforwarder.data.PreferencesHelper
import com.personal.msgforwarder.data.google.GoogleSheetsManager
import com.personal.msgforwarder.data.local.MessageDbHelper

/**
 * Background worker to sync pending SMS messages to Google Sheets.
 * Automatically triggered when internet connection is restored or enqueued after message arrival.
 */
class BackupSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "BackupSyncWorker"
        private const val WORK_NAME = "backup_sync_worker"

        fun enqueueSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<BackupSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            Log.d(TAG, "Enqueued BackupSyncWorker")
        }
    }

    override suspend fun doWork(): ListenableWorker.Result {
        val prefs = PreferencesHelper(context)
        if (!prefs.isSignedIn || !prefs.isBackupEnabled) {
            Log.d(TAG, "User not signed in or backup disabled, skipping sync")
            return ListenableWorker.Result.success()
        }

        val dbHelper = MessageDbHelper.getInstance(context)
        val pendingMessages = dbHelper.getPendingMessages()

        if (pendingMessages.isEmpty()) {
            Log.d(TAG, "No pending messages to sync")
            return ListenableWorker.Result.success()
        }

        Log.d(TAG, "Attempting to sync ${pendingMessages.size} pending message(s) to Google Sheets...")

        val result = GoogleSheetsManager.appendMessages(context, pendingMessages)
        return if (result.isSuccess) {
            val syncedIds = pendingMessages.map { it.id }
            dbHelper.markBatchSynced(syncedIds)
            Log.d(TAG, "Successfully synced ${syncedIds.size} message(s) to Google Sheets")
            ListenableWorker.Result.success()
        } else {
            Log.e(TAG, "Sync failed: ${result.exceptionOrNull()?.message}, will retry with WorkManager")
            ListenableWorker.Result.retry()
        }
    }
}
