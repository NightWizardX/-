package com.nwx.expensetracker.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** [label] is kept short: five of them share one row on a small phone. */
enum class PeriodType(val label: String) {
    DAY("วัน"),
    WEEK("สัปดาห์"),
    MONTH("เดือน"),
    YEAR("ปี"),
    RANGE("ช่วง"),
}

/** An inclusive date range [start]..[end]. */
data class Period(val type: PeriodType, val start: LocalDate, val end: LocalDate) {
    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1

    fun startMillis(): Long = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun endMillisExclusive(): Long = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    fun shift(n: Int): Period = when (type) {
        PeriodType.DAY -> of(type, start.plusDays(n.toLong()))
        PeriodType.WEEK -> of(type, start.plusWeeks(n.toLong()))
        PeriodType.MONTH -> of(type, start.plusMonths(n.toLong()))
        PeriodType.YEAR -> of(type, start.plusYears(n.toLong()))
        PeriodType.RANGE -> {
            val len = days.toLong() * n
            Period(type, start.plusDays(len), end.plusDays(len))
        }
    }

    fun label(): String = when (type) {
        PeriodType.DAY -> ThaiDate.long(start)
        PeriodType.MONTH -> ThaiDate.monthYear(start)
        PeriodType.YEAR -> ThaiDate.year(start)
        PeriodType.WEEK, PeriodType.RANGE ->
            if (start == end) ThaiDate.short(start) else "${ThaiDate.short(start)} – ${ThaiDate.short(end)}"
    }

    /** A date inside this period to use when switching to another period type. */
    fun anchor(today: LocalDate = LocalDate.now()): LocalDate = if (today in this) today else end

    companion object {
        fun of(type: PeriodType, anchor: LocalDate): Period = when (type) {
            PeriodType.DAY -> Period(type, anchor, anchor)
            PeriodType.WEEK -> {
                val s = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                Period(type, s, s.plusDays(6))
            }
            PeriodType.MONTH -> {
                val s = anchor.withDayOfMonth(1)
                Period(type, s, s.plusMonths(1).minusDays(1))
            }
            PeriodType.YEAR -> {
                val s = anchor.withDayOfYear(1)
                Period(type, s, s.plusYears(1).minusDays(1))
            }
            PeriodType.RANGE -> Period(type, anchor.minusDays(29), anchor)
        }
    }
}

/** "HH:mm", shared by the UI and the CSV backup. */
val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

object Money {
    // DecimalFormat is costly to build and not thread-safe; format() runs every frame of a count-up.
    // Fixed symbols: "1,234.5" everywhere, matching what parse() accepts.
    private val SYMBOLS = DecimalFormatSymbols(Locale.US)
    private val FULL = ThreadLocal.withInitial { DecimalFormat("#,##0.##", SYMBOLS) }
    private val SHORT = ThreadLocal.withInitial { DecimalFormat("#.#", SYMBOLS) }

    /** Up to 12 digits of baht and 2 of satang, e.g. "1234", "12.5", ".75". No signs or exponents. */
    private val AMOUNT = Regex("""\d{1,12}(\.\d{0,2})?|\.\d{1,2}""")

    fun format(satang: Long): String =
        "฿" + FULL.get()!!.format(BigDecimal.valueOf(satang, 2))

    /** Compact form for tight spaces, e.g. ฿1.2k */
    fun compact(satang: Long): String {
        val baht = satang / 100.0
        return when {
            baht >= 1_000_000 -> "฿" + SHORT.get()!!.format(baht / 1_000_000) + "M"
            baht >= 10_000 -> "฿" + SHORT.get()!!.format(baht / 1_000) + "k"
            else -> format(satang)
        }
    }

    /** For pre-filling an input field: 12.5, 100 */
    fun input(satang: Long): String = BigDecimal.valueOf(satang, 2).stripTrailingZeros().toPlainString()

    /**
     * Parses "1,234.50" into satang. Returns null if invalid or not positive.
     * The strict format check runs before BigDecimal so input like "1E999999999" from an
     * imported file can't make it allocate a gigantic number.
     */
    fun parse(text: String): Long? {
        val clean = text.replace(",", "").replace("฿", "").trim()
        if (!AMOUNT.matches(clean)) return null
        val v = BigDecimal(clean).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        return if (v > 0) v else null
    }
}

object ThaiDate {
    private val MONTHS = arrayOf(
        "มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน", "พฤษภาคม", "มิถุนายน",
        "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม",
    )
    private val MONTHS_SHORT = arrayOf(
        "ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.",
        "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.",
    )
    private val DOW = arrayOf("จันทร์", "อังคาร", "พุธ", "พฤหัสบดี", "ศุกร์", "เสาร์", "อาทิตย์")
    private val DOW_SHORT = arrayOf("จ.", "อ.", "พ.", "พฤ.", "ศ.", "ส.", "อา.")

    private fun buddhistYear(d: LocalDate) = d.year + 543

    fun short(d: LocalDate) = "${d.dayOfMonth} ${MONTHS_SHORT[d.monthValue - 1]} ${buddhistYear(d) % 100}"
    fun long(d: LocalDate) = "${DOW[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${MONTHS[d.monthValue - 1]} ${buddhistYear(d)}"
    fun dayMonth(d: LocalDate) = "${d.dayOfMonth} ${MONTHS_SHORT[d.monthValue - 1]}"
    fun monthYear(d: LocalDate) = "${MONTHS[d.monthValue - 1]} ${buddhistYear(d)}"
    fun monthShort(ym: YearMonth) = "${MONTHS_SHORT[ym.monthValue - 1]} ${(ym.year + 543) % 100}"
    fun monthAbbr(ym: YearMonth) = MONTHS_SHORT[ym.monthValue - 1]
    fun year(d: LocalDate) = "ปี ${buddhistYear(d)}"
    fun dowShort(d: LocalDate) = DOW_SHORT[d.dayOfWeek.value - 1]

