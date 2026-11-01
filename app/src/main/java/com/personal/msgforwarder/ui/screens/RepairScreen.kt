package com.personal.msgforwarder.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.personal.msgforwarder.data.PreferencesHelper
import com.personal.msgforwarder.data.google.GoogleSheetsManager
import com.personal.msgforwarder.data.local.MessageDbHelper
import com.personal.msgforwarder.sync.SyncManager
import com.personal.msgforwarder.ui.components.HamburgerMenuIcon
import com.personal.msgforwarder.ui.theme.*
import com.personal.msgforwarder.util.DeviceDiagnostics
import com.personal.msgforwarder.util.DeviceRepairHelper
import com.personal.msgforwarder.util.DeviceReportHelper
import kotlinx.coroutines.launch

@Composable
fun RepairScreen(
    onOpenDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferencesHelper(context) }
    val dbHelper = remember { MessageDbHelper.getInstance(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    var isHealing by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var isGeneratingReport by remember { mutableStateOf(false) }
    var isRepairingSettings by remember { mutableStateOf(false) }
    var pendingCount by remember { mutableStateOf(dbHelper.getPendingCount()) }
    var lastReportTime by remember { mutableStateOf(prefs.lastReportTimestamp) }
    var liveDiagnostics by remember { mutableStateOf<DeviceDiagnostics?>(null) }

    var hasDndPermission by remember { mutableStateOf(DeviceRepairHelper.canModifyDnd(context)) }
    var hasWriteSettingsPermission by remember { mutableStateOf(DeviceRepairHelper.canModifyBrightness(context)) }

    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    val isIgnoringBatteryOptimizations = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

    val scrollState = rememberScrollState()

    // Refresh state when returning to foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasDndPermission = DeviceRepairHelper.canModifyDnd(context)
                hasWriteSettingsPermission = DeviceRepairHelper.canModifyBrightness(context)
                liveDiagnostics = DeviceReportHelper.collectDiagnostics(context)
                pendingCount = dbHelper.getPendingCount()
                lastReportTime = prefs.lastReportTimestamp
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier.size(40.dp)
            ) {
                HamburgerMenuIcon(color = TextSecondary)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Repair",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    letterSpacing = 0.3.sp
                )
                Text(
                    text = "Self-Healing & Diagnostics",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(40.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Card 1: Phone Settings Auto-Repair
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AccentEmerald)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AUTO-REPAIR",
                            fontWeight = FontWeight.SemiBold,
                            color = AccentEmerald,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    Surface(
                        color = SurfaceElevated,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Text(
                            text = "SMS: REPAIR",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = AccentEmerald,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Repair Phone Settings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Resets device vitals if accidentally silenced or altered: turns OFF DND, restores normal ringer mode, maxes ringtone & alarm (100%), sets media (50%), brightness (80%), and disables Bluetooth.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                // Permission Warning & Setup Banners
                if (!hasDndPermission || !hasWriteSettingsPermission) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = SurfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AccentAmber.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "SETUP PERMISSIONS REQUIRED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentAmber,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Android requires explicit authorization to allow an app to turn off DND and set screen brightness.",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 15.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            if (!hasDndPermission) {
                                Button(
                                    onClick = {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Cannot open DND settings", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, BorderSubtle)
                                ) {
                                    Text(
                                        text = "Grant DND Policy Access",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                }
                                if (!hasWriteSettingsPermission) Spacer(modifier = Modifier.height(6.dp))
                            }

                            if (!hasWriteSettingsPermission) {
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Cannot open Write Settings", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, BorderSubtle)
                                ) {
                                    Text(
                                        text = "Grant Write Settings (Brightness)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isRepairingSettings = true
                            val summary = DeviceRepairHelper.executeRepair(context)
                            val freshDiag = DeviceReportHelper.collectDiagnostics(context)
                            liveDiagnostics = freshDiag

                            // If signed in, update Google Sheets Device_Status
                            if (prefs.isSignedIn) {
                                GoogleSheetsManager.appendDeviceReport(context, freshDiag)
                                lastReportTime = prefs.lastReportTimestamp
                            }

                            isRepairingSettings = false
                            Toast.makeText(
                                context,
                                "Repair applied: Ring 100%, DND OFF, Brightness 80%",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    enabled = !isRepairingSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isRepairingSettings) {
                        CircularProgressIndicator(
                            color = DeepBlack,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = "Repair Settings Now",
                            fontWeight = FontWeight.SemiBold,
                            color = DeepBlack,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 2: Remote Diagnostics Snapshot ("REPORT" Command)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AccentEmerald)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "REMOTE DIAGNOSTICS",
                            fontWeight = FontWeight.SemiBold,
                            color = AccentEmerald,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    Surface(
                        color = SurfaceElevated,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Text(
                            text = "SMS: REPORT",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = AccentEmerald,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Remote Status Report",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Send an SMS with text 'REPORT' to this phone from anywhere. The app immediately collects hardware vitals and appends them to the 'Device_Status' tab in SUPERAPP_SHEET.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                // Live Metrics Snapshot Container
                if (liveDiagnostics != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = SurfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "CURRENT PHONE SNAPSHOT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMuted,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val diag = liveDiagnostics!!
                            StatusRow(label = "Battery", value = diag.battery)
                            StatusRow(label = "Sound / Ringer", value = diag.ringer)
                            StatusRow(
                                label = "DND Mode",
                                value = diag.dnd,
                                highlight = diag.dnd.startsWith("ON")
                            )
                            StatusRow(label = "Brightness", value = diag.brightness)
                            StatusRow(label = "Wi-Fi", value = diag.wifi)
                            StatusRow(label = "Mobile Data", value = diag.mobileData)
                            StatusRow(label = "Airplane Mode", value = diag.airplaneMode)
                            StatusRow(label = "Bluetooth", value = diag.bluetooth)
                            StatusRow(label = "GPS Location", value = diag.location)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Last Cloud Report",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = formatRelativeTime(lastReportTime),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (lastReportTime > 0L) AccentEmerald else TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (!prefs.isSignedIn) {
                            Toast.makeText(context, "Sign in first from SMS Backup", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        scope.launch {
                            isGeneratingReport = true
                            val diag = DeviceReportHelper.collectDiagnostics(context)
                            liveDiagnostics = diag
                            val result = GoogleSheetsManager.appendDeviceReport(context, diag)
                            isGeneratingReport = false
                            if (result.isSuccess) {
                                lastReportTime = prefs.lastReportTimestamp
                                Toast.makeText(context, "Report uploaded to 'Device_Status' tab", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Upload failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isGeneratingReport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    if (isGeneratingReport) {
                        CircularProgressIndicator(
                            color = TextPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = "Generate & Upload Report Now",
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 3: Google Sheet Self-Healing
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(AccentEmerald)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GOOGLE SHEET INTEGRITY",
                        fontWeight = FontWeight.SemiBold,
                        color = AccentEmerald,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "SUPERAPP_SHEET Self-Healing",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Verifies cloud spreadsheet existence, auto-repairs missing tabs ('Messages' and 'Device_Status'), and restores formatting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (!prefs.isSignedIn) {
                            Toast.makeText(context, "Sign in first from SMS Backup", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        scope.launch {
                            isHealing = true
                            val result = GoogleSheetsManager.getOrCreateSpreadsheet(context)
                            isHealing = false
                            if (result.isSuccess) {
                                Toast.makeText(context, "SUPERAPP_SHEET verified & healthy", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Repair error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isHealing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    if (isHealing) {
                        CircularProgressIndicator(
                            color = TextPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = "Verify & Repair Spreadsheet",
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 4: Local SQLite Queue Flush
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(AccentAmber)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "QUEUE BUFFER",
                        fontWeight = FontWeight.SemiBold,
                        color = AccentAmber,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Pending Local Messages",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$pendingCount message(s) waiting for upload",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Text(
                        text = "$pendingCount",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = if (pendingCount > 0) AccentAmber else AccentEmerald
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isSyncing = true
                            val result = SyncManager.performSync(context)
                            pendingCount = dbHelper.getPendingCount()
                            isSyncing = false
                            if (result.isSuccess) {
                                Toast.makeText(context, "Queue flushed successfully (${result.getOrDefault(0)} synced)", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Sync failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            color = TextPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = "Force Sync Queue",
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 5: Background & Battery Health
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Background Execution Health",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isIgnoringBatteryOptimizations)
                        "Battery optimization is unrestricted. Background sync runs uninterrupted."
                    else
                        "Battery optimization is active. Android may delay background delivery when phone is idle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                if (!isIgnoringBatteryOptimizations) {
                    Spacer(modifier = Modifier.height(14.dp))
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Text(
                            text = "Whitelist Battery",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextSecondary
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) AccentAmber else TextPrimary
        )
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return "Never"
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hr ago"
        else -> "$days day(s) ago"
    }
}
