package com.example.merrygoround.domain

import com.example.merrygoround.domain.model.ContributionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContributionStateMachineTest {

    @Test
    fun `pending can be confirmed`() {
        assertTrue(ContributionStateMachine.canTransition(ContributionStatus.PENDING, ContributionStatus.CONFIRMED))
        assertEquals(
            ContributionStatus.CONFIRMED,
            ContributionStateMachine.transition(ContributionStatus.PENDING, ContributionStatus.CONFIRMED)
        )
    }

    @Test
    fun `pending can fail`() {
        assertTrue(ContributionStateMachine.canTransition(ContributionStatus.PENDING, ContributionStatus.FAILED))
    }

    @Test
    fun `confirmed can be reversed but not failed`() {
        assertTrue(ContributionStateMachine.canTransition(ContributionStatus.CONFIRMED, ContributionStatus.REVERSED))
        assertFalse(ContributionStateMachine.canTransition(ContributionStatus.CONFIRMED, ContributionStatus.FAILED))
    }

    @Test
    fun `failed is terminal`() {
        ContributionStatus.entries.filter { it != ContributionStatus.FAILED }.forEach { target ->
            assertFalse(ContributionStateMachine.canTransition(ContributionStatus.FAILED, target))
        }
        assertNull(ContributionStateMachine.transition(ContributionStatus.FAILED, ContributionStatus.CONFIRMED))
    }

    @Test
    fun `reversed is terminal`() {
        ContributionStatus.entries.filter { it != ContributionStatus.REVERSED }.forEach { target ->
            assertFalse(ContributionStateMachine.canTransition(ContributionStatus.REVERSED, target))
        }
    }

    @Test
    fun `only confirmed funds count`() {
        assertTrue(ContributionStatus.CONFIRMED.countsAsFunds)
        assertFalse(ContributionStatus.PENDING.countsAsFunds)
        assertFalse(ContributionStatus.FAILED.countsAsFunds)
        assertFalse(ContributionStatus.REVERSED.countsAsFunds)
    }
}
