package com.personal.msgforwarder.util

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.util.Log

data class RepairActionSummary(
    val dndFixed: Boolean,
    val volumeFixed: Boolean,
    val brightnessFixed: Boolean,
    val bluetoothFixed: Boolean,
    val log: List<String>
)

/**
 * Automates one-tap & remote phone hardware/system settings recovery.
 *
 * Actions Executed:
 * 1. Turns off Do Not Disturb (DND) mode and restores ringer mode to NORMAL.
 * 2. Sets Ringtone volume to 100%.
 * 3. Sets Alarm volume to 100%.
 * 4. Sets Voice Call volume to 100%.
 * 5. Sets Media volume to 50%.
 * 6. Sets Screen Brightness to 80% (Manual mode).
 * 7. Turns off Bluetooth.
 */
object DeviceRepairHelper {

    private const val TAG = "DeviceRepairHelper"

    /**
     * Checks if the app has permission to modify Do Not Disturb policy.
     */
    fun canModifyDnd(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return nm?.isNotificationPolicyAccessGranted == true
    }

    /**
     * Checks if the app has permission to write system settings (screen brightness).
     */
    fun canModifyBrightness(context: Context): Boolean {
        return Settings.System.canWrite(context)
    }

    /**
     * Executes the complete suite of phone repair actions.
     */
    fun executeRepair(context: Context): RepairActionSummary {
        val log = mutableListOf<String>()
        var dndOk = false
        var volOk = false
        var brightOk = false
        var btOk = false

        // 1. DND & Ringer Mode
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

            if (nm != null && nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                dndOk = true
                log.add("DND mode turned OFF")
            } else {
                log.add("DND policy access not granted")
            }

            if (audioManager != null) {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting DND or ringer mode", e)
            log.add("DND error: ${e.message}")
        }

        // 2. Volumes: Ring 100%, Alarm 100%, Voice Call 100%, Media 50%
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                audioManager.setStreamVolume(AudioManager.STREAM_RING, maxRing, 0)

                val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, 0)

                val maxCall = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxCall, 0)

                val maxMedia = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val halfMedia = (maxMedia / 2).coerceAtLeast(1)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, halfMedia, 0)

                volOk = true
                log.add("Volumes set: Ring 100%, Alarm 100%, Call 100%, Media 50%")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring stream volumes", e)
            log.add("Volume error: ${e.message}")
        }

        // 3. Screen Brightness: 80% (204/255) Manual Mode
        try {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                val targetBrightness = (255 * 0.8).toInt() // 204
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    targetBrightness
                )
                brightOk = true
                log.add("Brightness set to 80% (Manual)")
            } else {
                log.add("Write Settings permission not granted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adjusting screen brightness", e)
            log.add("Brightness error: ${e.message}")
        }

        // 4. Bluetooth: Turn OFF
        try {
            val btAdapter = BluetoothAdapter.getDefaultAdapter()
            if (btAdapter != null) {
                if (btAdapter.isEnabled) {
                    @Suppress("DEPRECATION")
                    val disabled = btAdapter.disable()
                    btOk = disabled
                    log.add(if (disabled) "Bluetooth turned OFF" else "Bluetooth disable requested")
                } else {
                    btOk = true
                    log.add("Bluetooth already OFF")
                }
            } else {
                log.add("Bluetooth hardware unavailable")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling Bluetooth", e)
            log.add("Bluetooth error: ${e.message}")
        }

        Log.d(TAG, "Phone settings repair completed: $log")
        return RepairActionSummary(dndOk, volOk, brightOk, btOk, log)
    }
}
