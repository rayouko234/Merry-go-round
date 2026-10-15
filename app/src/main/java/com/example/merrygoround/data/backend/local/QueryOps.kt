package com.example.merrygoround.data.backend.local

import com.example.merrygoround.core.common.AppErrorType
import com.example.merrygoround.core.common.AppResult
import com.example.merrygoround.core.common.TimeFormatter
import com.example.merrygoround.data.local.AppDatabase
import com.example.merrygoround.data.local.CycleEntity
import com.example.merrygoround.domain.model.ActivityEntry
import com.example.merrygoround.domain.model.AppNotification
import com.example.merrygoround.domain.model.ContributionStatus
import com.example.merrygoround.domain.model.ContributionVerification
import com.example.merrygoround.domain.model.Cycle
import com.example.merrygoround.domain.model.CycleStatus
import com.example.merrygoround.domain.model.DashboardSummary
import com.example.merrygoround.domain.model.GroupStatus
import com.example.merrygoround.domain.model.MembershipRole
import com.example.merrygoround.domain.model.NotificationType
import com.example.merrygoround.domain.model.PayoutStatus

/** Read models: dashboard aggregation, cycle schedule, activity feed, notifications. */
internal class QueryOps(
    private val db: AppDatabase,
    private val auth: AuthOps,
    private val groups: GroupOps,
    private val audit: AuditOps
) {

    suspend fun dashboard(groupId: String): AppResult<DashboardSummary> {
        val actor = auth.currentUser()
        groups.requireMember(groupId, actor.id)
        val group = db.groupDao().findById(groupId)
            ?: return AppResult.Failure(AppErrorType.NOT_FOUND, "Group not found")
        val myMembership = db.membershipDao().findByGroupAndUser(groupId, actor.id)
        val isOwner = group.ownerId == actor.id

        val activeCycle = db.cycleDao().findActiveByGroup(groupId)
        val nextCycle = db.cycleDao().findNextUpcoming(groupId)

        var expected = 0L
        var confirmed = 0L
        var pending = 0L
        var failed = 0L
        var reversed = 0L
        var paidCount = 0
        var pendingCount = 0
        var unpaidCount = 0
        var myStatus: ContributionStatus? = null
        var myContributionId: String? = null

        val activeMemberCount = db.groupDao().activeMemberCount(groupId)

        if (activeCycle != null) {
            expected = activeCycle.expectedTotalMinor
            db.contributionDao().statusRollupForCycle(activeCycle.id).forEach { rollup ->
                val total = rollup.total ?: 0L
                when (ContributionStatus.fromWire(rollup.status)) {
                    ContributionStatus.CONFIRMED -> confirmed += total
                    ContributionStatus.PENDING -> pending += total
                    ContributionStatus.FAILED -> failed += total
                    ContributionStatus.REVERSED -> reversed += total
                }
            }
            val obligations = db.contributionDao().activeInCycle(activeCycle.id)
            val settledUsers = obligations
                .filter { it.status == ContributionStatus.CONFIRMED.name }
                .map { it.userId }.toSet()
            val pendingUsers = obligations
                .filter { it.status == ContributionStatus.PENDING.name && it.userId !in settledUsers }
                .map { it.userId }.toSet()
            paidCount = settledUsers.size
            pendingCount = pendingUsers.size
            unpaidCount = (activeMemberCount - paidCount - pendingCount).coerceAtLeast(0)

            obligations.filter { it.userId == actor.id }.maxByOrNull { it.createdAtMillis }?.let {
                myStatus = ContributionStatus.fromWire(it.status)
                myContributionId = it.id
            }
        }

        // Recipient for "next payout": current cycle's recipient, else next cycle's.
        val recipientCycle = activeCycle ?: nextCycle
        val recipientName = recipientCycle?.let { c ->
            db.membershipDao().findById(c.recipientMembershipId)?.let { m ->
                db.userDao().findById(m.userId)?.fullName
            }
        }

        val membership = myMembership?.payoutOrder
        return AppResult.Success(
            DashboardSummary(
                groupId = groupId,
                groupName = group.name,
                currency = group.currency,
                status = GroupStatus.fromWire(group.status),
                contributionAmountMinor = group.contributionAmountMinor,
                memberCount = activeMemberCount,
                memberLimit = group.memberLimit,
                currentCycleNumber = activeCycle?.cycleNumber,
                cycleStartEpochDay = activeCycle?.startEpochDay,
                cycleEndEpochDay = activeCycle?.endEpochDay,
                deadlineEpochDay = activeCycle?.contributionDeadlineEpochDay
                    ?: nextCycle?.contributionDeadlineEpochDay,
                expectedCycleTotalMinor = expected,
                confirmedTotalMinor = confirmed,
                pendingTotalMinor = pending,
                failedTotalMinor = failed,
                reversedTotalMinor = reversed,
                outstandingTotalMinor = (expected - confirmed).coerceAtLeast(0),
                paidMemberCount = paidCount,
                pendingMemberCount = pendingCount,
                unpaidMemberCount = unpaidCount,
                nextRecipientName = recipientName,
                nextPayoutAtMillis = recipientCycle?.scheduledPayoutAtMillis,
                myContributionStatus = myStatus,
                myContributionId = myContributionId,
                myDueEpochDay = activeCycle?.contributionDeadlineEpochDay,
                isOwner = isOwner || (membership != null && myMembership?.role != null &&
                    MembershipRole.fromWire(myMembership.role).isAdminLike)
            )
        )
    }

    suspend fun cycles(groupId: String): AppResult<List<Cycle>> {
        val actor = auth.currentUser()
        groups.requireMember(groupId, actor.id)
        return AppResult.Success(db.cycleDao().listByGroup(groupId).map { it.toDomain(db) })
    }

    suspend fun activity(limit: Int): AppResult<List<ActivityEntry>> {
        val actor = auth.currentUser()
        val myGroups = db.groupDao().listForUser(actor.id)
        val groupNames = myGroups.associate { it.id to it.name }

        // Contributions in my groups (members can see their group's permitted history).
        val contributionRows = myGroups.flatMap { g ->
            db.contributionDao().list(groupId = g.id, cycleId = null, status = null)
                .map { c ->
                    val cycle = db.cycleDao().findById(c.cycleId)
                    val user = db.userDao().findById(c.userId)
                    ActivityEntry(
                        id = c.id,
                        kind = "CONTRIBUTION",
                        title = "Contribution ${ContributionStatus.fromWire(c.status).name.lowercase().replaceFirstChar { it.uppercase() }}",
                        subtitle = (user?.fullName ?: "Member") +
                            (cycle?.let { " · Cycle ${it.cycleNumber}" } ?: "") +
                            " · ${c.reference}",
                        amountMinor = c.amountMinor,
                        currency = c.currency,
                        status = c.status,
                        timestampMillis = c.updatedAtMillis,
                        groupId = c.groupId,
                        groupName = groupNames[c.groupId] ?: ""
                    )
                }
        }

        val payoutRows = myGroups.flatMap { g ->
            db.payoutDao().listByGroup(g.id).map { p ->
                val user = db.userDao().findById(p.recipientUserId)
                ActivityEntry(
                    id = p.id,
                    kind = "PAYOUT",
                    title = "Payout ${PayoutStatus.fromWire(p.status).name.lowercase().replaceFirstChar { it.uppercase() }}",
                    subtitle = "To ${user?.fullName ?: "recipient"} · Cycle ${p.cycleNumber} · ${p.reference}",
                    amountMinor = p.amountMinor,
                    currency = p.currency,
                    status = p.status,
                    timestampMillis = p.completedAtMillis ?: p.scheduledAtMillis,
                    groupId = p.groupId,
                    groupName = groupNames[p.groupId] ?: ""
                )
            }
        }

        val membershipRows = myGroups.flatMap { g ->
            db.auditEventDao().listByGroup(g.id)
                .filter {
                    it.eventType.startsWith("MEMBER_") || it.eventType.startsWith("GROUP_") ||
                        it.eventType == "PAYOUT_ORDER_SET"
                }
                .map { e ->
                    val actorUser = e.actorUserId?.let { db.userDao().findById(it) }
                    ActivityEntry(
                        id = e.id,
                        kind = "GROUP",
                        title = eventTitle(e.eventType),
                        subtitle = "${actorUser?.fullName ?: "System"} · ${e.metadata.take(80)}",
                        amountMinor = null,
                        currency = null,
                        status = e.eventType,
                        timestampMillis = e.timestampMillis,
                        groupId = e.groupId,
                        groupName = groupNames[e.groupId] ?: ""
                    )
                }
        }

        val all = (contributionRows + payoutRows + membershipRows)
            .distinctBy { it.id }
            .sortedByDescending { it.timestampMillis }
        return AppResult.Success(all.take(limit.coerceIn(1, 500)))
    }

    private fun eventTitle(eventType: String): String = when (eventType) {
        "GROUP_CREATED" -> "Group created"
        "GROUP_STARTED" -> "Group started"
        "GROUP_COMPLETED" -> "Group completed"
        "GROUP_SETTINGS_UPDATED" -> "Group settings updated"
        "MEMBER_JOINED" -> "Member joined"
        "MEMBER_REQUESTED" -> "Join requested"
        "MEMBER_APPROVED" -> "Member approved"
        "MEMBER_REJECTED" -> "Join request declined"
        "MEMBER_REMOVED" -> "Member removed"
        "PAYOUT_ORDER_SET" -> "Payout order updated"
        else -> eventType.lowercase().replaceFirstChar { it.uppercase() }
    }

    suspend fun notifications(): AppResult<List<AppNotification>> {
        val actor = auth.currentUser()
        return AppResult.Success(
            db.notificationDao().listForUser(actor.id).map {
                AppNotification(
                    id = it.id,
                    userId = it.userId,
                    type = NotificationType.fromWire(it.type),
                    title = it.title,
                    message = it.message,
                    isRead = it.isRead,
                    createdAtMillis = it.createdAtMillis,
                    groupId = it.groupId
                )
            }
        )
    }

    suspend fun markNotificationsRead(): AppResult<Unit> {
        val actor = auth.currentUser()
        db.notificationDao().markAllRead(actor.id)
        return AppResult.Success(Unit)
    }

    suspend fun auditEvents(groupId: String): AppResult<List<com.example.merrygoround.domain.model.AuditEvent>> =
        try {
            AppResult.Success(audit.listByGroup(groupId))
        } catch (e: Exception) {
            AppResult.Failure(AppErrorType.UNKNOWN, e.message ?: "Something went wrong")
        }

    private suspend fun CycleEntity.toDomain(db: AppDatabase): Cycle {
        val membership = db.membershipDao().findById(recipientMembershipId)
        val recipient = membership?.let { db.userDao().findById(it.userId) }
        return Cycle(
            id = id,
            groupId = groupId,
            cycleNumber = cycleNumber,
            startEpochDay = startEpochDay,
            endEpochDay = endEpochDay,
            contributionDeadlineEpochDay = contributionDeadlineEpochDay,
            recipientMembershipId = recipientMembershipId,
            recipientUserId = membership?.userId ?: "",
            recipientName = recipient?.fullName ?: "Unknown",
            expectedTotalMinor = expectedTotalMinor,
            status = CycleStatus.fromWire(status),
            scheduledPayoutAtMillis = scheduledPayoutAtMillis,
            actualPayoutAtMillis = actualPayoutAtMillis
        )
    }
}


