package com.personal.msgforwarder.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personal.msgforwarder.data.FirebaseHelper
import com.personal.msgforwarder.data.PreferencesHelper

/**
 * Periodic worker that writes a heartbeat timestamp to Firebase every 6 hours.
 * This lets the receiver phone know that the sender phone is still alive and connected.
 *
 * Battery cost: one tiny network write every 6 hours = negligible.
 * Scheduled via PeriodicWorkRequest, respects Android battery optimization.
 */
class HeartbeatWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "HeartbeatWorker"
    }

    override suspend fun doWork(): Result {
        val prefs = PreferencesHelper(applicationContext)
        val code = prefs.pairingCode

        if (code == null || !prefs.isActive) {
            Log.d(TAG, "Not active or not paired, skipping heartbeat")
            return Result.success()
        }

        val timestamp = System.currentTimeMillis()
        Log.d(TAG, "Writing heartbeat: $timestamp")

        FirebaseHelper.writeHeartbeat(code, timestamp)
        FirebaseHelper.purgeOldMessages(code)

        return Result.success()
    }
}
