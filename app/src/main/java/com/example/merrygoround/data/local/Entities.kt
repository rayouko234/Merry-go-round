package com.example.merrygoround.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room tables. In MVP "local" mode these ARE the authoritative store (the LocalBackend
 * plays the server role); in "remote" mode they are a read cache for previously
 * fetched data. Money columns are minor-unit Longs; timestamps are epoch millis UTC.
 */

@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true), Index(value = ["phone"], unique = true)])
data class UserEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val email: String,
    val phone: String,
    /** PBKDF2 hash — plaintext passwords are never stored. */
    val passwordHash: String,
    val passwordSalt: String,
    val status: String,
    val createdAtMillis: Long
)

@Entity(tableName = "sessions", indices = [Index(value = ["userId"])])
data class SessionEntity(
    @PrimaryKey val token: String,
    val userId: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long
)

@Entity(
    tableName = "groups",
    indices = [Index(value = ["ownerId"]) ]
)
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val ownerId: String,
    val currency: String,
    val contributionAmountMinor: Long,
    val frequency: String,
    val customIntervalDays: Int,
    val memberLimit: Int,
    val startEpochDay: Long,
    val status: String,
    val payoutOrderMethod: String,
    val joinPolicy: String,
    val invitationExpiryDays: Int,
    val createdAtMillis: Long
)

@Entity(
    tableName = "memberships",
    indices = [
        Index(value = ["groupId", "userId"], unique = true),
        Index(value = ["userId"])
    ]
)
data class MembershipEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val userId: String,
    val role: String,
    val status: String,
    val payoutOrder: Int?,
    val joinedAtMillis: Long
)

@Entity(
    tableName = "invitations",
    indices = [Index(value = ["code"], unique = true), Index(value = ["groupId"])]
)
data class InvitationEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val code: String,
    val expiresAtMillis: Long,
    val maxUses: Int,
    val usedCount: Int,
    val status: String
)

@Entity(
    tableName = "cycles",
    indices = [
        Index(value = ["groupId", "cycleNumber"], unique = true),
        Index(value = ["groupId"])
    ]
)
data class CycleEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val cycleNumber: Int,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val contributionDeadlineEpochDay: Long,
    val recipientMembershipId: String,
    val expectedTotalMinor: Long,
    val status: String,
    val scheduledPayoutAtMillis: Long?,
    val actualPayoutAtMillis: Long?
)

@Entity(
    tableName = "contributions",
    indices = [
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["groupId", "cycleId", "memberId"]),
        Index(value = ["userId"]),
        Index(value = ["groupId"])
    ]
)
data class ContributionEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val cycleId: String,
    val memberId: String,
    val userId: String,
    val amountMinor: Long,
    val currency: String,
    val status: String,
    val verification: String,
    val reference: String,
    val idempotencyKey: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val note: String,
    val failureReason: String
)

@Entity(
    tableName = "payouts",
    indices = [
        Index(value = ["groupId", "cycleId"], unique = true),
        Index(value = ["groupId"])
    ]
)
data class PayoutEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val cycleId: String,
    val cycleNumber: Int,
    val recipientUserId: String,
    val amountMinor: Long,
    val currency: String,
    val status: String,
    val reference: String,
    val scheduledAtMillis: Long,
    val completedAtMillis: Long?
)

@Entity(tableName = "audit_events", indices = [Index(value = ["groupId"]), Index(value = ["timestampMillis"])])
data class AuditEventEntity(
    @PrimaryKey val id: String,
    val groupId: String?,
    val entityType: String,
    val entityId: String,
    val eventType: String,
    val actorUserId: String?,
    val timestampMillis: Long,
    val metadata: String
)

@Entity(tableName = "notifications", indices = [Index(value = ["userId"]), Index(value = ["isRead"])])
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: String,
    val title: String,
    val message: String,
    val isRead: Boolean,
    val createdAtMillis: Long,
    val groupId: String?
)
