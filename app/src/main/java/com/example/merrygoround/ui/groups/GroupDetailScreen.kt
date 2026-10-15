package com.example.merrygoround.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.merrygoround.core.common.Money
import com.example.merrygoround.core.common.TimeFormatter
import com.example.merrygoround.domain.model.ContributionStatus
import com.example.merrygoround.domain.model.GroupStatus
import com.example.merrygoround.domain.model.Membership
import com.example.merrygoround.domain.model.MembershipRole
import com.example.merrygoround.domain.model.MembershipStatus
import com.example.merrygoround.ui.components.CardSection
import com.example.merrygoround.ui.components.ChipTone
import com.example.merrygoround.ui.components.ConfirmDialog
import com.example.merrygoround.ui.components.ErrorState
import com.example.merrygoround.ui.components.LoadingState
import com.example.merrygoround.ui.components.PrimaryButton
import com.example.merrygoround.ui.components.SecondaryButton
import com.example.merrygoround.ui.components.StatusChip
import com.example.merrygoround.ui.components.label
import com.example.merrygoround.ui.components.tone
import com.example.merrygoround.ui.theme.BorderGray
import com.example.merrygoround.ui.theme.BrandGreen
import com.example.merrygoround.ui.theme.TextPrimary
import com.example.merrygoround.ui.theme.TextSecondary

