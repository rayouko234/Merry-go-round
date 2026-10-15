package com.example.merrygoround.data.backend

import com.example.merrygoround.core.common.AppResult
import com.example.merrygoround.domain.model.ActivityEntry
import com.example.merrygoround.domain.model.AppNotification
import com.example.merrygoround.domain.model.AuditEvent
import com.example.merrygoround.domain.model.Contribution
import com.example.merrygoround.domain.model.ContributionStatus
import com.example.merrygoround.domain.model.Cycle
import com.example.merrygoround.domain.model.DashboardSummary
import com.example.merrygoround.domain.model.GroupStatus
import com.example.merrygoround.domain.model.Invitation
import com.example.merrygoround.domain.model.JoinPolicy
import com.example.merrygoround.domain.model.MemberObligation
import com.example.merrygoround.domain.model.Membership
import com.example.merrygoround.domain.model.Payout
import com.example.merrygoround.domain.model.SavingsGroup
import com.example.merrygoround.domain.model.User
import com.example.merrygoround.domain.model.ContributionFrequency
import com.example.merrygoround.domain.model.PayoutOrderMethod

/**
 * THE INTEGRATION BOUNDARY.
 *
 * Every feature flows through this interface. Two implementations exist:
 *  - [com.example.merrygoround.data.backend.local.LocalBackend]  — Room-backed authoritative
 *    store used for development/demo and single-device use (MVP default).
 *  - [com.example.merrygoround.data.backend.remote.RemoteBackend] — Retrofit client against
 *    the documented REST contract (docs/API.md), backed by the Django service in backend/.
 *
 * The backend is the source of truth: the app never mutates balances or statuses directly.
 * All methods authorize the acting session and return typed errors.
 */
interface BackendApi {

    // ---------- Auth ----------
    suspend fun register(
        fullName: String,
        email: String,
        phone: String,
        password: String
    ): AppResult<User>

    /** [identifier] may be an email or a phone number. */
    suspend fun login(identifier: String, password: String): AppResult<User>

    suspend fun logout(): AppResult<Unit>

    suspend fun me(): AppResult<User>

    suspend fun updateProfile(fullName: String?, phone: String?): AppResult<User>

    // ---------- Groups ----------
    suspend fun listMyGroups(): AppResult<List<SavingsGroup>>

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
    ): AppResult<SavingsGroup>

    suspend fun getGroup(groupId: String): AppResult<SavingsGroup>

    suspend fun updateGroupSettings(
        groupId: String,
        name: String?,
        description: String?,
        memberLimit: Int?,
        joinPolicy: JoinPolicy?
    ): AppResult<SavingsGroup>

    suspend fun joinGroup(inviteCode: String): AppResult<Membership>

    // ---------- Members ----------
    suspend fun listMembers(groupId: String): AppResult<List<Membership>>

    suspend fun decideMembership(
        groupId: String,
        membershipId: String,
        approve: Boolean
    ): AppResult<Membership>

    suspend fun removeMember(groupId: String, membershipId: String): AppResult<Unit>

    /** Reorders the payout rotation. Only allowed while the group is FORMING. */
    suspend fun setPayoutOrder(groupId: String, orderedMembershipIds: List<String>): AppResult<List<Membership>>

    /** Starts the first cycle and locks the payout order. Owner/admin only. */
    suspend fun startGroup(groupId: String): AppResult<SavingsGroup>

    suspend fun getInvitation(groupId: String): AppResult<Invitation>

    // ---------- Cycles & schedule ----------
    suspend fun listCycles(groupId: String): AppResult<List<Cycle>>

    // ---------- Contributions ----------
    suspend fun listContributions(
        groupId: String?,
        cycleId: String?,
        status: ContributionStatus?
    ): AppResult<List<Contribution>>

    /**
     * Initiate/record a contribution for the caller in [cycleId].
     * [idempotencyKey] must be reused on retries so duplicates are impossible.
     * In MVP (Mode A) this creates a SELF_REPORTED PENDING entry; an admin
     * [confirmContribution] promotes it to CONFIRMED. Nothing is ever faked as
     * provider-verified money.
     */
    suspend fun recordContribution(
        groupId: String,
        cycleId: String,
        amountMinor: Long,
        idempotencyKey: String,
        note: String
    ): AppResult<Contribution>

    /** Admin verification of a contribution. Authorization is enforced server-side. */
    suspend fun confirmContribution(contributionId: String, approve: Boolean, reason: String): AppResult<Contribution>

    suspend fun reverseContribution(contributionId: String, reason: String): AppResult<Contribution>

    // ---------- Payouts ----------
    suspend fun listPayouts(groupId: String): AppResult<List<Payout>>

    /** Marks a scheduled payout COMPLETED. Owner/admin only; audited; guarded against races. */
    suspend fun completePayout(payoutId: String): AppResult<Payout>

    // ---------- Dashboard / obligations ----------
    suspend fun getDashboard(groupId: String): AppResult<DashboardSummary>

    suspend fun listMemberObligations(groupId: String, cycleId: String?): AppResult<List<MemberObligation>>

    // ---------- Activity / audit / notifications ----------
    suspend fun listActivity(limit: Int): AppResult<List<ActivityEntry>>

    suspend fun listAuditEvents(groupId: String): AppResult<List<AuditEvent>>

    suspend fun listNotifications(): AppResult<List<AppNotification>>

    suspend fun markNotificationsRead(): AppResult<Unit>
}
