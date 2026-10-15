package com.example.merrygoround.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.merrygoround.core.common.AppErrorType
import com.example.merrygoround.core.common.AppResult
import com.example.merrygoround.core.common.Validators
import com.example.merrygoround.data.repository.AuthRepository
import com.example.merrygoround.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val formError: String? = null,
    val success: Boolean = false,
    val loggedInUser: User? = null
)

/**
 * Shared by Register and Login screens: validation, submission, error mapping.
 * Duplicate submits are blocked while [AuthUiState.isLoading] is true.
 */
class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun register(fullName: String, email: String, phone: String, password: String) {
        if (_uiState.value.isLoading) return
        val errors = buildMap {
            Validators.validateFullName(fullName)?.let { put("fullName", it) }
            Validators.validateEmail(email)?.let { put("email", it) }
            Validators.validatePhone(phone)?.let { put("phone", it) }
            Validators.validatePassword(password)?.let { put("password", it) }
        }
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = errors, formError = null) }
            return
        }
        submit { authRepository.register(fullName.trim(), email.trim(), phone.trim(), password) }
    }

    fun login(identifier: String, password: String) {
        if (_uiState.value.isLoading) return
        val errors = buildMap {
            if (identifier.isBlank()) put("identifier", "Email or phone is required")
            if (password.isBlank()) put("password", "Password is required")
        }
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = errors, formError = null) }
            return
        }
        submit { authRepository.login(identifier.trim(), password) }
    }

    private fun submit(block: suspend () -> AppResult<User>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, fieldErrors = emptyMap(), formError = null) }
            when (val result = block()) {
                is AppResult.Success -> _uiState.update {
                    it.copy(isLoading = false, success = true, loggedInUser = result.data)
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        fieldErrors = result.fieldErrors,
                        formError = if (result.type == AppErrorType.VALIDATION) null else result.message
                    )
                }
            }
        }
    }

    /** Clears one-off error state when the user starts typing again. */
    fun clearErrors() {
        _uiState.update { it.copy(fieldErrors = emptyMap(), formError = null) }
    }

    fun resetSuccess() {
        _uiState.update { it.copy(success = false, loggedInUser = null) }
    }
}
