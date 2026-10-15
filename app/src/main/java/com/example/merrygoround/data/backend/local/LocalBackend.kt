package com.example.merrygoround.data.backend.local

import com.example.merrygoround.core.common.AppErrorType
import com.example.merrygoround.core.common.AppException
import com.example.merrygoround.core.common.AppResult
import com.example.merrygoround.core.datastore.SessionStore
import com.example.merrygoround.data.backend.BackendApi
import com.example.merrygoround.data.local.AppDatabase
import com.example.merrygoround.domain.model.ActivityEntry
import com.example.merrygoround.domain.model.AppNotification
import com.example.merrygoround.domain.model.AuditEvent
import com.example.merrygoround.domain.model.Contribution
import com.example.merrygoround.domain.model.ContributionFrequency
import com.example.merrygoround.domain.model.ContributionStatus
import com.example.merrygoround.domain.model.Cycle
import com.example.merrygoround.domain.model.DashboardSummary
import com.example.merrygoround.domain.model.Invitation
import com.example.merrygoround.domain.model.JoinPolicy
import com.example.merrygoround.domain.model.MemberObligation
import com.example.merrygoround.domain.model.Membership
import com.example.merrygoround.domain.model.Payout
import com.example.merrygoround.domain.model.PayoutOrderMethod
import com.example.merrygoround.domain.model.SavingsGroup
import com.example.merrygoround.domain.model.User

/**
 * Room-backed authoritative implementation of [BackendApi].
 * Plays the role of the server for the MVP: it owns validation, authorization,
 * state transitions and audit. Every operation is wrapped in [guard] so the
 * ops layer may throw typed [AppException]s while callers always see AppResult.
 */
class LocalBackend internal constructor(
    db: AppDatabase,
    sessionStore: SessionStore,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : BackendApi {

    private val auth = AuthOps(db, sessionStore, clock)
    private val audit = AuditOps(db, clock)
    private val groups = GroupOps(db, auth, audit, clock)
    private val contributions = ContributionOps(db, auth, groups, audit, clock)
    private val payouts = PayoutOps(db, auth, groups, contributions, audit, clock)
    private val queries = QueryOps(db, auth, groups, audit)

    companion object {
        fun create(db: AppDatabase, sessionStore: SessionStore): LocalBackend =
            LocalBackend(db, sessionStore)
    }

    /** Converts thrown AppExceptions (and anything else) into typed failures. */
    private suspend fun <T> guard(block: suspend () -> AppResult<T>): AppResult<T> = try {
        block()
    } catch (e: AppException) {
        AppResult.Failure(e.type, e.message ?: "Something went wrong", e.fieldErrors)
    } catch (e: Exception) {
        AppResult.Failure(AppErrorType.UNKNOWN, e.message ?: "Something went wrong")
    }

    // ---------- Auth ----------
    override suspend fun register(fullName: String, email: String, phone: String, password: String) =
        guard { auth.register(fullName, email, phone, password) }

    override suspend fun login(identifier: String, password: String) =
        guard { auth.login(identifier, password) }

    override suspend fun logout() = guard { auth.logout() }

    override suspend fun me(): AppResult<User> = guard {
        AppResult.Success(auth.currentUser().toDomain())
    }

    override suspend fun updateProfile(fullName: String?, phone: String?) =
        guard { auth.updateProfile(fullName, phone) }

    // ---------- Groups ----------
    override suspend fun listMyGroups() = guard { groups.listMyGroups() }

    override suspend fun createGroup(
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
    ) = guard {
        groups.createGroup(
            name, description, contributionAmountMinor, frequency, customIntervalDays,
            memberLimit, startEpochDay, payoutOrderMethod, joinPolicy, invitationExpiryDays
        )
    }

    override suspend fun getGroup(groupId: String) = guard { groups.getGroup(groupId) }

    override suspend fun updateGroupSettings(
        groupId: String, name: String?, description: String?, memberLimit: Int?, joinPolicy: JoinPolicy?
    ) = guard { groups.updateGroupSettings(groupId, name, description, memberLimit, joinPolicy) }

    override suspend fun joinGroup(inviteCode: String) = guard { groups.joinGroup(inviteCode) }

    // ---------- Members ----------
    override suspend fun listMembers(groupId: String) = guard { groups.listMembers(groupId) }

    override suspend fun decideMembership(groupId: String, membershipId: String, approve: Boolean) =
        guard { groups.decideMembership(groupId, membershipId, approve) }

    override suspend fun removeMember(groupId: String, membershipId: String) =
        guard { groups.removeMember(groupId, membershipId) }

    override suspend fun setPayoutOrder(groupId: String, orderedMembershipIds: List<String>) =
        guard { groups.setPayoutOrder(groupId, orderedMembershipIds) }

    override suspend fun startGroup(groupId: String) = guard { groups.startGroup(groupId) }

    override suspend fun getInvitation(groupId: String): AppResult<Invitation> =
        guard { groups.getInvitation(groupId) }

    // ---------- Cycles ----------
    override suspend fun listCycles(groupId: String): AppResult<List<Cycle>> =
        guard { queries.cycles(groupId) }

    // ---------- Contributions ----------
    override suspend fun listContributions(
        groupId: String?, cycleId: String?, status: ContributionStatus?
    ) = guard { contributions.list(groupId, cycleId, status) }

    override suspend fun recordContribution(
        groupId: String, cycleId: String, amountMinor: Long, idempotencyKey: String, note: String
    ) = guard { contributions.record(groupId, cycleId, amountMinor, idempotencyKey, note) }

    override suspend fun confirmContribution(contributionId: String, approve: Boolean, reason: String) =
        guard { contributions.confirm(contributionId, approve, reason) }

    override suspend fun reverseContribution(contributionId: String, reason: String) =
        guard { contributions.reverse(contributionId, reason) }

    // ---------- Payouts ----------
    override suspend fun listPayouts(groupId: String) = guard { payouts.list(groupId) }
    override suspend fun completePayout(payoutId: String) = guard { payouts.complete(payoutId) }

    // ---------- Dashboard ----------
    override suspend fun getDashboard(groupId: String): AppResult<DashboardSummary> =
        guard { queries.dashboard(groupId) }

    override suspend fun listMemberObligations(groupId: String, cycleId: String?) =
        guard { contributions.memberObligations(groupId, cycleId) }

    // ---------- Activity / audit / notifications ----------
    override suspend fun listActivity(limit: Int): AppResult<List<ActivityEntry>> =
        guard { queries.activity(limit) }

    override suspend fun listAuditEvents(groupId: String): AppResult<List<AuditEvent>> =
        guard { queries.auditEvents(groupId) }

    override suspend fun listNotifications(): AppResult<List<AppNotification>> =
        guard { queries.notifications() }

    override suspend fun markNotificationsRead(): AppResult<Unit> =
        guard { queries.markNotificationsRead() }
}

