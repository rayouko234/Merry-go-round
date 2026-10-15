package com.example.merrygoround.ui.groups

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.merrygoround.ui.components.CardSection
import com.example.merrygoround.ui.components.LoadingState
import com.example.merrygoround.ui.components.PrimaryButton
import com.example.merrygoround.ui.components.AppTextField
import com.example.merrygoround.ui.theme.BrandGreen
import com.example.merrygoround.ui.theme.TextPrimary
import com.example.merrygoround.ui.theme.TextSecondary

/** 03 — Join a group with an invitation code. */
@Composable
fun JoinGroupScreen(
    state: JoinGroupUiState,
    onJoin: (code: String) -> Unit,
    onBack: () -> Unit,
    onJoined: (groupId: String) -> Unit
) {
    var code by remember { mutableStateOf("") }

    LaunchedEffect(state.joined) {
        state.joined?.let { onJoined(it.groupId) }
    }

    if (state.isLoading) {
        LoadingState(label = "Checking invitation…")
        return
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Join a group", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            }
            Spacer(Modifier.height(20.dp))

            CardSection {
                Icon(Icons.Filled.MarkEmailRead, contentDescription = null, tint = BrandGreen)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Enter the invitation code shared by your group's admin",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(16.dp))
                AppTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(12); },
                    label = "Invitation code",
                    supportingText = "e.g. K7M2P9QA",
                    error = state.codeError,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                )
                state.formError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(20.dp))
                PrimaryButton(
                    text = "Join group",
                    onClick = { onJoin(code) },
                    modifier = Modifier.testTag("join_submit")
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Ask the group admin for the code. You'll see whether you were added directly or need approval.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
