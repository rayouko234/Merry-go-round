package com.example.merrygoround.domain

import com.example.merrygoround.domain.model.ContributionFrequency
import java.time.LocalDate

/**
 * Pure, testable rotation scheduler (brief §4.5).
 *
 * Rules enforced here:
 *  - Exactly one designated recipient per cycle.
 *  - A member cannot receive two turns in the same rotation (one cycle per member,
 *    the recipient list is walked exactly once).
 *  - Only the members passed in (already filtered to eligible ACTIVE members) are scheduled.
 *  - Cycle windows are derived from the group start date and frequency; MONTHLY uses
 *    calendar-month arithmetic, WEEKLY is 7 days, CUSTOM uses the configured interval.
 */
object CyclePlanner {

    data class PlannedCycle(
        val cycleNumber: Int, // 1-based
        val startEpochDay: Long,
        val endEpochDay: Long,
        val deadlineEpochDay: Long,
        val recipientMembershipId: String,
        val expectedTotalMinor: Long,
        val scheduledPayoutAtMillis: Long
    )

    fun cycleLengthDays(frequency: ContributionFrequency, customIntervalDays: Int): Int =
        when (frequency) {
            ContributionFrequency.WEEKLY -> 7
            ContributionFrequency.MONTHLY -> 30 // documented approximation; calendar start dates are exact
            ContributionFrequency.CUSTOM -> customIntervalDays.coerceAtLeast(1)
        }

    /**
     * @param orderedMembershipIds active membership ids in payout order (size >= 2)
     * @param baseStartEpochDay first cycle start; callers clamp to today if the configured
     *        start date already passed when the group is started
     * @param nowMillis used to compute the scheduled payout timestamp after each deadline
     * @param dayStartToMillis converts an epoch day to the epoch millis at the start of that day (device zone)
     */
    fun plan(
        orderedMembershipIds: List<String>,
        frequency: ContributionFrequency,
        customIntervalDays: Int,
        contributionAmountMinor: Long,
        baseStartEpochDay: Long,
        nowMillis: Long,
        dayStartToMillis: (Long) -> Long
    ): List<PlannedCycle> {
        require(orderedMembershipIds.size >= 2) { "A rotation needs at least 2 members" }
        val expectedTotal = contributionAmountMinor * orderedMembershipIds.size
        val startDate = LocalDate.ofEpochDay(baseStartEpochDay)

        return orderedMembershipIds.mapIndexed { index, membershipId ->
            val (start, end) = when (frequency) {
                ContributionFrequency.WEEKLY -> {
                    val s = startDate.plusDays(7L * index)
                    s to s.plusDays(6)
                }
                ContributionFrequency.MONTHLY -> {
                    val s = startDate.plusMonths(index.toLong())
                    s to s.plusMonths(1).minusDays(1)
                }
                ContributionFrequency.CUSTOM -> {
                    val len = customIntervalDays.coerceAtLeast(1).toLong()
                    val s = startDate.plusDays(len * index)
                    s to s.plusDays(len - 1)
                }
            }
            val endEpochDay = end.toEpochDay()
            PlannedCycle(
                cycleNumber = index + 1,
                startEpochDay = start.toEpochDay(),
                endEpochDay = endEpochDay,
                deadlineEpochDay = endEpochDay,
                recipientMembershipId = membershipId,
                expectedTotalMinor = expectedTotal,
                scheduledPayoutAtMillis = dayStartToMillis(endEpochDay + 1)
            )
        }
    }
}
