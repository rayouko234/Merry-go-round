package com.example.merrygoround.ui.contributions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.merrygoround.core.common.AppResult
import com.example.merrygoround.core.common.Money
import com.example.merrygoround.data.repository.ContributionRepository
import com.example.merrygoround.data.repository.GroupRepository
import com.example.merrygoround.domain.model.Contribution
import com.example.merrygoround.domain.model.Cycle
import com.example.merrygoround.domain.model.DashboardSummary
import com.example.merrygoround.domain.model.SavingsGroup
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContributeUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val group: SavingsGroup? = null,
    val dashboard: DashboardSummary? = null,
    val currentCycle: Cycle? = null,
    val myHistory: List<Contribution> = emptyList(),
    val amountInput: String = "",
    val note: String = "",
    val fieldErrors: Map<String, String> = emptyMap(),
    val formError: String? = null,
    val successMessage: String? = null,
    val submitted: Contribution? = null,
    /** Stable idempotency key for this screen session — reused on retry. */
    val idempotencyKey: String = UUID.randomUUID().toString()
)

/**
 * Contribute flow: shows what's due, records a self-reported contribution with an
 * idempotency key (retries can't duplicate), then reflects the authoritative status.
 */
class ContributeViewModel(
    private val groupRepository: GroupRepository,
    private val contributionRepository: ContributionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContributeUiState())
    val uiState: StateFlow<ContributeUiState> = _uiState.asStateFlow()

    private var groupId: String = ""

    fun load(groupId: String) {
        if (this.groupId == groupId && _uiState.value.group != null) return
        this.groupId = groupId
        refresh()
    }

    fun refresh() {
        if (groupId.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, formError = null) }
            val group = (groupRepository.getGroup(groupId) as? AppResult.Success)?.data
            val dashboard = (groupRepository.getDashboard(groupId) as? AppResult.Success)?.data
            val cycle = (groupRepository.listCycles(groupId) as? AppResult.Success)?.data
                ?.firstOrNull { it.status.name == "ACTIVE" }
            val history = (contributionRepository.list(groupId, null, null) as? AppResult.Success)?.data
                ?: emptyList()

            if (group == null || dashboard == null) {
                _uiState.update { it.copy(isLoading = false, formError = "Could not load this group") }
                return@launch
            }
            val dueAmount = Money.format(group.contributionAmountMinor, group.currency, includeCode = false)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    group = group,
                    dashboard = dashboard,
                    currentCycle = cycle,
                    myHistory = history,
                    amountInput = if (it.amountInput.isBlank()) dueAmount else it.amountInput
                )
            }
        }
    }

    fun onAmountChange(value: String) {
        _uiState.update {
            it.copy(amountInput = value, fieldErrors = it.fieldErrors - "amount", formError = null)
        }
    }

    fun onNoteChange(value: String) = _uiState.update { it.copy(note = value) }

    /**
     * Records the contribution. The idempotency key is stable for this screen session,
     * so pressing retry after a failure CANNOT create a second record.
     */
    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return
        val group = state.group ?: return
        val cycle = state.currentCycle
        if (cycle == null) {
            _uiState.update { it.copy(formError = "There is no active cycle to contribute to") }
            return
        }
        val amountMinor = Money.parseToMinor(state.amountInput, group.currency)
        if (amountMinor == null) {
            _uiState.update {
                it.copy(fieldErrors = mapOf("amount" to "Enter a valid amount"), formError = null)
            }
            return
        }
        if (amountMinor != group.contributionAmountMinor) {
            _uiState.update {
                it.copy(
                    fieldErrors = mapOf(
                        "amount" to
                            "This group's contribution is ${Money.format(group.contributionAmountMinor, group.currency)}"
                    ),
                    formError = null
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, formError = null, successMessage = null) }
            val result = contributionRepository.record(
                groupId = groupId,
                cycleId = cycle.id,
                amountMinor = amountMinor,
                idempotencyKey = state.idempotencyKey,
                note = state.note
            )
            when (result) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            submitted = result.data,
                            successMessage = "Contribution recorded — awaiting admin verification"
                        )
                    }
                    refresh()
                }
                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            fieldErrors = result.fieldErrors,
                            formError = if (result.fieldErrors.isEmpty()) result.message else null
                        )
                    }
                }
            }
        }
    }

    fun clearFeedback() =
        _uiState.update { it.copy(successMessage = null, formError = null, submitted = null) }
}
