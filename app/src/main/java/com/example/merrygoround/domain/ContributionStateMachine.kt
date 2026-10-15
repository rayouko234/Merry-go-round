package com.example.merrygoround.domain

import com.example.merrygoround.domain.model.ContributionStatus

/**
 * Contribution state machine (brief §4.6).
 *
 * PENDING  → CONFIRMED   (admin/provider verification)
 * PENDING  → FAILED      (rejected / failed attempt)
 * CONFIRMED→ REVERSED    (audited reversal; history is never erased)
 *
 * FAILED and REVERSED are terminal. There is no path back to CONFIRMED —
 * a retry creates a NEW contribution with a NEW idempotency key.
 */
object ContributionStateMachine {

    fun canTransition(from: ContributionStatus, to: ContributionStatus): Boolean = when (from) {
        ContributionStatus.PENDING -> to == ContributionStatus.CONFIRMED || to == ContributionStatus.FAILED
        ContributionStatus.CONFIRMED -> to == ContributionStatus.REVERSED
        ContributionStatus.FAILED -> false
        ContributionStatus.REVERSED -> false
    }

    /** Returns true when the transition was applied. */
    fun transition(current: ContributionStatus, to: ContributionStatus): ContributionStatus? =
        if (canTransition(current, to)) to else null
}
