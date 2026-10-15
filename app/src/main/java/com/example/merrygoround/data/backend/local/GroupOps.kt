package com.example.merrygoround.data.backend.local

import com.example.merrygoround.core.common.AppErrorType
import com.example.merrygoround.core.common.AppException
import com.example.merrygoround.core.common.AppResult
import com.example.merrygoround.core.common.TimeFormatter
import com.example.merrygoround.core.common.Validators
import com.example.merrygoround.core.security.Ids
import com.example.merrygoround.data.local.AppDatabase
import com.example.merrygoround.data.local.AuditEventEntity
import com.example.merrygoround.data.local.CycleEntity
import com.example.merrygoround.data.local.GroupEntity
import com.example.merrygoround.data.local.InvitationEntity
import com.example.merrygoround.data.local.MembershipEntity
import com.example.merrygoround.data.local.NotificationEntity
import com.example.merrygoround.data.local.PayoutEntity
import com.example.merrygoround.domain.CyclePlanner
import com.example.merrygoround.domain.model.ContributionFrequency
import com.example.merrygoround.domain.model.CycleStatus
import com.example.merrygoround.domain.model.GroupStatus
import com.example.merrygoround.domain.model.Invitation
import com.example.merrygoround.domain.model.JoinPolicy
import com.example.merrygoround.domain.model.Membership
import com.example.merrygoround.domain.model.MembershipRole
import com.example.merrygoround.domain.model.MembershipStatus
import com.example.merrygoround.domain.model.NotificationType
import com.example.merrygoround.domain.model.PayoutOrderMethod
import com.example.merrygoround.domain.model.PayoutStatus
import com.example.merrygoround.domain.model.SavingsGroup

