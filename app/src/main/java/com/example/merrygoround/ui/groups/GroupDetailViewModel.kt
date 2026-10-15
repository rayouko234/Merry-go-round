package com.example.merrygoround.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.merrygoround.core.common.AppResult
import com.example.merrygoround.data.repository.ContributionRepository
import com.example.merrygoround.data.repository.GroupRepository
import com.example.merrygoround.domain.model.Invitation
import com.example.merrygoround.domain.model.MemberObligation
import com.example.merrygoround.domain.model.Membership
import com.example.merrygoround.domain.model.SavingsGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupDetailUiState(
    val isLoading: Boolean = true,
    val group: SavingsGroup? = null,
    val members: List<Membership> = emptyList(),
    val obligations: List<MemberObligation> = emptyList(),
    val invitation: Invitation? = null,
    val errorMessage: String? = null,
    val actionError: String? = null,
    val actionInFlight: Boolean = false,
    val actionMessage: String? = null,
    /** True when the caller is owner/admin — gates admin affordances client-side
     *  (the backend still enforces authorization independently). */
    val isAdmin: Boolean = false,
    val myUserId: String? = null
)

/** Group detail: members, obligations, invite sharing, admin actions, schedule start. */
class GroupDetailViewModel(
    private val groupRepository: GroupRepository,
    private val contributionRepository: ContributionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupDetailUiState())
    val uiState: StateFlow<GroupDetailUiState> = _uiState.asStateFlow()

    private var groupId: String = ""

    fun load(groupId: String, myUserId: String?) {
        this.groupId = groupId
        _uiState.update { it.copy(myUserId = myUserId) }
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
            val members = (groupRepository.listMembers(groupId) as? AppResult.Success)?.data
                ?: emptyList()
            val obligations = (groupRepository.obligations(groupId, null) as? AppResult.Success)?.data
                ?: emptyList()
            val invitation = (groupRepository.getInvitation(groupId) as? AppResult.Success)?.data
            val myUserId = _uiState.value.myUserId
            val myRole = members.firstOrNull { it.userId == myUserId }?.role
            _uiState.update {
                it.copy(
                    isLoading = false,
                    group = group,
                    members = members,
                    obligations = obligations,
                    invitation = invitation,
                    isAdmin = myRole?.isAdminLike == true,
                    errorMessage = null
                )
            }
        }
    }

    fun decide(membershipId: String, approve: Boolean) = runAction {
        when (val r = groupRepository.decideMembership(groupId, membershipId, approve)) {
            is AppResult.Success -> false to "Membership ${if (approve) "approved" else "declined"}"
            is AppResult.Failure -> true to r.message
        }
    }

    fun removeMember(membershipId: String) = runAction {
        when (val r = groupRepository.removeMember(groupId, membershipId)) {
            is AppResult.Success -> false to "Member removed"
            is AppResult.Failure -> true to r.message
        }
    }

    fun startGroup() = runAction {
        when (val r = groupRepository.startGroup(groupId)) {
            is AppResult.Success -> false to "Group started — the payout schedule is locked"
            is AppResult.Failure -> true to r.message
        }
    }

    fun moveMember(membershipId: String, up: Boolean) {
        val current = _uiState.value.members
            .filter { it.status.name == "ACTIVE" && it.payoutOrder != null }
            .sortedBy { it.payoutOrder }
        if (current.size < 2) return
        val index = current.indexOfFirst { it.id == membershipId }
        if (index < 0) return
        val target = if (up) index - 1 else index + 1
        if (target !in current.indices) return
        val reordered = current.toMutableList().apply {
            val item = removeAt(index)
            add(target, item)
        }
        runAction {
            when (val r = groupRepository.setPayoutOrder(groupId, reordered.map { it.id })) {
                is AppResult.Success -> false to "Payout order updated"
                is AppResult.Failure -> true to r.message
            }
        }
    }

    /** Runs a single action at a time; returns (isError, message). */
    private fun runAction(block: suspend () -> Pair<Boolean, String>) {
        if (_uiState.value.actionInFlight) return
        viewModelScope.launch {
            _uiState.update { it.copy(actionInFlight = true, actionError = null, actionMessage = null) }
            val (isError, message) = block()
            refresh()
            _uiState.update {
                it.copy(
                    actionInFlight = false,
                    actionMessage = if (isError) null else message,
                    actionError = if (isError) message else null
                )
            }
        }
    }

    fun clearActionFeedback() =
        _uiState.update { it.copy(actionMessage = null, actionError = null) }
}
