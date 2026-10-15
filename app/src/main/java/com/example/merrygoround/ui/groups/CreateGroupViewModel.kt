package com.example.merrygoround.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.merrygoround.core.common.AppResult
import com.example.merrygoround.core.common.TimeFormatter
import com.example.merrygoround.core.common.Validators
import com.example.merrygoround.data.repository.GroupRepository
import com.example.merrygoround.domain.model.ContributionFrequency
import com.example.merrygoround.domain.model.JoinPolicy
import com.example.merrygoround.domain.model.PayoutOrderMethod
import com.example.merrygoround.domain.model.SavingsGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateGroupUiState(
    val isLoading: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val formError: String? = null,
    val createdGroup: SavingsGroup? = null
)

class CreateGroupViewModel(
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateGroupUiState())
    val uiState: StateFlow<CreateGroupUiState> = _uiState.asStateFlow()

    fun create(
        name: String,
        description: String,
        amountInput: String,
        frequency: ContributionFrequency,
        customIntervalDays: Int,
        memberLimit: Int,
        startEpochDay: Long,
        joinPolicy: JoinPolicy,
        payoutOrderMethod: PayoutOrderMethod,
        invitationExpiryDays: Int
    ) {
        if (_uiState.value.isLoading) return
        val amountMinor = com.example.merrygoround.core.common.Money.parseToMinor(amountInput)
        val errors = mutableMapOf<String, String>()
        Validators.validateGroupName(name)?.let { errors["name"] = it }
        Validators.validateDescription(description)?.let { errors["description"] = it }
        if (amountMinor == null) {
            errors["amount"] = "Enter a valid amount"
        } else {
            Validators.validateContributionAmount(amountMinor)?.let { errors["amount"] = it }
        }
        Validators.validateMemberLimit(memberLimit)?.let { errors["memberLimit"] = it }
        Validators.validateStartDate(startEpochDay, TimeFormatter.todayEpochDay())
            ?.let { errors["startDate"] = it }
        if (frequency == ContributionFrequency.CUSTOM) {
            Validators.validateCustomIntervalDays(customIntervalDays)?.let { errors["interval"] = it }
        }
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = errors, formError = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, fieldErrors = emptyMap(), formError = null) }
            when (val result = groupRepository.createGroup(
                name = name.trim(),
                description = description.trim(),
                contributionAmountMinor = amountMinor!!,
                frequency = frequency,
                customIntervalDays = customIntervalDays,
                memberLimit = memberLimit,
                startEpochDay = startEpochDay,
                payoutOrderMethod = payoutOrderMethod,
                joinPolicy = joinPolicy,
                invitationExpiryDays = invitationExpiryDays
            )) {
                is AppResult.Success -> _uiState.update {
                    it.copy(isLoading = false, createdGroup = result.data)
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        fieldErrors = result.fieldErrors,
                        formError = if (result.type == com.example.merrygoround.core.common.AppErrorType.VALIDATION) null
                        else result.message
                    )
                }
            }
        }
    }

    fun clearErrors() = _uiState.update { it.copy(fieldErrors = emptyMap(), formError = null) }
    fun consumeCreated() = _uiState.update { it.copy(createdGroup = null) }
}
