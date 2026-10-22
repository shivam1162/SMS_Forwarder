package com.personal.msgforwarder.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.personal.msgforwarder.data.MessageData
import com.personal.msgforwarder.data.PreferencesHelper
import com.personal.msgforwarder.data.google.GoogleAuthManager
import com.personal.msgforwarder.data.google.GoogleSheetsManager
import com.personal.msgforwarder.data.local.MessageDbHelper
import com.personal.msgforwarder.ui.components.AboutDialog
import com.personal.msgforwarder.ui.components.HamburgerMenuIcon
import com.personal.msgforwarder.ui.theme.ActiveGreen
import com.personal.msgforwarder.ui.theme.DarkText
import com.personal.msgforwarder.ui.theme.SubText
import com.personal.msgforwarder.ui.theme.WarningOrange
import com.personal.msgforwarder.worker.BackupSyncWorker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferencesHelper(context) }
    val dbHelper = remember { MessageDbHelper.getInstance(context) }

    var isSignedIn by remember { mutableStateOf(prefs.isSignedIn) }
    var accountEmail by remember { mutableStateOf(prefs.googleAccountEmail) }
    var accountName by remember { mutableStateOf(prefs.googleAccountName) }
    var spreadsheetUrl by remember { mutableStateOf(prefs.spreadsheetUrl) }
    var isBackupEnabled by remember { mutableStateOf(prefs.isBackupEnabled) }

    var messages by remember { mutableStateOf(listOf<MessageData>()) }
    var pendingCount by remember { mutableStateOf(0) }
    var syncedCount by remember { mutableStateOf(0) }
    var isSyncing by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    fun refreshData() {
        messages = dbHelper.getRecentMessages(50)
        pendingCount = dbHelper.getPendingCount()
        syncedCount = dbHelper.getSyncedCount()
        spreadsheetUrl = prefs.spreadsheetUrl
    }

    LaunchedEffect(Unit) {
        refreshData()
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
                    isSyncing = true
                    val sheetResult = GoogleSheetsManager.getOrCreateSpreadsheet(context)
                    if (sheetResult.isSuccess) {
                        spreadsheetUrl = sheetResult.getOrNull()?.second
                        Toast.makeText(context, "Linked to Google Sheet 'msg_backup'", Toast.LENGTH_SHORT).show()
                    }
                    // Sync any pending messages
                    BackupSyncWorker.enqueueSync(context)
                    refreshData()
                    isSyncing = false
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

    // SMS & Notification permission check
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

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                    text = "msg_backup.xlsx / Sheet",
                    style = MaterialTheme.typography.bodySmall,
                    color = SubText
                )
            }

            IconButton(onClick = {
                scope.launch {
                    isSyncing = true
                    BackupSyncWorker.enqueueSync(context)
                    refreshData()
                    isSyncing = false
                }
            }) {
                Text(text = "🔄", fontSize = 18.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SMS Permission Warning Banner
        if (!hasSmsPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = WarningOrange.copy(alpha = 0.12f)),
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
                            color = DarkText
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
                        Text("Grant", fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Google Account & Drive Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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
                            color = DarkText
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Sign in to allow the app to create and update 'msg_backup' in your Google Drive.",
                        fontSize = 13.sp,
                        color = SubText
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            val intent = GoogleAuthManager.getSignInIntent(context)
                            googleSignInLauncher.launch(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Sign in with Google", fontWeight = FontWeight.SemiBold)
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
                                    refreshData()
                                    Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Sign Out", fontSize = 12.sp, color = SubText)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = accountEmail ?: "",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = DarkText
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Drive spreadsheet link button
                    OutlinedButton(
                        onClick = {
                            val targetUrl = spreadsheetUrl ?: "https://drive.google.com"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "📊 Open 'msg_backup' in Google Sheets ↗",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Backup Switch Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isBackupEnabled) "Automatic backup is Active" else "Automatic backup is Paused",
                            fontSize = 13.sp,
                            color = if (isBackupEnabled) DarkText else SubText
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

        Spacer(modifier = Modifier.height(14.dp))

        // Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "$syncedCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ActiveGreen)
                    Text(text = "Synced", fontSize = 12.sp, color = SubText)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$pendingCount",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pendingCount > 0) WarningOrange else SubText
                    )
                    Text(text = "Pending Queue", fontSize = 12.sp, color = SubText)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Recent Messages Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Extracted Messages",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (pendingCount > 0 && isSignedIn) {
                TextButton(onClick = {
                    scope.launch {
                        isSyncing = true
                        BackupSyncWorker.enqueueSync(context)
                        refreshData()
                        isSyncing = false
                    }
                }) {
                    Text("Sync Queue", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Message List
        if (messages.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "📥", fontSize = 32.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No messages backed up yet",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = DarkText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "When an SMS arrives, its full content will be captured and written as a new row in your Google Sheet.",
                        fontSize = 12.sp,
                        color = SubText,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageCard(message)
                }
            }
        }
    }
}

@Composable
private fun MessageCard(message: MessageData) {
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.sender,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Sync status chip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (message.isSynced) ActiveGreen.copy(alpha = 0.15f) else WarningOrange.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (message.isSynced) "✅ Synced" else "⏳ In Queue",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (message.isSynced) ActiveGreen else WarningOrange
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = DarkText
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = dateFormatter.format(Date(message.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = SubText,
                fontSize = 11.sp
            )
        }
    }
}
