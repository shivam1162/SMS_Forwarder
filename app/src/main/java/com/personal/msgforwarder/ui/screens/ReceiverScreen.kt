package com.personal.msgforwarder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.msgforwarder.data.FirebaseHelper
import com.personal.msgforwarder.data.MessageData
import com.personal.msgforwarder.data.PreferencesHelper
import com.personal.msgforwarder.service.ReceiverService
import com.personal.msgforwarder.ui.theme.ActiveGreen
import com.personal.msgforwarder.ui.theme.InactiveRed
import com.personal.msgforwarder.ui.theme.SubText
import com.personal.msgforwarder.ui.theme.WarningOrange

/**
 * Receiver screen (Your phone).
 * Big Activate/Deactivate toggle, list of forwarded messages,
 * and heartbeat indicator showing mom's phone status.
 */
@Composable
fun ReceiverScreen() {
    val context = LocalContext.current
    val prefs = PreferencesHelper(context)
    val code = prefs.pairingCode ?: ""

    var isActive by remember { mutableStateOf(prefs.isActive) }
    var messages by remember { mutableStateOf(listOf<MessageData>()) }
    var lastHeartbeat by remember { mutableStateOf(0L) }

    // Listen for activation state
    DisposableEffect(code) {
        val listener = FirebaseHelper.listenForActivation(code) { active ->
            isActive = active
            prefs.isActive = active
            if (active) {
                ReceiverService.start(context)
            } else {
                ReceiverService.stop(context)
            }
        }
        onDispose { FirebaseHelper.removeActivationListener(code, listener) }
    }

    // Listen for incoming messages
    DisposableEffect(code) {
        if (code.isNotBlank()) {
            FirebaseHelper.purgeOldMessages(code)
        }

        val listener = FirebaseHelper.listenForMessages(code) { rawMessage ->
            val message = rawMessage.decrypted(code)
            val thirtyMinutesAgo = System.currentTimeMillis() - (30 * 60 * 1000L)
            if (message.timestamp >= thirtyMinutesAgo) {
                messages = (listOf(message) + messages.filter {
                    it.timestamp >= thirtyMinutesAgo && (it.timestamp != message.timestamp || it.body != message.body)
                }).take(50)
            }
        }
        onDispose { FirebaseHelper.removeMessagesListener(code, listener) }
    }

    // Listen for heartbeat
    DisposableEffect(code) {
        val listener = FirebaseHelper.listenForHeartbeat(code) { timestamp ->
            lastHeartbeat = timestamp
        }
        onDispose { FirebaseHelper.removeHeartbeatListener(code, listener) }
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
            text = "Role: Receiver (My Phone) • Code: $code",
            style = MaterialTheme.typography.titleSmall,
            color = SubText
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Activate/Deactivate toggle button
        Button(
            onClick = {
                val newState = !isActive
                isActive = newState
                prefs.isActive = newState

                // Write to Firebase so sender phone picks it up
                FirebaseHelper.setActive(code, newState)

                if (newState) {
                    ReceiverService.start(context)
                } else {
                    ReceiverService.stop(context)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isActive) InactiveRed else ActiveGreen
            )
        ) {
            Text(
                text = if (isActive) "⏹ DEACTIVATE" else "▶ ACTIVATE",
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Heartbeat indicator
        if (lastHeartbeat > 0) {
            val timeSince = System.currentTimeMillis() - lastHeartbeat
            val isHealthy = timeSince < 12 * 60 * 60 * 1000 // 12 hours

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isHealthy) ActiveGreen.copy(alpha = 0.1f)
                    else WarningOrange.copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isHealthy) "✅" else "⚠️",
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mom's phone last seen: ${formatTimeAgo(lastHeartbeat)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Messages header
        Text(
            text = "Recent Messages",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Messages list
        if (messages.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "No messages yet. Tap ACTIVATE above, then send an SMS to mom's phone.",
                    modifier = Modifier.padding(16.dp),
                    color = SubText
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MessageCard(message)
                }
            }
        }
    }
}

@Composable
private fun MessageCard(message: MessageData) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = message.sender,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatTimeAgo(message.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = SubText
            )
        }
    }
}
