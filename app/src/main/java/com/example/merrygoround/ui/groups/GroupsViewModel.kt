package com.example.merrygoround.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.merrygoround.core.common.AppResult
import com.example.merrygoround.data.repository.GroupRepository
import com.example.merrygoround.domain.model.SavingsGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupsUiState(
    val isLoading: Boolean = true,
    val groups: List<SavingsGroup> = emptyList(),
    val errorMessage: String? = null
)

/** Group list (Groups tab): loading, empty, error+retry, data. */
class GroupsViewModel(
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupsUiState())
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = groupRepository.listMyGroups()) {
                is AppResult.Success -> _uiState.update {
                    it.copy(isLoading = false, groups = result.data, errorMessage = null)
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }
}
