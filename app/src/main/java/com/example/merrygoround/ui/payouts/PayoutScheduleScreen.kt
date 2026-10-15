package com.example.merrygoround.ui.payouts

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.merrygoround.core.common.Money
import com.example.merrygoround.core.common.TimeFormatter
import com.example.merrygoround.domain.model.CycleStatus
import com.example.merrygoround.domain.model.PayoutStatus
import com.example.merrygoround.ui.components.CardSection
import com.example.merrygoround.ui.components.ChipTone
import com.example.merrygoround.ui.components.ConfirmDialog
import com.example.merrygoround.ui.components.EmptyState
import com.example.merrygoround.ui.components.ErrorState
import com.example.merrygoround.ui.components.LoadingState
import com.example.merrygoround.ui.components.PrimaryButton
import com.example.merrygoround.ui.components.StatusChip
import com.example.merrygoround.ui.components.tone
import com.example.merrygoround.ui.theme.BorderGray
import com.example.merrygoround.ui.theme.BrandGreen
import com.example.merrygoround.ui.theme.TextPrimary
import com.example.merrygoround.ui.theme.TextSecondary

/** 07 — Payout schedule: ordered recipients, dates, statuses, completion history. */
@Composable
fun PayoutScheduleScreen(
    state: PayoutScheduleUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCompletePayout: (payoutId: String) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmPayoutId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.actionMessage, state.actionError) {
        val msg = state.actionMessage ?: state.actionError
        if (msg != null) snackbarHostState.showSnackbar(msg)
    }

    when {
        state.isLoading && state.group == null -> LoadingState(label = "Loading schedule…")
        state.errorMessage != null && state.group == null ->
            ErrorState(message = state.errorMessage ?: "Something went wrong", onRetry = onRefresh)
        state.cycles.isEmpty() -> {
            Column {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                EmptyState(
                    title = "No schedule yet",
                    message = "The rotation is generated when the admin starts the group with at least 2 members."
                )
            }
        }
        else -> PayoutContent(state, snackbarHostState, onBack) { confirmPayoutId = it }
    }

    confirmPayoutId?.let { payoutId ->
        val payout = state.payouts.firstOrNull { it.id == payoutId }
        if (payout != null) {
            ConfirmDialog(
                title = "Confirm payout completed?",
                message = "Confirm only after the money has actually been paid to " +
                    "${payout.recipientName} (${Money.format(payout.amountMinor, payout.currency)}). " +
                    "This records an audited payout event and advances the rotation.",
                confirmLabel = "Payout complete",
                onConfirm = {
                    confirmPayoutId = null
                    onCompletePayout(payoutId)
                },
                onDismiss = { confirmPayoutId = null }
            )
        }
    }
}

@Composable
private fun PayoutContent(
    state: PayoutScheduleUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRequestComplete: (String) -> Unit
) {
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text("Payout schedule", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                }
                Text(
                    "Estimated amounts are based on expected contributions. " +
                        "Confirmed payouts show the actual completed amount.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            items(state.cycles, key = { it.id }) { cycle ->
                val payout = state.payouts.firstOrNull { it.cycleId == cycle.id }
                val isActiveCycle = cycle.status == CycleStatus.ACTIVE
                CardSection {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Cycle ${cycle.cycleNumber}",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                "To ${cycle.recipientName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = BrandGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${TimeFormatter.formatEpochDay(cycle.startEpochDay)} – " +
                                    TimeFormatter.formatEpochDay(cycle.endEpochDay),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        StatusChip(
                            label = cycle.status.name.lowercase().replaceFirstChar { it.uppercase() },
                            tone = when (cycle.status) {
                                CycleStatus.COMPLETED -> ChipTone.SUCCESS
                                CycleStatus.ACTIVE -> ChipTone.INFO
                                CycleStatus.UPCOMING -> ChipTone.NEUTRAL
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = BorderGray)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (payout != null) {
                                Text(
                                    (if (payout.status == PayoutStatus.COMPLETED) "Paid " else "Expected ") +
                                        Money.format(payout.amountMinor, payout.currency),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    "Ref ${payout.reference}" +
                                        (payout.completedAtMillis?.let {
                                            " · completed ${TimeFormatter.formatDateTime(it)}"
                                        } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        if (payout != null) {
                            StatusChip(
                                label = payout.status.name.lowercase().replaceFirstChar { it.uppercase() },
                                tone = payout.status.tone()
                            )
                        }
                        if (state.isAdmin && isActiveCycle &&
                            payout?.status == PayoutStatus.SCHEDULED && !state.actionInFlight
                        ) {
                            Spacer(Modifier.width(8.dp))
                            PrimaryButton(
                                text = "Mark paid",
                                onClick = { onRequestComplete(payout.id) },
                                modifier = Modifier.testTag("complete_payout_${payout.id}")
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

