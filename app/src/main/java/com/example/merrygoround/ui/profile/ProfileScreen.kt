package com.example.merrygoround.ui.profile

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.merrygoround.ui.components.AppTextField
import com.example.merrygoround.ui.components.CardSection
import com.example.merrygoround.ui.components.ConfirmDialog
import com.example.merrygoround.ui.components.ErrorState
import com.example.merrygoround.ui.components.LoadingState
import com.example.merrygoround.ui.components.PrimaryButton
import com.example.merrygoround.ui.theme.BorderGray
import com.example.merrygoround.ui.theme.BrandGreen
import com.example.merrygoround.ui.theme.TextPrimary
import com.example.merrygoround.ui.theme.TextSecondary

private enum class InfoDialog { HELP, PRIVACY }

/** 08 — Profile & settings: details, preferences, help, logout. */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onRefresh: () -> Unit,
    onSave: (fullName: String, phone: String) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var confirmLogout by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf<InfoDialog?>(null) }

    LaunchedEffect(state.user) {
        state.user?.let {
            fullName = it.fullName
            phone = it.phone
        }
    }

    if (state.isLoading && state.user == null) {
        LoadingState(label = "Loading profile…")
        return
    }
    if (state.user == null) {
        ErrorState(message = state.errorMessage ?: "Could not load profile", onRetry = onRefresh)
        return
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "Sign out?",
            message = "You'll need your email/phone and password to sign back in.",
            confirmLabel = "Sign out",
            destructive = true,
            onConfirm = { confirmLogout = false; onLogout() },
            onDismiss = { confirmLogout = false }
        )
    }

    infoDialog?.let { dialog ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { infoDialog = null },
            title = {
                Text(
                    if (dialog == InfoDialog.HELP) "Help & support" else "Privacy & terms",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    if (dialog == InfoDialog.HELP) {
                        "Need a hand?\n\n• Make sure your group admin has shared a valid invitation code.\n" +
                            "• Contributions stay Pending until an admin verifies them.\n" +
                            "• Payouts are marked paid by the group admin after the real transfer.\n\n" +
                            "Support: support@merrygoround.example"
                    } else {
                        "Your name, phone and group activity are stored to provide the service. " +
                            "Passwords are hashed and never stored in plain text. Financial entries " +
                            "are append-only records: confirmed contributions are never silently " +
                            "changed — corrections happen through audited reversal events.\n\n" +
                            "This MVP tracks contributions; it does not process payments."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { infoDialog = null }) { Text("Close", color = BrandGreen) }
            }
        )
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("Profile", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            }

            item {
                CardSection {
                    Text("Your details", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    Text("Email", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Text(state.user.email, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    AppTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = "Full name",
                        error = state.fieldErrors["fullName"],
                        imeAction = ImeAction.Next
                    )
                    Spacer(Modifier.height(4.dp))
                    AppTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = "Phone number",
                        keyboardType = KeyboardType.Phone,
                        error = state.fieldErrors["phone"],
                        imeAction = ImeAction.Done
                    )
                    state.saveMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = BrandGreen, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = if (state.isSaving) "Saving…" else "Save changes",
                        onClick = { onSave(fullName, phone) },
                        enabled = !state.isSaving,
                        loading = state.isSaving,
                        modifier = Modifier.testTag("save_profile")
                    )
                }
            }
            item {
                CardSection {
                    Text("Preferences", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Notifications, contentDescription = null, tint = BrandGreen)
                        Spacer(Modifier.padding(6.dp))
                        Text(
                            "In-app notifications",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = state.notificationsEnabled,
                            onCheckedChange = onToggleNotifications
                        )
                    }
                    Text(
                        "Deadline reminders, contribution updates and payout alerts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = BorderGray)
                    TextButton(onClick = onOpenNotifications) {
                        Text("View notifications", color = BrandGreen)
                    }
                }
            }

            item {
                CardSection {
                    Text("About & support", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Help, contentDescription = null, tint = BrandGreen)
                        Spacer(Modifier.padding(6.dp))
                        TextButton(onClick = { infoDialog = InfoDialog.HELP }) {
                            Text("Help & support", color = TextPrimary)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PrivacyTip, contentDescription = null, tint = BrandGreen)
                        Spacer(Modifier.padding(6.dp))
                        TextButton(onClick = { infoDialog = InfoDialog.PRIVACY }) {
                            Text("Privacy policy & terms", color = TextPrimary)
                        }
                    }
                    Text(
                        "MVP build · Contributions are tracked records, not processed payments.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            item {
                CardSection {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.padding(6.dp))
                        TextButton(
                            onClick = { confirmLogout = true },
                            modifier = Modifier.testTag("logout_button")
                        ) {
                            Text("Sign out", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }

        }
    }
}
