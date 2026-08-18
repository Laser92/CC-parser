package com.laser92.cheddar.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.laser92.cheddar.ui.theme.*

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    gradientStart: Color = AccentStart,
    gradientEnd: Color = AccentEnd,
    icon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val brush = Brush.horizontalGradient(listOf(gradientStart, gradientEnd))
    val alpha = if (enabled) (if (isPressed) 0.8f else 1f) else 0.5f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(brush = brush)
            .alpha(alpha)
            .clickable(
                onClick = { if (!loading && enabled) onClick() },
                enabled = enabled,
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple()
            )
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = TextPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
            }
        }
    }
}
