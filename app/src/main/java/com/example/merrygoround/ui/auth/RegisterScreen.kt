package com.example.merrygoround.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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

/** 02 — Registration: full name, email, phone, password with per-field validation. */
@Composable
fun RegisterScreen(
    state: AuthUiState,
    onRegister: (fullName: String, email: String, phone: String, password: String) -> Unit,
    onBack: () -> Unit,
    onGoToLogin: () -> Unit,
    onRegistered: () -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(state.success) {
        if (state.success) onRegistered()
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
            Text("Create your account", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "Join savings groups and track every contribution.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(24.dp))

            state.formError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
            }

            AppTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = "Full name",
                error = state.fieldErrors["fullName"],
                imeAction = ImeAction.Next
            )
            Spacer(Modifier.height(4.dp))
            AppTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                keyboardType = KeyboardType.Email,
                error = state.fieldErrors["email"],
                imeAction = ImeAction.Next
            )
            Spacer(Modifier.height(4.dp))
            AppTextField(
                value = phone,
                onValueChange = { phone = it },
                label = "Phone number",
                supportingText = "e.g. 0712345678",
                keyboardType = KeyboardType.Phone,
                error = state.fieldErrors["phone"],
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
                text = "Create account",
                onClick = { onRegister(fullName, email, phone, password) },
                enabled = !state.isLoading,
                loading = state.isLoading,
                modifier = Modifier.testTag("register_submit")
            )

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onGoToLogin) {
                Text("Already have an account? Sign in", color = BrandGreen)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
