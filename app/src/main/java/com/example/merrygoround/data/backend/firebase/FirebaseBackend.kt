package com.example.merrygoround.data.backend.firebase

import com.example.merrygoround.core.common.AppErrorType
import com.example.merrygoround.core.common.AppException
import com.example.merrygoround.core.common.AppResult
import com.example.merrygoround.core.common.TimeFormatter
import com.example.merrygoround.core.common.Validators
import com.example.merrygoround.core.datastore.SessionStore
import com.example.merrygoround.core.security.Ids
import com.example.merrygoround.data.backend.BackendApi
import com.example.merrygoround.domain.ContributionStateMachine
import com.example.merrygoround.domain.CyclePlanner
import com.example.merrygoround.domain.model.*
import java.security.MessageDigest

/**
 * Firebase implementation of [BackendApi]: Firebase Auth for accounts and
 * Cloud Firestore as the authoritative database. Rules are enforced here AND
 * again in firestore.rules as the server-side gate (see docs/FIREBASE.md):
 *
 *   users/{uid}
 *   groups/{gid}                               (denormalized member_count)
 *   groups/{gid}/members/{uid}                 (doc id = uid → duplicate join impossible)
 *   groups/{gid}/cycles/{cycleId}
 *   groups/{gid}/contributions/{id}            (id keyed by idempotency hash → retries safe)
 *   groups/{gid}/payouts/{cycleId}             (doc id = cycleId → one payout per cycle)
 *   groups/{gid}/audit/{eventId}
 *   invitations/{code}                         (doc id = code → unique codes)
 *   notifications/{uid}/items/{itemId}
 */
