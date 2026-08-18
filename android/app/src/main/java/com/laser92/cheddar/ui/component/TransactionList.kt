package com.laser92.cheddar.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.laser92.cheddar.sheets.ReconcileResult
import com.laser92.cheddar.sheets.TransactionSummary
import com.laser92.cheddar.ui.theme.*
import kotlin.math.abs

@Composable
fun TransactionList(
    result: ReconcileResult,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        if (result.addedTransactions.isNotEmpty()) {
            Text(
                text = "Added to Sheet",
                style = MaterialTheme.typography.titleLarge,
                color = Success,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            result.addedTransactions.forEach { tx ->
                TransactionRow(tx = tx, dotColor = Success)
            }
        }
        
        if (result.skippedTransactions.isNotEmpty()) {
            Text(
                text = "Already in Sheet (skipped)",
                style = MaterialTheme.typography.titleLarge,
                color = TextMuted,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            result.skippedTransactions.forEach { tx ->
                TransactionRow(tx = tx, dotColor = TextMuted)
            }
        }
    }
}

@Composable
fun TransactionRow(
    tx: TransactionSummary,
    dotColor: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(BgCardHover)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tx.merchant,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tx.date,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentStart.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = tx.card,
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentEnd
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        val isCredit = tx.amount < 0
        val amountColor = if (isCredit) Success else TextPrimary
        val sign = if (isCredit) "+" else ""
        Text(
            text = "$sign₹${abs(tx.amount)}",
            style = MaterialTheme.typography.titleLarge,
            color = amountColor
        )
    }
}
