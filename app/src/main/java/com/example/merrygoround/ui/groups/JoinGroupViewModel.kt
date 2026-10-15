package com.example.merrygoround.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.merrygoround.core.common.AppResult
import com.example.merrygoround.core.common.Validators
import com.example.merrygoround.data.repository.GroupRepository
import com.example.merrygoround.domain.model.Membership
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JoinGroupUiState(
    val isLoading: Boolean = false,
    val codeError: String? = null,
    val formError: String? = null,
    val joined: Membership? = null
)

class JoinGroupViewModel(
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(JoinGroupUiState())
    val uiState: StateFlow<JoinGroupUiState> = _uiState.asStateFlow()

    fun join(code: String) {
        if (_uiState.value.isLoading) return
        Validators.validateInviteCode(code)?.let { error ->
            _uiState.update { it.copy(codeError = error, formError = null) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, codeError = null, formError = null) }
            when (val result = groupRepository.join(code.trim().uppercase())) {
                is AppResult.Success -> _uiState.update {
                    it.copy(isLoading = false, joined = result.data)
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        codeError = result.fieldErrors["code"],
                        formError = if (result.fieldErrors.containsKey("code")) null else result.message
                    )
                }
            }
        }
    }

    fun clearErrors() = _uiState.update { it.copy(codeError = null, formError = null) }
    fun consumeJoined() = _uiState.update { it.copy(joined = null) }
}
