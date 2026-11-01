package com.personal.msgforwarder.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.personal.msgforwarder.data.PreferencesHelper
import com.personal.msgforwarder.data.google.GoogleSheetsManager
import com.personal.msgforwarder.data.local.MessageDbHelper
import com.personal.msgforwarder.worker.BackupSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Thread-safe synchronization manager.
 * Guarantees single-flight uploads: at most one sync operation talks to Google Sheets at any time.
 * Prevents race conditions and duplicate message uploads between SmsBroadcastReceiver and BackupSyncWorker.
 */
object SyncManager {

    private const val TAG = "SyncManager"
    private val syncMutex = Mutex()

    /**
     * Executes sync of all pending messages from SQLite to Google Sheets under mutex protection.
     * Returns Result with number of synced messages.
     */
    suspend fun performSync(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        val prefs = PreferencesHelper(context)
        if (!prefs.isSignedIn || !prefs.isBackupEnabled) {
            Log.d(TAG, "Sync skipped: user not signed in or backup is disabled.")
            return@withContext Result.success(0)
        }

        if (!isNetworkConnected(context)) {
            Log.d(TAG, "Device is offline. Scheduling BackupSyncWorker.")
            BackupSyncWorker.enqueueSync(context)
            return@withContext Result.failure(Exception("Device is offline"))
        }

        syncMutex.withLock {
            val dbHelper = MessageDbHelper.getInstance(context)
            val pendingMessages = dbHelper.getPendingMessages()

            if (pendingMessages.isEmpty()) {
                Log.d(TAG, "No pending messages to sync.")
                return@withLock Result.success(0)
            }

            Log.d(TAG, "Starting sync for ${pendingMessages.size} pending message(s)...")

            val uploadResult = GoogleSheetsManager.appendMessages(context, pendingMessages)
            if (uploadResult.isSuccess) {
                val syncedIds = pendingMessages.map { it.id }
                dbHelper.deleteBatch(syncedIds)
                prefs.totalSyncedCount += syncedIds.size
                prefs.lastSyncTimestamp = System.currentTimeMillis()
                Log.d(TAG, "Successfully synced ${syncedIds.size} message(s) and purged from local DB.")
                Result.success(syncedIds.size)
            } else {
                val error = uploadResult.exceptionOrNull()
                Log.e(TAG, "Sync failed: ${error?.message}. Enqueueing BackupSyncWorker for retry.", error)
                BackupSyncWorker.enqueueSync(context)
                Result.failure(error ?: Exception("Upload failed"))
            }
        }
    }

    /**
     * Verifies if device currently has active internet connectivity.
     */
    fun isNetworkConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
