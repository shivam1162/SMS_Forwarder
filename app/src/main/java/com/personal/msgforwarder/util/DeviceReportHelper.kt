package com.personal.msgforwarder.util

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.provider.Settings
import android.util.Log

data class DeviceDiagnostics(
    val timestamp: Long = System.currentTimeMillis(),
    val battery: String,
    val ringer: String,
    val dnd: String,
    val brightness: String,
    val wifi: String,
    val mobileData: String,
    val hotspot: String,
    val airplaneMode: String,
    val bluetooth: String,
    val location: String
)

/**
 * Utility to gather hardware & system status metrics for remote diagnostics reports.
 */
object DeviceReportHelper {

    private const val TAG = "DeviceReportHelper"

    fun collectDiagnostics(context: Context): DeviceDiagnostics {
        // 1. Battery Level & Charging Status
        val batteryStatus: String = try {
            val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryIntent = context.registerReceiver(null, iFilter)
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1

            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val chargeType = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "Plugged (AC)"
                BatteryManager.BATTERY_PLUGGED_USB -> "Plugged (USB)"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Plugged (Wireless)"
                else -> if (isCharging) "Charging" else "Discharging"
            }
            if (pct >= 0) "$pct% ($chargeType)" else "Unknown"
        } catch (e: Exception) {
            Log.e(TAG, "Error reading battery status", e)
            "Unknown"
        }

        // 2. Sound, Ringer Mode & Volume
        val ringerStatus: String = try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val modeStr = when (audioManager?.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> "SILENT"
                AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
                AudioManager.RINGER_MODE_NORMAL -> "NORMAL"
                else -> "UNKNOWN"
            }
            val ringVol = audioManager?.getStreamVolume(AudioManager.STREAM_RING) ?: 0
            val maxRing = audioManager?.getStreamMaxVolume(AudioManager.STREAM_RING) ?: 1
            "Mode: $modeStr (Vol: $ringVol/$maxRing)"
        } catch (e: Exception) {
            Log.e(TAG, "Error reading ringer status", e)
            "Unknown"
        }

        // 3. Do Not Disturb (DND) Mode
        val dndStatus: String = try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            when (nm?.currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> "OFF"
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "ON (Priority Only)"
                NotificationManager.INTERRUPTION_FILTER_NONE -> "ON (Total Silence)"
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> "ON (Alarms Only)"
                else -> "OFF"
            }
        } catch (e: Exception) {
            "Unknown"
        }

        // 4. Screen Brightness
        val brightnessStatus: String = try {
            val cur = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
            val mode = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
            val modeStr = if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) "Adaptive" else "Manual"
            if (cur >= 0) {
                val pct = (cur * 100) / 255
                "$pct% ($modeStr)"
            } else {
                "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }

        // 5. Connectivity (Wi-Fi & Mobile Data)
        var wifiStr = "Disconnected"
        var dataStr = "Disconnected"
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val net = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(net)
            if (caps != null) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    wifiStr = "Connected"
                }
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    dataStr = "Active (Cellular)"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading network capabilities", e)
        }

        // 6. Mobile Hotspot (Wi-Fi Tethering)
        val hotspotStatus = try {
            var isApOn = false
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                try {
                    val method = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
                    method.isAccessible = true
                    isApOn = method.invoke(wifiManager) as? Boolean ?: false
                } catch (_: Exception) {
                    val stateMethod = wifiManager.javaClass.getDeclaredMethod("getWifiApState")
                    stateMethod.isAccessible = true
                    val state = stateMethod.invoke(wifiManager) as? Int ?: -1
                    isApOn = (state == 13) // 13 = WIFI_AP_STATE_ENABLED
                }
            }
            if (isApOn) "Active" else "Disabled"
        } catch (e: Exception) {
            "Disabled"
        }

        // 7. Airplane Mode
        val airplaneStatus = try {
            val isAirplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
            if (isAirplane) "ACTIVE" else "OFF"
        } catch (e: Exception) {
            "Unknown"
        }

        // 8. Bluetooth
        val bluetoothStatus = try {
            val bt = BluetoothAdapter.getDefaultAdapter()
            when {
                bt == null -> "Unavailable"
                bt.isEnabled -> "Enabled"
                else -> "Disabled"
            }
        } catch (e: Exception) {
            "Disabled"
        }

        // 9. GPS / Location
        val locationStatus = try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val isGps = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
            if (isGps) "Enabled" else "Disabled"
        } catch (e: Exception) {
            "Disabled"
        }

        return DeviceDiagnostics(
            battery = batteryStatus,
            ringer = ringerStatus,
            dnd = dndStatus,
            brightness = brightnessStatus,
            wifi = wifiStr,
            mobileData = dataStr,
            hotspot = hotspotStatus,
            airplaneMode = airplaneStatus,
            bluetooth = bluetoothStatus,
            location = locationStatus
        )
    }
}
