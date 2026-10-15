package com.example.merrygoround.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {

    @Test
    fun `valid Kenyan phone formats normalize to +254`() {
        assertNull(Validators.validatePhone("0712345678"))
        assertNull(Validators.validatePhone("+254712345678"))
        assertNull(Validators.validatePhone("0110123456"))
        assertEquals("+254712345678", Validators.normalizePhone("0712345678"))
        assertEquals("+254712345678", Validators.normalizePhone("254712345678"))
    }

    @Test
    fun `invalid phone rejected`() {
        assertNotNull(Validators.validatePhone("12345"))
        assertNotNull(Validators.validatePhone("0812345678"))
        assertNotNull(Validators.validatePhone(""))
    }

    @Test
    fun `email validation`() {
        assertNull(Validators.validateEmail("user@example.com"))
        assertNotNull(Validators.validateEmail("not-an-email"))
        assertNotNull(Validators.validateEmail(""))
    }

    @Test
    fun `password rules`() {
        assertNotNull(Validators.validatePassword("short1"))       // < 8
        assertNotNull(Validators.validatePassword("abcdefgh"))     // no digit
        assertNull(Validators.validatePassword("abcdefg1"))
    }

    @Test
    fun `group amount bounds in minor units`() {
        assertNull(Validators.validateContributionAmount(500_000L))
        assertNotNull(Validators.validateContributionAmount(0L))
        assertNotNull(Validators.validateContributionAmount(-100L))
        assertNotNull(Validators.validateContributionAmount(50L)) // below 1 KES minimum
    }

    @Test
    fun `member limit bounds`() {
        assertNull(Validators.validateMemberLimit(5))
        assertNotNull(Validators.validateMemberLimit(1))
        assertNotNull(Validators.validateMemberLimit(51))
    }

    @Test
    fun `start date must be today or later`() {
        val today = 20_000L
        assertNull(Validators.validateStartDate(today, today))
        assertNotNull(Validators.validateStartDate(today - 1, today))
    }

    @Test
    fun `invite code format`() {
        assertNull(Validators.validateInviteCode("K7M2P9QA"))
        assertNotNull(Validators.validateInviteCode("SHORT"))
        assertNotNull(Validators.validateInviteCode("HAS O I 0 1".replace(" ", ""))) // contains O,I,0,1? no...
    }

    @Test
    fun `invite code rejects ambiguous characters`() {
        assertNotNull(Validators.validateInviteCode("ABC1O0I")) // 1, O, 0, I excluded
        assertNull(Validators.validateInviteCode("ABCDEFGH"))
    }
}
