package com.example.merrygoround.data.backend

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.merrygoround.core.common.AppResult
import com.example.merrygoround.core.common.TimeFormatter
import com.example.merrygoround.core.datastore.SessionStore
import com.example.merrygoround.data.backend.local.LocalBackend
import com.example.merrygoround.data.local.AppDatabase
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** In-memory session store for tests (avoids DataStore/Robolectric coupling). */
class InMemorySessionStore : SessionStore {
    private var tokenValue: String? = null
    private var userIdValue: String? = null
    private val loggedIn = MutableStateFlow(false)
    private val uid = MutableStateFlow<String?>(null)
    override val isLoggedIn: kotlinx.coroutines.flow.Flow<Boolean> get() = loggedIn
    override val currentUserId: kotlinx.coroutines.flow.Flow<String?> get() = uid
    override suspend fun token(): String? = tokenValue
    override suspend fun userId(): String? = userIdValue
    override suspend fun saveSession(token: String, userId: String) {
        tokenValue = token
        userIdValue = userId
        loggedIn.value = true
        uid.value = userId
    }
    override suspend fun clear() {
        tokenValue = null
        userIdValue = null
        loggedIn.value = false
        uid.value = null
    }
}

/**
 * End-to-end backend tests against the authoritative local store.
 * These encode the brief's essential acceptance scenarios (§12).
 */
@RunWith(AndroidJUnit4::class)
class LocalBackendTest {

