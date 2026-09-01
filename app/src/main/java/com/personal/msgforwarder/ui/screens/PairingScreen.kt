package com.personal.msgforwarder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.msgforwarder.data.FcmTokenManager
import com.personal.msgforwarder.data.PreferencesHelper

/**
 * One-time pairing screen.
 * Enter a 6-digit code, select role (Sender/Receiver), and connect.
 */
@Composable
fun PairingScreen(onPaired: (String) -> Unit) {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(PreferencesHelper.ROLE_SENDER) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SMS Forwarder",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Enter Pairing Code",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = code,
            onValueChange = {
                if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                    code = it
                    errorMessage = null
                }
            },
            label = { Text("6-digit code") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "I am:",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            RadioButton(
                selected = selectedRole == PreferencesHelper.ROLE_SENDER,
                onClick = { selectedRole = PreferencesHelper.ROLE_SENDER }
            )
            Text(
                text = "Sender (Mom's phone)",
                modifier = Modifier.padding(start = 8.dp),
                fontSize = 16.sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            RadioButton(
                selected = selectedRole == PreferencesHelper.ROLE_RECEIVER,
                onClick = { selectedRole = PreferencesHelper.ROLE_RECEIVER }
            )
            Text(
                text = "Receiver (My phone)",
                modifier = Modifier.padding(start = 8.dp),
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        Button(
            onClick = {
                if (code.length != 6) {
                    errorMessage = "Please enter a 6-digit code"
                    return@Button
                }

                // Save pairing info locally
                val prefs = PreferencesHelper(context)
                prefs.pairingCode = code
                prefs.role = selectedRole

                // Register FCM token in Firebase
                FcmTokenManager.registerToken(context)

                // Navigate to the appropriate screen
                onPaired(selectedRole)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Connect", fontSize = 18.sp)
        }
    }
}
