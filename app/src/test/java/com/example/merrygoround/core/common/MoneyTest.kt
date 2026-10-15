package com.example.merrygoround.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun `parse whole KES amount to minor units`() {
        assertEquals(500_000L, Money.parseToMinor("5000"))
    }

    @Test
    fun `parse decimal amount to minor units`() {
        assertEquals(500_050L, Money.parseToMinor("5000.5"))
    }

    @Test
    fun `parse accepts grouped input and whitespace`() {
        assertEquals(125_000L, Money.parseToMinor(" 1,250.00 ")) // KES 1,250.00
    }

    @Test
    fun `parse rejects negative and garbage`() {
        assertNull(Money.parseToMinor("-5"))
        assertNull(Money.parseToMinor("abc"))
        assertNull(Money.parseToMinor(""))
        assertNull(Money.parseToMinor("1.2.3"))
    }

    @Test
    fun `format minor units as KES`() {
        assertEquals("KES 5,000.00", Money.format(500_000L))
    }

    @Test
    fun `format without currency code`() {
        assertEquals("50,000.00", Money.format(5_000_000L, includeCode = false))
    }

    @Test
    fun `round trip parse and format`() {
        val minor = Money.parseToMinor("12345.67")!!
        assertEquals("KES 12,345.67", Money.format(minor))
    }

    @Test
    fun `sum of minor units`() {
        assertEquals(1_500_000L, Money.sum(listOf(500_000L, 500_000L, 500_000L)))
    }
}
