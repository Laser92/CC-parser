package com.laser92.cheddar.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.laser92.cheddar.ui.theme.*

@Composable
fun StatCard(
    value: Int,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = valueColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
    }
}
