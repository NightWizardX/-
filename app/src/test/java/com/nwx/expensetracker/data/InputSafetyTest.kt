package com.nwx.expensetracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputSafetyTest {
    @Test
    fun amountParsingIsStrict() {
        assertEquals(50L, Money.parse(".5"))
        assertEquals(1200L, Money.parse("12."))
        assertEquals(99_999_999_999_999L, Money.parse("999,999,999,999.99"))
        assertNull(Money.parse("1234567890123")) // 13 digits of baht
        assertNull(Money.parse("12.345"))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("+5"))
        assertNull(Money.parse("1e3"))
        assertNull(Money.parse("."))
        assertNull(Money.parse(""))
    }

    @Test
    fun hugeExponentIsRejectedQuickly() {
        val start = System.nanoTime()
        assertNull(Money.parse("1E999999999"))
        assertTrue("took too long", System.nanoTime() - start < 1_000_000_000L)
    }

    @Test
    fun formulaCellsAreGuardedAndRestored() {
        val values = listOf("=HYPERLINK(\"x\")", "+1", "-50 ส่วนลด", "@SUM(A1)", "'=a", "'hello", "'", "ข้าว, ไข่", "say \"hi\"", "")
        for (v in values) {
            val escaped = csvEscape(v)
            val parsed = parseCsv(escaped + "\n").single().single()
            assertTrue("formula not guarded: $escaped", !parsed.startsWith("=") && !parsed.startsWith("+") && !parsed.startsWith("@") && !parsed.startsWith("-"))
            assertEquals(v, unguardCsvCell(parsed))
        }
    }

    @Test
    fun safeTakeDoesNotSplitEmoji() {
        assertEquals("ab", safeTake("ab", 5))
        assertEquals("a", safeTake("a😀", 2)) // would otherwise end in half a surrogate pair
        assertEquals("a😀", safeTake("a😀b", 3))
    }

    @Test
    fun missingCategoryKeepsItsId() {
        val c = categoryOf(42, emptyMap())
        assertEquals(42L, c.id)
        assertEquals("ไม่ระบุหมวด", c.name)
    }
}
