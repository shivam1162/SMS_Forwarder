package com.personal.msgforwarder.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.msgforwarder.data.PreferencesHelper
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
                .padding(top = 18.dp, bottom = 24.dp)
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
                        text = "SuperApp System",
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

            // Card: Created by SHIVAM GUPTA
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
