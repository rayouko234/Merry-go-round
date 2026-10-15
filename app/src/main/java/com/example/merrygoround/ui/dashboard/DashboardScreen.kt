package com.example.merrygoround.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.merrygoround.core.common.Money
import com.example.merrygoround.core.common.TimeFormatter
import com.example.merrygoround.domain.model.ContributionStatus
import com.example.merrygoround.domain.model.GroupStatus
import com.example.merrygoround.ui.components.ActivityRow
import com.example.merrygoround.ui.components.CardSection
import com.example.merrygoround.ui.components.ChipTone
import com.example.merrygoround.ui.components.EmptyState
import com.example.merrygoround.ui.components.ErrorState
import com.example.merrygoround.ui.components.LoadingState
import com.example.merrygoround.ui.components.PrimaryButton
import com.example.merrygoround.ui.components.SecondaryButton
import com.example.merrygoround.ui.components.StatusChip
import com.example.merrygoround.ui.components.label
import com.example.merrygoround.ui.components.tone
import com.example.merrygoround.ui.theme.BorderGray
import com.example.merrygoround.ui.theme.BrandGreen
import com.example.merrygoround.ui.theme.BrandGreenLight
import com.example.merrygoround.ui.theme.ErrorRed
import com.example.merrygoround.ui.theme.TextPrimary
import com.example.merrygoround.ui.theme.TextSecondary
import com.example.merrygoround.ui.theme.WarningAmber

