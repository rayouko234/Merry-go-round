package com.example.merrygoround.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun findById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE email = :email COLLATE NOCASE")
    suspend fun findByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE phone = :phone")
    suspend fun findByPhone(phone: String): UserEntity?

    @Query("SELECT * FROM users WHERE email = :value COLLATE NOCASE OR phone = :value")
    suspend fun findByIdentifier(value: String): UserEntity?

    @Query("UPDATE users SET fullName = :fullName, phone = :phone WHERE id = :id")
    suspend fun updateProfile(id: String, fullName: String, phone: String)
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE token = :token")
    suspend fun findByToken(token: String): SessionEntity?

    @Query("DELETE FROM sessions WHERE token = :token")
    suspend fun delete(token: String)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}

@Dao
interface GroupDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(group: GroupEntity)

    @Update
    suspend fun update(group: GroupEntity)

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun findById(id: String): GroupEntity?

    @Query(
        """SELECT g.* FROM groups g
           INNER JOIN memberships m ON m.groupId = g.id
           WHERE m.userId = :userId AND m.status = 'ACTIVE'
           ORDER BY g.createdAtMillis DESC"""
    )
    suspend fun listForUser(userId: String): List<GroupEntity>

    @Query("SELECT COUNT(*) FROM memberships WHERE groupId = :groupId AND status = 'ACTIVE'")
    suspend fun activeMemberCount(groupId: String): Int

    @Query("UPDATE groups SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)

    @Query(
        """UPDATE groups SET name = :name, description = :description,
           memberLimit = :memberLimit, joinPolicy = :joinPolicy WHERE id = :id"""
    )
    suspend fun updateSettings(id: String, name: String, description: String, memberLimit: Int, joinPolicy: String)
}

@Dao
interface MembershipDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(membership: MembershipEntity)

    @Update
    suspend fun update(membership: MembershipEntity)

    @Delete
    suspend fun delete(membership: MembershipEntity)

    @Query("SELECT * FROM memberships WHERE id = :id")
    suspend fun findById(id: String): MembershipEntity?

    @Query("SELECT * FROM memberships WHERE groupId = :groupId AND userId = :userId")
    suspend fun findByGroupAndUser(groupId: String, userId: String): MembershipEntity?

    @Query(
        """SELECT m.* FROM memberships m
           WHERE m.groupId = :groupId
           ORDER BY CASE m.role WHEN 'OWNER' THEN 0 WHEN 'ADMIN' THEN 1 ELSE 2 END,
                    m.payoutOrder IS NULL, m.payoutOrder, m.joinedAtMillis"""
    )
    suspend fun listByGroup(groupId: String): List<MembershipEntity>

    @Query(
        """SELECT * FROM memberships WHERE groupId = :groupId AND status = 'ACTIVE'
           ORDER BY payoutOrder IS NULL, payoutOrder, joinedAtMillis"""
    )
    suspend fun listActiveByGroup(groupId: String): List<MembershipEntity>

    @Query("SELECT COUNT(*) FROM memberships WHERE groupId = :groupId")
    suspend fun countAll(groupId: String): Int

    @Query("SELECT COUNT(*) FROM memberships WHERE groupId = :groupId AND status IN ('ACTIVE','PENDING')")
    suspend fun countJoinedOrPending(groupId: String): Int

    @Query("UPDATE memberships SET payoutOrder = :order WHERE id = :id")
    suspend fun setPayoutOrder(id: String, order: Int)

    @Query("SELECT userId FROM memberships WHERE groupId = :groupId AND role = 'OWNER'")
    suspend fun ownerUserId(groupId: String): String?

    @Query("SELECT * FROM memberships WHERE userId = :userId ORDER BY joinedAtMillis DESC")
    suspend fun listForUser(userId: String): List<MembershipEntity>
}

@Dao
interface InvitationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(invitation: InvitationEntity)

    @Update
    suspend fun update(invitation: InvitationEntity)

    @Query("SELECT * FROM invitations WHERE code = :code")
    suspend fun findByCode(code: String): InvitationEntity?

    @Query("SELECT * FROM invitations WHERE groupId = :groupId ORDER BY expiresAtMillis DESC LIMIT 1")
    suspend fun latestForGroup(groupId: String): InvitationEntity?
}

