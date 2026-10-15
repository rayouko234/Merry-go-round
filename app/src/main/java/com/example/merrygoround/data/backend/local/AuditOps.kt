package com.example.merrygoround.data.backend.local

import com.example.merrygoround.core.security.Ids
import com.example.merrygoround.data.local.AppDatabase
import com.example.merrygoround.data.local.AuditEventEntity
import com.example.merrygoround.domain.model.AuditEvent

/**
 * Append-only audit trail. Financial and administrative actions must never be
 * erased — reversal/new events are added instead (brief §5).
 */
internal class AuditOps(
    private val db: AppDatabase,
    private val now: () -> Long
) {

    suspend fun record(
        groupId: String?,
        entityType: String,
        entityId: String,
        eventType: String,
        actorUserId: String?,
        metadata: String = ""
    ) {
        db.auditEventDao().insert(
            AuditEventEntity(
                id = Ids.newId(),
                groupId = groupId,
                entityType = entityType,
                entityId = entityId,
                eventType = eventType,
                actorUserId = actorUserId,
                timestampMillis = now(),
                metadata = metadata
            )
        )
    }

    suspend fun listByGroup(groupId: String): List<AuditEvent> =
        db.auditEventDao().listByGroup(groupId).map { it.toDomain(db) }

    suspend fun listRecent(limit: Int): List<AuditEvent> =
        db.auditEventDao().listRecent(limit).map { it.toDomain(db) }

    private suspend fun AuditEventEntity.toDomain(db: AppDatabase): AuditEvent {
        val actor = actorUserId?.let { db.userDao().findById(it) }
        return AuditEvent(
            id = id,
            groupId = groupId,
            entityType = entityType,
            entityId = entityId,
            eventType = eventType,
            actorUserId = actorUserId,
            actorName = actor?.fullName ?: "System",
            timestampMillis = timestampMillis,
            metadata = metadata
        )
    }
}
