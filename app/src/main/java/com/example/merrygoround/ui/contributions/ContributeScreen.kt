package com.example.merrygoround.ui.contributions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.merrygoround.core.common.Money
import com.example.merrygoround.core.common.TimeFormatter
import com.example.merrygoround.ui.components.ErrorState
import com.example.merrygoround.ui.components.LoadingState
import com.example.merrygoround.ui.components.tone
import com.example.merrygoround.ui.theme.BrandGreen
import com.example.merrygoround.ui.theme.BrandGreenLight
import com.example.merrygoround.ui.theme.TextPrimary
import com.example.merrygoround.ui.theme.TextSecondary

/**
 * 06 — Contribute: amount due, idempotent record, statuses.
 * Mode A tracking: the entry is self-reported until an admin verifies it.
 */
@Composable
fun ContributeScreen(
    state: ContributeUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    when {
        state.isLoading && state.group == null -> LoadingState(label = "Loading…")
        state.group == null ->
            ErrorState(message = state.formError ?: "Could not load this screen", onRetry = onRefresh)
        else -> ContributeContent(state, onBack, onAmountChange, onNoteChange, onSubmit)
    }
}

@Composable
private fun ContributeContent(
    state: ContributeUiState,
    onBack: () -> Unit,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val group = state.group!!
    val cycle = state.currentCycle
    Scaffold { padding ->
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
                    Text("Contribute", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = BrandGreenLight)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Amount due", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            Money.format(group.contributionAmountMinor, group.currency),
                            style = MaterialTheme.typography.headlineSmall,
                            color = BrandGreen,
                            fontWeight = FontWeight.Bold
                        )
                        if (cycle != null) {
                            Text(
                                "Cycle ${cycle.cycleNumber} · ${TimeFormatter.formatEpochDay(cycle.startEpochDay)} – " +
                                    "${TimeFormatter.formatEpochDay(cycle.endEpochDay)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                "Due by ${TimeFormatter.formatEpochDay(cycle.contributionDeadlineEpochDay)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        state.dashboard?.nextRecipientName?.let {
                            Text(
                                "This cycle pays out to $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            item {
                when {
                    state.successMessage != null -> Card(colors = CardDefaults.cardColors(containerColor = BrandGreenLight)) {
                        Text(
                            state.successMessage ?: "",
                            modifier = Modifier.padding(14.dp),
                            color = BrandGreen,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    state.formError != null -> Text(
                        state.formError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            item {
                if (state.submitted == null && state.dashboard?.myContributionStatus == null) {
                    com.example.merrygoround.ui.components.CardSection {
                        Text("Record your payment", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Spacer(Modifier.height(10.dp))
                        com.example.merrygoround.ui.components.AppTextField(
                            value = state.amountInput,
                            onValueChange = onAmountChange,
                            label = "Amount (KES)",
                            keyboardType = KeyboardType.Decimal,
                            error = state.fieldErrors["amount"],
                            imeAction = ImeAction.Next
                        )
                        Spacer(Modifier.height(4.dp))
                        com.example.merrygoround.ui.components.AppTextField(
                            value = state.note,
                            onValueChange = onNoteChange,
                            label = "Note / reference (optional)",
                            supportingText = "e.g. M-Pesa ref, cash handed to admin",
                            imeAction = ImeAction.Done
                        )
                        Spacer(Modifier.height(14.dp))
                        com.example.merrygoround.ui.components.PrimaryButton(
                            text = if (state.isSubmitting) "Recording…" else "Record contribution",
                            onClick = onSubmit,
                            enabled = !state.isSubmitting,
                            loading = state.isSubmitting,
                            modifier = Modifier.testTag("contribute_submit")
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Self-reported entries show as Pending until a group admin verifies them. " +
                                "Retrying is safe — duplicates are blocked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
            if (state.myHistory.isNotEmpty()) {
                item {
                    Text("My contributions", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }
                items(state.myHistory, key = { it.id }) { c ->
                    com.example.merrygoround.ui.components.CardSection {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    Money.format(c.amountMinor, c.currency),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    "${c.reference} · ${TimeFormatter.formatRelative(c.updatedAtMillis)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                if (c.failureReason.isNotBlank()) {
                                    Text(
                                        c.failureReason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                com.example.merrygoround.ui.components.StatusChip(c.status.name, c.status.tone())
                                Spacer(Modifier.height(4.dp))
                                com.example.merrygoround.ui.components.VerificationBadge(c.verification)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }


        }
    }
}

