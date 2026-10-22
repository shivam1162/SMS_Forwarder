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
import com.personal.msgforwarder.ui.theme.BorderSubtle
import com.personal.msgforwarder.ui.theme.SurfaceDark
import com.personal.msgforwarder.ui.theme.TextPrimary

/**
 * Clean, minimalist 3-line menu icon.
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
 * Minimalist, premium dialog displaying ONLY "Created by SHIVAM GUPTA".
 */
@Composable
fun AboutDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = SurfaceDark,
        tonalElevation = 6.dp,
        text = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Created by SHIVAM GUPTA",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Close",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    )
}