/** Group lifecycle: creation, invitations, joining, membership, schedule start. */
internal class GroupOps(
    private val db: AppDatabase,
    private val auth: AuthOps,
    private val audit: AuditOps,
    private val now: () -> Long
) {

    // ---------- create ----------

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
    ): AppResult<SavingsGroup> {
        val actor = auth.currentUser()
        val fieldErrors = mutableMapOf<String, String>()
        Validators.validateGroupName(name)?.let { fieldErrors["name"] = it }
        Validators.validateDescription(description)?.let { fieldErrors["description"] = it }
        Validators.validateContributionAmount(contributionAmountMinor)?.let { fieldErrors["amount"] = it }
        Validators.validateMemberLimit(memberLimit)?.let { fieldErrors["memberLimit"] = it }
        Validators.validateStartDate(startEpochDay, TimeFormatter.todayEpochDay())
            ?.let { fieldErrors["startDate"] = it }
        if (frequency == ContributionFrequency.CUSTOM) {
            Validators.validateCustomIntervalDays(customIntervalDays)
                ?.let { fieldErrors["interval"] = it }
        }
        if (invitationExpiryDays !in 1..90) {
            fieldErrors["invitationExpiryDays"] = "Invitation expiry must be 1-90 days"
        }
        if (fieldErrors.isNotEmpty()) {
            return AppResult.Failure(AppErrorType.VALIDATION, "Please fix the highlighted fields", fieldErrors)
        }

        val groupId = Ids.newId()
        val group = GroupEntity(
            id = groupId,
            name = name.trim(),
            description = description.trim(),
            ownerId = actor.id,
            currency = "KES",
            contributionAmountMinor = contributionAmountMinor,
            frequency = frequency.name,
            customIntervalDays = if (frequency == ContributionFrequency.CUSTOM) customIntervalDays else 0,
            memberLimit = memberLimit,
            startEpochDay = startEpochDay,
            status = GroupStatus.FORMING.name,
            payoutOrderMethod = payoutOrderMethod.name,
            joinPolicy = joinPolicy.name,
            invitationExpiryDays = invitationExpiryDays,
            createdAtMillis = now()
        )
        db.groupDao().insert(group)

        val ownerMembership = MembershipEntity(
            id = Ids.newId(),
            groupId = groupId,
            userId = actor.id,
            role = MembershipRole.OWNER.name,
            status = MembershipStatus.ACTIVE.name,
            payoutOrder = 1,
            joinedAtMillis = now()
        )
        db.membershipDao().insert(ownerMembership)

        createInvitation(groupId, invitationExpiryDays)
        audit.record(
            groupId = groupId,
            entityType = "group",
            entityId = groupId,
            eventType = "GROUP_CREATED",
            actorUserId = actor.id,
            metadata = """{"name":"${group.name}","amountMinor":$contributionAmountMinor}"""
        )
        return AppResult.Success(group.toDomain(memberCount = 1))
    }

    // ---------- join ----------

    suspend fun joinGroup(inviteCode: String): AppResult<Membership> {
        val actor = auth.currentUser()
        val code = inviteCode.trim().uppercase()
        Validators.validateInviteCode(code)?.let {
            return AppResult.Failure(AppErrorType.VALIDATION, it, mapOf("code" to it))
        }
        val invitation = db.invitationDao().findByCode(code)
            ?: return AppResult.Failure(AppErrorType.NOT_FOUND, "That invitation code is not valid")
        if (invitation.status != "ACTIVE") {
            return AppResult.Failure(AppErrorType.CONFLICT, "This invitation is no longer active")
        }
        if (invitation.expiresAtMillis < now()) {
            db.invitationDao().update(invitation.copy(status = "EXPIRED"))
            return AppResult.Failure(AppErrorType.CONFLICT, "This invitation has expired")
        }
        if (invitation.usedCount >= invitation.maxUses) {
            db.invitationDao().update(invitation.copy(status = "EXHAUSTED"))
            return AppResult.Failure(AppErrorType.CONFLICT, "This invitation has already been used")
        }

        val group = db.groupDao().findById(invitation.groupId)
            ?: return AppResult.Failure(AppErrorType.NOT_FOUND, "Group not found")
        if (group.status == GroupStatus.COMPLETED.name || group.status == GroupStatus.PAUSED.name) {
            return AppResult.Failure(AppErrorType.CONFLICT, "This group is not accepting new members")
        }
        if (db.membershipDao().countJoinedOrPending(group.id) >= group.memberLimit) {
            return AppResult.Failure(AppErrorType.CONFLICT, "This group is full")
        }
        db.membershipDao().findByGroupAndUser(group.id, actor.id)?.let { existing ->
            return when (MembershipStatus.fromWire(existing.status)) {
                MembershipStatus.PENDING ->
                    AppResult.Failure(AppErrorType.CONFLICT, "Your request to join is pending approval")
                MembershipStatus.ACTIVE ->
                    AppResult.Failure(AppErrorType.CONFLICT, "You are already a member of this group")
                MembershipStatus.REJECTED, MembershipStatus.REMOVED ->
                    AppResult.Failure(AppErrorType.PERMISSION_DENIED, "You cannot rejoin this group")
            }
        }

        val policy = JoinPolicy.fromWire(group.joinPolicy)
        val status = if (policy == JoinPolicy.OPEN) MembershipStatus.ACTIVE else MembershipStatus.PENDING
        val membership = MembershipEntity(
            id = Ids.newId(),
            groupId = group.id,
            userId = actor.id,
            role = MembershipRole.MEMBER.name,
            status = status.name,
            payoutOrder = null,
            joinedAtMillis = now()
        )
        db.membershipDao().insert(membership)
        db.invitationDao().update(invitation.copy(usedCount = invitation.usedCount + 1))

        audit.record(
            groupId = group.id,
            entityType = "membership",
            entityId = membership.id,
            eventType = if (status == MembershipStatus.ACTIVE) "MEMBER_JOINED" else "MEMBER_REQUESTED",
            actorUserId = actor.id,
            metadata = """{"user":"${actor.fullName}"}"""
        )
        notifyGroupAdmins(
            group = group,
            excludeUserId = actor.id,
            type = NotificationType.MEMBER_JOINED,
            title = "New member",
            message = if (status == MembershipStatus.ACTIVE)
                "${actor.fullName} joined ${group.name}"
            else
                "${actor.fullName} requested to join ${group.name}"
        )
        if (status == MembershipStatus.PENDING) {
            notifyUser(
                userId = actor.id,
                type = NotificationType.GROUP_INVITE,
                title = "Awaiting approval",
                message = "Your request to join ${group.name} is pending approval",
                groupId = group.id
            )
        }
        return AppResult.Success(membership.toDomain(actor.fullName, actor.phone, actor.email))
    }

    // ---------- membership administration ----------

    suspend fun decideMembership(
        groupId: String,
        membershipId: String,
        approve: Boolean
    ): AppResult<Membership> {
        val actor = auth.currentUser()
        requireAdmin(groupId, actor.id)
        val membership = db.membershipDao().findById(membershipId)
            ?: return AppResult.Failure(AppErrorType.NOT_FOUND, "Membership not found")
        if (membership.groupId != groupId) {
            return AppResult.Failure(AppErrorType.NOT_FOUND, "Membership not found")
        }
        if (membership.status != MembershipStatus.PENDING.name) {
            return AppResult.Failure(AppErrorType.CONFLICT, "This request has already been decided")
        }
        val group = db.groupDao().findById(groupId)
            ?: return AppResult.Failure(AppErrorType.NOT_FOUND, "Group not found")
        if (approve && db.membershipDao().countJoinedOrPending(groupId) > group.memberLimit) {
            return AppResult.Failure(AppErrorType.CONFLICT, "Approving would exceed the member limit")
        }
        val newStatus = if (approve) MembershipStatus.ACTIVE else MembershipStatus.REJECTED
        val updated = membership.copy(status = newStatus.name)
        db.membershipDao().update(updated)

        audit.record(
            groupId = groupId,
            entityType = "membership",
            entityId = membershipId,
            eventType = if (approve) "MEMBER_APPROVED" else "MEMBER_REJECTED",
            actorUserId = actor.id,
            metadata = """{"targetUser":"${membership.userId}"}"""
        )
        val target = db.userDao().findById(membership.userId)
        if (target != null) {
            notifyUser(
                userId = target.id,
                type = NotificationType.MEMBER_APPROVED,
                title = if (approve) "You are in!" else "Request declined",
                message = if (approve) "You are now a member of ${group.name}"
                else "Your request to join ${group.name} was declined",
                groupId = groupId
            )
        }
        return AppResult.Success(
            updated.toDomain(target?.fullName ?: "", target?.phone ?: "", target?.email ?: "")
        )
    }

    suspend fun removeMember(groupId: String, membershipId: String): AppResult<Unit> {
        val actor = auth.currentUser()
        requireAdmin(groupId, actor.id)
        val membership = db.membershipDao().findById(membershipId)
            ?: return AppResult.Failure(AppErrorType.NOT_FOUND, "Membership not found")
        if (membership.groupId != groupId) {
            return AppResult.Failure(AppErrorType.NOT_FOUND, "Membership not found")
        }
        if (membership.role == MembershipRole.OWNER.name) {
            return AppResult.Failure(AppErrorType.PERMISSION_DENIED, "The group owner cannot be removed")
        }
        if (membership.userId == actor.id) {
            return AppResult.Failure(AppErrorType.PERMISSION_DENIED, "You cannot remove yourself here")
        }
        if (membership.status == MembershipStatus.REMOVED.name) {
            return AppResult.Failure(AppErrorType.CONFLICT, "This member has already been removed")
        }
        // Soft-remove so past contributions and history stay attributable.
        db.membershipDao().update(membership.copy(status = MembershipStatus.REMOVED.name))
        audit.record(
            groupId = groupId,
            entityType = "membership",
            entityId = membershipId,
            eventType = "MEMBER_REMOVED",
            actorUserId = actor.id,
            metadata = """{"targetUser":"${membership.userId}"}"""
        )
        val group = db.groupDao().findById(groupId)
        if (group != null) {
            notifyUser(
                userId = membership.userId,
                type = NotificationType.SCHEDULE_CHANGED,
                title = "Membership ended",
                message = "You were removed from ${group.name}",
                groupId = groupId
            )
        }
        return AppResult.Success(Unit)
    }

    suspend fun setPayoutOrder(groupId: String, orderedMembershipIds: List<String>): AppResult<List<Membership>> {
        val actor = auth.currentUser()
        requireAdmin(groupId, actor.id)
        val group = db.groupDao().findById(groupId)
            ?: return AppResult.Failure(AppErrorType.NOT_FOUND, "Group not found")
        if (group.status != GroupStatus.FORMING.name) {
            return AppResult.Failure(AppErrorType.CONFLICT, "Payout order is locked once the group has started")
        }
        val active = db.membershipDao().listActiveByGroup(groupId).map { it.id }.toSet()
        if (orderedMembershipIds.size != active.size || orderedMembershipIds.toSet() != active) {
            return AppResult.Failure(
                AppErrorType.VALIDATION,
                "The payout order must include every active member exactly once"
            )
        }
        orderedMembershipIds.forEachIndexed { index, id ->
            db.membershipDao().setPayoutOrder(id, index + 1)
        }
        audit.record(
            groupId = groupId,
            entityType = "group",
            entityId = groupId,
            eventType = "PAYOUT_ORDER_SET",
            actorUserId = actor.id,
            metadata = """{"order":${orderedMembershipIds.size}}"""
        )
        return AppResult.Success(listMembersInternal(groupId))
    }

    /** Locks the order and generates the first rotation. Owner/admin only. */
    suspend fun startGroup(groupId: String): AppResult<SavingsGroup> {
        val actor = auth.currentUser()
        requireAdmin(groupId, actor.id)
        val group = db.groupDao().findById(groupId)
            ?: return AppResult.Failure(AppErrorType.NOT_FOUND, "Group not found")
        if (group.status != GroupStatus.FORMING.name) {
            return AppResult.Failure(AppErrorType.CONFLICT, "This group has already started")
        }
        val activeMembers = db.membershipDao().listActiveByGroup(groupId)
        if (activeMembers.size < 2) {
            return AppResult.Failure(
                AppErrorType.CONFLICT,
                "A rotation needs at least 2 active members before it can start"
            )
        }
        if (activeMembers.size > group.memberLimit) {
            return AppResult.Failure(AppErrorType.CONFLICT, "Member count exceeds the configured limit")
        }

        // Payout order: admin-assigned order if present, otherwise join order.
        val ordered = activeMembers.sortedWith(
            compareBy(nullsLast()) { m: MembershipEntity -> m.payoutOrder }
                .thenBy { it.joinedAtMillis }
        )
        ordered.forEachIndexed { index, m -> db.membershipDao().setPayoutOrder(m.id, index + 1) }

        val today = TimeFormatter.todayEpochDay()
        val baseStart = maxOf(group.startEpochDay, today)
        val planned = CyclePlanner.plan(
            orderedMembershipIds = ordered.map { it.id },
            frequency = ContributionFrequency.fromWire(group.frequency),
            customIntervalDays = group.customIntervalDays,
            contributionAmountMinor = group.contributionAmountMinor,
            baseStartEpochDay = baseStart,
            nowMillis = now(),
            dayStartToMillis = { day -> TimeFormatter.epochMillisAtStartOfDay(day) }
        )

        planned.forEachIndexed { index, p ->
            val cycleId = Ids.newId()
            db.cycleDao().insert(
                CycleEntity(
                    id = cycleId,
                    groupId = groupId,
                    cycleNumber = p.cycleNumber,
                    startEpochDay = p.startEpochDay,
                    endEpochDay = p.endEpochDay,
                    contributionDeadlineEpochDay = p.deadlineEpochDay,
                    recipientMembershipId = p.recipientMembershipId,
                    expectedTotalMinor = p.expectedTotalMinor,
                    status = if (index == 0) CycleStatus.ACTIVE.name else CycleStatus.UPCOMING.name,
                    scheduledPayoutAtMillis = p.scheduledPayoutAtMillis,
                    actualPayoutAtMillis = null
                )
            )
            val recipient = ordered.first { it.id == p.recipientMembershipId }
            db.payoutDao().insert(
                PayoutEntity(
                    id = Ids.newId(),
                    groupId = groupId,
                    cycleId = cycleId,
                    cycleNumber = p.cycleNumber,
                    recipientUserId = recipient.userId,
                    amountMinor = p.expectedTotalMinor,
                    currency = group.currency,
                    status = PayoutStatus.SCHEDULED.name,
                    reference = Ids.newReference("PAY"),
                    scheduledAtMillis = p.scheduledPayoutAtMillis,
                    completedAtMillis = null
                )
            )
        }

        db.groupDao().setStatus(groupId, GroupStatus.ACTIVE.name)
        audit.record(
            groupId = groupId,
            entityType = "group",
            entityId = groupId,
            eventType = "GROUP_STARTED",
            actorUserId = actor.id,
            metadata = """{"cycles":${planned.size},"members":${ordered.size}}"""
        )
        activeMembers.forEach { m ->
            if (m.userId != actor.id) {
                notifyUser(
                    userId = m.userId,
                    type = NotificationType.SCHEDULE_CHANGED,
                    title = "${group.name} has started",
                    message = "The payout schedule is now locked. Cycle 1 is open for contributions.",
                    groupId = groupId
                )
            }
        }
        return AppResult.Success(group.copy(status = GroupStatus.ACTIVE.name).toDomain(ordered.size))
    }

    // ---------- settings ----------

    suspend fun updateGroupSettings(
        groupId: String,
        name: String?,
        description: String?,
        memberLimit: Int?,
        joinPolicy: JoinPolicy?
    ): AppResult<SavingsGroup> {
        val actor = auth.currentUser()
        requireAdmin(groupId, actor.id)
        val group = db.groupDao().findById(groupId)
            ?: return AppResult.Failure(AppErrorType.NOT_FOUND, "Group not found")

        val fieldErrors = mutableMapOf<String, String>()
        val newName = name?.trim() ?: group.name
        val newDescription = description?.trim() ?: group.description
        Validators.validateGroupName(newName)?.let { fieldErrors["name"] = it }
        Validators.validateDescription(newDescription)?.let { fieldErrors["description"] = it }

        val activeCount = db.groupDao().activeMemberCount(groupId)
        val newLimit = memberLimit ?: group.memberLimit
        Validators.validateMemberLimit(newLimit)?.let { fieldErrors["memberLimit"] = it }
        if (newLimit < activeCount) {
            fieldErrors["memberLimit"] = "Cannot be below the current active members ($activeCount)"
        }
        if (fieldErrors.isNotEmpty()) {
            return AppResult.Failure(AppErrorType.VALIDATION, "Please fix the highlighted fields", fieldErrors)
        }

        db.groupDao().updateSettings(
            id = groupId,
            name = newName,
            description = newDescription,
            memberLimit = newLimit,
            joinPolicy = (joinPolicy ?: JoinPolicy.fromWire(group.joinPolicy)).name
        )
        audit.record(
            groupId = groupId,
            entityType = "group",
            entityId = groupId,
            eventType = "GROUP_SETTINGS_UPDATED",
            actorUserId = actor.id,
            metadata = """{"memberLimit":$newLimit}"""
        )
        val updated = db.groupDao().findById(groupId)!!
        return AppResult.Success(updated.toDomain(activeCount))
    }

    // ---------- invitations ----------

    suspend fun createInvitation(groupId: String, expiryDays: Int): Invitation {
        repeat(5) {
            val candidate = Ids.newInviteCode()
            if (db.invitationDao().findByCode(candidate) == null) {
                val invitation = InvitationEntity(
                    id = Ids.newId(),
                    groupId = groupId,
                    code = candidate,
                    expiresAtMillis = now() + expiryDays * 24L * 60 * 60 * 1000,
                    maxUses = Int.MAX_VALUE,
                    usedCount = 0,
                    status = "ACTIVE"
                )
                db.invitationDao().insert(invitation)
                return invitation.toDomain()
            }
        }
        throw AppException(AppErrorType.UNKNOWN, "Could not generate an invitation code. Try again.")
    }

    suspend fun getInvitation(groupId: String): AppResult<Invitation> {
        val actor = auth.currentUser()
        db.membershipDao().findByGroupAndUser(groupId, actor.id)
            ?: return AppResult.Failure(AppErrorType.PERMISSION_DENIED, "You are not a member of this group")
        val group = db.groupDao().findById(groupId)
            ?: return AppResult.Failure(AppErrorType.NOT_FOUND, "Group not found")
        val existing = db.invitationDao().latestForGroup(groupId)
            ?: return AppResult.Success(createInvitation(groupId, group.invitationExpiryDays))
        if (existing.expiresAtMillis < now() || existing.status != "ACTIVE") {
            return AppResult.Success(createInvitation(groupId, group.invitationExpiryDays))
        }
        return AppResult.Success(existing.toDomain())
    }

    // ---------- reads ----------

    suspend fun listMyGroups(): AppResult<List<SavingsGroup>> {
        val actor = auth.currentUser()
        return AppResult.Success(
            db.groupDao().listForUser(actor.id).map { g ->
                g.toDomain(db.groupDao().activeMemberCount(g.id))
            }
        )
    }

    suspend fun getGroup(groupId: String): AppResult<SavingsGroup> {
        val actor = auth.currentUser()
        val group = db.groupDao().findById(groupId)
            ?: return AppResult.Failure(AppErrorType.NOT_FOUND, "Group not found")
        db.membershipDao().findByGroupAndUser(groupId, actor.id)
            ?: return AppResult.Failure(AppErrorType.PERMISSION_DENIED, "You are not a member of this group")
        return AppResult.Success(group.toDomain(db.groupDao().activeMemberCount(groupId)))
    }

    suspend fun listMembers(groupId: String): AppResult<List<Membership>> {
        val actor = auth.currentUser()
        db.membershipDao().findByGroupAndUser(groupId, actor.id)
            ?: return AppResult.Failure(AppErrorType.PERMISSION_DENIED, "You are not a member of this group")
        return AppResult.Success(listMembersInternal(groupId))
    }

    private suspend fun listMembersInternal(groupId: String): List<Membership> =
        db.membershipDao().listByGroup(groupId).map { m ->
            val u = db.userDao().findById(m.userId)
            m.toDomain(u?.fullName ?: "", u?.phone ?: "", u?.email ?: "")
        }

    // ---------- helpers ----------

    suspend fun requireAdmin(groupId: String, userId: String) {
        val membership = db.membershipDao().findByGroupAndUser(groupId, userId)
        if (membership == null || membership.status != MembershipStatus.ACTIVE.name) {
            throw AppException(AppErrorType.PERMISSION_DENIED, "You are not an active member of this group")
        }
        if (!MembershipRole.fromWire(membership.role).isAdminLike) {
            throw AppException(AppErrorType.PERMISSION_DENIED, "Only the group owner/admin can do this")
        }
    }

    suspend fun requireMember(groupId: String, userId: String) {
        val membership = db.membershipDao().findByGroupAndUser(groupId, userId)
        if (membership == null || membership.status != MembershipStatus.ACTIVE.name) {
            throw AppException(AppErrorType.PERMISSION_DENIED, "You are not an active member of this group")
        }
    }

    suspend fun notifyUser(userId: String, type: NotificationType, title: String, message: String, groupId: String?) {
        db.notificationDao().insert(
            NotificationEntity(
                id = Ids.newId(),
                userId = userId,
                type = type.name,
                title = title,
                message = message,
                isRead = false,
                createdAtMillis = now(),
                groupId = groupId
            )
        )
    }

    suspend fun notifyGroupAdmins(
        group: GroupEntity,
        excludeUserId: String,
        type: NotificationType,
        title: String,
        message: String
    ) {
        db.membershipDao().listByGroup(groupId = group.id)
            .filter { it.userId != excludeUserId && MembershipRole.fromWire(it.role).isAdminLike }
            .forEach { notifyUser(it.userId, type, title, message, group.id) }
    }

    // ---------- entity -> domain mappers ----------

    fun GroupEntity.toDomain(memberCount: Int) = SavingsGroup(
        id = id,
        name = name,
        description = description,
        ownerId = ownerId,
        currency = currency,
        contributionAmountMinor = contributionAmountMinor,
        contributionFrequency = ContributionFrequency.fromWire(frequency),
        customIntervalDays = customIntervalDays,
        memberLimit = memberLimit,
        memberCount = memberCount,
        startEpochDay = startEpochDay,
        status = GroupStatus.fromWire(status),
        payoutOrderMethod = PayoutOrderMethod.fromWire(payoutOrderMethod),
        joinPolicy = JoinPolicy.fromWire(joinPolicy),
        invitationExpiryDays = invitationExpiryDays,
        createdAtMillis = createdAtMillis
    )

    fun MembershipEntity.toDomain(userName: String, userPhone: String, userEmail: String) = Membership(
        id = id,
        groupId = groupId,
        userId = userId,
        role = MembershipRole.fromWire(role),
        status = MembershipStatus.fromWire(status),
        payoutOrder = payoutOrder,
        joinedAtMillis = joinedAtMillis,
        userName = userName,
        userPhone = userPhone,
        userEmail = userEmail
    )

    fun InvitationEntity.toDomain() = Invitation(
        id = id,
        groupId = groupId,
        code = code,
        expiresAtMillis = expiresAtMillis,
        maxUses = maxUses,
        usedCount = usedCount,
        status = status
    )
}
