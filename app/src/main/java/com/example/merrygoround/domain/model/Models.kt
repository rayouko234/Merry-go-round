package com.example.merrygoround.domain.model

/**
 * Domain models shared conceptually with the REST contract (docs/API.md).
 * All money values are integer minor units with an explicit currency code.
 * All timestamps are epoch millis UTC unless the field name says EpochDay.
 */

data class User(
    val id: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val status: String = "ACTIVE",
    val createdAtMillis: Long = 0L
)

data class SavingsGroup(
    val id: String,
    val name: String,
    val description: String,
    val ownerId: String,
    val currency: String,
    val contributionAmountMinor: Long,
    val contributionFrequency: ContributionFrequency,
    /** Interval length in days when frequency == CUSTOM (ignored otherwise). */
    val customIntervalDays: Int = 0,
    val memberLimit: Int,
    val memberCount: Int,
    val startEpochDay: Long,
    val status: GroupStatus,
    val payoutOrderMethod: PayoutOrderMethod,
    val joinPolicy: JoinPolicy,
    val invitationExpiryDays: Int,
    val createdAtMillis: Long = 0L,
    /** The current user's membership state in this group (PENDING = awaiting owner approval). */
    val membershipStatus: MembershipStatus = MembershipStatus.ACTIVE
) {
    val isForming: Boolean get() = status == GroupStatus.FORMING
    val hasSpace: Boolean get() = memberCount < memberLimit
}

data class Membership(
    val id: String,
    val groupId: String,
    val userId: String,
    val role: MembershipRole,
    val status: MembershipStatus,
    /** 1-based position in the payout rotation; null until the order is locked. */
    val payoutOrder: Int?,
    val joinedAtMillis: Long,
    val userName: String = "",
    val userPhone: String = "",
    val userEmail: String = ""
)

data class Invitation(
    val id: String,
    val groupId: String,
    val code: String,
    val expiresAtMillis: Long,
    val maxUses: Int,
    val usedCount: Int,
    val status: String // ACTIVE | EXHAUSTED | EXPIRED | REVOKED
) {
    val isUsable: Boolean
        get() = status.equals("ACTIVE", ignoreCase = true) && usedCount < maxUses
}

/**
 * One rotation slot: a period during which members owe the fixed contribution
 * and exactly one designated recipient is scheduled to be paid out.
 */
data class Cycle(
    val id: String,
    val groupId: String,
    val cycleNumber: Int,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val contributionDeadlineEpochDay: Long,
    val recipientMembershipId: String,
    val recipientUserId: String,
    val recipientName: String,
    val expectedTotalMinor: Long,
    val status: CycleStatus,
    val scheduledPayoutAtMillis: Long?,
    val actualPayoutAtMillis: Long?
)

data class Contribution(
    val id: String,
    val groupId: String,
    val cycleId: String,
    val memberId: String,
    val userId: String,
    val userName: String,
    val amountMinor: Long,
    val currency: String,
    val status: ContributionStatus,
    val verification: ContributionVerification,
    val reference: String,
    val idempotencyKey: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val note: String = "",
    val failureReason: String = ""
)

data class Payout(
    val id: String,
    val groupId: String,
    val cycleId: String,
    val cycleNumber: Int,
    val recipientUserId: String,
    val recipientName: String,
    val amountMinor: Long,
    val currency: String,
    val status: PayoutStatus,
    val reference: String,
    val scheduledAtMillis: Long,
    val completedAtMillis: Long?
)

/** Append-only audit trail entry for financial/administrative actions. */
data class AuditEvent(
    val id: String,
    val groupId: String?,
    val entityType: String,
    val entityId: String,
    val eventType: String,
    val actorUserId: String?,
    val actorName: String,
    val timestampMillis: Long,
    val metadata: String = ""
)

data class AppNotification(
    val id: String,
    val userId: String,
    val type: NotificationType,
    val title: String,
    val message: String,
    val isRead: Boolean,
    val createdAtMillis: Long,
    val groupId: String? = null
)

/**
 * Aggregated numbers shown on the dashboard for the *current* cycle of one group.
 * Expected = activeMembers x fixedAmount. Confirmed only counts CONFIRMED funds.
 */
data class DashboardSummary(
    val groupId: String,
    val groupName: String,
    val currency: String,
    val status: GroupStatus,
    val contributionAmountMinor: Long,
    val memberCount: Int,
    val memberLimit: Int,
    val currentCycleNumber: Int?,
    val cycleStartEpochDay: Long?,
    val cycleEndEpochDay: Long?,
    val deadlineEpochDay: Long?,
    val expectedCycleTotalMinor: Long,
    val confirmedTotalMinor: Long,
    val pendingTotalMinor: Long,
    val failedTotalMinor: Long,
    val reversedTotalMinor: Long,
    val outstandingTotalMinor: Long,
    val paidMemberCount: Int,
    val pendingMemberCount: Int,
    val unpaidMemberCount: Int,
    val nextRecipientName: String?,
    val nextPayoutAtMillis: Long?,
    val myContributionStatus: ContributionStatus?,
    val myContributionId: String?,
    val myDueEpochDay: Long?,
    val isOwner: Boolean
)

data class MemberObligation(
    val userId: String,
    val userName: String,
    val role: MembershipRole,
    val status: ContributionStatus?,
    val verification: ContributionVerification?,
    val paidAmountMinor: Long,
    val dueAmountMinor: Long,
    val updatedAtMillis: Long?
) {
    val isSettled: Boolean get() = status == ContributionStatus.CONFIRMED
}

/** Recent activity row for the Activity screen. */
data class ActivityEntry(
    val id: String,
    val kind: String, // CONTRIBUTION | PAYOUT | MEMBERSHIP | GROUP | CYCLE
    val title: String,
    val subtitle: String,
    val amountMinor: Long?,
    val currency: String?,
    val status: String,
    val timestampMillis: Long,
    val groupId: String?,
    val groupName: String
)
