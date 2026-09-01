package com.personal.msgforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.personal.msgforwarder.data.PreferencesHelper
import com.personal.msgforwarder.worker.HeartbeatWorker
import java.util.concurrent.TimeUnit

/**
 * Manifest-registered receiver for BOOT_COMPLETED.
 * On phone restart, checks if forwarding was active and re-schedules the heartbeat worker.
 * The SMS BroadcastReceiver is manifest-registered so it auto-survives reboots — no action needed for it.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        const val HEARTBEAT_WORK_NAME = "heartbeat_worker"

        fun scheduleHeartbeat(context: Context) {
            val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(6, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                HEARTBEAT_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancelHeartbeat(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(HEARTBEAT_WORK_NAME)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = PreferencesHelper(context)

        if (prefs.isActive && prefs.isPaired()) {
            Log.d(TAG, "Boot completed, forwarding is active — scheduling heartbeat")
            scheduleHeartbeat(context)
        } else {
            Log.d(TAG, "Boot completed, forwarding is inactive — no action needed")
        }
    }
}
