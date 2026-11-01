package com.personal.msgforwarder.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.msgforwarder.R
import com.personal.msgforwarder.data.PreferencesHelper
import com.personal.msgforwarder.ui.AppScreen
import com.personal.msgforwarder.ui.theme.AccentEmerald
import com.personal.msgforwarder.ui.theme.BorderSubtle
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
 * Stylish double chevron "<<" icon for closing the side drawer.
 */
@Composable
fun DoubleChevronLeftIcon(
    modifier: Modifier = Modifier.size(18.dp),
    color: Color = AccentEmerald
) {
    Canvas(modifier = modifier) {
        val stroke = 2.2.dp.toPx()
        val w = size.width
        val h = size.height

        // First chevron (Left)
        val path1 = Path().apply {
            moveTo(w * 0.44f, h * 0.18f)
            lineTo(w * 0.14f, h * 0.5f)
            lineTo(w * 0.44f, h * 0.82f)
        }
        drawPath(
            path = path1,
            color = color,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Second chevron (Right)
        val path2 = Path().apply {
            moveTo(w * 0.84f, h * 0.18f)
            lineTo(w * 0.54f, h * 0.5f)
            lineTo(w * 0.84f, h * 0.82f)
        }
        drawPath(
            path = path2,
            color = color,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/**
 * Reusable menu item for SuperApp components.
 */
@Composable
private fun DrawerComponentItem(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) AccentEmerald.copy(alpha = 0.12f) else SurfaceElevated,
        border = BorderStroke(
            1.dp,
            if (isSelected) AccentEmerald.copy(alpha = 0.45f) else BorderSubtle
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) AccentEmerald else TextMuted)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) TextPrimary else TextSecondary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) AccentEmerald else TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Side slide-out navigation drawer sheet hosting SuperApp components.
 */
@Composable
fun AppDrawerContent(
    prefs: PreferencesHelper,
    currentScreen: AppScreen = AppScreen.SMS_BACKUP,
    onSelectScreen: (AppScreen) -> Unit = {},
    onCloseDrawer: () -> Unit = {}
) {
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
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 22.dp, bottom = 24.dp)
        ) {
            // Header: Logo & Title
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "SuperApp Logo",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "SuperApp",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 22.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section Label
            Text(
                text = "COMPONENTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Component 1: SMS Backup
            DrawerComponentItem(
                title = "SMS Backup",
                subtitle = "Google Drive Sync",
                isSelected = currentScreen == AppScreen.SMS_BACKUP,
                onClick = { onSelectScreen(AppScreen.SMS_BACKUP) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Component 2: Repair
            DrawerComponentItem(
                title = "Repair",
                subtitle = "Self-Healing & Diagnostics",
                isSelected = currentScreen == AppScreen.REPAIR,
                onClick = { onSelectScreen(AppScreen.REPAIR) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Component 3: Setting
            DrawerComponentItem(
                title = "Setting",
                subtitle = "Preferences & System",
                isSelected = currentScreen == AppScreen.SETTING,
                onClick = { onSelectScreen(AppScreen.SETTING) }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Bottom: Stylish "<<" Closing Menu Button
            Surface(
                onClick = onCloseDrawer,
                shape = RoundedCornerShape(14.dp),
                color = SurfaceElevated,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DoubleChevronLeftIcon(
                        modifier = Modifier.size(18.dp),
                        color = AccentEmerald
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Close Menu",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        letterSpacing = 0.4.sp
                    )
                }
            }
        }
    }
}
