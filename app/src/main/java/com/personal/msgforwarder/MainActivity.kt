package com.personal.msgforwarder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.personal.msgforwarder.data.FcmTokenManager
import com.personal.msgforwarder.data.PreferencesHelper
import com.personal.msgforwarder.ui.AppNavigation
import com.personal.msgforwarder.ui.theme.AppTheme
import com.personal.msgforwarder.util.NotificationHelper

/**
 * Single Activity — sets up Compose theme + navigation.
 * Requests SMS + notification permissions on launch.
 */
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions granted or denied — app continues either way
        // SMS forwarding only works if SMS permission is granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create notification channels
        NotificationHelper.createChannels(this)

        // Register FCM token if already paired
        val prefs = PreferencesHelper(this)
        if (prefs.isPaired()) {
            FcmTokenManager.registerToken(this)
        }

        // Request permissions
        requestAppPermissions()

        setContent {
            AppTheme {
                com.personal.msgforwarder.ui.components.UpdateCheckerWrapper {
                    AppNavigation()
                }
            }
        }
    }

    private fun requestAppPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // SMS permissions (needed on sender phone)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.RECEIVE_SMS)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.READ_SMS)
        }

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
