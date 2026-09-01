package com.personal.msgforwarder.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.personal.msgforwarder.data.FirebaseHelper
import com.personal.msgforwarder.data.MessageData
import com.personal.msgforwarder.data.PreferencesHelper
import com.personal.msgforwarder.ui.theme.ActiveGreen
import com.personal.msgforwarder.ui.theme.InactiveRed
import com.personal.msgforwarder.ui.theme.SubText
import com.personal.msgforwarder.ui.theme.WarningOrange

/**
 * Sender screen (Mom's phone).
 * Shows: role, pairing code, active/inactive status, SMS permission status, last forwarded message.
 */
@Composable
fun SenderScreen() {
    val context = LocalContext.current
    val prefs = PreferencesHelper(context)
    val code = prefs.pairingCode ?: ""

    var isActive by remember { mutableStateOf(prefs.isActive) }
    var lastMessage by remember { mutableStateOf<MessageData?>(null) }

    // Check SMS permissions
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasSmsPermission = permissions[Manifest.permission.RECEIVE_SMS] == true ||
                           ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    }

    // Listen for activation state changes from Firebase
    DisposableEffect(code) {
        val listener = FirebaseHelper.listenForActivation(code) { active ->
            isActive = active
            prefs.isActive = active
        }

        // Send an initial heartbeat on launch to confirm connection
        if (code.isNotBlank()) {
            FirebaseHelper.writeHeartbeat(code, System.currentTimeMillis())
        }

        onDispose {
            FirebaseHelper.removeActivationListener(code, listener)
        }
    }

    // Listen for messages to show the last forwarded one
    DisposableEffect(code) {
        val listener = FirebaseHelper.listenForMessages(code) { rawMessage ->
            lastMessage = rawMessage.decrypted(code)
        }

        onDispose {
            FirebaseHelper.removeMessagesListener(code, listener)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SMS Forwarder",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Role: Sender (Mom's Phone) • Code: $code",
            style = MaterialTheme.typography.titleSmall,
            color = SubText
        )

        Spacer(modifier = Modifier.height(20.dp))

        // SMS Permission Warning if missing
        if (!hasSmsPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = WarningOrange.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚠️ SMS Permission Required",
                        style = MaterialTheme.typography.titleMedium,
                        color = WarningOrange
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "This phone needs SMS permission to forward incoming OTPs.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.RECEIVE_SMS,
                                    Manifest.permission.READ_SMS
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WarningOrange)
                    ) {
                        Text("Grant SMS Permission")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Status indicator
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isActive) ActiveGreen.copy(alpha = 0.1f)
                else InactiveRed.copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "●",
                    color = if (isActive) ActiveGreen else InactiveRed,
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isActive) "Active" else "Inactive",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isActive) ActiveGreen else InactiveRed
                    )
                    Text(
                        text = if (isActive) "Forwarding incoming SMS to your phone"
                        else "Waiting for activation from your phone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SubText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Last forwarded message
        Text(
            text = "Last Forwarded Message",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (lastMessage != null) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "From: ${lastMessage!!.sender}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SubText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = lastMessage!!.body,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTimeAgo(lastMessage!!.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = SubText
                    )
                }
            } else {
                Text(
                    text = "No messages forwarded yet.",
                    modifier = Modifier.padding(16.dp),
                    color = SubText
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Troubleshoot button
        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com"))
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Troubleshoot Background Battery Issues")
        }
    }
}

/**
 * Formats a timestamp into a human-readable "time ago" string.
 */
internal fun formatTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hr ago"
        else -> "$days day${if (days > 1) "s" else ""} ago"
    }
}