    /** Header for a day in the transaction list. */
    fun relative(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
        today -> "วันนี้ · ${dayMonth(d)}"
        today.minusDays(1) -> "เมื่อวาน · ${dayMonth(d)}"
        else -> long(d)
    }
}

fun Long.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
fun LocalDateTime.toMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Material date pickers work with UTC midnight millis. */
fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
fun Long.utcToLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

data class Bar(val label: String, val value: Long, val detail: String, val showLabel: Boolean)

data class CategoryTotal(val category: Category, val total: Long, val count: Int)

data class DashboardData(
    val expenses: List<Expense>,
    val total: Long,
    val prevTotal: Long,
    val avgPerDay: Long,
    val avgPerMonth: Long,
    val byCategory: List<CategoryTotal>,
    val bars: List<Bar>,
    val categories: Map<Long, Category>,
    val monthlyBudget: Long,
    /** Budget for the period shown: the monthly budget for a month, 12x it for a year, 0 otherwise. */
    val periodBudget: Long,
)

private val UNKNOWN_CATEGORY = Category(id = -1, name = "ไม่ระบุหมวด", emoji = "❓", color = 0xFF9E9E9E.toInt(), hidden = true)

/** Keeps the real id for a missing category so filtering by it still matches its expenses. */
fun categoryOf(id: Long, categories: Map<Long, Category>): Category = categories[id] ?: UNKNOWN_CATEGORY.copy(id = id)

fun buildDashboard(
    period: Period,
    expenses: List<Expense>,
    prevTotal: Long,
    categories: Map<Long, Category>,
    monthlyBudget: Long,
    today: LocalDate = LocalDate.now(),
): DashboardData {
    val total = expenses.sumOf { it.amount }
    // For a period still in progress, average over the days elapsed so far.
    val elapsedDays = when {
        today in period -> ChronoUnit.DAYS.between(period.start, today).toInt() + 1
        else -> period.days
    }
    val firstMonth = YearMonth.from(period.start)
    val elapsedMonths = ChronoUnit.MONTHS.between(
        firstMonth,
        YearMonth.from(if (today in period) today else period.end),
    ).toInt() + 1
    val byCategory = expenses.groupBy { it.categoryId }
        .map { (id, list) -> CategoryTotal(categoryOf(id, categories), list.sumOf { it.amount }, list.size) }
        .sortedByDescending { it.total }
    return DashboardData(
        expenses = expenses,
        total = total,
        prevTotal = prevTotal,
        avgPerDay = total / elapsedDays.coerceAtLeast(1),
        avgPerMonth = total / elapsedMonths.coerceAtLeast(1),
        byCategory = byCategory,
        bars = buildBars(period, expenses),
        categories = categories,
        monthlyBudget = monthlyBudget,
        periodBudget = when (period.type) {
            PeriodType.MONTH -> monthlyBudget
            PeriodType.YEAR -> monthlyBudget * 12
            else -> 0L
        },
    )
}

fun buildBars(period: Period, expenses: List<Expense>): List<Bar> {
    val times = expenses.map { it.time.toLocalDateTime() to it.amount }
    return when {
        period.type == PeriodType.DAY -> {
            val sums = LongArray(24)
            times.forEach { (t, a) -> sums[t.hour] += a }
            (0 until 24).map { h ->
                Bar(
                    label = String.format(Locale.ROOT, "%02d", h),
                    value = sums[h],
                    detail = String.format(Locale.ROOT, "%02d:00–%02d:59", h, h),
                    showLabel = h % 6 == 0,
                )
            }
        }
        period.days <= 62 -> {
            val byDay = times.groupBy({ it.first.toLocalDate() }, { it.second }).mapValues { it.value.sum() }
            val step = if (period.type == PeriodType.WEEK || period.days <= 10) 1 else (period.days + 6) / 7
            (0 until period.days).map { i ->
                val d = period.start.plusDays(i.toLong())
                Bar(
                    label = if (period.type == PeriodType.WEEK) ThaiDate.dowShort(d) else d.dayOfMonth.toString(),
                    value = byDay[d] ?: 0L,
                    detail = ThaiDate.long(d),
                    showLabel = i % step == 0,
                )
            }
        }
        else -> {
            val byMonth = times.groupBy({ YearMonth.from(it.first) }, { it.second }).mapValues { it.value.sum() }
            val first = YearMonth.from(period.start)
            val last = YearMonth.from(period.end)
            val count = ChronoUnit.MONTHS.between(first, last).toInt() + 1
            // A year shows every month (the chart skips labels that would overlap); longer ranges thin out.
            val step = if (period.type == PeriodType.YEAR) 1 else (count + 5) / 6
            (0 until count).map { i ->
                val ym = first.plusMonths(i.toLong())
                Bar(
                    label = if (first.year == last.year) ThaiDate.monthAbbr(ym) else ThaiDate.monthShort(ym),
                    value = byMonth[ym] ?: 0L,
                    detail = ThaiDate.monthYear(ym.atDay(1)),
                    showLabel = i % step == 0,
                )
            }
        }
    }
}
