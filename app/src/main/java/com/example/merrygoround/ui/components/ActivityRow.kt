package com.example.merrygoround.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.merrygoround.core.common.Money
import com.example.merrygoround.core.common.TimeFormatter
import com.example.merrygoround.domain.model.ActivityEntry
import com.example.merrygoround.domain.model.ContributionStatus
import com.example.merrygoround.ui.theme.BorderGray
import com.example.merrygoround.ui.theme.BrandGreen
import com.example.merrygoround.ui.theme.BrandGreenLight
import com.example.merrygoround.ui.theme.InfoBlue
import com.example.merrygoround.ui.theme.InfoBlueLight
import com.example.merrygoround.ui.theme.SurfaceGray
import com.example.merrygoround.ui.theme.TextPrimary
import com.example.merrygoround.ui.theme.TextSecondary
import com.example.merrygoround.ui.theme.WarningAmberLight

/**
 * Transaction/activity list row used on the Activity tab and dashboard
 * (brief §06: reference, timestamp, status, amount at a glance).
 */
@Composable
fun ActivityRow(entry: ActivityEntry, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (iconChar, tint, bg) = when (entry.kind) {
            "CONTRIBUTION" -> Triple("C", BrandGreen, BrandGreenLight)
            "PAYOUT" -> Triple("P", InfoBlue, InfoBlueLight)
            else -> Triple("G", WarningAmberLight, SurfaceGray)
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(bg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(iconChar, style = MaterialTheme.typography.titleSmall, color = tint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 2
            )
            Spacer(Modifier.height(2.dp))
            Text(
                TimeFormatter.formatRelative(entry.timestampMillis),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            entry.amountMinor?.let {
                Text(
                    Money.format(it, entry.currency ?: "KES", includeCode = false),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
            }
            Spacer(Modifier.height(4.dp))
            val status = runCatching { ContributionStatus.fromWire(entry.status) }.getOrNull()
            StatusChip(
                label = entry.status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                tone = status?.tone() ?: ChipTone.NEUTRAL
            )
        }
    }
}
