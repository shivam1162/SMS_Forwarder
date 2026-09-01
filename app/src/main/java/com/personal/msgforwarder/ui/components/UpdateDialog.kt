package com.personal.msgforwarder.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.msgforwarder.util.AppUpdateInfo
import com.personal.msgforwarder.util.UpdateManager
import kotlinx.coroutines.launch

@Composable
fun UpdateCheckerWrapper(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }

    // Check for update on launch
    LaunchedEffect(Unit) {
        UpdateManager.checkForUpdates { info ->
            updateInfo = info
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()

        // Show update dialog if new version detected
        if (updateInfo != null) {
            AlertDialog(
                onDismissRequest = {
                    if (!isDownloading) updateInfo = null
                },
                title = {
                    Text(text = "🎉 New Update Available")
                },
                text = {
                    Column {
                        Text(
                            text = "Version: ${updateInfo?.versionName} (Build ${updateInfo?.versionCode})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!updateInfo?.releaseNotes.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = updateInfo?.releaseNotes ?: "",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (isDownloading) {
                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (downloadProgress > 0) "Downloading: ${(downloadProgress * 100).toInt()}%" else "Starting download...",
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                },
                confirmButton = {
                    if (!isDownloading) {
                        Button(
                            onClick = {
                                val url = updateInfo?.downloadUrl ?: return@Button
                                isDownloading = true
                                scope.launch {
                                    val result = UpdateManager.downloadAndInstall(context, url) { progress ->
                                        downloadProgress = progress
                                    }
                                    isDownloading = false
                                    if (result.isFailure) {
                                        Toast.makeText(
                                            context,
                                            "Download failed: ${result.exceptionOrNull()?.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        ) {
                            Text("Update Now")
                        }
                    }
                },
                dismissButton = {
                    if (!isDownloading) {
                        TextButton(onClick = { updateInfo = null }) {
                            Text("Later")
                        }
                    }
                }
            )
        }
    }
}
