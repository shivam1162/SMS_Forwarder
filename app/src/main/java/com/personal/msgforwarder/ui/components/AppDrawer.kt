package com.personal.msgforwarder.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.msgforwarder.data.PreferencesHelper
import com.personal.msgforwarder.ui.theme.AccentEmerald
import com.personal.msgforwarder.ui.theme.BorderSubtle
import com.personal.msgforwarder.ui.theme.DeepBlack
import com.personal.msgforwarder.ui.theme.SurfaceDark
import com.personal.msgforwarder.ui.theme.SurfaceElevated
import com.personal.msgforwarder.ui.theme.TextMuted
import com.personal.msgforwarder.ui.theme.TextPrimary
import com.personal.msgforwarder.ui.theme.TextSecondary

/**
 * Clean, minimalist 3-line hamburger menu icon.
 */
@Composable
fun HamburgerMenuIcon(
    modifier: Modifier = Modifier.size(22.dp),
    color: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.8.dp.toPx()
        val spacing = size.height / 3

        // Line 1 (Top)
        drawLine(
            color = color,
            start = Offset(0f, spacing * 0.5f),
            end = Offset(size.width, spacing * 0.5f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Line 2 (Middle)
        drawLine(
            color = color,
            start = Offset(0f, spacing * 1.5f),
            end = Offset(size.width * 0.75f, spacing * 1.5f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Line 3 (Bottom)
        drawLine(
            color = color,
            start = Offset(0f, spacing * 2.5f),
            end = Offset(size.width, spacing * 2.5f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Clean, minimalist 'X' close icon.
 */
@Composable
fun CloseIcon(
    modifier: Modifier = Modifier.size(16.dp),
    color: Color = TextSecondary
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.8.dp.toPx()
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(0f, size.height),
            end = Offset(size.width, 0f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Side slide-out navigation drawer sheet.
 */
@Composable
fun AppDrawerContent(
    prefs: PreferencesHelper,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var isEncrypted by remember { mutableStateOf(prefs.isEncryptionEnabled) }
    var showKeyDialog by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf(prefs.encryptionKey ?: "") }
    var isKeyVisible by remember { mutableStateOf(false) }

    // Secret Key Input Dialog
    if (showKeyDialog) {
        AlertDialog(
            onDismissRequest = {
                showKeyDialog = false
                if (prefs.encryptionKey.isNullOrBlank()) {
                    isEncrypted = false
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = SurfaceDark,
            tonalElevation = 6.dp,
            title = {
                Text(
                    text = "AES-256 Secret Key",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 17.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a secret key. All message content uploaded to Google Sheets will be encrypted with AES-256.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        singleLine = true,
                        label = { Text("Secret Key", color = TextSecondary) },
                        placeholder = { Text("Enter secret key...", color = TextMuted) },
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(
                                onClick = { isKeyVisible = !isKeyVisible },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = if (isKeyVisible) "HIDE" else "SHOW",
                                    color = AccentEmerald,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentEmerald,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = AccentEmerald,
                            focusedContainerColor = DeepBlack,
                            unfocusedContainerColor = DeepBlack
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = keyInput.trim()
                        if (trimmed.isNotEmpty()) {
                            prefs.encryptionKey = trimmed
                            prefs.isEncryptionEnabled = true
                            isEncrypted = true
                            showKeyDialog = false
                            Toast.makeText(context, "AES-256 encryption enabled", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Key cannot be empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentEmerald,
                        contentColor = DeepBlack
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Save",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showKeyDialog = false
                        if (prefs.encryptionKey.isNullOrBlank()) {
                            isEncrypted = false
                        }
                    }
                ) {
                    Text(
                        text = "Cancel",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        )
    }

    ModalDrawerSheet(
        drawerContainerColor = SurfaceDark,
        drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
        drawerTonalElevation = 0.dp,
        modifier = Modifier
            .width(320.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            // Header: Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "SMS Backup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        letterSpacing = 0.3.sp
                    )
                    Text(
                        text = "Menu & Security",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp)
                ) {
                    CloseIcon(color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Card 1: Created by SHIVAM GUPTA
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceElevated,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Created by SHIVAM GUPTA",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        fontSize = 15.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 2: AES-256 Encryption Toggle
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceElevated,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AES-256 Encryption",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Canvas(modifier = Modifier.size(6.dp)) {
                                    drawCircle(
                                        color = if (isEncrypted) AccentEmerald else TextMuted
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isEncrypted) "Active - Body Encrypted" else "Disabled - Plain Text",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isEncrypted) AccentEmerald else TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Switch(
                            checked = isEncrypted,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    keyInput = prefs.encryptionKey ?: ""
                                    showKeyDialog = true
                                } else {
                                    prefs.isEncryptionEnabled = false
                                    isEncrypted = false
                                    Toast.makeText(context, "AES-256 encryption disabled", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TextPrimary,
                                checkedTrackColor = AccentEmerald,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = DeepBlack,
                                uncheckedBorderColor = BorderSubtle
                            )
                        )
                    }

                    if (isEncrypted) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(
                            color = BorderSubtle,
                            thickness = 1.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Key: \u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022",
                                color = TextMuted,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            TextButton(
                                onClick = {
                                    keyInput = prefs.encryptionKey ?: ""
                                    showKeyDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Change Key",
                                    color = AccentEmerald,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Footer Close Button
            TextButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Close Menu",
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        }
    }
}
