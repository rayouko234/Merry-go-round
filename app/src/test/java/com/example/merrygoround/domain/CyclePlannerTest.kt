package com.example.merrygoround.domain

import com.example.merrygoround.domain.model.ContributionFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CyclePlannerTest {

    private val dayZeroToMillis: (Long) -> Long = { day -> day * 86_400_000L }

    @Test
    fun `one cycle per member and each member receives exactly once`() {
        val members = listOf("m1", "m2", "m3", "m4")
        val plan = CyclePlanner.plan(
            orderedMembershipIds = members,
            frequency = ContributionFrequency.WEEKLY,
            customIntervalDays = 0,
            contributionAmountMinor = 500_000L,
            baseStartEpochDay = 100L,
            nowMillis = 0L,
            dayStartToMillis = dayZeroToMillis
        )
        assertEquals(4, plan.size)
        assertEquals(members, plan.map { it.recipientMembershipId })
        assertEquals(4, plan.map { it.recipientMembershipId }.toSet().size)
        assertEquals(listOf(1, 2, 3, 4), plan.map { it.cycleNumber })
    }

    @Test
    fun `weekly cycles are seven days long and follow each other`() {
        val plan = CyclePlanner.plan(
            orderedMembershipIds = listOf("a", "b"),
            frequency = ContributionFrequency.WEEKLY,
            customIntervalDays = 0,
            contributionAmountMinor = 100L,
            baseStartEpochDay = 0L,
            nowMillis = 0L,
            dayStartToMillis = dayZeroToMillis
        )
        assertEquals(0L, plan[0].startEpochDay)
        assertEquals(6L, plan[0].endEpochDay)
        assertEquals(7L, plan[1].startEpochDay)
        assertEquals(13L, plan[1].endEpochDay)
    }

    @Test
    fun `expected total is members times fixed contribution`() {
        val plan = CyclePlanner.plan(
            orderedMembershipIds = listOf("a", "b", "c"),
            frequency = ContributionFrequency.MONTHLY,
            customIntervalDays = 0,
            contributionAmountMinor = 500_000L,
            baseStartEpochDay = 0L,
            nowMillis = 0L,
            dayStartToMillis = dayZeroToMillis
        )
        // 10 x 5,000 example from the brief generalises: 3 x 500,000 minor
        plan.forEach { assertEquals(1_500_000L, it.expectedTotalMinor) }
    }

    @Test
    fun `custom interval respected`() {
        val plan = CyclePlanner.plan(
            orderedMembershipIds = listOf("a", "b"),
            frequency = ContributionFrequency.CUSTOM,
            customIntervalDays = 10,
            contributionAmountMinor = 100L,
            baseStartEpochDay = 5L,
            nowMillis = 0L,
            dayStartToMillis = dayZeroToMillis
        )
        assertEquals(5L, plan[0].startEpochDay)
        assertEquals(14L, plan[0].endEpochDay)
        assertEquals(15L, plan[1].startEpochDay)
    }

    @Test
    fun `payout is scheduled after the contribution deadline`() {
        val plan = CyclePlanner.plan(
            orderedMembershipIds = listOf("a", "b"),
            frequency = ContributionFrequency.WEEKLY,
            customIntervalDays = 0,
            contributionAmountMinor = 100L,
            baseStartEpochDay = 10L,
            nowMillis = 0L,
            dayStartToMillis = dayZeroToMillis
        )
        val first = plan[0]
        assertTrue(first.scheduledPayoutAtMillis > dayZeroToMillis(first.deadlineEpochDay))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rotation requires at least two members`() {
        CyclePlanner.plan(
            orderedMembershipIds = listOf("only-one"),
            frequency = ContributionFrequency.WEEKLY,
            customIntervalDays = 0,
            contributionAmountMinor = 100L,
            baseStartEpochDay = 0L,
            nowMillis = 0L,
            dayStartToMillis = dayZeroToMillis
        )
    }
}
