package com.example.merrygoround.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.merrygoround.core.common.Money
import com.example.merrygoround.domain.model.SavingsGroup
import com.example.merrygoround.ui.components.CardSection
import com.example.merrygoround.domain.model.MembershipStatus
import com.example.merrygoround.ui.components.ChipTone
import com.example.merrygoround.ui.components.EmptyState
import com.example.merrygoround.ui.components.ErrorState
import com.example.merrygoround.ui.components.LoadingState
import com.example.merrygoround.ui.components.SecondaryButton
import com.example.merrygoround.ui.components.StatusChip
import com.example.merrygoround.ui.components.label
import com.example.merrygoround.ui.theme.BrandGreen
import com.example.merrygoround.ui.theme.TextPrimary
import com.example.merrygoround.ui.theme.TextSecondary

/** 03/05 — Groups tab: my groups + create/join entry points. */
@Composable
fun GroupsScreen(
    state: GroupsUiState,
    onRefresh: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit
) {
    Scaffold(
        floatingActionButton = {
            if (!state.isLoading && state.groups.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onCreateGroup,
                    containerColor = BrandGreen,
                    modifier = Modifier.testTag("fab_create_group")
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Create group",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Your groups",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
            )
            when {
                state.isLoading && state.groups.isEmpty() -> LoadingState(label = "Loading groups…")
                state.errorMessage != null && state.groups.isEmpty() ->
                    ErrorState(message = state.errorMessage ?: "Something went wrong", onRetry = onRefresh)
                state.groups.isEmpty() -> EmptyState(
                    title = "No groups yet",
                    message = "Start a savings group with friends, or join one using an invitation code.",
                    actionText = "Join with a code",
                    onAction = onJoinGroup
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SecondaryButton(
                                text = "Create group",
                                onClick = onCreateGroup,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("create_group_button")
                            )
                            SecondaryButton(
                                text = "Join with code",
                                onClick = onJoinGroup,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("join_group_button")
                            )
                        }
                    }
                    items(state.groups, key = { it.id }) { group ->
                        GroupCard(
                            group = group,
                            onClick = {
                                if (group.membershipStatus == MembershipStatus.ACTIVE) onOpenGroup(group.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupCard(group: SavingsGroup, onClick: () -> Unit) {
    CardSection(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("group_card_${group.id}")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Group, contentDescription = null, tint = BrandGreen)
            Spacer(Modifier.padding(4.dp))
            Column(Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                if (group.description.isNotBlank()) {
                    Text(
                        group.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
            }
            if (group.membershipStatus == MembershipStatus.PENDING) {
                StatusChip(group.membershipStatus.label(), ChipTone.PENDING)
            } else {
                StatusChip(
                    group.status.label(),
                    if (group.status.name == "ACTIVE") ChipTone.SUCCESS else ChipTone.INFO
                )
            }
        }
        if (group.membershipStatus == MembershipStatus.PENDING) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Waiting for the group owner to approve your request.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                Money.format(group.contributionAmountMinor, group.currency) + " / cycle",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            Text(
                "${group.memberCount}/${group.memberLimit} members",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