@Dao
interface CycleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(cycles: List<CycleEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(cycle: CycleEntity)

    @Update
    suspend fun update(cycle: CycleEntity)

    @Query("SELECT * FROM cycles WHERE id = :id")
    suspend fun findById(id: String): CycleEntity?

    @Query("SELECT * FROM cycles WHERE groupId = :groupId ORDER BY cycleNumber")
    suspend fun listByGroup(groupId: String): List<CycleEntity>

    @Query("SELECT * FROM cycles WHERE groupId = :groupId AND status = 'ACTIVE' LIMIT 1")
    suspend fun findActiveByGroup(groupId: String): CycleEntity?

    @Query("SELECT * FROM cycles WHERE groupId = :groupId AND status = 'UPCOMING' ORDER BY cycleNumber LIMIT 1")
    suspend fun findNextUpcoming(groupId: String): CycleEntity?

    @Query("SELECT * FROM cycles WHERE groupId = :groupId ORDER BY cycleNumber LIMIT 1")
    suspend fun findFirst(groupId: String): CycleEntity?

    @Query("UPDATE cycles SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)

    @Query("UPDATE cycles SET status = :status, actualPayoutAtMillis = :actualAt WHERE id = :id")
    suspend fun setStatusWithPayout(id: String, status: String, actualAt: Long?)

    @Query("SELECT COUNT(*) FROM cycles WHERE groupId = :groupId")
    suspend fun countByGroup(groupId: String): Int
}

data class StatusRollup(val status: String, val cnt: Int, val total: Long?)

@Dao
interface ContributionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(contribution: ContributionEntity)

    @Update
    suspend fun update(contribution: ContributionEntity)

    @Query("SELECT * FROM contributions WHERE id = :id")
    suspend fun findById(id: String): ContributionEntity?

    @Query("SELECT * FROM contributions WHERE idempotencyKey = :key")
    suspend fun findByIdempotencyKey(key: String): ContributionEntity?

    @Query(
        """SELECT * FROM contributions
           WHERE (:groupId IS NULL OR groupId = :groupId)
             AND (:cycleId IS NULL OR cycleId = :cycleId)
             AND (:status IS NULL OR status = :status)
           ORDER BY createdAtMillis DESC"""
    )
    suspend fun list(groupId: String?, cycleId: String?, status: String?): List<ContributionEntity>

    @Query(
        """SELECT * FROM contributions
           WHERE userId = :userId AND cycleId = :cycleId AND status != 'FAILED'
           ORDER BY createdAtMillis DESC LIMIT 1"""
    )
    suspend fun findLatestForUserInCycle(userId: String, cycleId: String): ContributionEntity?

    @Query("SELECT * FROM contributions WHERE cycleId = :cycleId AND status = 'CONFIRMED'")
    suspend fun confirmedInCycle(cycleId: String): List<ContributionEntity>

    @Query("SELECT * FROM contributions WHERE cycleId = :cycleId AND status IN ('PENDING','CONFIRMED')")
    suspend fun activeInCycle(cycleId: String): List<ContributionEntity>

    @Query(
        """SELECT status, COUNT(*) as cnt, SUM(amountMinor) as total FROM contributions
           WHERE cycleId = :cycleId GROUP BY status"""
    )
    suspend fun statusRollupForCycle(cycleId: String): List<StatusRollup>

    @Transaction
    suspend fun insertOrGetExisting(contribution: ContributionEntity): Pair<ContributionEntity, Boolean> {
        val existing = findByIdempotencyKey(contribution.idempotencyKey)
        return if (existing != null) {
            existing to false
        } else {
            insert(contribution)
            contribution to true
        }
    }
}

@Dao
interface PayoutDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(payout: PayoutEntity)

    @Update
    suspend fun update(payout: PayoutEntity)

    @Query("SELECT * FROM payouts WHERE id = :id")
    suspend fun findById(id: String): PayoutEntity?

    @Query("SELECT * FROM payouts WHERE groupId = :groupId ORDER BY cycleNumber")
    suspend fun listByGroup(groupId: String): List<PayoutEntity>

    @Query("SELECT * FROM payouts WHERE cycleId = :cycleId")
    suspend fun findByCycle(cycleId: String): PayoutEntity?

    @Query("UPDATE payouts SET status = :status, completedAtMillis = :completedAt WHERE id = :id")
    suspend fun setStatus(id: String, status: String, completedAt: Long?)
}

@Dao
interface AuditEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AuditEventEntity)

    @Query("SELECT * FROM audit_events WHERE groupId = :groupId ORDER BY timestampMillis DESC")
    suspend fun listByGroup(groupId: String): List<AuditEventEntity>

    @Query("SELECT * FROM audit_events ORDER BY timestampMillis DESC LIMIT :limit")
    suspend fun listRecent(limit: Int): List<AuditEventEntity>

    @Query("SELECT * FROM audit_events WHERE entityId = :entityId ORDER BY timestampMillis")
    suspend fun listByEntity(entityId: String): List<AuditEventEntity>
}

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<NotificationEntity>)

    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY createdAtMillis DESC")
    suspend fun listForUser(userId: String): List<NotificationEntity>

    @Query("SELECT COUNT(*) FROM notifications WHERE userId = :userId AND isRead = 0")
    suspend fun unreadCount(userId: String): Int

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId")
    suspend fun markAllRead(userId: String)
}

