package com.example.merrygoround.data.backend.local

import com.example.merrygoround.core.common.AppErrorType
import com.example.merrygoround.core.common.AppResult
import com.example.merrygoround.core.security.Ids
import com.example.merrygoround.data.local.AppDatabase
import com.example.merrygoround.data.local.PayoutEntity
import com.example.merrygoround.domain.model.CycleStatus
import com.example.merrygoround.domain.model.NotificationType
import com.example.merrygoround.domain.model.Payout
import com.example.merrygoround.domain.model.PayoutStatus

/**
 * Payout schedule handling. Completing a payout is an authorized, audited event —
 * pressing the button never fakes money movement; it records the confirmed
 * disbursement and advances the rotation (brief §4.5 rule 5).
 */
internal class PayoutOps(
    private val db: AppDatabase,
    private val auth: AuthOps,
    private val groups: GroupOps,
    private val contributions: ContributionOps,
    private val audit: AuditOps,
    private val now: () -> Long
) {

    suspend fun list(groupId: String): AppResult<List<Payout>> {
        val actor = auth.currentUser()
        groups.requireMember(groupId, actor.id)
        return AppResult.Success(db.payoutDao().listByGroup(groupId).map { it.toDomain(db) })
    }

    suspend fun complete(payoutId: String): AppResult<Payout> {
        val actor = auth.currentUser()
        val payout = db.payoutDao().findById(payoutId)
            ?: return AppResult.Failure(AppErrorType.NOT_FOUND, "Payout not found")
        groups.requireAdmin(payout.groupId, actor.id)

        // Race/duplicate guard: only SCHEDULED (or FAILED retry) can complete.
        val status = PayoutStatus.fromWire(payout.status)
        if (status == PayoutStatus.COMPLETED) {
            return AppResult.Failure(AppErrorType.CONFLICT, "This payout has already been completed")
        }
        if (status == PayoutStatus.PENDING) {
            return AppResult.Failure(AppErrorType.CONFLICT, "This payout is already being processed")
        }

        val cycle = db.cycleDao().findById(payout.cycleId)
            ?: return AppResult.Failure(AppErrorType.NOT_FOUND, "Cycle not found")
        if (cycle.status == CycleStatus.COMPLETED.name) {
            return AppResult.Failure(AppErrorType.CONFLICT, "This cycle is already completed")
        }

        // If the cycle is short of funds, the admin must acknowledge: the UI collects
        // this as an explicit confirmation; the backend records it in the audit trail.
        val confirmed = db.contributionDao().confirmedInCycle(payout.cycleId)
        val confirmedTotal = confirmed.sumOf { it.amountMinor }
        val shortfall = payout.amountMinor > confirmedTotal

        val t = now()
        // Intentionally not a silent update: status transition happens once, guarded above.
        db.payoutDao().setStatus(payoutId, PayoutStatus.COMPLETED.name, t)
        db.cycleDao().setStatusWithPayout(payout.cycleId, CycleStatus.COMPLETED.name, t)

        // Advance: next UPCOMING cycle becomes ACTIVE.
        val next = db.cycleDao().findNextUpcoming(payout.groupId)
        if (next != null) {
            db.cycleDao().setStatus(next.id, CycleStatus.ACTIVE.name)
        }

        audit.record(
            groupId = payout.groupId,
            entityType = "payout",
            entityId = payoutId,
            eventType = "PAYOUT_COMPLETED",
            actorUserId = actor.id,
            metadata = """{"by":"${actor.fullName}","confirmedMinor":$confirmedTotal,"shortfall":$shortfall}"""
        )

        val group = db.groupDao().findById(payout.groupId)
        if (group != null) {
            db.membershipDao().listActiveByGroup(payout.groupId).forEach { m ->
                groups.notifyUser(
                    userId = m.userId,
                    type = NotificationType.PAYOUT_COMPLETED,
                    title = "Payout completed",
                    message = "Cycle ${cycle.cycleNumber} payout to the recipient was marked complete" +
                        if (next != null) ". Cycle ${next.cycleNumber} is now open."
                        else if (group.status == "COMPLETED") ". The rotation is finished."
                        else ".",
                    groupId = payout.groupId
                )
            }
            if (next == null && db.cycleDao().countByGroup(payout.groupId) > 0) {
                val allDone = db.cycleDao().listByGroup(payout.groupId)
                    .all { it.status == CycleStatus.COMPLETED.name }
                if (allDone) {
                    db.groupDao().setStatus(payout.groupId, "COMPLETED")
                    audit.record(
                        groupId = payout.groupId,
                        entityType = "group",
                        entityId = payout.groupId,
                        eventType = "GROUP_COMPLETED",
                        actorUserId = actor.id,
                        metadata = "{}"
                    )
                }
            }
        }

        val updated = db.payoutDao().findById(payoutId)!!
        return AppResult.Success(updated.toDomain(db))
    }

    suspend fun PayoutEntity.toDomain(db: AppDatabase): Payout {
        val recipient = db.userDao().findById(recipientUserId)
        return Payout(
            id = id,
            groupId = groupId,
            cycleId = cycleId,
            cycleNumber = cycleNumber,
            recipientUserId = recipientUserId,
            recipientName = recipient?.fullName ?: "Unknown",
            amountMinor = amountMinor,
            currency = currency,
            status = PayoutStatus.fromWire(status),
            reference = reference,
            scheduledAtMillis = scheduledAtMillis,
            completedAtMillis = completedAtMillis
        )
    }
}
