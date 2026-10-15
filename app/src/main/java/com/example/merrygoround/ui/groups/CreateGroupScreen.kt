package com.example.merrygoround.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.merrygoround.core.common.TimeFormatter
import com.example.merrygoround.domain.model.ContributionFrequency
import com.example.merrygoround.domain.model.JoinPolicy
import com.example.merrygoround.domain.model.PayoutOrderMethod
import com.example.merrygoround.ui.components.AppTextField
import com.example.merrygoround.ui.components.FieldError
import com.example.merrygoround.ui.components.LoadingState
import com.example.merrygoround.ui.components.PrimaryButton
import com.example.merrygoround.ui.theme.BrandGreen
import com.example.merrygoround.ui.theme.TextPrimary
import com.example.merrygoround.ui.theme.TextSecondary

/**
 * 03 — Create a group: name, description, amount, frequency, member limit,
 * start date, join policy, payout order method, invitation expiry.
 */
@Composable
fun CreateGroupScreen(
    state: CreateGroupUiState,
    onCreate: (
        name: String, description: String, amount: String,
        frequency: ContributionFrequency, interval: Int, memberLimit: Int,
        startEpochDay: Long, joinPolicy: JoinPolicy,
        payoutOrderMethod: PayoutOrderMethod, expiryDays: Int
    ) -> Unit,
    onBack: () -> Unit,
    onCreated: (groupId: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(ContributionFrequency.MONTHLY) }
    var interval by remember { mutableStateOf("7") }
    var memberLimit by remember { mutableStateOf("5") }
    var startEpochDay by remember { mutableLongStateOf(TimeFormatter.todayEpochDay() + 7) }
    var joinPolicy by remember { mutableStateOf(JoinPolicy.APPROVAL_REQUIRED) }
    var orderMethod by remember { mutableStateOf(PayoutOrderMethod.ADMIN_ASSIGNED) }
    var expiryDays by remember { mutableStateOf("7") }

    LaunchedEffect(state.createdGroup) {
        state.createdGroup?.let { onCreated(it.id) }
    }

    if (state.isLoading) {
        LoadingState(label = "Creating group…")
        return
    }

    androidx.compose.material3.Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Create a group", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Set the rules your group will save by. Financial terms lock once the group starts.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(20.dp))

            state.formError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
            }

            AppTextField(name, { name = it }, "Group name",
                error = state.fieldErrors["name"], imeAction = ImeAction.Next)
            AppTextField(description, { description = it }, "Description (optional)",
                error = state.fieldErrors["description"], singleLine = false, imeAction = ImeAction.Next)
            AppTextField(amount, { amount = it }, "Contribution per cycle (KES)",
                supportingText = "e.g. 5000 — fixed amount each member pays",
                keyboardType = KeyboardType.Decimal, error = state.fieldErrors["amount"],
                imeAction = ImeAction.Next)
            Spacer(Modifier.height(10.dp))
            Text("Frequency", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ContributionFrequency.entries.forEach { freq ->
                    FilterChip(
                        selected = frequency == freq,
                        onClick = { frequency = freq },
                        label = { Text(freq.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
            if (frequency == ContributionFrequency.CUSTOM) {
                Spacer(Modifier.height(8.dp))
                AppTextField(interval, { interval = it }, "Interval (days)",
                    keyboardType = KeyboardType.Number, error = state.fieldErrors["interval"])
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(memberLimit, { memberLimit = it }, "Max members",
                    keyboardType = KeyboardType.Number, error = state.fieldErrors["memberLimit"],
                    modifier = Modifier.weight(1f))
                AppTextField(expiryDays, { expiryDays = it }, "Invite expires (days)",
                    keyboardType = KeyboardType.Number,
                    error = state.fieldErrors["invitationExpiryDays"],
                    modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))
            AppTextField(
                value = TimeFormatter.formatEpochDay(startEpochDay),
                onValueChange = {},
                label = "Start date",
                enabled = false,
                supportingText = "Tap a quick option below to change the start date",
                error = state.fieldErrors["startDate"]
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7 to "In 1 week", 14 to "In 2 weeks", 30 to "In 1 month").forEach { (days, label) ->
                    FilterChip(
                        selected = startEpochDay == TimeFormatter.todayEpochDay() + days,
                        onClick = { startEpochDay = TimeFormatter.todayEpochDay() + days },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("Join policy", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Require admin approval for new members",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = joinPolicy == JoinPolicy.APPROVAL_REQUIRED,
                    onCheckedChange = {
                        joinPolicy = if (it) JoinPolicy.APPROVAL_REQUIRED else JoinPolicy.OPEN
                    }
                )
            }

            Spacer(Modifier.height(14.dp))
            Text("Payout order", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = orderMethod == PayoutOrderMethod.ADMIN_ASSIGNED,
                    onClick = { orderMethod = PayoutOrderMethod.ADMIN_ASSIGNED },
                    label = { Text("Admin assigns") }
                )
                FilterChip(
                    selected = orderMethod == PayoutOrderMethod.JOIN_ORDER,
                    onClick = { orderMethod = PayoutOrderMethod.JOIN_ORDER },
                    label = { Text("Order of joining") }
                )
            }
            Text(
                "The order is locked when the group starts so everyone can rely on the schedule.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = "Create group",
                onClick = {
                    onCreate(
                        name, description, amount, frequency,
                        interval.toIntOrNull() ?: 7,
                        memberLimit.toIntOrNull() ?: 5,
                        startEpochDay, joinPolicy, orderMethod,
                        expiryDays.toIntOrNull() ?: 7
                    )
                },
                modifier = Modifier.testTag("create_group_submit")
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}


