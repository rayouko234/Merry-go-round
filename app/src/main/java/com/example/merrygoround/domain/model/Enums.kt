package com.example.merrygoround.domain.model

/** How often members contribute and payouts rotate. */
enum class ContributionFrequency {
    WEEKLY,
    MONTHLY,
    CUSTOM;

    companion object {
        fun fromWire(value: String): ContributionFrequency =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: WEEKLY
    }
}

enum class GroupStatus {
    /** Created, not yet started — collecting members, order can still change. */
    FORMING,
    ACTIVE,
    PAUSED,
    COMPLETED;

    companion object {
        fun fromWire(value: String): GroupStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FORMING
    }
}

/**
 * Explicit contribution lifecycle. A contribution is never a bare boolean.
 * PENDING = initiated/self-reported, awaiting verification.
 * CONFIRMED = verified funds counted toward the cycle.
 * FAILED = rejected/failed attempt, never counted.
 * REVERSED = was confirmed, later reversed via an audited event; history retained.
 */
enum class ContributionStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    REVERSED;

    val countsAsFunds: Boolean get() = this == CONFIRMED

    companion object {
        fun fromWire(value: String): ContributionStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PENDING
    }
}

/** How a contribution's truth was established. */
enum class ContributionVerification {
    /** Member self-reported the payment; not yet counted as confirmed funds. */
    SELF_REPORTED,
    /** Group owner/admin verified the payment. */
    ADMIN_VERIFIED,
    /** Verified by a configured payment provider (future Mode B integration). */
    PROVIDER_VERIFIED;

    companion object {
        fun fromWire(value: String): ContributionVerification =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SELF_REPORTED
    }
}

enum class PayoutStatus {
    SCHEDULED,
    PENDING,
    COMPLETED,
    FAILED;

    companion object {
        fun fromWire(value: String): PayoutStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SCHEDULED
    }
}

enum class CycleStatus {
    UPCOMING,
    ACTIVE,
    COMPLETED;

    companion object {
        fun fromWire(value: String): CycleStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UPCOMING
    }
}

enum class MembershipRole {
    OWNER,
    ADMIN,
    MEMBER;

    val isAdminLike: Boolean get() = this == OWNER || this == ADMIN

    companion object {
        fun fromWire(value: String): MembershipRole =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MEMBER
    }
}

enum class MembershipStatus {
    PENDING,
    ACTIVE,
    REJECTED,
    REMOVED;

    val isActive: Boolean get() = this == ACTIVE

    companion object {
        fun fromWire(value: String): MembershipStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PENDING
    }
}

/** Payout ordering policy — explicit, decided before the first cycle begins. */
enum class PayoutOrderMethod {
    /** Admin assigns/edits the order while the group is FORMING; locked once started. */
    ADMIN_ASSIGNED,
    /** Order of joining decides the rotation; locked once started. */
    JOIN_ORDER;

    companion object {
        fun fromWire(value: String): PayoutOrderMethod =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ADMIN_ASSIGNED
    }
}

/** Who may enter the group with just an invitation code vs needing approval. */
enum class JoinPolicy {
    /** Anyone with a valid code joins immediately. */
    OPEN,
    /** Valid code creates a PENDING membership the owner/admin must approve. */
    APPROVAL_REQUIRED;

    companion object {
        fun fromWire(value: String): JoinPolicy =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: APPROVAL_REQUIRED
    }
}

enum class NotificationType {
    GROUP_INVITE,
    MEMBER_JOINED,
    MEMBER_APPROVED,
    CONTRIBUTION_DUE,
    CONTRIBUTION_CONFIRMED,
    CONTRIBUTION_FAILED,
    PAYOUT_UPCOMING,
    PAYOUT_COMPLETED,
    SCHEDULE_CHANGED,
    GENERIC;

    companion object {
        fun fromWire(value: String): NotificationType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: GENERIC
    }
}
