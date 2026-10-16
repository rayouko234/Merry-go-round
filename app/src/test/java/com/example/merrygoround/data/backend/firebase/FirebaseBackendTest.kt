package com.example.merrygoround.data.backend.firebase

import com.example.merrygoround.core.common.AppErrorType
import com.example.merrygoround.core.common.AppResult
import com.example.merrygoround.core.common.TimeFormatter
import com.example.merrygoround.data.backend.InMemorySessionStore
import com.example.merrygoround.domain.model.ContributionFrequency
import com.example.merrygoround.domain.model.ContributionStatus
import com.example.merrygoround.domain.model.ContributionVerification
import com.example.merrygoround.domain.model.CycleStatus
import com.example.merrygoround.domain.model.GroupStatus
import com.example.merrygoround.domain.model.JoinPolicy
import com.example.merrygoround.domain.model.MembershipRole
import com.example.merrygoround.domain.model.MembershipStatus
import com.example.merrygoround.domain.model.PayoutOrderMethod
import com.example.merrygoround.domain.model.PayoutStatus
import com.example.merrygoround.domain.model.SavingsGroup
import com.example.merrygoround.domain.model.User
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end business-rule tests for [FirebaseBackend] against the in-memory
 * gateway fakes — same acceptance scenarios as LocalBackendTest, proving the
 * Firebase implementation enforces identical domain rules (brief §12).
 */
class FirebaseBackendTest {

    private lateinit var identity: InMemoryFirebaseIdentity
    private lateinit var store: InMemoryFirebaseStore
    private lateinit var session: InMemorySessionStore
    private lateinit var backend: FirebaseBackend
    private var clock = 1_700_000_000_000L

    @Before
    fun setUp() {
        identity = InMemoryFirebaseIdentity()
        store = InMemoryFirebaseStore()
        session = InMemorySessionStore()
        backend = FirebaseBackend(identity, store, session) { clock }
    }

    private fun <T> AppResult<T>.ok(): T = (this as AppResult.Success).data
    private fun <T> AppResult<T>.fail(): AppResult.Failure = (this as AppResult.Failure)

    private suspend fun register(name: String, email: String, phone: String): User =
        backend.register(name, email, phone, "passw0rd123").ok()

    private suspend fun asUser(user: User) {
        backend.logout()
        backend.login(user.email, "passw0rd123")
    }

    private suspend fun createDefaultGroup(owner: User? = null, memberLimit: Int = 5): SavingsGroup {
        val o = owner ?: register("Owner One", "owner@ex.com", "0711111111")
        asUser(o)
        return backend.createGroup(
            name = "Nairobi Chama", description = "Office savings",
            contributionAmountMinor = 500_000L, frequency = ContributionFrequency.WEEKLY,
            customIntervalDays = 0, memberLimit = memberLimit,
            startEpochDay = TimeFormatter.todayEpochDay() + 7,
            payoutOrderMethod = PayoutOrderMethod.ADMIN_ASSIGNED,
            joinPolicy = JoinPolicy.OPEN, invitationExpiryDays = 7
        ).ok()
    }

    // ---------- auth ----------

    @Test
    fun registerLogoutLoginAgainByEmailAndPhone() = runBlocking {
        val user = register("Alice Wanjiku", "alice@ex.com", "0722222221")
        backend.logout().ok()
        assertEquals(user.id, backend.login("alice@ex.com", "passw0rd123").ok().id)
        backend.logout().ok()
        assertEquals(user.id, backend.login("0722222221", "passw0rd123").ok().id)
        // SessionStore keeps the app shell (start destination, logout) in sync.
        assertEquals(user.id, session.userId())
    }

    @Test
    fun duplicateEmailRejectedAndWrongPasswordRejected() = runBlocking {
        register("Alice", "dup@ex.com", "0722000001")
        val dup = backend.register("Bob", "dup@ex.com", "0722000002", "passw0rd123")
        assertEquals(AppErrorType.CONFLICT, dup.fail().type)
        backend.logout()
        val bad = backend.login("dup@ex.com", "wrongPass9")
        assertEquals(AppErrorType.AUTHENTICATION, bad.fail().type)
    }

    // ---------- groups & joining ----------

    @Test
    fun secondUserJoinsWithValidCodeAndDuplicateJoinRejected() = runBlocking {
        val owner = register("Owner Two", "owner2@ex.com", "0711111112")
        val group = createDefaultGroup(owner)
        val code = backend.getInvitation(group.id).ok().code
        assertTrue(code.length == 8)

        val joiner = register("Member Two", "member2@ex.com", "0711111113")
        asUser(joiner)
        val membership = backend.joinGroup(code).ok()
        assertEquals(MembershipStatus.ACTIVE, membership.status)

        val dup = backend.joinGroup(code)
        assertEquals(AppErrorType.CONFLICT, dup.fail().type)

        asUser(owner)
        assertEquals(2, backend.getGroup(group.id).ok().memberCount)
    }

