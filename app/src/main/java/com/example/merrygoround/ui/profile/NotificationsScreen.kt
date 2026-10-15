package com.example.merrygoround.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.merrygoround.core.common.TimeFormatter
import com.example.merrygoround.ui.components.CardSection
import com.example.merrygoround.ui.components.EmptyState
import com.example.merrygoround.ui.components.ErrorState
import com.example.merrygoround.ui.components.LoadingState
import com.example.merrygoround.ui.theme.BrandGreen
import com.example.merrygoround.ui.theme.TextPrimary
import com.example.merrygoround.ui.theme.TextSecondary

/** In-app notification list (brief §11 MVP scope). */
@Composable
fun NotificationsScreen(
    state: NotificationsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Notifications",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(12.dp))

            when {
                state.isLoading && state.items.isEmpty() -> LoadingState(label = "Loading…")
                state.errorMessage != null && state.items.isEmpty() ->
                    ErrorState(message = state.errorMessage ?: "Something went wrong", onRetry = onRefresh)
                state.items.isEmpty() -> EmptyState(
                    title = "No notifications",
                    message = "You'll hear about joins, contribution updates and payouts here."
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.items, key = { it.id }) { n ->
                        CardSection {
                            Text(
                                n.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (n.isRead) TextSecondary else BrandGreen
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(n.message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                TimeFormatter.formatRelative(n.createdAtMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
