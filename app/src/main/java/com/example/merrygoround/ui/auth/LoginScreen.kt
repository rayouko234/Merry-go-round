package com.example.merrygoround.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.example.merrygoround.ui.components.PasswordField
import com.example.merrygoround.ui.components.PrimaryButton
import com.example.merrygoround.ui.theme.BrandGreen
import com.example.merrygoround.ui.theme.TextPrimary
import com.example.merrygoround.ui.theme.TextSecondary

/** 02 — Login with email or phone (per backend's supported identifiers). */
@Composable
fun LoginScreen(
    state: AuthUiState,
    onLogin: (identifier: String, password: String) -> Unit,
    onBack: () -> Unit,
    onGoToRegister: () -> Unit,
    onLoggedIn: () -> Unit
) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(state.success) {
        if (state.success) onLoggedIn()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.height(4.dp))
            Text("Welcome back", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "Sign in with your email or phone number.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(24.dp))

            state.formError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
            }

            AppTextField(
                value = identifier,
                onValueChange = { identifier = it },
                label = "Email or phone",
                keyboardType = KeyboardType.Email,
                error = state.fieldErrors["identifier"],
                imeAction = ImeAction.Next
            )
            Spacer(Modifier.height(4.dp))
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                error = state.fieldErrors["password"],
                imeAction = ImeAction.Done
            )

            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = "Sign in",
                onClick = { onLogin(identifier, password) },
                enabled = !state.isLoading,
                loading = state.isLoading,
                modifier = Modifier.testTag("login_submit")
            )

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onGoToRegister) {
                Text("New here? Create an account", color = BrandGreen)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