    @Test
    fun invalidCodeAndCapacityRejected() = runBlocking {
        val owner = register("Owner Three", "owner3@ex.com", "0711111114")
        val group = createDefaultGroup(owner, memberLimit = 2)
        val code = backend.getInvitation(group.id).ok().code

        val m1 = register("Member A", "m-a@ex.com", "0711111115")
        asUser(m1)
        assertEquals(AppErrorType.NOT_FOUND, backend.joinGroup("ZZZZZZZZ").fail().type)
        backend.joinGroup(code).ok()

        val m2 = register("Member B", "m-b@ex.com", "0711111116")
        asUser(m2)
        val full = backend.joinGroup(code)
        assertEquals(AppErrorType.CONFLICT, full.fail().type)
        assertTrue(full.fail().message.contains("full"))
    }

    @Test
    fun expiredInvitationRejected() = runBlocking {
        val owner = register("Owner Exp", "owner-exp@ex.com", "0711111142")
        val group = createDefaultGroup(owner)
        val code = backend.getInvitation(group.id).ok().code
        // Force-expire the invitation doc.
        val path = "invitations/$code"
        store.docs[path] = store.docs[path]!! + mapOf("expires_at" to (clock - 1))

        val joiner = register("Member Exp", "m-exp@ex.com", "0711111143")
        asUser(joiner)
        val result = backend.joinGroup(code)
        assertEquals(AppErrorType.CONFLICT, result.fail().type)
        assertTrue(result.fail().message.contains("expired"))
    }

    @Test
    fun pendingJoinerSeesGroupAsPendingUntilApproved() = runBlocking {
        val owner = register("Owner Pend", "owner-p@ex.com", "0711111150")
        val group = createDefaultGroup(owner)
        asUser(owner)
        backend.updateGroupSettings(group.id, null, null, null, JoinPolicy.APPROVAL_REQUIRED).ok()
        val code = backend.getInvitation(group.id).ok().code

        val joiner = register("Member Pend", "m-pend@ex.com", "0711111151")
        asUser(joiner)
        assertEquals(MembershipStatus.PENDING, backend.joinGroup(code).ok().status)
        val listed = backend.listMyGroups().ok().single()
        assertEquals(MembershipStatus.PENDING, listed.membershipStatus)

        asUser(owner)
        backend.decideMembership(group.id, joiner.id, approve = true).ok()
        asUser(joiner)
        assertEquals(MembershipStatus.ACTIVE, backend.listMyGroups().ok().single().membershipStatus)
        assertEquals(group.id, backend.getGroup(group.id).ok().id)
    }

    @Test
    fun nonMemberCannotReadGroupData() = runBlocking {
        val owner = register("Owner Four", "owner4@ex.com", "0711111117")
        val group = createDefaultGroup(owner)
        val stranger = register("Stranger", "stranger@ex.com", "0711111118")
        asUser(stranger)
        assertEquals(AppErrorType.PERMISSION_DENIED, backend.getGroup(group.id).fail().type)
        assertEquals(AppErrorType.PERMISSION_DENIED, backend.listMembers(group.id).fail().type)
    }

    // ---------- schedule, contributions, payouts ----------

    /** Owner + member fixture; ends signed in as the OWNER. */
    private suspend fun twoMemberGroup(
        ownerEmail: String, ownerPhone: String, memberEmail: String, memberPhone: String
    ): Triple<User, User, SavingsGroup> {
        val owner = register("Fixture Owner", ownerEmail, ownerPhone)
        val group = createDefaultGroup(owner)
        val code = backend.getInvitation(group.id).ok().code
        val member = register("Fixture Member", memberEmail, memberPhone)
        asUser(member)
        backend.joinGroup(code).ok()
        asUser(owner)
        return Triple(owner, member, group)
    }

