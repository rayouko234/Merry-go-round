package com.example.merrygoround.data.backend.local

import com.example.merrygoround.core.common.AppErrorType
import com.example.merrygoround.core.common.AppResult
import com.example.merrygoround.core.common.Validators
import com.example.merrygoround.core.security.Ids
import com.example.merrygoround.data.local.AppDatabase
import com.example.merrygoround.data.local.ContributionEntity
import com.example.merrygoround.domain.ContributionStateMachine
import com.example.merrygoround.domain.model.Contribution
import com.example.merrygoround.domain.model.ContributionStatus
import com.example.merrygoround.domain.model.ContributionVerification
import com.example.merrygoround.domain.model.CycleStatus
import com.example.merrygoround.domain.model.GroupStatus
import com.example.merrygoround.domain.model.MemberObligation
import com.example.merrygoround.domain.model.MembershipRole
import com.example.merrygoround.domain.model.MembershipStatus
import com.example.merrygoround.domain.model.NotificationType

/**
 * Contribution tracking (MVP Mode A). A member's entry starts as PENDING /
 * SELF_REPORTED; only an admin verification (or a future provider callback)
 * makes it CONFIRMED. Nothing is ever presented as provider-verified money.
 */
internal class ContributionOps(
    private val db: AppDatabase,
    private val auth: AuthOps,
    private val groups: GroupOps,
    private val audit: AuditOps,
    private val now: () -> Long
) {

    suspend fun record(
        groupId: String,
        cycleId: String,
        amountMinor: Long,
        idempotencyKey: String,
        note: String
    ): AppResult<Contribution> {
        val actor = auth.currentUser()
        groups.requireMember(groupId, actor.id)

        val group = db.groupDao().findById(groupId)
            ?: return AppResult.Failure(AppErrorType.NOT_FOUND, "Group not found")
        if (group.status != GroupStatus.ACTIVE.name) {
            return AppResult.Failure(AppErrorType.CONFLICT, "This group is not accepting contributions")
        }
        val cycle = db.cycleDao().findById(cycleId)
            ?: return AppResult.Failure(AppErrorType.NOT_FOUND, "Cycle not found")
        if (cycle.groupId != groupId) {
            return AppResult.Failure(AppErrorType.VALIDATION, "That cycle does not belong to this group")
        }
        if (cycle.status != CycleStatus.ACTIVE.name) {
            return AppResult.Failure(
                AppErrorType.CONFLICT, "Contributions are only accepted for the current cycle"
            )
        }
        if (idempotencyKey.isBlank() || idempotencyKey.length > 100) {
            return AppResult.Failure(AppErrorType.VALIDATION, "Missing idempotency key")
        }

        // Retry with the same key returns the SAME record — never a duplicate.
        db.contributionDao().findByIdempotencyKey(idempotencyKey)?.let { existing ->
            return AppResult.Success(existing.toDomain(db))
        }

        // Amount rule: the full fixed contribution only; partials/overpays rejected
        // explicitly rather than silently accepted (brief §4.4).
        if (amountMinor != group.contributionAmountMinor) {
            return AppResult.Failure(
                AppErrorType.VALIDATION,
                "Contribution must match the group's fixed amount",
                mapOf("amount" to "Amount must match the group's fixed contribution")
            )
        }
        Validators.validateContributionAmount(amountMinor, group.currency)?.let {
            return AppResult.Failure(AppErrorType.VALIDATION, it, mapOf("amount" to it))
        }

        // One live (PENDING/CONFIRMED) contribution per member per cycle.
        val membership = db.membershipDao().findByGroupAndUser(groupId, actor.id)
            ?: return AppResult.Failure(AppErrorType.PERMISSION_DENIED, "You are not a member of this group")
        db.contributionDao().findLatestForUserInCycle(actor.id, cycleId)?.let { live ->
            if (live.status == ContributionStatus.CONFIRMED.name) {
                return AppResult.Failure(
                    AppErrorType.CONFLICT, "Your contribution for this cycle is already confirmed"
                )
            }
            if (live.status == ContributionStatus.PENDING.name) {
                return AppResult.Failure(
                    AppErrorType.CONFLICT, "Your contribution for this cycle is awaiting verification"
                )
            }
        }

        val t = now()
        val entity = ContributionEntity(
            id = Ids.newId(),
            groupId = groupId,
            cycleId = cycleId,
            memberId = membership.id,
            userId = actor.id,
            amountMinor = amountMinor,
            currency = group.currency,
            status = ContributionStatus.PENDING.name,
            verification = ContributionVerification.SELF_REPORTED.name,
            reference = Ids.newReference("MGR"),
            idempotencyKey = idempotencyKey,
            createdAtMillis = t,
            updatedAtMillis = t,
            note = note.trim().take(200),
            failureReason = ""
        )
        val (saved, created) = db.contributionDao().insertOrGetExisting(entity)
        if (!created) {
            // Lost the race — return the winner instead of duplicating.
            return AppResult.Success(saved.toDomain(db))
        }

        audit.record(
            groupId = groupId,
            entityType = "contribution",
            entityId = saved.id,
            eventType = "CONTRIBUTION_RECORDED",
            actorUserId = actor.id,
            metadata = """{"amountMinor":$amountMinor,"status":"PENDING"}"""
        )
        groups.notifyGroupAdmins(
            group = group,
            excludeUserId = actor.id,
            type = NotificationType.CONTRIBUTION_DUE,
            title = "Contribution reported",
            message = "${actor.fullName} reported a contribution for cycle ${cycle.cycleNumber}"
        )
        return AppResult.Success(saved.toDomain(db))
    }

    /** Admin verification. Enforces the contribution state machine. */
    suspend fun confirm(contributionId: String, approve: Boolean, reason: String): AppResult<Contribution> {
        val actor = auth.currentUser()
        val contribution = db.contributionDao().findById(contributionId)
            ?: return AppResult.Failure(AppErrorType.NOT_FOUND, "Contribution not found")
        groups.requireAdmin(contribution.groupId, actor.id)

        val target = if (approve) ContributionStatus.CONFIRMED else ContributionStatus.FAILED
        val newStatus = ContributionStateMachine.transition(
            ContributionStatus.fromWire(contribution.status), target
        ) ?: return AppResult.Failure(
            AppErrorType.CONFLICT,
            "A ${contribution.status} contribution cannot move to ${target.name}"
        )

        val updated = contribution.copy(
            status = newStatus.name,
            verification = if (approve) ContributionVerification.ADMIN_VERIFIED.name
            else contribution.verification,
            updatedAtMillis = now(),
            failureReason = if (approve) "" else reason.trim().take(200)
        )
        db.contributionDao().update(updated)

        audit.record(
            groupId = contribution.groupId,
            entityType = "contribution",
            entityId = contributionId,
            eventType = if (approve) "CONTRIBUTION_CONFIRMED" else "CONTRIBUTION_FAILED",
            actorUserId = actor.id,
            metadata = """{"by":"${actor.fullName}","reason":"${reason.take(80)}"}"""
        )
        val group = db.groupDao().findById(contribution.groupId)
        val cycle = db.cycleDao().findById(contribution.cycleId)
        if (group != null && cycle != null) {
            groups.notifyUser(
                userId = contribution.userId,
                type = if (approve) NotificationType.CONTRIBUTION_CONFIRMED else NotificationType.CONTRIBUTION_FAILED,
                title = if (approve) "Contribution confirmed" else "Contribution not accepted",
                message = if (approve)
                    "Your contribution for cycle ${cycle.cycleNumber} was confirmed"
                else
                    "Your contribution for cycle ${cycle.cycleNumber} was rejected" +
                        (if (reason.isNotBlank()) ": ${reason.take(80)}" else ""),
                groupId = contribution.groupId
            )
        }
        return AppResult.Success(updated.toDomain(db))
    }

    /** Audited reversal of a CONFIRMED contribution. History is preserved. */
    suspend fun reverse(contributionId: String, reason: String): AppResult<Contribution> {
        val actor = auth.currentUser()
        val contribution = db.contributionDao().findById(contributionId)
            ?: return AppResult.Failure(AppErrorType.NOT_FOUND, "Contribution not found")
        groups.requireAdmin(contribution.groupId, actor.id)
        if (reason.trim().length < 3) {
            return AppResult.Failure(
                AppErrorType.VALIDATION, "A reason is required to reverse a contribution",
                mapOf("reason" to "Required")
            )
        }
        val newStatus = ContributionStateMachine.transition(
            ContributionStatus.fromWire(contribution.status), ContributionStatus.REVERSED
        ) ?: return AppResult.Failure(
            AppErrorType.CONFLICT,
            "Only a CONFIRMED contribution can be reversed (current: ${contribution.status})"
        )

        val updated = contribution.copy(
            status = newStatus.name,
            updatedAtMillis = now(),
            failureReason = reason.trim().take(200)
        )
        db.contributionDao().update(updated)
        audit.record(
            groupId = contribution.groupId,
            entityType = "contribution",
            entityId = contributionId,
            eventType = "CONTRIBUTION_REVERSED",
            actorUserId = actor.id,
            metadata = """{"by":"${actor.fullName}","reason":"${reason.take(120)}"}"""
        )
        return AppResult.Success(updated.toDomain(db))
    }

    suspend fun list(
        groupId: String?,
        cycleId: String?,
        status: ContributionStatus?
    ): AppResult<List<Contribution>> {
        val actor = auth.currentUser()
        if (groupId != null) {
            groups.requireMember(groupId, actor.id)
        }
        return AppResult.Success(
            db.contributionDao()
                .list(groupId, cycleId, status?.name)
                .map { it.toDomain(db) }
        )
    }

    /** Per-member payment status for a cycle (dashboard + group detail). */
    suspend fun memberObligations(groupId: String, cycleId: String?): AppResult<List<MemberObligation>> {
        val actor = auth.currentUser()
        groups.requireMember(groupId, actor.id)
        val group = db.groupDao().findById(groupId)
            ?: return AppResult.Failure(AppErrorType.NOT_FOUND, "Group not found")
        val effectiveCycleId = cycleId
            ?: db.cycleDao().findActiveByGroup(groupId)?.id
            ?: return AppResult.Success(emptyList())

        val contributions = db.contributionDao()
            .list(groupId = groupId, cycleId = effectiveCycleId, status = null)
            .filter { it.status != ContributionStatus.FAILED.name }

        return AppResult.Success(
            db.membershipDao().listActiveByGroup(groupId).map { membership ->
                val user = db.userDao().findById(membership.userId)
                // Latest non-failed contribution wins for display.
                val contribution = contributions
                    .filter { it.userId == membership.userId }
                    .maxByOrNull { it.createdAtMillis }
                MemberObligation(
                    userId = membership.userId,
                    userName = user?.fullName ?: "Unknown",
                    role = MembershipRole.fromWire(membership.role),
                    status = contribution?.let { ContributionStatus.fromWire(it.status) },
                    verification = contribution?.let { ContributionVerification.fromWire(it.verification) },
                    paidAmountMinor = if (contribution?.status == ContributionStatus.CONFIRMED.name)
                        contribution.amountMinor else 0L,
                    dueAmountMinor = group.contributionAmountMinor,
                    updatedAtMillis = contribution?.updatedAtMillis
                )
            }
        )
    }

    suspend fun ContributionEntity.toDomain(db: AppDatabase): Contribution {
        val user = db.userDao().findById(userId)
        return Contribution(
            id = id,
            groupId = groupId,
            cycleId = cycleId,
            memberId = memberId,
            userId = userId,
            userName = user?.fullName ?: "Unknown",
            amountMinor = amountMinor,
            currency = currency,
            status = ContributionStatus.fromWire(status),
            verification = ContributionVerification.fromWire(verification),
            reference = reference,
            idempotencyKey = idempotencyKey,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
            note = note,
            failureReason = failureReason
        )
    }
}
