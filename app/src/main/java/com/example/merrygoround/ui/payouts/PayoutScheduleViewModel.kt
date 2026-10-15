package com.example.merrygoround.ui.payouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.merrygoround.core.common.AppResult
import com.example.merrygoround.data.repository.GroupRepository
import com.example.merrygoround.domain.model.Cycle
import com.example.merrygoround.domain.model.Payout
import com.example.merrygoround.domain.model.SavingsGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PayoutScheduleUiState(
    val isLoading: Boolean = true,
    val group: SavingsGroup? = null,
    val cycles: List<Cycle> = emptyList(),
    val payouts: List<Payout> = emptyList(),
    val errorMessage: String? = null,
    val actionInFlight: Boolean = false,
    val actionMessage: String? = null,
    val actionError: String? = null,
    val isAdmin: Boolean = false,
    val myUserId: String? = null
)

/** Ordered rotation view: recipients, dates, statuses, history, admin completion. */
class PayoutScheduleViewModel(
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PayoutScheduleUiState())
    val uiState: StateFlow<PayoutScheduleUiState> = _uiState.asStateFlow()

    private var groupId: String = ""

    fun load(groupId: String, myUserId: String?) {
        val isNew = this.groupId != groupId
        this.groupId = groupId
        if (isNew) _uiState.update { it.copy(myUserId = myUserId) }
        refresh()
    }

    fun refresh() {
        if (groupId.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val group = when (val r = groupRepository.getGroup(groupId)) {
                is AppResult.Success -> r.data
                is AppResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = r.message) }
                    return@launch
                }
            }
            val cycles = (groupRepository.listCycles(groupId) as? AppResult.Success)?.data ?: emptyList()
            val payouts = (groupRepository.listPayouts(groupId) as? AppResult.Success)?.data ?: emptyList()
            val members = (groupRepository.listMembers(groupId) as? AppResult.Success)?.data ?: emptyList()
            val myRole = members.firstOrNull { it.userId == _uiState.value.myUserId }?.role
            _uiState.update {
                it.copy(
                    isLoading = false,
                    group = group,
                    cycles = cycles,
                    payouts = payouts,
                    isAdmin = myRole?.isAdminLike == true,
                    errorMessage = null
                )
            }
        }
    }

    /** Admin marks a payout completed — confirmed via dialog before calling. */
    fun completePayout(payoutId: String) {
        if (_uiState.value.actionInFlight) return
        viewModelScope.launch {
            _uiState.update { it.copy(actionInFlight = true, actionError = null, actionMessage = null) }
            when (val r = groupRepository.completePayout(payoutId)) {
                is AppResult.Success -> {
                    refresh()
                    _uiState.update {
                        it.copy(actionInFlight = false, actionMessage = "Payout completed and recorded")
                    }
                }
                is AppResult.Failure -> {
                    refresh()
                    _uiState.update { it.copy(actionInFlight = false, actionError = r.message) }
                }
            }
        }
    }

    fun clearFeedback() =
        _uiState.update { it.copy(actionMessage = null, actionError = null) }
}
