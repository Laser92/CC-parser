package com.laser92.cheddar.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.laser92.cheddar.ui.theme.AccentStart

@Composable
fun DocumentScannerAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val lineOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lineOffset"
    )

    Canvas(modifier = modifier.size(48.dp)) {
        val width = size.width
        val height = size.height

        // Draw Document Outline
        drawRoundRect(
            color = Color.White.copy(alpha = 0.8f),
            topLeft = Offset(width * 0.15f, height * 0.1f),
            size = Size(width * 0.7f, height * 0.8f),
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 4f)
        )

        // Draw text lines
        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(width * 0.3f, height * 0.3f),
            end = Offset(width * 0.7f, height * 0.3f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(width * 0.3f, height * 0.45f),
            end = Offset(width * 0.6f, height * 0.45f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(width * 0.3f, height * 0.6f),
            end = Offset(width * 0.5f, height * 0.6f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )

        // Draw scanning laser
        val scanY = height * 0.1f + (height * 0.8f * lineOffset)
        drawLine(
            color = AccentStart,
            start = Offset(width * 0.05f, scanY),
            end = Offset(width * 0.95f, scanY),
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )
        
        // Laser glow
        drawLine(
            color = AccentStart.copy(alpha = 0.4f),
            start = Offset(width * 0.05f, scanY - 4f),
            end = Offset(width * 0.95f, scanY - 4f),
            strokeWidth = 12f,
            cap = StrokeCap.Round
        )
    }
}