    @Test
    fun startGroupNeedsTwoMembersThenGeneratesLockedRotation() = runBlocking {
        val owner = register("Solo Owner", "solo@ex.com", "0711111144")
        val solo = createDefaultGroup(owner)
        assertEquals(AppErrorType.CONFLICT, backend.startGroup(solo.id).fail().type)

        val (_, _, group) = twoMemberGroup("st-a@ex.com", "0711111145", "st-b@ex.com", "0711111146")
        val started = backend.startGroup(group.id).ok()
        assertEquals(GroupStatus.ACTIVE, started.status)

        val cycles = backend.listCycles(group.id).ok()
        assertEquals(2, cycles.size)
        assertEquals(CycleStatus.ACTIVE, cycles[0].status)
        assertEquals(CycleStatus.UPCOMING, cycles[1].status)
        // One recipient per cycle, nobody receives twice, expected = members × amount.
        assertEquals(2, cycles.map { it.recipientUserId }.toSet().size)
        assertEquals(1_000_000L, cycles[0].expectedTotalMinor)

        // Order locked after start; member cannot reorder or start twice.
        val members = backend.listMembers(group.id).ok()
        assertEquals(
            AppErrorType.CONFLICT,
            backend.setPayoutOrder(group.id, members.map { it.id }.reversed()).fail().type
        )
        assertEquals(AppErrorType.CONFLICT, backend.startGroup(group.id).fail().type)

        // A plain member has no admin powers.
        val (_, member, g2) = twoMemberGroup("st-c@ex.com", "0711111147", "st-d@ex.com", "0711111148")
        asUser(member)
        assertEquals(AppErrorType.PERMISSION_DENIED, backend.startGroup(g2.id).fail().type)
        assertEquals(MembershipRole.MEMBER, backend.listMembers(g2.id).ok().first { it.userId == member.id }.role)
    }

    @Test
    fun contributionIdempotencyVerifyAndStateMachine() = runBlocking {
        val (owner, member, group) = twoMemberGroup("ct-a@ex.com", "0711111149", "ct-b@ex.com", "0711111150")
        backend.startGroup(group.id).ok()
        val cycle = backend.listCycles(group.id).ok().first { it.status == CycleStatus.ACTIVE }

        // Wrong amount rejected.
        assertEquals(
            AppErrorType.VALIDATION,
            backend.recordContribution(group.id, cycle.id, 400_000L, "k0", "").fail().type
        )

        // The member records; retry with same idempotency key cannot duplicate.
        asUser(member)
        val first = backend.recordContribution(group.id, cycle.id, 500_000L, "same-key", "M-Pesa A1").ok()
        assertEquals(ContributionStatus.PENDING, first.status)
        assertEquals(ContributionVerification.SELF_REPORTED, first.verification)
        val retry = backend.recordContribution(group.id, cycle.id, 500_000L, "same-key", "M-Pesa A1").ok()
        assertEquals(first.id, retry.id)
        assertEquals(1, backend.listContributions(group.id, cycle.id, null).ok().size)

        // Member cannot verify; owner can; double-confirm blocked by the state machine.
        assertEquals(
            AppErrorType.PERMISSION_DENIED,
            backend.confirmContribution(first.id, approve = true, reason = "").fail().type
        )
        asUser(owner)
        val confirmed = backend.confirmContribution(first.id, approve = true, reason = "").ok()
        assertEquals(ContributionStatus.CONFIRMED, confirmed.status)
        assertEquals(ContributionVerification.ADMIN_VERIFIED, confirmed.verification)
        assertEquals(
            AppErrorType.CONFLICT,
            backend.confirmContribution(first.id, approve = true, reason = "").fail().type
        )

        // Dashboard math: expected 1,000,000 − confirmed 500,000 = outstanding 500,000.
        val dash = backend.getDashboard(group.id).ok()
        assertEquals(1_000_000L, dash.expectedCycleTotalMinor)
        assertEquals(500_000L, dash.confirmedTotalMinor)
        assertEquals(500_000L, dash.outstandingTotalMinor)
        assertEquals(1, dash.paidMemberCount)

        // Reversal keeps history and removes it from confirmed totals.
        val reversed = backend.reverseContribution(first.id, reason = "Wrong member paid").ok()
        assertEquals(ContributionStatus.REVERSED, reversed.status)
        assertEquals(0L, backend.getDashboard(group.id).ok().confirmedTotalMinor)
        assertEquals(
            AppErrorType.CONFLICT,
            backend.reverseContribution(first.id, reason = "Again now").fail().type
        )
    }

    @Test
    fun payoutCompletionRequiresAdminAndAdvancesRotation() = runBlocking {
        val (owner, member, group) = twoMemberGroup("py-a@ex.com", "0711111151", "py-b@ex.com", "0711111152")
        backend.startGroup(group.id).ok()
        val payout = backend.listPayouts(group.id).ok().first { it.cycleNumber == 1 }

        // The fixture signs in as owner; switch to the plain member for the denial check.
        asUser(member)
        assertEquals(AppErrorType.PERMISSION_DENIED, backend.completePayout(payout.id).fail().type)

        asUser(owner)
        val completed = backend.completePayout(payout.id).ok()
        assertEquals(PayoutStatus.COMPLETED, completed.status)
        assertNotNull(completed.completedAtMillis)

        val refreshed = backend.listCycles(group.id).ok()
        assertEquals(CycleStatus.COMPLETED, refreshed[0].status)
        assertEquals(CycleStatus.ACTIVE, refreshed[1].status)

        // Race/double-click guard.
        assertEquals(AppErrorType.CONFLICT, backend.completePayout(payout.id).fail().type)
    }

    // <<END>>
}


