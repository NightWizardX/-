package com.nwx.expensetracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class YearViewTest {
    private fun at(y: Int, m: Int, d: Int) = LocalDateTime.of(y, m, d, 12, 0).toMillis()

    @Test
    fun yearCoversJanuaryToDecember() {
        val p = Period.of(PeriodType.YEAR, LocalDate.of(2026, 10, 16))
        assertEquals(LocalDate.of(2026, 1, 1), p.start)
        assertEquals(LocalDate.of(2026, 12, 31), p.end)
        assertEquals(365, p.days)
        assertEquals("ปี 2569", p.label())
    }

    @Test
    fun yearShiftsByWholeYearsIncludingLeapYears() {
        val p = Period.of(PeriodType.YEAR, LocalDate.of(2026, 6, 1))
        assertEquals(LocalDate.of(2025, 1, 1), p.shift(-1).start)
        val leap = p.shift(2)
        assertEquals(LocalDate.of(2028, 1, 1), leap.start)
        assertEquals(LocalDate.of(2028, 12, 31), leap.end)
        assertEquals(366, leap.days)
    }

    @Test
    fun yearHasTwelveMonthlyBarsAllLabelled() {
        val p = Period.of(PeriodType.YEAR, LocalDate.of(2026, 1, 1))
        val list = listOf(
            Expense(1, 1000, 1, "", at(2026, 3, 5)),
            Expense(2, 500, 1, "", at(2026, 3, 20)),
            Expense(3, 700, 1, "", at(2026, 12, 31)),
        )
        val bars = buildBars(p, list)
        assertEquals(12, bars.size)
        assertEquals("ม.ค.", bars[0].label)
        assertEquals("ธ.ค.", bars[11].label)
        assertEquals(1500L, bars[2].value)
        assertEquals(700L, bars[11].value)
        assertEquals("มีนาคม 2569", bars[2].detail)
        assertTrue(bars.all { it.showLabel })
    }

    @Test
    fun yearAveragesPerElapsedMonthAndUsesTwelveMonthBudget() {
        val p = Period.of(PeriodType.YEAR, LocalDate.of(2026, 1, 1))
        val list = listOf(Expense(1, 30000, 1, "", at(2026, 2, 1)))
        // Mid-March: three months have started, so the average is over three.
        val current = buildDashboard(p, list, 0, emptyMap(), monthlyBudget = 10000, today = LocalDate.of(2026, 3, 15))
        assertEquals(10000L, current.avgPerMonth)
        assertEquals(120000L, current.periodBudget)
        // A past year averages over all twelve months.
        val past = buildDashboard(p, list, 0, emptyMap(), monthlyBudget = 0, today = LocalDate.of(2027, 5, 1))
        assertEquals(2500L, past.avgPerMonth)
        assertEquals(0L, past.periodBudget)
    }

    @Test
    fun budgetOnlyAppliesToMonthAndYear() {
        val week = Period.of(PeriodType.WEEK, LocalDate.of(2026, 10, 16))
        val month = Period.of(PeriodType.MONTH, LocalDate.of(2026, 10, 16))
        assertEquals(0L, buildDashboard(week, emptyList(), 0, emptyMap(), 5000).periodBudget)
        assertEquals(5000L, buildDashboard(month, emptyList(), 0, emptyMap(), 5000).periodBudget)
    }
}
