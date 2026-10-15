package com.example.merrygoround.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.merrygoround.core.common.AppResult
import com.example.merrygoround.data.repository.ActivityRepository
import com.example.merrygoround.data.repository.ContributionRepository
import com.example.merrygoround.data.repository.GroupRepository
import com.example.merrygoround.domain.model.ActivityEntry
import com.example.merrygoround.domain.model.DashboardSummary
import com.example.merrygoround.domain.model.SavingsGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = true,
    val groups: List<SavingsGroup> = emptyList(),
    val selectedGroupId: String? = null,
    val summary: DashboardSummary? = null,
    val recentActivity: List<ActivityEntry> = emptyList(),
    val errorMessage: String? = null,
    /** True when the user has no groups at all — show onboarding CTAs. */
    val noGroups: Boolean = false
)

/**
 * Home tab. Loads the group list (for the selector), the selected group's
 * dashboard aggregation, and recent activity. All numbers come from the backend.
 */
class DashboardViewModel(
    private val groupRepository: GroupRepository,
    private val activityRepository: ActivityRepository,
    @Suppress("unused") private val contributionRepository: ContributionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val groupsResult = groupRepository.listMyGroups()) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = groupsResult.message)
                }
                is AppResult.Success -> {
                    val groups = groupsResult.data
                    if (groups.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                groups = emptyList(),
                                selectedGroupId = null,
                                summary = null,
                                recentActivity = emptyList(),
                                noGroups = true
                            )
                        }
                        return@launch
                    }
                    val selected = _uiState.value.selectedGroupId
                        ?.let { id -> groups.firstOrNull { it.id == id } }
                        ?: groups.first()
                    loadForGroup(groups, selected.id)
                }
            }
        }
    }

    fun selectGroup(groupId: String) {
        val groups = _uiState.value.groups
        if (groups.none { it.id == groupId }) return
        viewModelScope.launch { loadForGroup(groups, groupId) }
    }

    private suspend fun loadForGroup(groups: List<SavingsGroup>, groupId: String) {
        _uiState.update { it.copy(isLoading = true, selectedGroupId = groupId, errorMessage = null) }
        val summary = when (val r = groupRepository.getDashboard(groupId)) {
            is AppResult.Success -> r.data
            is AppResult.Failure -> {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = r.message, groups = groups, noGroups = false)
                }
                return
            }
        }
        val activity = when (val r = activityRepository.recent(8)) {
            is AppResult.Success -> r.data.filter { it.groupId == groupId }
            is AppResult.Failure -> emptyList()
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                groups = groups,
                selectedGroupId = groupId,
                summary = summary,
                recentActivity = activity,
                noGroups = false,
                errorMessage = null
            )
        }
    }
}
