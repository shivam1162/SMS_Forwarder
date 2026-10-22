package com.personal.msgforwarder.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.personal.msgforwarder.data.PreferencesHelper
import com.personal.msgforwarder.data.google.GoogleAuthManager
import com.personal.msgforwarder.data.google.GoogleSheetsManager
import com.personal.msgforwarder.ui.components.AboutDialog
import com.personal.msgforwarder.ui.components.HamburgerMenuIcon
import com.personal.msgforwarder.ui.theme.ActiveGreen
import com.personal.msgforwarder.ui.theme.TextGray
import com.personal.msgforwarder.ui.theme.TextWhite
import com.personal.msgforwarder.ui.theme.WarningOrange
import com.personal.msgforwarder.worker.BackupSyncWorker
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferencesHelper(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    var isSignedIn by remember { mutableStateOf(prefs.isSignedIn) }
    var accountEmail by remember { mutableStateOf(prefs.googleAccountEmail) }
    var accountName by remember { mutableStateOf(prefs.googleAccountName) }
    var spreadsheetUrl by remember { mutableStateOf(prefs.spreadsheetUrl) }
    var isBackupEnabled by remember { mutableStateOf(prefs.isBackupEnabled) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // Battery Optimization Check
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true)
    }

    // SMS permission check
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Refresh permissions & battery state when user returns to app from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBatteryOptimizations = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                hasSmsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Google Sign-In Activity Result Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val parseResult = GoogleAuthManager.parseSignInResult(result.data)
            if (parseResult.isSuccess) {
                val account = parseResult.getOrThrow()
                prefs.googleAccountEmail = account.email
                prefs.googleAccountName = account.displayName ?: account.email
                isSignedIn = true
                accountEmail = account.email
                accountName = account.displayName ?: account.email

                Toast.makeText(context, "Signed in as ${account.email}", Toast.LENGTH_SHORT).show()

                // Create or link spreadsheet in Drive
                scope.launch {
                    val sheetResult = GoogleSheetsManager.getOrCreateSpreadsheet(context)
                    if (sheetResult.isSuccess) {
                        spreadsheetUrl = sheetResult.getOrNull()?.second
                        Toast.makeText(context, "Linked to Google Sheet 'msg_backup'", Toast.LENGTH_SHORT).show()
                    }
                    BackupSyncWorker.enqueueSync(context)
                }
            } else {
                Toast.makeText(
                    context,
                    "Google Sign-In failed: ${parseResult.exceptionOrNull()?.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasSmsPermission = permissions[Manifest.permission.RECEIVE_SMS] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { showAboutDialog = true }) {
                HamburgerMenuIcon()
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "SMS to Google Sheets",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "msg_backup (Google Drive)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }

            // Balanced spacing for top bar
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SMS Permission Warning Banner
        if (!hasSmsPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = WarningOrange.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "⚠️", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SMS Permission Needed",
                            fontWeight = FontWeight.SemiBold,
                            color = WarningOrange,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Required to read incoming messages and append rows to your Google Sheet.",
                            fontSize = 12.sp,
                            color = TextWhite
                        )
                    }
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.RECEIVE_SMS,
                                    Manifest.permission.READ_SMS
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WarningOrange),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Grant", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Battery Optimization Warning Banner (Doze & OEM Killer Protection)
        if (!isIgnoringBatteryOptimizations) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "⚡", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Unrestricted Battery Sync",
                            fontWeight = FontWeight.SemiBold,
                            color = TextWhite,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Allow app to run unrestricted so Android won't pause sync when your phone is locked.",
                            fontSize = 12.sp,
                            color = TextGray
                        )
                    }
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Allow", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Google Account & Drive Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                if (!isSignedIn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(WarningOrange)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Not Connected to Google",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = TextWhite
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sign in to allow the app to automatically create and update 'msg_backup' in your Google Drive.",
                        fontSize = 13.sp,
                        color = TextGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val intent = GoogleAuthManager.getSignInIntent(context)
                            googleSignInLauncher.launch(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "Sign in with Google",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(ActiveGreen)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Google Drive Connected",
                                fontWeight = FontWeight.SemiBold,
                                color = ActiveGreen,
                                fontSize = 14.sp
                            )
                        }

                        TextButton(
                            onClick = {
                                scope.launch {
                                    GoogleAuthManager.signOut(context)
                                    prefs.clearGoogleAccount()
                                    isSignedIn = false
                                    accountEmail = null
                                    accountName = null
                                    spreadsheetUrl = null
                                    Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Sign Out", fontSize = 12.sp, color = TextGray)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = accountEmail ?: "",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextWhite
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Open in Google Sheets button
                    Button(
                        onClick = {
                            val targetUrl = spreadsheetUrl ?: "https://drive.google.com"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "📊 Open 'msg_backup' in Google Sheets ↗",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Backup Switch Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isBackupEnabled) "Automatic backup is Active" else "Automatic backup is Paused",
                            fontSize = 13.sp,
                            color = if (isBackupEnabled) TextWhite else TextGray
                        )
                        Switch(
                            checked = isBackupEnabled,
                            onCheckedChange = { checked ->
                                isBackupEnabled = checked
                                prefs.isBackupEnabled = checked
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Privacy & Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🔒", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Direct Sync & Privacy",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextWhite
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Incoming SMS messages are appended directly to your private Google Sheet and are not stored on this device.",
                    fontSize = 12.sp,
                    color = TextGray,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
