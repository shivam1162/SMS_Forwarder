package com.personal.msgforwarder.ui

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.personal.msgforwarder.data.PreferencesHelper
import com.personal.msgforwarder.ui.components.AppDrawerContent
import com.personal.msgforwarder.ui.screens.HomeScreen
import com.personal.msgforwarder.ui.screens.RepairScreen
import com.personal.msgforwarder.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

/**
 * SuperApp Component Destinations.
 */
enum class AppScreen {
    SMS_BACKUP,
    REPAIR,
    SETTING
}

/**
 * App navigation hosting SuperApp drawer and active component screens.
 */
@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val prefs = remember { PreferencesHelper(context) }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var currentScreen by remember { mutableStateOf(AppScreen.SMS_BACKUP) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        drawerContent = {
            AppDrawerContent(
                prefs = prefs,
                currentScreen = currentScreen,
                onSelectScreen = { screen ->
                    currentScreen = screen
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        when (currentScreen) {
            AppScreen.SMS_BACKUP -> {
                HomeScreen(
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    }
                )
            }
            AppScreen.REPAIR -> {
                RepairScreen(
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    }
                )
            }
            AppScreen.SETTING -> {
                SettingsScreen(
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    }
                )
            }
        }
    }
}