class FirebaseBackend(
    private val identity: FirebaseIdentity,
    private val store: FirebaseStore,
    private val session: SessionStore,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : BackendApi {

    private suspend fun <T> guard(block: suspend () -> AppResult<T>): AppResult<T> = try {
        block()
    } catch (e: AppException) {
        AppResult.Failure(e.type, e.message ?: "Something went wrong", e.fieldErrors)
    } catch (e: Exception) {
        AppResult.Failure(AppErrorType.NETWORK, e.message ?: "Connection failed. Try again.")
    }

    private fun uid(): String = identity.currentUid
        ?: throw AppException(AppErrorType.AUTHENTICATION, "Please sign in to continue")

    private fun str(data: Map<String, Any?>?, key: String, default: String = ""): String =
        data?.get(key) as? String ?: default

    private fun long(data: Map<String, Any?>?, key: String, default: Long = 0L): Long =
        when (val v = data?.get(key)) {
            is Long -> v
            is Int -> v.toLong()
            is Double -> v.toLong()
            else -> default
        }

    private fun int(data: Map<String, Any?>?, key: String, default: Int = 0): Int =
        long(data, key, default.toLong()).toInt()

    private fun bool(data: Map<String, Any?>?, key: String, default: Boolean = false): Boolean =
        data?.get(key) as? Boolean ?: default

    private fun nullableStr(data: Map<String, Any?>?, key: String): String? = data?.get(key) as? String

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private suspend fun requireMember(groupId: String, userId: String) {
        val m = store.get("groups/$groupId/members/$userId")
        if (m == null || str(m, "status") != MembershipStatus.ACTIVE.name) {
            throw AppException(AppErrorType.PERMISSION_DENIED, "You are not an active member of this group")
        }
    }

    private suspend fun requireAdmin(groupId: String, userId: String) {
        val m = store.get("groups/$groupId/members/$userId")
        if (m == null || str(m, "status") != MembershipStatus.ACTIVE.name) {
            throw AppException(AppErrorType.PERMISSION_DENIED, "You are not an active member of this group")
        }
        if (!MembershipRole.fromWire(str(m, "role")).isAdminLike) {
            throw AppException(AppErrorType.PERMISSION_DENIED, "Only the group owner/admin can do this")
        }
    }

    private suspend fun audit(
        groupId: String, entityType: String, entityId: String, eventType: String,
        actorUid: String?, metadata: String = "{}"
    ) {
        store.set(
            "groups/$groupId/audit/${Ids.newId()}",
            mapOf(
                "entity_type" to entityType, "entity_id" to entityId, "event_type" to eventType,
                "actor_uid" to (actorUid ?: ""), "timestamp" to clock(), "metadata" to metadata
            )
        )
    }

    private suspend fun notify(
        targetUid: String, type: NotificationType, title: String, message: String, groupId: String?
    ) {
        store.set(
            "notifications/$targetUid/items/${Ids.newId()}",
            mapOf(
                "type" to type.name, "title" to title, "message" to message,
                "is_read" to false, "group_id" to (groupId ?: ""), "created_at" to clock()
            )
        )
    }

    private suspend fun notifyGroupAdmins(
        groupId: String, excludeUid: String, type: NotificationType, title: String, message: String
    ) {
        store.query("groups/$groupId/members")
            .filter {
                it.data["user_id"] != excludeUid &&
                    MembershipRole.fromWire(str(it.data, "role")).isAdminLike
            }
            .forEach { notify(str(it.data, "user_id"), type, title, message, groupId) }
    }

    // ---------- entity mappers ----------

    private fun toUser(uidValue: String, data: Map<String, Any?>): User = User(
        id = uidValue,
        fullName = str(data, "full_name"),
        email = str(data, "email"),
        phone = str(data, "phone"),
        status = str(data, "status", "ACTIVE"),
        createdAtMillis = long(data, "created_at")
    )

    private fun toGroup(groupId: String, data: Map<String, Any?>, memberCount: Int): SavingsGroup = SavingsGroup(
        id = groupId,
        name = str(data, "name"),
        description = str(data, "description"),
        ownerId = str(data, "owner_id"),
        currency = str(data, "currency", "KES"),
        contributionAmountMinor = long(data, "contribution_amount_minor"),
        contributionFrequency = ContributionFrequency.fromWire(str(data, "frequency")),
        customIntervalDays = int(data, "custom_interval_days"),
        memberLimit = int(data, "member_limit"),
        memberCount = memberCount,
        startEpochDay = long(data, "start_epoch_day"),
        status = GroupStatus.fromWire(str(data, "status")),
        payoutOrderMethod = PayoutOrderMethod.fromWire(str(data, "payout_order_method")),
        joinPolicy = JoinPolicy.fromWire(str(data, "join_policy")),
        invitationExpiryDays = int(data, "invitation_expiry_days", 7),
        createdAtMillis = long(data, "created_at")
    )

    private fun toMembership(membershipId: String, data: Map<String, Any?>, name: String = "", phone: String = "", email: String = ""): Membership = Membership(
        id = str(data, "user_id").ifEmpty { membershipId },
        groupId = str(data, "group_id"),
        userId = str(data, "user_id"),
        role = MembershipRole.fromWire(str(data, "role")),
        status = MembershipStatus.fromWire(str(data, "status")),
        payoutOrder = if (data?.containsKey("payout_order") == true) int(data, "payout_order") else null,
        joinedAtMillis = long(data, "joined_at"),
        userName = name, userPhone = phone, userEmail = email
    )

    // ---------- auth ----------

    override suspend fun register(fullName: String, email: String, phone: String, password: String): AppResult<User> =
        guard { AppResult.Success(registerInner(fullName, email, phone, password)) }

    private suspend fun registerInner(fullName: String, email: String, phone: String, password: String): User {
        val fieldErrors = mutableMapOf<String, String>()
        Validators.validateFullName(fullName)?.let { fieldErrors["fullName"] = it }
        Validators.validateEmail(email)?.let { fieldErrors["email"] = it }
        Validators.validatePhone(phone)?.let { fieldErrors["phone"] = it }
        Validators.validatePassword(password)?.let { fieldErrors["password"] = it }
        if (fieldErrors.isNotEmpty()) {
            throw AppException(AppErrorType.VALIDATION, "Please fix the highlighted fields", fieldErrors)
        }
        val normEmail = email.trim().lowercase()
        val normPhone = Validators.normalizePhone(phone)

        val newUid = try {
            identity.createUser(normEmail, password)
        } catch (e: Exception) {
            if (e.message?.contains("already", ignoreCase = true) == true) {
                throw AppException(
                    AppErrorType.CONFLICT, "An account with this email already exists",
                    mapOf("email" to "This email is already registered")
                )
            }
            throw e
        }
        // Firestore rules require a signed-in user to read profiles, so the phone
        // uniqueness check must run after the Auth user exists.
        try {
            if (store.query("users", "phone" to normPhone).isNotEmpty()) {
                throw AppException(
                    AppErrorType.CONFLICT, "An account with this phone number already exists",
                    mapOf("phone" to "This phone number is already registered")
                )
            }
        } catch (e: Exception) {
            runCatching { identity.deleteCurrentUser() }
            throw e
        }
        val profile = mapOf(
            "full_name" to fullName.trim(), "email" to normEmail, "phone" to normPhone,
            "status" to "ACTIVE", "created_at" to clock()
        )
        try {
            store.set("users/$newUid", profile)
        } catch (e: Exception) {
            runCatching { identity.deleteCurrentUser() }
            throw e
        }
        session.saveSession(newUid, newUid)
        return User(newUid, fullName.trim(), normEmail, normPhone, "ACTIVE", clock())
    }

    override suspend fun login(identifier: String, password: String): AppResult<User> =
        guard { AppResult.Success(loginInner(identifier, password)) }

    private suspend fun loginInner(identifier: String, password: String): User {
        if (identifier.isBlank() || password.isBlank()) {
            throw AppException(
                AppErrorType.VALIDATION, "Enter your email/phone and password",
                buildMap {
                    if (identifier.isBlank()) put("identifier", "Required")
                    if (password.isBlank()) put("password", "Required")
                }
            )
        }
        // Firebase Auth signs in with email; phone identifiers resolve to the email first.
        val email = if (identifier.contains("@")) {
            identifier.trim()
        } else {
            val normalized = Validators.normalizePhone(identifier)
            store.query("users", "phone" to normalized).firstOrNull()
                ?.let { str(it.data, "email") }
                ?: throw AppException(AppErrorType.AUTHENTICATION, "Incorrect email/phone or password")
        }
        val signedUid = try {
            identity.signIn(email, password)
        } catch (e: Exception) {
            throw AppException(AppErrorType.AUTHENTICATION, "Incorrect email/phone or password")
        }
        val profile = store.get("users/$signedUid")
            ?: throw AppException(AppErrorType.AUTHENTICATION, "Account profile missing")
        if (str(profile, "status") != "ACTIVE") {
            throw AppException(AppErrorType.AUTHENTICATION, "This account is suspended. Contact support.")
        }
        session.saveSession(signedUid, signedUid)
        return toUser(signedUid, profile)
    }

    override suspend fun logout() = guard {
        identity.signOut()
        session.clear()
        AppResult.Success(Unit)
    }

    override suspend fun me(): AppResult<User> = guard {
        val me = uid()
        val profile = store.get("users/$me")
            ?: throw AppException(AppErrorType.AUTHENTICATION, "Account no longer exists")
        AppResult.Success(toUser(me, profile))
    }

    override suspend fun updateProfile(fullName: String?, phone: String?): AppResult<User> = guard {
        val me = uid()
        val current = store.get("users/$me")
            ?: throw AppException(AppErrorType.AUTHENTICATION, "Account no longer exists")
        val newName = fullName?.trim() ?: str(current, "full_name")
        val newPhone = Validators.normalizePhone(phone ?: str(current, "phone"))
        Validators.validateFullName(newName)?.let {
            throw AppException(AppErrorType.VALIDATION, it, mapOf("fullName" to it))
        }
        Validators.validatePhone(newPhone)?.let {
            throw AppException(AppErrorType.VALIDATION, it, mapOf("phone" to it))
        }
        store.query("users", "phone" to newPhone)
            .firstOrNull { it.path != "users/$me" }?.let {
                throw AppException(
                    AppErrorType.CONFLICT, "That phone number is used by another account",
                    mapOf("phone" to "Already in use")
                )
            }
        store.update("users/$me", mapOf("full_name" to newName, "phone" to newPhone))
        AppResult.Success(toUser(me, current + mapOf("full_name" to newName, "phone" to newPhone)))
    }

    // ---------- groups ----------

    override suspend fun listMyGroups(): AppResult<List<SavingsGroup>> = guard {
        val me = uid()
        val memberDocs = store.queryGroup("members", "user_id" to me)
            .filter { str(it.data, "status") in setOf(MembershipStatus.ACTIVE.name, MembershipStatus.PENDING.name) }
        val groups = memberDocs.mapNotNull { doc ->
            val gid = str(doc.data, "group_id")
            val g = store.get("groups/$gid") ?: return@mapNotNull null
            toGroup(gid, g, int(g, "member_count", 1))
                .copy(membershipStatus = MembershipStatus.fromWire(str(doc.data, "status")))
        }.sortedByDescending { it.createdAtMillis }
        AppResult.Success(groups)
    }

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
    ): AppResult<SavingsGroup> = guard {
        val me = uid()
        val fieldErrors = mutableMapOf<String, String>()
        Validators.validateGroupName(name)?.let { fieldErrors["name"] = it }
        Validators.validateDescription(description)?.let { fieldErrors["description"] = it }
        Validators.validateContributionAmount(contributionAmountMinor)?.let { fieldErrors["amount"] = it }
        Validators.validateMemberLimit(memberLimit)?.let { fieldErrors["memberLimit"] = it }
        Validators.validateStartDate(startEpochDay, TimeFormatter.todayEpochDay())
            ?.let { fieldErrors["startDate"] = it }
        if (frequency == ContributionFrequency.CUSTOM) {
            Validators.validateCustomIntervalDays(customIntervalDays)?.let { fieldErrors["interval"] = it }
        }
        if (invitationExpiryDays !in 1..90) {
            fieldErrors["invitationExpiryDays"] = "Invitation expiry must be 1-90 days"
        }
        if (fieldErrors.isNotEmpty()) {
            throw AppException(AppErrorType.VALIDATION, "Please fix the highlighted fields", fieldErrors)
        }

        val groupId = Ids.newId()
        val groupData = mapOf(
            "name" to name.trim(), "description" to description.trim(), "owner_id" to me,
            "currency" to "KES", "contribution_amount_minor" to contributionAmountMinor,
            "frequency" to frequency.name, "custom_interval_days" to customIntervalDays,
            "member_limit" to memberLimit, "member_count" to 1L,
            "start_epoch_day" to startEpochDay, "status" to GroupStatus.FORMING.name,
            "payout_order_method" to payoutOrderMethod.name, "join_policy" to joinPolicy.name,
            "invitation_expiry_days" to invitationExpiryDays, "created_at" to clock()
        )
        // Phase 1: group + owner membership.
        store.set("groups/$groupId", groupData)
        store.set(
            "groups/$groupId/members/$me",
            mapOf(
                "group_id" to groupId, "user_id" to me, "role" to MembershipRole.OWNER.name,
                "status" to MembershipStatus.ACTIVE.name, "payout_order" to 1,
                "joined_at" to clock(), "invite_code" to ""
            )
        )
        // Phase 2 (separate call so firestore.rules can verify via get() that THIS
        // user owns the group before the invitation code — a batched write could
        // not be validated that way): unique code via doc-id existence check.
        val code = generateUniqueCode()
        store.transaction(listOf("invitations/$code")) { reads ->
            if (reads["invitations/$code"]?.isNotEmpty() == true) {
                throw AppException(AppErrorType.UNKNOWN, "Could not generate an invitation code. Try again.")
            }
            TxOutcome(
                code,
                listOf(
                    TxWrite(
                        "invitations/$code",
                        mapOf(
                            "group_id" to groupId, "status" to "ACTIVE", "used_count" to 0L,
                            "expires_at" to clock() + invitationExpiryDays * 86_400_000L
                        )
                    )
                )
            )
        }
        audit(groupId, "group", groupId, "GROUP_CREATED", me, """{"name":"${groupData["name"]}"}""")
        AppResult.Success(toGroup(groupId, groupData, 1))
    }

    private suspend fun generateUniqueCode(): String {
        repeat(5) {
            val candidate = Ids.newInviteCode()
            if (store.get("invitations/$candidate") == null) return candidate
        }
        throw AppException(AppErrorType.UNKNOWN, "Could not generate an invitation code. Try again.")
    }

    override suspend fun getGroup(groupId: String): AppResult<SavingsGroup> = guard {
        val me = uid()
        requireMember(groupId, me)
        val g = store.get("groups/$groupId")
            ?: throw AppException(AppErrorType.NOT_FOUND, "Group not found")
        AppResult.Success(toGroup(groupId, g, int(g, "member_count", 1)))
    }

    override suspend fun updateGroupSettings(
        groupId: String, name: String?, description: String?, memberLimit: Int?, joinPolicy: JoinPolicy?
    ): AppResult<SavingsGroup> = guard {
        val me = uid()
        requireAdmin(groupId, me)
        val group = store.get("groups/$groupId")
            ?: throw AppException(AppErrorType.NOT_FOUND, "Group not found")
        val fieldErrors = mutableMapOf<String, String>()
        val newName = (name ?: str(group, "name")).trim()
        val newDescription = (description ?: str(group, "description")).trim()
        val newLimit = memberLimit ?: int(group, "member_limit")
        Validators.validateGroupName(newName)?.let { fieldErrors["name"] = it }
        Validators.validateDescription(newDescription)?.let { fieldErrors["description"] = it }
        Validators.validateMemberLimit(newLimit)?.let { fieldErrors["memberLimit"] = it }
        if (newLimit < int(group, "member_count", 1)) {
            fieldErrors["memberLimit"] = "Cannot be below the current active members"
        }
        if (fieldErrors.isNotEmpty()) {
            throw AppException(AppErrorType.VALIDATION, "Please fix the highlighted fields", fieldErrors)
        }
        val patch = mutableMapOf<String, Any?>(
            "name" to newName, "description" to newDescription, "member_limit" to newLimit
        )
        if (joinPolicy != null) patch["join_policy"] = joinPolicy.name
        store.update("groups/$groupId", patch)
        audit(groupId, "group", groupId, "GROUP_SETTINGS_UPDATED", me, """{"member_limit":$newLimit}""")
        val updated = store.get("groups/$groupId")!!
        AppResult.Success(toGroup(groupId, updated, int(updated, "member_count", 1)))
    }

    override suspend fun joinGroup(inviteCode: String): AppResult<Membership> = guard {
        val me = uid()
        val code = inviteCode.trim().uppercase()
        Validators.validateInviteCode(code)?.let {
            throw AppException(AppErrorType.VALIDATION, it, mapOf("code" to it))
        }
        val invitation = store.get("invitations/$code")
            ?: throw AppException(AppErrorType.NOT_FOUND, "That invitation code is not valid")
        if (str(invitation, "status") != "ACTIVE") {
            throw AppException(AppErrorType.CONFLICT, "This invitation is no longer active")
        }
        if (long(invitation, "expires_at") < clock()) {
            // Rules only let joiners bump used_count, so marking expiry is best-effort.
            runCatching { store.update("invitations/$code", mapOf("status" to "EXPIRED")) }
            throw AppException(AppErrorType.CONFLICT, "This invitation has expired")
        }
        val groupId = str(invitation, "group_id")
        val group = store.get("groups/$groupId")
            ?: throw AppException(AppErrorType.NOT_FOUND, "Group not found")
        if (str(group, "status") == GroupStatus.COMPLETED.name || str(group, "status") == GroupStatus.PAUSED.name) {
            throw AppException(AppErrorType.CONFLICT, "This group is not accepting new members")
        }

        // Doc id = uid → the unique-membership constraint is structural, and the
        // transaction keeps capacity counting race-free.
        val memberPath = "groups/$groupId/members/$me"
        store.transaction(listOf(memberPath, "groups/$groupId", "invitations/$code")) { reads ->
            val existing = reads[memberPath]!!
            if (existing.isNotEmpty()) {
                val status = str(existing, "status")
                val message = when (MembershipStatus.fromWire(status)) {
                    MembershipStatus.PENDING -> "Your request to join is pending approval"
                    MembershipStatus.ACTIVE -> "You are already a member of this group"
                    else -> "You cannot rejoin this group"
                }
                val type = if (MembershipStatus.fromWire(status) == MembershipStatus.REJECTED ||
                    MembershipStatus.fromWire(status) == MembershipStatus.REMOVED
                ) AppErrorType.PERMISSION_DENIED else AppErrorType.CONFLICT
                throw AppException(type, message)
            }
            val g = reads["groups/$groupId"]!!
            val inv = reads["invitations/$code"]!!
            if (long(g, "member_count") >= long(g, "member_limit")) {
                throw AppException(AppErrorType.CONFLICT, "This group is full")
            }
            val policy = JoinPolicy.fromWire(str(g, "join_policy"))
            val status = if (policy == JoinPolicy.OPEN) MembershipStatus.ACTIVE else MembershipStatus.PENDING
            val writes = mutableListOf(
                TxWrite(
                    memberPath,
                    mapOf(
                        "group_id" to groupId, "user_id" to me, "role" to MembershipRole.MEMBER.name,
                        "status" to status.name, "payout_order" to null,
                        "joined_at" to clock(), "invite_code" to code
                    )
                ),
                TxWrite("invitations/$code", inv + mapOf("used_count" to (long(inv, "used_count") + 1)))
            )
            if (status == MembershipStatus.ACTIVE) {
                writes += TxWrite("groups/$groupId", g + mapOf("member_count" to (long(g, "member_count") + 1)))
            }
            TxOutcome(status, writes)
        }
        val myName = str(store.get("users/$me"), "full_name")
        audit(groupId, "membership", me, "MEMBER_REQUESTED", me, """{"user":"$myName"}""")
        val saved = store.get(memberPath)!!
        val joinMessage = "$myName requested to join ${str(group, "name")}"
        if (str(saved, "status") == MembershipStatus.ACTIVE.name) {
            notifyGroupAdmins(groupId, me, NotificationType.MEMBER_JOINED, "New member", joinMessage)
        } else {
            // A pending joiner cannot list members, so notify the owner from the group doc.
            notify(str(group, "owner_id"), NotificationType.MEMBER_JOINED, "New member", joinMessage, groupId)
        }
        AppResult.Success(toMembership(me, saved))
    }

    // ---------- membership administration ----------

    override suspend fun listMembers(groupId: String): AppResult<List<Membership>> = guard {
        val me = uid()
        requireMember(groupId, me)
        AppResult.Success(listMembersInternal(groupId))
    }

    private suspend fun listMembersInternal(groupId: String): List<Membership> =
        store.query("groups/$groupId/members")
            .sortedWith(
                compareBy({ MembershipRole.fromWire(str(it.data, "role")).let { r -> if (r == MembershipRole.OWNER) 0 else if (r == MembershipRole.ADMIN) 1 else 2 } },
                    { if (it.data.containsKey("payout_order")) int(it.data, "payout_order") else Int.MAX_VALUE },
                    { long(it.data, "joined_at") })
            )
            .map { doc ->
                val user = store.get("users/${str(doc.data, "user_id")}")
                toMembership(str(doc.data, "user_id"), doc.data,
                    name = str(user, "full_name"), phone = str(user, "phone"), email = str(user, "email"))
            }

    override suspend fun decideMembership(
        groupId: String, membershipId: String, approve: Boolean
    ): AppResult<Membership> = guard {
        val me = uid()
        requireAdmin(groupId, me)
        val memberPath = "groups/$groupId/members/$membershipId"
        store.transaction(listOf(memberPath, "groups/$groupId")) { reads ->
            val member = reads[memberPath]!!
            if (member.isEmpty()) throw AppException(AppErrorType.NOT_FOUND, "Membership not found")
            if (str(member, "status") != MembershipStatus.PENDING.name) {
                throw AppException(AppErrorType.CONFLICT, "This request has already been decided")
            }
            val g = reads["groups/$groupId"]!!
            val writes = mutableListOf(TxWrite(memberPath, member + mapOf("status" to (if (approve) "ACTIVE" else "REJECTED"))))
            if (approve) writes += TxWrite("groups/$groupId", g + mapOf("member_count" to (long(g, "member_count") + 1)))
            TxOutcome(member, writes)
        }
        audit(groupId, "membership", membershipId,
            if (approve) "MEMBER_APPROVED" else "MEMBER_REJECTED", me, "{}")
        val targetName = str(store.get("users/$membershipId"), "full_name")
        notify(membershipId,
            if (approve) NotificationType.MEMBER_APPROVED else NotificationType.MEMBER_APPROVED,
            if (approve) "You are in!" else "Request declined",
            if (approve) "You are now a member of ${str(store.get("groups/$groupId"), "name")}"
            else "Your request to join ${str(store.get("groups/$groupId"), "name")} was declined",
            groupId)
        val saved = store.get(memberPath)!!
        AppResult.Success(toMembership(membershipId, saved, name = targetName))
    }

    override suspend fun removeMember(groupId: String, membershipId: String): AppResult<Unit> = guard {
        val me = uid()
        requireAdmin(groupId, me)
        if (membershipId == me) throw AppException(AppErrorType.PERMISSION_DENIED, "You cannot remove yourself here")
        val memberPath = "groups/$groupId/members/$membershipId"
        store.transaction(listOf(memberPath, "groups/$groupId")) { reads ->
            val member = reads[memberPath]!!
            if (member.isEmpty()) throw AppException(AppErrorType.NOT_FOUND, "Membership not found")
            if (str(member, "role") == MembershipRole.OWNER.name) {
                throw AppException(AppErrorType.PERMISSION_DENIED, "The group owner cannot be removed")
            }
            if (str(member, "status") == MembershipStatus.REMOVED.name) {
                throw AppException(AppErrorType.CONFLICT, "This member has already been removed")
            }
            val g = reads["groups/$groupId"]!!
            val writes = mutableListOf(TxWrite(memberPath, member + mapOf("status" to "REMOVED")))
            if (str(member, "status") == MembershipStatus.ACTIVE.name) {
                writes += TxWrite("groups/$groupId", g + mapOf("member_count" to (long(g, "member_count") - 1)))
            }
            TxOutcome(Unit, writes)
        }
        audit(groupId, "membership", membershipId, "MEMBER_REMOVED", me, "{}")
        notify(membershipId, NotificationType.SCHEDULE_CHANGED, "Membership ended",
            "You were removed from ${str(store.get("groups/$groupId"), "name")}", groupId)
        AppResult.Success(Unit)
    }

    override suspend fun setPayoutOrder(groupId: String, orderedMembershipIds: List<String>): AppResult<List<Membership>> = guard {
        val me = uid()
        requireAdmin(groupId, me)
        val group = store.get("groups/$groupId")
            ?: throw AppException(AppErrorType.NOT_FOUND, "Group not found")
        if (str(group, "status") != GroupStatus.FORMING.name) {
            throw AppException(AppErrorType.CONFLICT, "Payout order is locked once the group has started")
        }
        val active = store.query("groups/$groupId/members")
            .filter { str(it.data, "status") == MembershipStatus.ACTIVE.name }
        if (active.map { str(it.data, "user_id") }.toSet() != orderedMembershipIds.toSet() ||
            active.size != orderedMembershipIds.size
        ) {
            throw AppException(
                AppErrorType.VALIDATION,
                "The payout order must include every active member exactly once"
            )
        }
        orderedMembershipIds.forEachIndexed { index, uidValue ->
            store.update("groups/$groupId/members/$uidValue", mapOf("payout_order" to (index + 1)))
        }
        audit(groupId, "group", groupId, "PAYOUT_ORDER_SET", me, """{"order":${orderedMembershipIds.size}}""")
        AppResult.Success(listMembersInternal(groupId))
    }

    /** Locks the payout order and generates the full rotation. Owner/admin only. */
    override suspend fun startGroup(groupId: String): AppResult<SavingsGroup> = guard {
        val me = uid()
        requireAdmin(groupId, me)
        val group = store.get("groups/$groupId")
            ?: throw AppException(AppErrorType.NOT_FOUND, "Group not found")
        if (str(group, "status") != GroupStatus.FORMING.name) {
            throw AppException(AppErrorType.CONFLICT, "This group has already started")
        }
        val active = store.query("groups/$groupId/members")
            .filter { str(it.data, "status") == MembershipStatus.ACTIVE.name }
            .sortedWith(
                compareBy(
                    { if (it.data.containsKey("payout_order")) int(it.data, "payout_order") else Int.MAX_VALUE },
                    { long(it.data, "joined_at") })
            )
        if (active.size < 2) {
            throw AppException(
                AppErrorType.CONFLICT,
                "A rotation needs at least 2 active members before it can start"
            )
        }
        if (active.size > int(group, "member_limit")) {
            throw AppException(AppErrorType.CONFLICT, "Member count exceeds the configured limit")
        }

        val baseStart = maxOf(long(group, "start_epoch_day"), TimeFormatter.todayEpochDay())
        val planned = CyclePlanner.plan(
            orderedMembershipIds = active.map { str(it.data, "user_id") },
            frequency = ContributionFrequency.fromWire(str(group, "frequency")),
            customIntervalDays = int(group, "custom_interval_days"),
            contributionAmountMinor = long(group, "contribution_amount_minor"),
            baseStartEpochDay = baseStart,
            nowMillis = clock(),
            dayStartToMillis = { day -> TimeFormatter.epochMillisAtStartOfDay(day) }
        )

        // The transaction writes every cycle + payout + the status flip as one unit.
        val cycleDocs = planned.map { Ids.newId() }
        val readPaths =
            listOf("groups/$groupId") + active.map { "groups/$groupId/members/${str(it.data, "user_id")}" }
        store.transaction(readPaths) { reads ->
            val g = reads["groups/$groupId"]!!
            if (str(g, "status") != GroupStatus.FORMING.name) {
                throw AppException(AppErrorType.CONFLICT, "This group has already started")
            }
            val writes = mutableListOf<TxWrite>()
            active.forEachIndexed { index, memberDoc ->
                val uidValue = str(memberDoc.data, "user_id")
                writes += TxWrite(
                    "groups/$groupId/members/$uidValue",
                    memberDoc.data + mapOf("payout_order" to (index + 1))
                )
            }
            planned.forEachIndexed { index, cycle ->
                val cycleId = cycleDocs[index]
                writes += TxWrite(
                    "groups/$groupId/cycles/$cycleId",
                    mapOf(
                        "group_id" to groupId, "cycle_number" to cycle.cycleNumber,
                        "start_epoch_day" to cycle.startEpochDay, "end_epoch_day" to cycle.endEpochDay,
                        "deadline_epoch_day" to cycle.deadlineEpochDay,
                        "recipient_uid" to active[index].data["user_id"],
                        "expected_total_minor" to cycle.expectedTotalMinor,
                        "status" to if (index == 0) CycleStatus.ACTIVE.name else CycleStatus.UPCOMING.name,
                        "scheduled_payout_at" to cycle.scheduledPayoutAtMillis, "actual_payout_at" to 0L
                    )
                )
                writes += TxWrite(
                    "groups/$groupId/payouts/$cycleId",
                    mapOf(
                        "group_id" to groupId, "cycle_id" to cycleId,
                        "cycle_number" to cycle.cycleNumber,
                        "recipient_uid" to active[index].data["user_id"],
                        "amount_minor" to cycle.expectedTotalMinor,
                        "currency" to str(g, "currency", "KES"),
                        "status" to PayoutStatus.SCHEDULED.name,
                        "reference" to Ids.newReference("PAY"),
                        "scheduled_at" to cycle.scheduledPayoutAtMillis, "completed_at" to 0L
                    )
                )
            }
            writes += TxWrite("groups/$groupId", g + mapOf("status" to GroupStatus.ACTIVE.name))
            TxOutcome(Unit, writes)
        }

        audit(groupId, "group", groupId, "GROUP_STARTED", me,
            """{"cycles":${planned.size},"members":${active.size}}""")
        active.forEach { m ->
            val uidValue = str(m.data, "user_id")
            if (uidValue != me) {
                notify(uidValue, NotificationType.SCHEDULE_CHANGED, "${str(group, "name")} has started",
                    "The payout schedule is now locked. Cycle 1 is open for contributions.", groupId)
            }
        }
        AppResult.Success(toGroup(groupId, group + mapOf("status" to GroupStatus.ACTIVE.name), active.size))
    }

    override suspend fun getInvitation(groupId: String): AppResult<Invitation> = guard {
        val me = uid()
        requireMember(groupId, me)
        val group = store.get("groups/$groupId")
            ?: throw AppException(AppErrorType.NOT_FOUND, "Group not found")
        val existing = store.query("invitations", "group_id" to groupId)
            .filter { str(it.data, "status") == "ACTIVE" && long(it.data, "expires_at") >= clock() }
            .maxByOrNull { long(it.data, "expires_at") }
        val code = existing?.path?.substringAfterLast("/")
            ?: generateUniqueCode().also {
                store.set(
                    "invitations/$it",
                    mapOf(
                        "group_id" to groupId, "status" to "ACTIVE", "used_count" to 0L,
                        "expires_at" to clock() + int(group, "invitation_expiry_days", 7) * 86_400_000L
                    )
                )
            }
        val data = store.get("invitations/$code")!!
        AppResult.Success(
            Invitation(
                id = code, groupId = groupId, code = code,
                expiresAtMillis = long(data, "expires_at"),
                maxUses = Int.MAX_VALUE,
                usedCount = int(data, "used_count"),
                status = str(data, "status", "ACTIVE")
            )
        )
    }

    // ---------- cycles & contributions ----------

    private fun toCycle(cycleId: String, data: Map<String, Any?>, recipientName: String): Cycle = Cycle(
        id = cycleId,
        groupId = str(data, "group_id"),
        cycleNumber = int(data, "cycle_number"),
        startEpochDay = long(data, "start_epoch_day"),
        endEpochDay = long(data, "end_epoch_day"),
        contributionDeadlineEpochDay = long(data, "deadline_epoch_day"),
        recipientMembershipId = str(data, "recipient_uid"),
        recipientUserId = str(data, "recipient_uid"),
        recipientName = recipientName,
        expectedTotalMinor = long(data, "expected_total_minor"),
        status = CycleStatus.fromWire(str(data, "status")),
        scheduledPayoutAtMillis = long(data, "scheduled_payout_at").takeIf { it > 0 },
        actualPayoutAtMillis = long(data, "actual_payout_at").takeIf { it > 0 }
    )

    private fun toContribution(path: String, data: Map<String, Any?>, userName: String): Contribution = Contribution(
        id = path.substringAfterLast("/"),
        groupId = str(data, "group_id"),
        cycleId = str(data, "cycle_id"),
        memberId = str(data, "user_id"),
        userId = str(data, "user_id"),
        userName = userName,
        amountMinor = long(data, "amount_minor"),
        currency = str(data, "currency", "KES"),
        status = ContributionStatus.fromWire(str(data, "status")),
        verification = ContributionVerification.fromWire(str(data, "verification")),
        reference = str(data, "reference"),
        idempotencyKey = str(data, "idempotency_key"),
        createdAtMillis = long(data, "created_at"),
        updatedAtMillis = long(data, "updated_at"),
        note = str(data, "note"),
        failureReason = str(data, "failure_reason")
    )

    override suspend fun listCycles(groupId: String): AppResult<List<Cycle>> = guard {
        val me = uid()
        requireMember(groupId, me)
        val cycles = store.query("groups/$groupId/cycles")
            .sortedBy { int(it.data, "cycle_number") }
            .map { doc ->
                val name = str(store.get("users/${str(doc.data, "recipient_uid")}"), "full_name")
                toCycle(doc.path.substringAfterLast("/"), doc.data, name)
            }
        AppResult.Success(cycles)
    }

    override suspend fun listContributions(
        groupId: String?, cycleId: String?, status: ContributionStatus?
    ): AppResult<List<Contribution>> = guard {
        val me = uid()
        val groupIds = if (groupId != null) {
            requireMember(groupId, me)
            listOf(groupId)
        } else {
            store.queryGroup("members", "user_id" to me)
                .filter { str(it.data, "status") == MembershipStatus.ACTIVE.name }
                .map { str(it.data, "group_id") }
        }
        val rows = groupIds.flatMap { gid ->
            store.query("groups/$gid/contributions")
                .filter { row ->
                    (cycleId == null || str(row.data, "cycle_id") == cycleId) &&
                        (status == null || str(row.data, "status") == status.name)
                }
                .map { row -> row to str(row.data, "user_id") }
        }.sortedByDescending { long(it.first.data, "created_at") }
        AppResult.Success(rows.map { (row, uidValue) ->
            toContribution(row.path, row.data, str(store.get("users/$uidValue"), "full_name"))
        })
    }

    override suspend fun recordContribution(
        groupId: String, cycleId: String, amountMinor: Long, idempotencyKey: String, note: String
    ): AppResult<Contribution> = guard {
        val me = uid()
        requireMember(groupId, me)
        val group = store.get("groups/$groupId")
            ?: throw AppException(AppErrorType.NOT_FOUND, "Group not found")
        if (str(group, "status") != GroupStatus.ACTIVE.name) {
            throw AppException(AppErrorType.CONFLICT, "This group is not accepting contributions")
        }
        if (amountMinor != long(group, "contribution_amount_minor")) {
            throw AppException(
                AppErrorType.VALIDATION, "Contribution must match the group's fixed amount",
                mapOf("amount" to "Amount must match the group's fixed contribution")
            )
        }
        if (idempotencyKey.isBlank() || idempotencyKey.length > 100) {
            throw AppException(AppErrorType.VALIDATION, "Missing idempotency key")
        }
        val cycle = store.get("groups/$groupId/cycles/$cycleId")
            ?: throw AppException(AppErrorType.NOT_FOUND, "Cycle not found")
        if (str(cycle, "status") != CycleStatus.ACTIVE.name) {
            throw AppException(AppErrorType.CONFLICT, "Contributions are only accepted for the current cycle")
        }

        // Deterministic doc id from the idempotency key → a retry can never
        // create a second record: the transaction returns the first write's doc.
        val contributionPath = "groups/$groupId/contributions/idem-${sha256(idempotencyKey)}"
        val (outcome, created) = store.transaction(listOf(contributionPath)) { reads ->
            val existing = reads[contributionPath]!!
            if (existing.isNotEmpty()) {
                TxOutcome(Pair(existing, false), emptyList())
            } else {
                val now = clock()
                val data = mapOf(
                    "group_id" to groupId, "cycle_id" to cycleId, "user_id" to me,
                    "amount_minor" to amountMinor, "currency" to str(group, "currency", "KES"),
                    "status" to ContributionStatus.PENDING.name,
                    "verification" to ContributionVerification.SELF_REPORTED.name,
                    "reference" to Ids.newReference("MGR"), "idempotency_key" to idempotencyKey,
                    "note" to note.trim().take(200), "failure_reason" to "",
                    "created_at" to now, "updated_at" to now
                )
                TxOutcome(Pair(data, true), listOf(TxWrite(contributionPath, data)))
            }
        }
        if (created) {
            val cycleNo = int(cycle, "cycle_number")
            val myName = str(store.get("users/$me"), "full_name")
            notifyGroupAdmins(groupId, me, NotificationType.CONTRIBUTION_DUE, "Contribution reported",
                "$myName reported a contribution for cycle $cycleNo")
        }
        AppResult.Success(toContribution(contributionPath, outcome, str(store.get("users/$me"), "full_name")))
    }

    /** Admin verification. Enforces the contribution state machine. */
    override suspend fun confirmContribution(
        contributionId: String, approve: Boolean, reason: String
    ): AppResult<Contribution> = guard {
        val me = uid()
        // Locate the contribution across my groups (id is the doc id).
        val groupIds = store.queryGroup("members", "user_id" to me).map { str(it.data, "group_id") }
        val groupId = groupIds.firstOrNull { gid ->
            store.get("groups/$gid/contributions/$contributionId") != null
        } ?: throw AppException(AppErrorType.NOT_FOUND, "Contribution not found")
        requireAdmin(groupId, me)

        val path = "groups/$groupId/contributions/$contributionId"
        val updated = store.transaction(listOf(path)) { reads ->
            val c = reads[path]!!
            if (c.isEmpty()) throw AppException(AppErrorType.NOT_FOUND, "Contribution not found")
            val current = ContributionStatus.fromWire(str(c, "status"))
            val target = if (approve) ContributionStatus.CONFIRMED else ContributionStatus.FAILED
            val next = ContributionStateMachine.transition(current, target)
                ?: throw AppException(
                    AppErrorType.CONFLICT,
                    "A ${current.name} contribution cannot move to ${target.name}"
                )
            val patch = c + mapOf(
                "status" to next.name, "updated_at" to clock(),
                "failure_reason" to if (approve) "" else reason.trim().take(200)
            ) + if (approve) mapOf("verification" to ContributionVerification.ADMIN_VERIFIED.name) else emptyMap()
            TxOutcome(patch, listOf(TxWrite(path, patch)))
        }
        audit(groupId, "contribution", contributionId,
            if (approve) "CONTRIBUTION_CONFIRMED" else "CONTRIBUTION_FAILED", me,
            """{"by":"${str(store.get("users/$me"), "full_name")}","reason":"${reason.take(80)}"}""")
        val targetUid = str(updated, "user_id")
        notify(targetUid,
            if (approve) NotificationType.CONTRIBUTION_CONFIRMED else NotificationType.CONTRIBUTION_FAILED,
            if (approve) "Contribution confirmed" else "Contribution not accepted",
            if (approve) "Your contribution was confirmed"
            else "Your contribution was rejected" + (if (reason.isNotBlank()) ": ${reason.take(80)}" else ""),
            groupId)
        AppResult.Success(toContribution(path, updated, str(store.get("users/$targetUid"), "full_name")))
    }

    /** Audited reversal of a CONFIRMED contribution. History is preserved. */
    override suspend fun reverseContribution(contributionId: String, reason: String): AppResult<Contribution> = guard {
        val me = uid()
        if (reason.trim().length < 3) {
            throw AppException(
                AppErrorType.VALIDATION, "A reason is required to reverse a contribution",
                mapOf("reason" to "Required")
            )
        }
        val groupIds = store.queryGroup("members", "user_id" to me).map { str(it.data, "group_id") }
        val groupId = groupIds.firstOrNull { gid ->
            store.get("groups/$gid/contributions/$contributionId") != null
        } ?: throw AppException(AppErrorType.NOT_FOUND, "Contribution not found")
        requireAdmin(groupId, me)

        val path = "groups/$groupId/contributions/$contributionId"
        val updated = store.transaction(listOf(path)) { reads ->
            val c = reads[path]!!
            if (c.isEmpty()) throw AppException(AppErrorType.NOT_FOUND, "Contribution not found")
            val current = ContributionStatus.fromWire(str(c, "status"))
            val next = ContributionStateMachine.transition(current, ContributionStatus.REVERSED)
                ?: throw AppException(
                    AppErrorType.CONFLICT,
                    "Only a CONFIRMED contribution can be reversed (current: ${current.name})"
                )
            val patch = c + mapOf(
                "status" to next.name, "updated_at" to clock(),
                "failure_reason" to reason.trim().take(200)
            )
            TxOutcome(patch, listOf(TxWrite(path, patch)))
        }
        audit(groupId, "contribution", contributionId, "CONTRIBUTION_REVERSED", me,
            """{"by":"${str(store.get("users/$me"), "full_name")}","reason":"${reason.take(120)}"}""")
        val targetUid = str(updated, "user_id")
        AppResult.Success(toContribution(path, updated, str(store.get("users/$targetUid"), "full_name")))
    }

    // ---------- payouts ----------

    private fun toPayout(path: String, data: Map<String, Any?>, recipientName: String): Payout = Payout(
        id = path.substringAfterLast("/"),
        groupId = str(data, "group_id"),
        cycleId = str(data, "cycle_id"),
        cycleNumber = int(data, "cycle_number"),
        recipientUserId = str(data, "recipient_uid"),
        recipientName = recipientName,
        amountMinor = long(data, "amount_minor"),
        currency = str(data, "currency", "KES"),
        status = PayoutStatus.fromWire(str(data, "status")),
        reference = str(data, "reference"),
        scheduledAtMillis = long(data, "scheduled_at"),
        completedAtMillis = long(data, "completed_at").takeIf { it > 0 }
    )

    override suspend fun listPayouts(groupId: String): AppResult<List<Payout>> = guard {
        val me = uid()
        requireMember(groupId, me)
        val payouts = store.query("groups/$groupId/payouts")
            .sortedBy { int(it.data, "cycle_number") }
            .map { doc ->
                val name = str(store.get("users/${str(doc.data, "recipient_uid")}"), "full_name")
                toPayout(doc.path, doc.data, name)
            }
        AppResult.Success(payouts)
    }

    /**
     * Marks a scheduled payout COMPLETED (admin only, audited) and advances the
     * rotation. The whole state flip runs in one transaction, so concurrent
     * clients can never double-complete a payout (brief §4.5 rules 5 & 7).
     */
    override suspend fun completePayout(payoutId: String): AppResult<Payout> = guard {
        val me = uid()
        val groupIds = store.queryGroup("members", "user_id" to me).map { str(it.data, "group_id") }
        val groupId = groupIds.firstOrNull { gid -> store.get("groups/$gid/payouts/$payoutId") != null }
            ?: throw AppException(AppErrorType.NOT_FOUND, "Payout not found")
        requireAdmin(groupId, me)

        val payoutPath = "groups/$groupId/payouts/$payoutId"
        val cycleId = str(store.get(payoutPath), "cycle_id")
        val cyclePath = "groups/$groupId/cycles/$cycleId"
        val upcoming = store.query("groups/$groupId/cycles")
            .filter { str(it.data, "status") == CycleStatus.UPCOMING.name }
            .minByOrNull { int(it.data, "cycle_number") }
        val allCycles = store.query("groups/$groupId/cycles")

        val updated = store.transaction(
            listOf(payoutPath, cyclePath, "groups/$groupId") +
                (upcoming?.let { listOf(it.path) } ?: emptyList())
        ) { reads ->
            val payout = reads[payoutPath]!!
            if (payout.isEmpty()) throw AppException(AppErrorType.NOT_FOUND, "Payout not found")
            val status = PayoutStatus.fromWire(str(payout, "status"))
            if (status == PayoutStatus.COMPLETED) {
                throw AppException(AppErrorType.CONFLICT, "This payout has already been completed")
            }
            if (status == PayoutStatus.PENDING) {
                throw AppException(AppErrorType.CONFLICT, "This payout is already being processed")
            }
            val cycle = reads[cyclePath]!!
            if (str(cycle, "status") == CycleStatus.COMPLETED.name) {
                throw AppException(AppErrorType.CONFLICT, "This cycle is already completed")
            }
            val now = clock()
            val writes = mutableListOf(
                TxWrite(payoutPath, payout + mapOf("status" to PayoutStatus.COMPLETED.name, "completed_at" to now)),
                TxWrite(cyclePath, cycle + mapOf("status" to CycleStatus.COMPLETED.name, "actual_payout_at" to now))
            )
            val nextPath = upcoming?.path
            if (nextPath != null) {
                writes += TxWrite(nextPath, reads[nextPath]!! + mapOf("status" to CycleStatus.ACTIVE.name))
            } else if (allCycles.all { str(it.data, "status") == CycleStatus.COMPLETED.name || it.path == cyclePath }) {
                val g = reads["groups/$groupId"]!!
                writes += TxWrite("groups/$groupId", g + mapOf("status" to GroupStatus.COMPLETED.name))
            }
            TxOutcome(payout + mapOf("status" to PayoutStatus.COMPLETED.name, "completed_at" to now), writes)
        }

        val confirmedTotal = store.query("groups/$groupId/contributions")
            .filter { str(it.data, "cycle_id") == cycleId && str(it.data, "status") == ContributionStatus.CONFIRMED.name }
            .sumOf { long(it.data, "amount_minor") }
        audit(groupId, "payout", payoutId, "PAYOUT_COMPLETED", me,
            """{"by":"${str(store.get("users/$me"), "full_name")}","confirmed_minor":$confirmedTotal}""")
        store.query("groups/$groupId/members")
            .filter { str(it.data, "status") == MembershipStatus.ACTIVE.name }
            .forEach { m ->
                notify(str(m.data, "user_id"), NotificationType.PAYOUT_COMPLETED, "Payout completed",
                    "Cycle ${int(store.get(cyclePath), "cycle_number")} payout to the recipient was marked complete",
                    groupId)
            }
        AppResult.Success(toPayout(payoutPath, updated, str(store.get("users/${str(updated, "recipient_uid")}"), "full_name")))
    }

    // ---------- dashboard ----------

    override suspend fun getDashboard(groupId: String): AppResult<DashboardSummary> = guard {
        val me = uid()
        requireMember(groupId, me)
        val group = store.get("groups/$groupId")
            ?: throw AppException(AppErrorType.NOT_FOUND, "Group not found")
        val myMembership = store.get("groups/$groupId/members/$me")

        val cycles = store.query("groups/$groupId/cycles").sortedBy { int(it.data, "cycle_number") }
        val activeCycle = cycles.firstOrNull { str(it.data, "status") == CycleStatus.ACTIVE.name }
        val nextCycle = cycles.firstOrNull { str(it.data, "status") == CycleStatus.UPCOMING.name }
        val memberCount = int(group, "member_count", 1)

        var expected = 0L; var confirmed = 0L; var pending = 0L; var failed = 0L; var reversed = 0L
        var paidCount = 0; var pendingCount = 0
        var myStatus: ContributionStatus? = null
        var myContributionId: String? = null
        if (activeCycle != null) {
            expected = long(activeCycle.data, "expected_total_minor")
            val activeCycleId = activeCycle.path.substringAfterLast("/")
            val live = store.query("groups/$groupId/contributions")
                .filter { str(it.data, "cycle_id") == activeCycleId }
            val byUser = mutableMapOf<String, String>()
            live.forEach { row ->
                val rowStatus = ContributionStatus.fromWire(str(row.data, "status"))
                val amount = long(row.data, "amount_minor")
                when (rowStatus) {
                    ContributionStatus.CONFIRMED -> confirmed += amount
                    ContributionStatus.PENDING -> pending += amount
                    ContributionStatus.FAILED -> failed += amount
                    ContributionStatus.REVERSED -> reversed += amount
                    else -> {}
                }
                if (rowStatus == ContributionStatus.CONFIRMED || rowStatus == ContributionStatus.PENDING) {
                    val uidValue = str(row.data, "user_id")
                    if (rowStatus == ContributionStatus.CONFIRMED || byUser[uidValue] != ContributionStatus.CONFIRMED.name) {
                        byUser[uidValue] = rowStatus.name
                    }
                }
                if (str(row.data, "user_id") == me && rowStatus != ContributionStatus.FAILED &&
                    rowStatus != ContributionStatus.REVERSED
                ) {
                    if (myStatus == null || rowStatus == ContributionStatus.CONFIRMED) {
                        myStatus = rowStatus
                        myContributionId = row.path.substringAfterLast("/")
                    }
                }
            }
            paidCount = byUser.values.count { it == ContributionStatus.CONFIRMED.name }
            pendingCount = byUser.values.count { it == ContributionStatus.PENDING.name }
        }

        val recipientCycle = activeCycle ?: nextCycle
        val recipientName = recipientCycle?.let {
            str(store.get("users/${str(it.data, "recipient_uid")}"), "full_name")
        }

        AppResult.Success(
            DashboardSummary(
                groupId = groupId,
                groupName = str(group, "name"),
                currency = str(group, "currency", "KES"),
                status = GroupStatus.fromWire(str(group, "status")),
                contributionAmountMinor = long(group, "contribution_amount_minor"),
                memberCount = memberCount,
                memberLimit = int(group, "member_limit"),
                currentCycleNumber = activeCycle?.let { int(it.data, "cycle_number") },
                cycleStartEpochDay = activeCycle?.let { long(it.data, "start_epoch_day") },
                cycleEndEpochDay = activeCycle?.let { long(it.data, "end_epoch_day") },
                deadlineEpochDay = (activeCycle ?: nextCycle)?.let { long(it.data, "deadline_epoch_day") },
                expectedCycleTotalMinor = expected,
                confirmedTotalMinor = confirmed,
                pendingTotalMinor = pending,
                failedTotalMinor = failed,
                reversedTotalMinor = reversed,
                outstandingTotalMinor = (expected - confirmed).coerceAtLeast(0),
                paidMemberCount = paidCount,
                pendingMemberCount = pendingCount,
                unpaidMemberCount = (memberCount - paidCount - pendingCount).coerceAtLeast(0),
                nextRecipientName = recipientName,
                nextPayoutAtMillis = recipientCycle?.let { long(it.data, "scheduled_payout_at").takeIf { t -> t > 0 } },
                myContributionStatus = myStatus,
                myContributionId = myContributionId,
                myDueEpochDay = activeCycle?.let { long(it.data, "deadline_epoch_day") },
                isOwner = MembershipRole.fromWire(str(myMembership, "role")).isAdminLike
            )
        )
    }

    override suspend fun listMemberObligations(
        groupId: String, cycleId: String?
    ): AppResult<List<MemberObligation>> = guard {
        val me = uid()
        requireMember(groupId, me)
        val group = store.get("groups/$groupId")
            ?: throw AppException(AppErrorType.NOT_FOUND, "Group not found")
        val effectiveCycleId = cycleId
            ?: store.query("groups/$groupId/cycles")
                .filter { str(it.data, "status") == CycleStatus.ACTIVE.name }
                .firstOrNull()?.path?.substringAfterLast("/")
            ?: return@guard AppResult.Success(emptyList())

        val contributions = store.query("groups/$groupId/contributions")
            .filter {
                str(it.data, "cycle_id") == effectiveCycleId &&
                    str(it.data, "status") != ContributionStatus.FAILED.name
            }
        AppResult.Success(
            store.query("groups/$groupId/members")
                .filter { str(it.data, "status") == MembershipStatus.ACTIVE.name }
                .map { memberDoc ->
                    val uidValue = str(memberDoc.data, "user_id")
                    val user = store.get("users/$uidValue")
                    val contribution = contributions
                        .filter { str(it.data, "user_id") == uidValue }
                        .maxByOrNull { long(it.data, "created_at") }
                    MemberObligation(
                        userId = uidValue,
                        userName = str(user, "full_name", "Unknown"),
                        role = MembershipRole.fromWire(str(memberDoc.data, "role")),
                        status = contribution?.let { ContributionStatus.fromWire(str(it.data, "status")) },
                        verification = contribution?.let {
                            ContributionVerification.fromWire(str(it.data, "verification"))
                        },
                        paidAmountMinor = contribution
                            ?.takeIf { str(it.data, "status") == ContributionStatus.CONFIRMED.name }
                            ?.let { long(it.data, "amount_minor") } ?: 0L,
                        dueAmountMinor = long(group, "contribution_amount_minor"),
                        updatedAtMillis = contribution?.let { long(it.data, "updated_at") }
                    )
                }
        )
    }

    // ---------- activity / audit / notifications ----------

    override suspend fun listActivity(limit: Int): AppResult<List<ActivityEntry>> = guard {
        val me = uid()
        val myGroupIds = store.queryGroup("members", "user_id" to me)
            .filter { str(it.data, "status") == MembershipStatus.ACTIVE.name }
            .map { str(it.data, "group_id") }
        val entries = mutableListOf<ActivityEntry>()
        myGroupIds.forEach { gid ->
            val groupName = str(store.get("groups/$gid"), "name")
            store.query("groups/$gid/contributions").forEach { c ->
                val user = str(store.get("users/${str(c.data, "user_id")}"), "full_name")
                val cStatus = str(c.data, "status")
                entries += ActivityEntry(
                    id = c.path.substringAfterLast("/"), kind = "CONTRIBUTION",
                    title = "Contribution " + cStatus.lowercase().replaceFirstChar { it.uppercase() },
                    subtitle = "$user · ${str(c.data, "reference")}",
                    amountMinor = long(c.data, "amount_minor"),
                    currency = str(c.data, "currency", "KES"),
                    status = cStatus,
                    timestampMillis = long(c.data, "updated_at"),
                    groupId = gid, groupName = groupName
                )
            }
            store.query("groups/$gid/payouts").forEach { p ->
                val user = str(store.get("users/${str(p.data, "recipient_uid")}"), "full_name")
                val pStatus = str(p.data, "status")
                entries += ActivityEntry(
                    id = p.path.substringAfterLast("/"), kind = "PAYOUT",
                    title = "Payout " + pStatus.lowercase().replaceFirstChar { it.uppercase() },
                    subtitle = "To $user · Cycle ${int(p.data, "cycle_number")} · ${str(p.data, "reference")}",
                    amountMinor = long(p.data, "amount_minor"),
                    currency = str(p.data, "currency", "KES"),
                    status = pStatus,
                    timestampMillis = long(p.data, "completed_at").takeIf { it > 0 }
                        ?: long(p.data, "scheduled_at"),
                    groupId = gid, groupName = groupName
                )
            }
            store.query("groups/$gid/audit").forEach { e ->
                val eventType = str(e.data, "event_type")
                if (eventType.startsWith("MEMBER_") || eventType.startsWith("GROUP_") ||
                    eventType == "PAYOUT_ORDER_SET"
                ) {
                    val actorUid = str(e.data, "actor_uid")
                    val actorName = str(store.get("users/$actorUid"), "full_name", "System")
                    entries += ActivityEntry(
                        id = e.path.substringAfterLast("/"), kind = "GROUP",
                        title = eventTypeTitle(eventType),
                        subtitle = "$actorName · ${str(e.data, "metadata").take(80)}",
                        amountMinor = null, currency = null, status = eventType,
                        timestampMillis = long(e.data, "timestamp"),
                        groupId = gid, groupName = groupName
                    )
                }
            }
        }
        AppResult.Success(entries.sortedByDescending { it.timestampMillis }.take(limit.coerceIn(1, 500)))
    }

    private fun eventTypeTitle(eventType: String): String = when (eventType) {
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

    override suspend fun listAuditEvents(groupId: String): AppResult<List<AuditEvent>> = guard {
        val me = uid()
        requireMember(groupId, me)
        AppResult.Success(
            store.query("groups/$groupId/audit")
                .sortedByDescending { long(it.data, "timestamp") }
                .map { e ->
                    val actorUid = str(e.data, "actor_uid")
                    AuditEvent(
                        id = e.path.substringAfterLast("/"),
                        groupId = groupId,
                        entityType = str(e.data, "entity_type"),
                        entityId = str(e.data, "entity_id"),
                        eventType = str(e.data, "event_type"),
                        actorUserId = actorUid.ifEmpty { null },
                        actorName = str(store.get("users/$actorUid"), "full_name", "System"),
                        timestampMillis = long(e.data, "timestamp"),
                        metadata = str(e.data, "metadata")
                    )
                }
        )
    }

    override suspend fun listNotifications(): AppResult<List<AppNotification>> = guard {
        val me = uid()
        AppResult.Success(
            store.query("notifications/$me/items")
                .sortedByDescending { long(it.data, "created_at") }
                .map { n ->
                    AppNotification(
                        id = n.path.substringAfterLast("/"),
                        userId = me,
                        type = NotificationType.fromWire(str(n.data, "type")),
                        title = str(n.data, "title"),
                        message = str(n.data, "message"),
                        isRead = bool(n.data, "is_read"),
                        createdAtMillis = long(n.data, "created_at"),
                        groupId = str(n.data, "group_id").ifEmpty { null }
                    )
                }
        )
    }

    override suspend fun markNotificationsRead(): AppResult<Unit> = guard {
        val me = uid()
        store.query("notifications/$me/items")
            .filter { !bool(it.data, "is_read") }
            .forEach { store.update(it.path, mapOf("is_read" to true)) }
        AppResult.Success(Unit)
    }
}















