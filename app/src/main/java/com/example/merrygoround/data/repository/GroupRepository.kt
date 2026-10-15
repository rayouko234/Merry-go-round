package com.example.merrygoround.data.repository

import com.example.merrygoround.core.common.AppResult
import com.example.merrygoround.data.backend.BackendApi
import com.example.merrygoround.domain.model.Contribution
import com.example.merrygoround.domain.model.ContributionStatus
import com.example.merrygoround.domain.model.Cycle
import com.example.merrygoround.domain.model.DashboardSummary
import com.example.merrygoround.domain.model.Invitation
import com.example.merrygoround.domain.model.JoinPolicy
import com.example.merrygoround.domain.model.MemberObligation
import com.example.merrygoround.domain.model.Membership
import com.example.merrygoround.domain.model.Payout
import com.example.merrygoround.domain.model.SavingsGroup
import com.example.merrygoround.domain.model.ContributionFrequency
import com.example.merrygoround.domain.model.PayoutOrderMethod

/** Thin repository over [BackendApi] for group-scoped operations. */
class GroupRepository(private val backend: BackendApi) {

    suspend fun listMyGroups(): AppResult<List<SavingsGroup>> = backend.listMyGroups()

    suspend fun createGroup(
        name: String,
        description: String,
        contributionAmountMinor: Long,
        frequency: ContributionFrequency,
        customIntervalDays: Int,
        memberLimit: Int,
        startEpochDay: Long,
        payoutOrderMethod: PayoutOrderMethod,
        joinPolicy: JoinPolicy,
        invitationExpiryDays: Int
    ): AppResult<SavingsGroup> = backend.createGroup(
        name, description, contributionAmountMinor, frequency, customIntervalDays,
        memberLimit, startEpochDay, payoutOrderMethod, joinPolicy, invitationExpiryDays
    )

    suspend fun getGroup(groupId: String): AppResult<SavingsGroup> = backend.getGroup(groupId)

    suspend fun updateSettings(
        groupId: String, name: String?, description: String?, memberLimit: Int?, joinPolicy: JoinPolicy?
    ): AppResult<SavingsGroup> = backend.updateGroupSettings(groupId, name, description, memberLimit, joinPolicy)

    suspend fun join(inviteCode: String): AppResult<Membership> = backend.joinGroup(inviteCode)

    suspend fun listMembers(groupId: String): AppResult<List<Membership>> = backend.listMembers(groupId)

    suspend fun decideMembership(groupId: String, membershipId: String, approve: Boolean): AppResult<Membership> =
        backend.decideMembership(groupId, membershipId, approve)

    suspend fun removeMember(groupId: String, membershipId: String): AppResult<Unit> =
        backend.removeMember(groupId, membershipId)

    suspend fun setPayoutOrder(groupId: String, orderedIds: List<String>): AppResult<List<Membership>> =
        backend.setPayoutOrder(groupId, orderedIds)

    suspend fun startGroup(groupId: String): AppResult<SavingsGroup> = backend.startGroup(groupId)

    suspend fun getInvitation(groupId: String): AppResult<Invitation> = backend.getInvitation(groupId)

    suspend fun listCycles(groupId: String): AppResult<List<Cycle>> = backend.listCycles(groupId)

    suspend fun getDashboard(groupId: String): AppResult<DashboardSummary> = backend.getDashboard(groupId)

    suspend fun obligations(groupId: String, cycleId: String?): AppResult<List<MemberObligation>> =
        backend.listMemberObligations(groupId, cycleId)

    suspend fun listPayouts(groupId: String): AppResult<List<Payout>> = backend.listPayouts(groupId)

    suspend fun completePayout(payoutId: String): AppResult<Payout> = backend.completePayout(payoutId)
}

/** Contribution + activity operations. */
class ContributionRepository(private val backend: BackendApi) {

    suspend fun list(
        groupId: String?, cycleId: String?, status: ContributionStatus?
    ): AppResult<List<Contribution>> = backend.listContributions(groupId, cycleId, status)

    suspend fun record(
        groupId: String, cycleId: String, amountMinor: Long, idempotencyKey: String, note: String
    ): AppResult<Contribution> = backend.recordContribution(groupId, cycleId, amountMinor, idempotencyKey, note)

    suspend fun confirm(contributionId: String, approve: Boolean, reason: String): AppResult<Contribution> =
        backend.confirmContribution(contributionId, approve, reason)

    suspend fun reverse(contributionId: String, reason: String): AppResult<Contribution> =
        backend.reverseContribution(contributionId, reason)
}

class ActivityRepository(private val backend: BackendApi) {
    suspend fun recent(limit: Int) = backend.listActivity(limit)
    suspend fun auditEvents(groupId: String) = backend.listAuditEvents(groupId)
    suspend fun notifications() = backend.listNotifications()
    suspend fun markNotificationsRead() = backend.markNotificationsRead()
}