/** 04 — Dashboard: cycle totals, member status, next recipient, contribute CTA. */
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onContribute: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    onOpenNotifications: () -> Unit
) {
    when {
        state.isLoading && state.summary == null && state.groups.isEmpty() ->
            LoadingState(
                label = "Loading your groups…",
                modifier = Modifier.statusBarsPadding()
            )

        state.errorMessage != null && state.summary == null ->
            ErrorState(
                message = state.errorMessage ?: "Something went wrong",
                onRetry = onRefresh,
                modifier = Modifier.statusBarsPadding()
            )

        state.noGroups ->
            Column(Modifier.statusBarsPadding()) {
                EmptyState(
                    title = "No savings groups yet",
                    message = "Create your own group or join one with an invitation code to start saving together.",
                    actionText = "Create a group",
                    onAction = onCreateGroup
                )
                androidx.compose.material3.TextButton(onClick = onJoinGroup) {
                    androidx.compose.material3.Text("or join with a code", color = BrandGreen)
                }
            }

        else -> DashboardContent(
            state = state,
            onSelectGroup = onSelectGroup,
            onContribute = onContribute,
            onOpenGroup = onOpenGroup,
            onOpenNotifications = onOpenNotifications
        )
    }
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    onSelectGroup: (String) -> Unit,
    onContribute: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenNotifications: () -> Unit
) {
    val summary = state.summary ?: return
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("dashboard_list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                var menuOpen by remember { mutableStateOf(false) }
                if (state.groups.size > 1) {
                    TextButtonLike(text = summary.groupName, onClick = { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        state.groups.forEach { g ->
                            DropdownMenuItem(
                                text = { Text(g.name) },
                                onClick = {
                                    menuOpen = false
                                    onSelectGroup(g.id)
                                }
                            )
                        }
                    }
                } else {
                    Text(
                        summary.groupName,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenNotifications) {
                    Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = TextSecondary)
                }
            }
        }

        item {
            CardSection {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (summary.currentCycleNumber != null)
                                "Cycle ${summary.currentCycleNumber} contribution"
                            else "Getting ready",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            Money.format(summary.contributionAmountMinor, summary.currency),
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        summary.deadlineEpochDay?.let {
                            Text(
                                "Due by ${TimeFormatter.formatEpochDay(it)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = WarningAmber
                            )
                        }
                    }
                    StatusChip(
                        label = when (summary.myContributionStatus) {
                            ContributionStatus.CONFIRMED -> "You've paid"
                            ContributionStatus.PENDING -> "Awaiting verification"
                            ContributionStatus.FAILED -> "Failed"
                            ContributionStatus.REVERSED -> "Reversed"
                            null -> "Not paid yet"
                        },
                        tone = summary.myContributionStatus?.tone() ?: ChipTone.PENDING
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (summary.status == GroupStatus.ACTIVE) {
                    PrimaryButton(
                        text = if (summary.myContributionStatus == ContributionStatus.CONFIRMED)
                            "View my contributions" else "Contribute Now",
                        onClick = { onContribute(summary.groupId) },
                        modifier = Modifier.testTag("contribute_now")
                    )
                } else {
                    SecondaryButton(
                        text = "Open group",
                        onClick = { onOpenGroup(summary.groupId) }
                    )
                }
            }
        }

        item {
            // Cycle money roll-up: expected / confirmed / pending / outstanding
            CardSection {
                Text("This cycle's pot", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                Spacer(Modifier.height(12.dp))
                StatRow("Expected", Money.format(summary.expectedCycleTotalMinor, summary.currency), TextPrimary)
                StatRow(
                    "Confirmed",
                    Money.format(summary.confirmedTotalMinor, summary.currency),
                    BrandGreen
                )
                StatRow(
                    "Pending verification",
                    Money.format(summary.pendingTotalMinor, summary.currency),
                    WarningAmber
                )
                if (summary.failedTotalMinor > 0) {
                    StatRow(
                        "Failed",
                        Money.format(summary.failedTotalMinor, summary.currency),
                        ErrorRed
                    )
                }
                if (summary.reversedTotalMinor > 0) {
                    StatRow(
                        "Reversed",
                        Money.format(summary.reversedTotalMinor, summary.currency),
                        TextSecondary
                    )
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.HorizontalDivider(color = BorderGray)
                Spacer(Modifier.height(8.dp))
                StatRow(
                    "Outstanding",
                    Money.format(summary.outstandingTotalMinor, summary.currency),
                    TextPrimary,
                    bold = true
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = {
                            if (summary.expectedCycleTotalMinor == 0L) 0f
                            else summary.confirmedTotalMinor.toFloat() / summary.expectedCycleTotalMinor.toFloat()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp),
                        color = BrandGreen,
                        trackColor = BrandGreenLight
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${summary.paidMemberCount}/${summary.memberCount} paid",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        item {
            CardSection {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Next payout", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            summary.nextRecipientName ?: "To be confirmed",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        summary.nextPayoutAtMillis?.let {
                            Text(
                                TimeFormatter.formatDate(it),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    StatusChip(
                        label = if (summary.status == GroupStatus.ACTIVE) "Scheduled" else summary.status.label(),
                        tone = if (summary.status == GroupStatus.ACTIVE) ChipTone.INFO else ChipTone.NEUTRAL
                    )
                }
                Spacer(Modifier.height(12.dp))
                MemberStatusRow(
                    paid = summary.paidMemberCount,
                    pending = summary.pendingMemberCount,
                    unpaid = summary.unpaidMemberCount
                )
            }
        }

        if (state.recentActivity.isNotEmpty()) {
            item {
                Text(
                    "Recent activity",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(state.recentActivity, key = { it.id }) { entry ->
                com.example.merrygoround.ui.components.ActivityRow(entry)
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, color: androidx.compose.ui.graphics.Color, bold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(
            value,
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun MemberStatusRow(paid: Int, pending: Int, unpaid: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusChip("$paid confirmed", ChipTone.SUCCESS)
        if (pending > 0) StatusChip("$pending pending", ChipTone.PENDING)
        if (unpaid > 0) StatusChip("$unpaid not paid", ChipTone.NEUTRAL)
    }
}


@Composable
private fun TextButtonLike(text: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Switch group", tint = TextSecondary)
    }
}
