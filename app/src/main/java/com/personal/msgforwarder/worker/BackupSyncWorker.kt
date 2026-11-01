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
import com.personal.msgforwarder.sync.SyncManager

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
                ExistingWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Enqueued BackupSyncWorker (policy=KEEP)")
        }
    }

    override suspend fun doWork(): ListenableWorker.Result {
        Log.d(TAG, "BackupSyncWorker running")
        val result = SyncManager.performSync(context)
        return if (result.isSuccess) {
            Log.d(TAG, "BackupSyncWorker completed successfully")
            ListenableWorker.Result.success()
        } else {
            Log.e(TAG, "BackupSyncWorker failed, will retry: ${result.exceptionOrNull()?.message}")
            ListenableWorker.Result.retry()
        }
    }
}
