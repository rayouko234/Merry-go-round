package com.example.merrygoround.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.merrygoround.core.common.AppResult
import com.example.merrygoround.data.repository.ActivityRepository
import com.example.merrygoround.data.repository.ContributionRepository
import com.example.merrygoround.data.repository.GroupRepository
import com.example.merrygoround.domain.model.ActivityEntry
import com.example.merrygoround.domain.model.Contribution
import com.example.merrygoround.domain.model.ContributionStatus
import com.example.merrygoround.domain.model.SavingsGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActivityTab { ALL, CONTRIBUTIONS, PAYOUTS }

data class ActivityUiState(
    val isLoading: Boolean = true,
    val tab: ActivityTab = ActivityTab.ALL,
    val groups: List<SavingsGroup> = emptyList(),
    /** null = all groups */
    val filterGroupId: String? = null,
    /** null = all statuses */
    val filterStatus: ContributionStatus? = null,
    val entries: List<ActivityEntry> = emptyList(),
    val contributions: List<Contribution> = emptyList(),
    val errorMessage: String? = null
)

/** Activity tab: cross-group history with group/status filters (brief §06). */
class ActivityViewModel(
    private val activityRepository: ActivityRepository,
    private val contributionRepository: ContributionRepository,
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivityUiState())
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val groups = (groupRepository.listMyGroups() as? AppResult.Success)?.data ?: emptyList()
            when (val activity = activityRepository.recent(200)) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = activity.message, groups = groups)
                }
                is AppResult.Success -> {
                    val contributions = (contributionRepository.list(null, null, null) as? AppResult.Success)?.data
                        ?: emptyList()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            groups = groups,
                            entries = activity.data,
                            contributions = contributions,
                            errorMessage = null
                        )
                    }
                    applyFilters()
                }
            }
        }
    }

    fun setTab(tab: ActivityTab) {
        _uiState.update { it.copy(tab = tab) }
        applyFilters()
    }

    fun setGroupFilter(groupId: String?) {
        _uiState.update { it.copy(filterGroupId = groupId) }
        applyFilters()
    }

    fun setStatusFilter(status: ContributionStatus?) {
        _uiState.update { it.copy(filterStatus = status) }
        applyFilters()
    }

    private fun applyFilters() {
        val state = _uiState.value
        val filteredEntries = state.entries.filter { entry ->
            (state.filterGroupId == null || entry.groupId == state.filterGroupId) &&
                (state.filterStatus == null || entry.status == state.filterStatus.name ||
                    state.filterStatus.name == entry.status)
        }
        val filteredContributions = state.contributions.filter { c ->
            (state.filterGroupId == null || c.groupId == state.filterGroupId) &&
                (state.filterStatus == null || c.status == state.filterStatus)
        }
        _uiState.update {
            it.copy(entries = filteredEntries, contributions = filteredContributions)
        }
    }
}