/** 05 — Group details: members, roles, obligations, invite sharing, admin actions. */
@Composable
fun GroupDetailScreen(
    state: GroupDetailUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDecide: (membershipId: String, approve: Boolean) -> Unit,
    onRemove: (membershipId: String) -> Unit,
    onStartGroup: () -> Unit,
    onMoveMember: (membershipId: String, up: Boolean) -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenContribute: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(state.actionMessage, state.actionError) {
        val msg = state.actionMessage ?: state.actionError
        if (msg != null) snackbarHostState.showSnackbar(msg)
    }

    var confirmRemoveId by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    if (state.isLoading && state.group == null) {
        LoadingState(label = "Loading group…")
        return
    }
    val group = state.group
    if (group == null) {
        ErrorState(message = state.errorMessage ?: "Group not found", onRetry = onRefresh)
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(group.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Spacer(Modifier.weight(1f))
                    StatusChip(group.status.label(), if (group.status == GroupStatus.ACTIVE) ChipTone.SUCCESS else ChipTone.INFO)
                }
            }

            item {
                CardSection {
                    if (group.description.isNotBlank()) {
                        Text(group.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Spacer(Modifier.height(12.dp))
                    }
                    DetailRow("Contribution", Money.format(group.contributionAmountMinor, group.currency) + " / " +
                        group.contributionFrequency.name.lowercase())
                    DetailRow("Members", "${group.memberCount} / ${group.memberLimit}")
                    DetailRow("Starts", TimeFormatter.formatEpochDay(group.startEpochDay))
                    DetailRow("Join policy", group.joinPolicy.name.lowercase().replace('_', ' '))
                    DetailRow("Payout order", group.payoutOrderMethod.name.lowercase().replace('_', ' '))
                }
            }

            item {
                CardSection {
                    Text("Invitation code", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    val invite = state.invitation
                    if (invite == null) {
                        Text("Loading invitation…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                invite.code,
                                style = MaterialTheme.typography.headlineSmall,
                                color = BrandGreen,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("invite_code")
                            )
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(invite.code))
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy invitation code", tint = BrandGreen)
                            }
                        }
                        Text(
                            "Expires ${TimeFormatter.formatDate(invite.expiresAtMillis)} · " +
                                "${invite.usedCount} use${if (invite.usedCount == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            if (group.status == GroupStatus.ACTIVE) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrimaryButton("Contribute", onOpenContribute, modifier = Modifier.weight(1f))
                        SecondaryButton("Payout schedule", onOpenSchedule, modifier = Modifier.weight(1f))
                    }
                }
            }

            if (state.isAdmin && group.status == GroupStatus.FORMING) {
                item {
                    CardSection {
                        Text("Start the group", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Locks the payout order and generates the full rotation. " +
                                "At least 2 active members are required.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton(
                            text = if (state.actionInFlight) "Starting…" else "Start rotation",
                            onClick = onStartGroup,
                            enabled = state.members.count { it.status == MembershipStatus.ACTIVE } >= 2 &&
                                !state.actionInFlight,
                            loading = state.actionInFlight,
                            modifier = Modifier.testTag("start_group")
                        )
                    }
                }
            }

            item {
                Text("Members (${state.members.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            items(state.members, key = { it.id }) { member ->
                MemberRow(
                    member = member,
                    obligation = state.obligations.firstOrNull { it.userId == member.userId },
                    isAdmin = state.isAdmin,
                    groupIsForming = group.status == GroupStatus.FORMING,
                    actionInFlight = state.actionInFlight,
                    onApprove = { onDecide(member.id, true) },
                    onReject = { onDecide(member.id, false) },
                    onRemove = { confirmRemoveId = member.id },
                    onMoveUp = { onMoveMember(member.id, true) },
                    onMoveDown = { onMoveMember(member.id, false) }
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }


    confirmRemoveId?.let { id ->
        val member = state.members.firstOrNull { it.id == id }
        if (member != null) {
            ConfirmDialog(
                title = "Remove ${member.userName}?",
                message = "They will lose access to this group. Their past contributions stay in the records.",
                confirmLabel = "Remove",
                destructive = true,
                onConfirm = { confirmRemoveId = null; onRemove(id) },
                onDismiss = { confirmRemoveId = null }
            )
        }
    }
}


@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}

@Composable
private fun MemberRow(
    member: Membership,
    obligation: com.example.merrygoround.domain.model.MemberObligation?,
    isAdmin: Boolean,
    groupIsForming: Boolean,
    actionInFlight: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    CardSection {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(member.userName, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    if (member.role != MembershipRole.MEMBER) {
                        StatusChip(member.role.name, ChipTone.INFO)
                    }
                }
                if (member.userPhone.isNotBlank()) {
                    Text(member.userPhone, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                member.payoutOrder?.let {
                    Text("Payout position #$it", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusChip(member.status.label(), member.status.chipTone())
                if (obligation != null && member.status == MembershipStatus.ACTIVE) {
                    Spacer(Modifier.height(4.dp))
                    val status = obligation.status
                    StatusChip(
                        label = when (status) {
                            ContributionStatus.CONFIRMED -> "Paid"
                            ContributionStatus.PENDING -> "Pending"
                            null -> "Not paid"
                            else -> status.name.lowercase().replaceFirstChar { it.uppercase() }
                        },
                        tone = status?.tone() ?: ChipTone.NEUTRAL
                    )
                }
            }
        }

        if (isAdmin && member.status == MembershipStatus.PENDING) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Approve", onApprove, modifier = Modifier.weight(1f), enabled = !actionInFlight)
                SecondaryButton("Decline", onReject, modifier = Modifier.weight(1f), enabled = !actionInFlight)
            }
        }
        if (isAdmin && groupIsForming && member.status == MembershipStatus.ACTIVE &&
            member.role != MembershipRole.OWNER
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onMoveUp, enabled = !actionInFlight) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up in payout order", tint = BrandGreen)
                    Text("Up", color = BrandGreen)
                }
                TextButton(onClick = onMoveDown, enabled = !actionInFlight) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down in payout order", tint = BrandGreen)
                    Text("Down", color = BrandGreen)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRemove, enabled = !actionInFlight) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun MembershipStatus.chipTone(): com.example.merrygoround.ui.components.ChipTone =
    when (this) {
        MembershipStatus.ACTIVE -> com.example.merrygoround.ui.components.ChipTone.SUCCESS
        MembershipStatus.PENDING -> com.example.merrygoround.ui.components.ChipTone.PENDING
        MembershipStatus.REJECTED, MembershipStatus.REMOVED ->
            com.example.merrygoround.ui.components.ChipTone.ERROR
    }
