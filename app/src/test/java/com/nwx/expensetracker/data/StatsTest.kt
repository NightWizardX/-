package com.nwx.expensetracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class StatsTest {
    @Test
    fun weekStartsOnMonday() {
        val p = Period.of(PeriodType.WEEK, LocalDate.of(2026, 10, 16)) // Friday
        assertEquals(LocalDate.of(2026, 10, 12), p.start)
        assertEquals(LocalDate.of(2026, 10, 18), p.end)
        assertEquals(LocalDate.of(2026, 10, 5), p.shift(-1).start)
    }

    @Test
    fun monthShiftHandlesShortMonths() {
        val jan = Period.of(PeriodType.MONTH, LocalDate.of(2026, 1, 31))
        val feb = jan.shift(1)
        assertEquals(LocalDate.of(2026, 2, 1), feb.start)
        assertEquals(LocalDate.of(2026, 2, 28), feb.end)
    }

    @Test
    fun rangePreviousIsSameLength() {
        val p = Period(PeriodType.RANGE, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10))
        val prev = p.shift(-1)
        assertEquals(LocalDate.of(2026, 9, 21), prev.start)
        assertEquals(LocalDate.of(2026, 9, 30), prev.end)
    }

    @Test
    fun moneyParsing() {
        assertEquals(123450L, Money.parse("1,234.5"))
        assertEquals(1L, Money.parse("0.01"))
        assertNull(Money.parse("0"))
        assertNull(Money.parse("abc"))
        assertEquals("฿1,234.5", Money.format(123450))
        assertEquals("12.5", Money.input(1250))
    }

    @Test
    fun thaiDates() {
        assertEquals("16 ต.ค. 69", ThaiDate.short(LocalDate.of(2026, 10, 16)))
        assertEquals("ตุลาคม 2569", ThaiDate.monthYear(LocalDate.of(2026, 10, 1)))
    }

    @Test
    fun csvRoundTripQuoting() {
        val rows = parseCsv("a,\"b,c\",\"say \"\"hi\"\"\"\r\n1,2,3\n")
        assertEquals(listOf(listOf("a", "b,c", "say \"hi\""), listOf("1", "2", "3")), rows)
    }

    @Test
    fun dashboardTotalsAndBars() {
        val period = Period.of(PeriodType.WEEK, LocalDate.of(2026, 10, 12))
        fun at(d: Int, h: Int) = LocalDateTime.of(2026, 10, d, h, 0).toMillis()
        val cats = mapOf(1L to Category(1, "อาหาร", "🍜", 0), 2L to Category(2, "เดินทาง", "🚗", 0))
        val list = listOf(
            Expense(1, 5000, 1, "", at(12, 8)),
            Expense(2, 3000, 2, "", at(12, 18)),
            Expense(3, 2000, 1, "", at(14, 12)),
        )
        val d = buildDashboard(period, list, prevTotal = 5000, categories = cats, monthlyBudget = 0, today = LocalDate.of(2026, 12, 1))
        assertEquals(10000L, d.total)
        assertEquals(10000L / 7, d.avgPerDay)
        assertEquals(1L, d.byCategory.first().category.id)
        assertEquals(7, d.bars.size)
        assertEquals(8000L, d.bars[0].value)
        assertEquals(2000L, d.bars[2].value)
    }
}
