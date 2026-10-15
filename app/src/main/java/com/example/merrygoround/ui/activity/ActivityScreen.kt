package com.example.merrygoround.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.merrygoround.domain.model.ContributionStatus
import com.example.merrygoround.ui.components.ActivityRow
import com.example.merrygoround.ui.components.CardSection
import com.example.merrygoround.ui.components.EmptyState
import com.example.merrygoround.ui.components.ErrorState
import com.example.merrygoround.ui.components.LoadingState
import com.example.merrygoround.ui.components.VerificationBadge
import com.example.merrygoround.ui.components.tone
import com.example.merrygoround.core.common.Money
import com.example.merrygoround.core.common.TimeFormatter
import com.example.merrygoround.ui.theme.BrandGreen
import com.example.merrygoround.ui.theme.TextPrimary
import com.example.merrygoround.ui.theme.TextSecondary

/** 06/Activity tab — contributions, payouts, membership events with filters. */
@Composable
fun ActivityScreen(
    state: ActivityUiState,
    onRefresh: () -> Unit,
    onSetTab: (ActivityTab) -> Unit,
    onSetGroupFilter: (String?) -> Unit,
    onSetStatusFilter: (ContributionStatus?) -> Unit
) {
    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Activity",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
            )

            // Tabs
            Row(modifier = Modifier.padding(horizontal = 12.dp)) {
                ActivityTab.entries.forEach { tab ->
                    TextButton(onClick = { onSetTab(tab) }) {
                        Text(
                            tab.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = if (state.tab == tab) BrandGreen else TextSecondary
                        )
                    }
                }
            }

            // Group filter
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.filterGroupId == null,
                        onClick = { onSetGroupFilter(null) },
                        label = { Text("All groups") }
                    )
                }
                items(state.groups, key = { it.id }) { g ->
                    FilterChip(
                        selected = state.filterGroupId == g.id,
                        onClick = { onSetGroupFilter(g.id) },
                        label = { Text(g.name) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.filterStatus == null,
                        onClick = { onSetStatusFilter(null) },
                        label = { Text("Any status") }
                    )
                }
                items(ContributionStatus.entries, key = { it.name }) { status ->
                    FilterChip(
                        selected = state.filterStatus == status,
                        onClick = { onSetStatusFilter(status) },
                        label = { Text(status.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            when {
                state.isLoading && state.entries.isEmpty() -> LoadingState(label = "Loading activity…")
                state.errorMessage != null && state.entries.isEmpty() ->
                    ErrorState(message = state.errorMessage ?: "Something went wrong", onRetry = onRefresh)

                state.tab == ActivityTab.CONTRIBUTIONS -> {
                    if (state.contributions.isEmpty()) {
                        EmptyState(
                            title = "No contributions",
                            message = "Contributions recorded in your groups will appear here."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(state.contributions, key = { it.id }) { c ->
                                CardSection {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                Money.format(c.amountMinor, c.currency),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = TextPrimary
                                            )
                                            Text(
                                                "${c.userName} · ${c.reference}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                            Text(
                                                TimeFormatter.formatRelative(c.updatedAtMillis),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            com.example.merrygoround.ui.components.StatusChip(
                                                c.status.name, c.status.tone()
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            VerificationBadge(c.verification)
                                        }
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(24.dp)) }
                        }
                    }
                }

                else -> {
                    val entries = state.entries.filter { entry ->
                        when (state.tab) {
                            ActivityTab.PAYOUTS -> entry.kind == "PAYOUT"
                            ActivityTab.ALL -> true
                            ActivityTab.CONTRIBUTIONS -> entry.kind == "CONTRIBUTION"
                        }
                    }
                    if (entries.isEmpty()) {
                        EmptyState(
                            title = "Nothing here yet",
                            message = "Contributions, payouts and group events will show up here as they happen."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.testTag("activity_list")
                        ) {
                            items(entries, key = { it.id }) { entry ->
                                ActivityRow(entry)
                            }
                            item { Spacer(Modifier.height(24.dp)) }
                        }
                    }
                }
            }
        }
    }
}

