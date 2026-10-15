package com.example.merrygoround.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.merrygoround.core.common.AppResult
import com.example.merrygoround.core.common.Validators
import com.example.merrygoround.data.repository.ActivityRepository
import com.example.merrygoround.data.repository.AuthRepository
import com.example.merrygoround.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val saveMessage: String? = null,
    val loggedOut: Boolean = false,
    /** Notification preference (stored locally for MVP in-app notifications). */
    val notificationsEnabled: Boolean = true
)

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val activityRepository: ActivityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val r = authRepository.me()) {
                is AppResult.Success -> _uiState.update {
                    it.copy(isLoading = false, user = r.data, errorMessage = null)
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = r.message)
                }
            }
        }
    }

    fun saveProfile(fullName: String, phone: String) {
        if (_uiState.value.isSaving) return
        val errors = buildMap {
            Validators.validateFullName(fullName)?.let { put("fullName", it) }
            Validators.validatePhone(phone)?.let { put("phone", it) }
        }
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = errors, saveMessage = null) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, fieldErrors = emptyMap(), saveMessage = null) }
            when (val r = authRepository.updateProfile(fullName.trim(), phone.trim())) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(isSaving = false, user = r.data, saveMessage = "Profile updated")
                    }
                    refresh()
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        fieldErrors = r.fieldErrors,
                        saveMessage = if (r.fieldErrors.isEmpty()) r.message else null
                    )
                }
            }
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        _uiState.update { it.copy(notificationsEnabled = enabled) }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.update { it.copy(loggedOut = true) }
        }
    }

    fun clearSaveMessage() = _uiState.update { it.copy(saveMessage = null) }
    fun consumeLogout() = _uiState.update { it.copy(loggedOut = false) }
    fun clearErrors() = _uiState.update { it.copy(fieldErrors = emptyMap()) }
}

data class NotificationsUiState(
    val isLoading: Boolean = true,
    val items: List<com.example.merrygoround.domain.model.AppNotification> = emptyList(),
    val errorMessage: String? = null
)

class NotificationsViewModel(
    private val activityRepository: ActivityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val r = activityRepository.notifications()) {
                is AppResult.Success -> {
                    activityRepository.markNotificationsRead()
                    _uiState.update { it.copy(isLoading = false, items = r.data, errorMessage = null) }
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = r.message)
                }
            }
        }
    }
}