    private lateinit var db: AppDatabase
    private lateinit var session: SessionStore
    private var clock = 1_700_000_000_000L
    private lateinit var backend: LocalBackend

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        session = InMemorySessionStore()
        backend = LocalBackend(db, session) { clock }
    }

    @After
    fun tearDown() = db.close()

    // ---------- helpers ----------

    private fun <T> AppResult<T>.ok(): T = (this as AppResult.Success).data

    private fun <T> AppResult<T>.fail(): AppResult.Failure = (this as AppResult.Failure)

    private suspend fun register(name: String, email: String, phone: String): User =
        backend.register(name, email, phone, "passw0rd123").ok()

    private suspend fun asUser(user: User) {
        backend.logout()
        backend.login(user.email, "passw0rd123")
    }

    private suspend fun createDefaultGroup(ownerOrNull: User? = null): SavingsGroup {
        val owner = ownerOrNull ?: register("Owner One", "owner@ex.com", "0711111111")
        asUser(owner)
        return backend.createGroup(
            name = "Nairobi Chama",
            description = "Office savings",
            contributionAmountMinor = 500_000L,
            frequency = ContributionFrequency.WEEKLY,
            customIntervalDays = 0,
            memberLimit = 5,
            startEpochDay = TimeFormatter.todayEpochDay() + 7,
            payoutOrderMethod = PayoutOrderMethod.ADMIN_ASSIGNED,
            joinPolicy = JoinPolicy.OPEN,
            invitationExpiryDays = 7
        ).ok()
    }

    // ---------- auth ----------

    @Test
    fun registerThenLogoutThenLoginAgain() = runBlocking {
        val user = register("Alice Wanjiku", "alice@ex.com", "0722222221")
        assertEquals("Alice Wanjiku", user.fullName)
        backend.logout().ok()
        val again = backend.login("alice@ex.com", "passw0rd123").ok()
        assertEquals(user.id, again.id)
        // Login also works with the phone identifier.
        backend.logout().ok()
        val byPhone = backend.login("0722222221", "passw0rd123").ok()
        assertEquals(user.id, byPhone.id)
    }

    @Test
    fun duplicateEmailIsRejected() = runBlocking {
        register("Alice", "dup@ex.com", "0722000001")
        val second = backend.register("Bob", "dup@ex.com", "0722000002", "passw0rd123")
        assertEquals(com.example.merrygoround.core.common.AppErrorType.CONFLICT, second.fail().type)
    }

    @Test
    fun wrongPasswordIsRejected() = runBlocking {
        register("Alice", "alice2@ex.com", "0722000003")
        backend.logout()
        val result = backend.login("alice2@ex.com", "wrongPass1")
        assertEquals(com.example.merrygoround.core.common.AppErrorType.AUTHENTICATION, result.fail().type)
    }

    // ---------- groups & joining ----------

    @Test
    fun createGroupMakesOwnerActiveMemberWithInvitation() = runBlocking {
        val owner = register("Owner One", "owner1@ex.com", "0711111111")
        val group = createDefaultGroup(owner)
        assertEquals(GroupStatus.FORMING, group.status)
        assertEquals(1, group.memberCount)
        val invite = backend.getInvitation(group.id).ok()
        assertTrue(invite.code.length == 8)
        val members = backend.listMembers(group.id).ok()
        assertEquals(1, members.size)
        assertEquals(MembershipRole.OWNER, members[0].role)
        assertEquals(MembershipStatus.ACTIVE, members[0].status)
    }

    @Test
    fun secondUserJoinsWithValidCode() = runBlocking {
        val owner = register("Owner Two", "owner2@ex.com", "0711111112")
        val group = createDefaultGroup(owner)
        val code = backend.getInvitation(group.id).ok().code

        val joiner = register("Member Two", "member2@ex.com", "0711111113")
        asUser(joiner)
        val membership = backend.joinGroup(code).ok()
        assertEquals(group.id, membership.groupId)
        assertEquals(MembershipStatus.ACTIVE, membership.status)

        asUser(owner)
        val refreshed = backend.getGroup(group.id).ok()
        assertEquals(2, refreshed.memberCount)
    }

    @Test
    fun invalidAndExpiredInvitationsAreRejected() = runBlocking {
        val owner = register("Owner Three", "owner3@ex.com", "0711111114")
        val group = createDefaultGroup(owner)

        val joiner = register("Member Three", "member3@ex.com", "0711111115")
        asUser(joiner)
        assertEquals(
            com.example.merrygoround.core.common.AppErrorType.NOT_FOUND,
            backend.joinGroup("ZZZZZZZZ").fail().type
        )
        // Advance the clock past the invitation expiry.
        clock += 10L * 24 * 60 * 60 * 1000
        asUser(owner)
        // getInvitation refreshes expired codes, so fetch then force expiry via the DAO:
        val expired = backend.getInvitation(group.id).ok()
        val row = db.invitationDao().findByCode(expired.code)
        assertNotNull(row)
        db.invitationDao().update(row!!.copy(expiresAtMillis = clock - 1))
        asUser(joiner)
        val result = backend.joinGroup(expired.code)
        assertEquals(com.example.merrygoround.core.common.AppErrorType.CONFLICT, result.fail().type)
        assertTrue(result.fail().message.contains("expired"))
    }

    @Test
    fun duplicateJoinIsRejected() = runBlocking {
        val owner = register("Owner Four", "owner4@ex.com", "0711111116")
        val group = createDefaultGroup(owner)
        val code = backend.getInvitation(group.id).ok().code
        val joiner = register("Member Four", "member4@ex.com", "0711111117")
        asUser(joiner)
        backend.joinGroup(code).ok()
        val second = backend.joinGroup(code)
        assertEquals(com.example.merrygoround.core.common.AppErrorType.CONFLICT, second.fail().type)
    }

    @Test
    fun joinAtCapacityIsRejected() = runBlocking {
        val owner = register("Owner Five", "owner5@ex.com", "0711111118")
        asUser(owner)
        val group = backend.createGroup(
            name = "Full House", description = "", contributionAmountMinor = 100_000L,
            frequency = ContributionFrequency.WEEKLY, customIntervalDays = 0,
            memberLimit = 2, startEpochDay = TimeFormatter.todayEpochDay() + 7,
            payoutOrderMethod = PayoutOrderMethod.JOIN_ORDER,
            joinPolicy = JoinPolicy.OPEN, invitationExpiryDays = 7
        ).ok()
        val code = backend.getInvitation(group.id).ok().code
        val member = register("Member Five", "member5@ex.com", "0711111119")
        asUser(member)
        backend.joinGroup(code).ok()
        val overflow = register("Member Six", "member6@ex.com", "0711111120")
        asUser(overflow)
        val result = backend.joinGroup(code)
        assertEquals(com.example.merrygoround.core.common.AppErrorType.CONFLICT, result.fail().type)
        assertTrue(result.fail().message.contains("full"))
    }

    // ---------- starting the rotation ----------

    @Test
    fun startGroupRequiresAtLeastTwoActiveMembers() = runBlocking {
        val owner = register("Owner Six", "owner6@ex.com", "0711111121")
        val group = createDefaultGroup(owner)
        val result = backend.startGroup(group.id)
        assertEquals(com.example.merrygoround.core.common.AppErrorType.CONFLICT, result.fail().type)
    }

    @Test
    fun startGroupLocksOrderAndGeneratesRotation() = runBlocking {
        val fx = twoMemberGroup("start-a@ex.com", "0711111122", "start-b@ex.com", "0711111123")
        asUser(fx.owner)
        val started = backend.startGroup(fx.group.id).ok()
        assertEquals(GroupStatus.ACTIVE, started.status)

        val cycles = backend.listCycles(fx.group.id).ok()
        assertEquals(2, cycles.size)
        assertEquals(CycleStatus.ACTIVE, cycles[0].status)
        assertEquals(CycleStatus.UPCOMING, cycles[1].status)
        // One designated recipient per cycle and no member receives twice.
        assertEquals(2, cycles.map { it.recipientUserId }.toSet().size)
        // Expected total = members x fixed amount.
        assertEquals(1_000_000L, cycles[0].expectedTotalMinor)

        // Order is locked after start.
        val members = backend.listMembers(fx.group.id).ok()
        val reorder = backend.setPayoutOrder(fx.group.id, members.map { it.id }.reversed())
        assertEquals(com.example.merrygoround.core.common.AppErrorType.CONFLICT, reorder.fail().type)
    }

    @Test
    fun memberCannotStartGroupOrSetPayoutOrder() = runBlocking {
        val fx = twoMemberGroup("start-c@ex.com", "0711111124", "start-d@ex.com", "0711111125")
        // Currently signed in as the plain member.
        val me = backend.me().ok()
        val start = backend.startGroup(fx.group.id)
        assertEquals(com.example.merrygoround.core.common.AppErrorType.PERMISSION_DENIED, start.fail().type)
        val order = backend.setPayoutOrder(fx.group.id, listOf("x", "y"))
        assertEquals(com.example.merrygoround.core.common.AppErrorType.PERMISSION_DENIED, order.fail().type)
        val myRole = backend.listMembers(fx.group.id).ok().first { m -> m.userId == me.id }.role
        assertEquals(MembershipRole.MEMBER, myRole)
    }

    @Test
    fun approvalPolicyCreatesPendingMembership() = runBlocking {
        val owner = register("Owner Seven", "owner7@ex.com", "0711111126")
        asUser(owner)
        val group = backend.createGroup(
            name = "Closed Chama", description = "", contributionAmountMinor = 200_000L,
            frequency = ContributionFrequency.MONTHLY, customIntervalDays = 0,
            memberLimit = 10, startEpochDay = TimeFormatter.todayEpochDay() + 7,
            payoutOrderMethod = PayoutOrderMethod.ADMIN_ASSIGNED,
            joinPolicy = JoinPolicy.APPROVAL_REQUIRED, invitationExpiryDays = 7
        ).ok()
        val code = backend.getInvitation(group.id).ok().code
        val joiner = register("Member Seven", "member7@ex.com", "0711111127")
        asUser(joiner)
        val membership = backend.joinGroup(code).ok()
        assertEquals(MembershipStatus.PENDING, membership.status)

        // Owner approves.
        asUser(owner)
        val decided = backend.decideMembership(group.id, membership.id, approve = true).ok()
        assertEquals(MembershipStatus.ACTIVE, decided.status)
        assertEquals(2, backend.getGroup(group.id).ok().memberCount)
    }

    // ---------- contributions ----------

    @Test
    fun contributionIsRecordedPendingAndVerifiedByAdmin() = runBlocking {
        val fx = twoMemberGroup("ctr-a@ex.com", "0711111130", "ctr-b@ex.com", "0711111131")
        asUser(fx.owner)
        backend.startGroup(fx.group.id).ok()
        val cycle = backend.listCycles(fx.group.id).ok().first { it.status == CycleStatus.ACTIVE }

        // The plain member (currently signed in) records a contribution.
        val contribution = backend.recordContribution(
            groupId = fx.group.id, cycleId = cycle.id,
            amountMinor = 500_000L, idempotencyKey = "key-1", note = "M-Pesa ABC123"
        ).ok()
        assertEquals(ContributionStatus.PENDING, contribution.status)
        assertEquals(ContributionVerification.SELF_REPORTED, contribution.verification)

        // Owner verifies it.
        asUser(fx.owner)
        val confirmed = backend.confirmContribution(contribution.id, approve = true, reason = "").ok()
        assertEquals(ContributionStatus.CONFIRMED, confirmed.status)
        assertEquals(ContributionVerification.ADMIN_VERIFIED, confirmed.verification)

        val dashboard = backend.getDashboard(fx.group.id).ok()
        assertEquals(500_000L, dashboard.confirmedTotalMinor)
        assertEquals(1_000_000L, dashboard.expectedCycleTotalMinor)
        assertEquals(500_000L, dashboard.outstandingTotalMinor)
        assertEquals(1, dashboard.paidMemberCount)
    }

    @Test
    fun retryWithSameIdempotencyKeyDoesNotDuplicate() = runBlocking {
        val fx = twoMemberGroup("idem-a@ex.com", "0711111132", "idem-b@ex.com", "0711111133")
        asUser(fx.owner)
        backend.startGroup(fx.group.id).ok()
        val cycle = backend.listCycles(fx.group.id).ok().first { it.status == CycleStatus.ACTIVE }

        val first = backend.recordContribution(fx.group.id, cycle.id, 500_000L, "same-key", "").ok()
        val retry = backend.recordContribution(fx.group.id, cycle.id, 500_000L, "same-key", "").ok()
        assertEquals(first.id, retry.id)
        val all = backend.listContributions(fx.group.id, cycle.id, null).ok()
        assertEquals(1, all.size)
    }

    @Test
    fun wrongAmountIsRejectedAndNonMemberCannotContribute() = runBlocking {
        val fx = twoMemberGroup("amt-a@ex.com", "0711111134", "amt-b@ex.com", "0711111135")
        asUser(fx.owner)
        backend.startGroup(fx.group.id).ok()
        val cycle = backend.listCycles(fx.group.id).ok().first { it.status == CycleStatus.ACTIVE }

        val wrongAmount = backend.recordContribution(fx.group.id, cycle.id, 400_000L, "k1", "")
        assertEquals(com.example.merrygoround.core.common.AppErrorType.VALIDATION, wrongAmount.fail().type)

        val stranger = register("Outsider", "outsider@ex.com", "0711111199")
        asUser(stranger)
        val denied = backend.recordContribution(fx.group.id, cycle.id, 500_000L, "k2", "")
        assertEquals(com.example.merrygoround.core.common.AppErrorType.PERMISSION_DENIED, denied.fail().type)
    }

    @Test
    fun stateMachineGuardsAndReversalKeepHistory() = runBlocking {
        val fx = twoMemberGroup("rev-a@ex.com", "0711111136", "rev-b@ex.com", "0711111137")
        asUser(fx.owner)
        backend.startGroup(fx.group.id).ok()
        val cycle = backend.listCycles(fx.group.id).ok().first { it.status == CycleStatus.ACTIVE }

        // The plain member records a contribution…
        asUser(fx.member)
        val contribution = backend.recordContribution(fx.group.id, cycle.id, 500_000L, "k3", "").ok()

        // …and cannot verify it themselves.
        val selfVerify = backend.confirmContribution(contribution.id, approve = true, reason = "")
        assertEquals(com.example.merrygoround.core.common.AppErrorType.PERMISSION_DENIED, selfVerify.fail().type)

        asUser(fx.owner)
        backend.confirmContribution(contribution.id, approve = true, reason = "").ok()
        val again = backend.confirmContribution(contribution.id, approve = true, reason = "")
        assertEquals(com.example.merrygoround.core.common.AppErrorType.CONFLICT, again.fail().type)

        val reversed = backend.reverseContribution(contribution.id, reason = "Wrong member paid").ok()
        assertEquals(ContributionStatus.REVERSED, reversed.status)
        assertTrue(reversed.failureReason.contains("Wrong member"))
        val dashboard = backend.getDashboard(fx.group.id).ok()
        assertEquals(0L, dashboard.confirmedTotalMinor)
        assertEquals(500_000L, dashboard.reversedTotalMinor)
        assertTrue(backend.listAuditEvents(fx.group.id).ok().size >= 3)
    }

    @Test
    fun payoutCompletionRequiresAdminAndAdvancesRotation() = runBlocking {
        val fx = twoMemberGroup("pay-a@ex.com", "0711111138", "pay-b@ex.com", "0711111139")
        asUser(fx.owner)
        backend.startGroup(fx.group.id).ok()
        val payout = backend.listPayouts(fx.group.id).ok().first { it.cycleNumber == 1 }

        asUser(fx.member)
        val denied = backend.completePayout(payout.id)
        assertEquals(com.example.merrygoround.core.common.AppErrorType.PERMISSION_DENIED, denied.fail().type)

        asUser(fx.owner)
        val completed = backend.completePayout(payout.id).ok()
        assertEquals(PayoutStatus.COMPLETED, completed.status)
        assertNotNull(completed.completedAtMillis)

        val refreshed = backend.listCycles(fx.group.id).ok()
        assertEquals(CycleStatus.COMPLETED, refreshed[0].status)
        assertEquals(CycleStatus.ACTIVE, refreshed[1].status)

        val duplicate = backend.completePayout(payout.id)
        assertEquals(com.example.merrygoround.core.common.AppErrorType.CONFLICT, duplicate.fail().type)
    }

    @Test
    fun nonMemberCannotReadGroupData() = runBlocking {
        val owner = register("Owner Eight", "owner8@ex.com", "0711111140")
        val group = createDefaultGroup(owner)
        val stranger = register("Stranger Eight", "stranger8@ex.com", "0711111141")
        asUser(stranger)
        assertEquals(
            com.example.merrygoround.core.common.AppErrorType.PERMISSION_DENIED,
            backend.getGroup(group.id).fail().type
        )
        assertEquals(
            com.example.merrygoround.core.common.AppErrorType.PERMISSION_DENIED,
            backend.listMembers(group.id).fail().type
        )
    }



    // ---------- shared fixture ----------

    private data class Fixture(val owner: User, val member: User, val group: SavingsGroup)

    private suspend fun twoMemberGroup(
        ownerEmail: String, ownerPhone: String,
        memberEmail: String, memberPhone: String
    ): Fixture {
        val owner = register("Fixture Owner", ownerEmail, ownerPhone)
        val group = createDefaultGroup(owner)
        val code = backend.getInvitation(group.id).ok().code
        val member = register("Fixture Member", memberEmail, memberPhone)
        asUser(member)
        backend.joinGroup(code).ok()
        return Fixture(owner, member, group)
    }
}

