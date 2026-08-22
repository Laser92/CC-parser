package com.laser92.cheddar.ui.component

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.laser92.cheddar.ui.theme.*

@Composable
fun FilePicker(
    selectedFileName: String?,
    selectedFileSize: String?,
    onFileClick: () -> Unit,
    onRemoveFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isSelected = selectedFileName != null

    AnimatedContent(
        targetState = isSelected,
        label = "FilePickerAnimation",
        transitionSpec = {
            androidx.compose.animation.fadeIn(animationSpec = tween(300)) togetherWith androidx.compose.animation.fadeOut(animationSpec = tween(300))
        }
    ) { hasFile ->
        if (hasFile) {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BgCard)
                    .border(1.dp, SuccessBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = "File",
                        tint = Success,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedFileName ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                        if (selectedFileSize != null) {
                            Text(
                                text = selectedFileSize,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                    IconButton(onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRemoveFile() 
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove file",
                            tint = TextSecondary
                        )
                    }
                }
            }
        } else {
            val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.02f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(1000),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "scale"
            )
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(1000),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "alpha"
            )

            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .scale(scale)
                    .clip(RoundedCornerShape(24.dp))
                    .background(AccentStart.copy(alpha = 0.08f))
                    .border(1.dp, AccentStart.copy(alpha = 0.3f * alpha), RoundedCornerShape(24.dp))
                    .clickable(onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onFileClick() 
                    })
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = "Upload",
                        tint = AccentStart,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Tap to select file",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "PDF, PNG, JPG, BMP, TIFF, WEBP",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
