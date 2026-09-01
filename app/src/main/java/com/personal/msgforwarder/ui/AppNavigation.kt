package com.personal.msgforwarder.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.personal.msgforwarder.data.PreferencesHelper
import com.personal.msgforwarder.ui.screens.PairingScreen
import com.personal.msgforwarder.ui.screens.ReceiverScreen
import com.personal.msgforwarder.ui.screens.SenderScreen

/**
 * App navigation with 3 routes: pairing, sender, receiver.
 * On launch, checks if already paired and skips to the appropriate screen.
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val prefs = PreferencesHelper(context)

    // Determine start destination based on saved state
    val startDestination = when {
        !prefs.isPaired() -> "pairing"
        prefs.role == PreferencesHelper.ROLE_SENDER -> "sender"
        else -> "receiver"
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable("pairing") {
            PairingScreen(
                onPaired = { role ->
                    val route = if (role == PreferencesHelper.ROLE_SENDER) "sender" else "receiver"
                    navController.navigate(route) {
                        popUpTo("pairing") { inclusive = true }
                    }
                }
            )
        }

        composable("sender") {
            SenderScreen()
        }

        composable("receiver") {
            ReceiverScreen()
        }
    }
}
