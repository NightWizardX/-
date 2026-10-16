package com.nwx.expensetracker.data

/** Amounts are stored in satang (1/100 baht) to avoid floating point errors. */
data class Expense(
    val id: Long = 0,
    val amount: Long,
    val categoryId: Long,
    val note: String,
    val time: Long,
)

data class Category(
    val id: Long = 0,
    val name: String,
    val emoji: String,
    val color: Int,
    val hidden: Boolean = false,
)

val CATEGORY_COLORS: List<Int> = listOf(
    0xFFEF5350, 0xFFFF7043, 0xFFFFA726, 0xFFFFCA28, 0xFF9CCC65, 0xFF66BB6A,
    0xFF26A69A, 0xFF26C6DA, 0xFF42A5F5, 0xFF5C6BC0, 0xFF7E57C2, 0xFFAB47BC,
    0xFFEC407A, 0xFF8D6E63, 0xFF78909C,
).map { it.toInt() }

/** Longest values accepted for user-entered text (also applied to CSV imports). */
const val MAX_NOTE_LENGTH = 200
const val MAX_CATEGORY_NAME_LENGTH = 40
const val MAX_EMOJI_LENGTH = 16

/** Cuts [s] to at most [max] chars without splitting a surrogate pair (emoji). */
fun safeTake(s: String, max: Int): String {
    if (s.length <= max) return s
    val end = if (Character.isHighSurrogate(s[max - 1])) max - 1 else max
    return s.substring(0, end)
}

private val FORMULA_START = charArrayOf('=', '+', '-', '@', '\t', '\r')

/** True if a spreadsheet would treat [s] as a formula (also when hidden behind leading quotes). */
private fun looksLikeFormula(s: String): Boolean {
    val first = s.trimStart('\'').firstOrNull() ?: return false
    return first in FORMULA_START
}

/**
 * Escapes one CSV cell. Cells a spreadsheet would run as a formula get a leading `'`
 * (CSV/formula injection), which [unguardCsvCell] removes again on import.
 */
internal fun csvEscape(s: String): String {
    val guarded = if (looksLikeFormula(s)) "'$s" else s
    return if (guarded.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + guarded.replace("\"", "\"\"") + "\""
    } else {
        guarded
    }
}

/** Reverses the formula guard added by [csvEscape]. */
internal fun unguardCsvCell(s: String): String =
    if (s.startsWith("'") && looksLikeFormula(s.substring(1))) s.substring(1) else s

internal fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val cell = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        if (inQuotes) {
            if (ch == '"') {
                if (i + 1 < text.length && text[i + 1] == '"') {
                    cell.append('"')
                    i++
                } else {
                    inQuotes = false
                }
            } else {
                cell.append(ch)
            }
        } else {
            when (ch) {
                '"' -> inQuotes = true
                ',' -> {
                    row.add(cell.toString())
                    cell.clear()
                }
                '\r' -> Unit
                '\n' -> {
                    row.add(cell.toString())
                    cell.clear()
                    rows.add(row)
                    row = mutableListOf()
                }
                else -> cell.append(ch)
            }
        }
        i++
    }
    if (cell.isNotEmpty() || row.isNotEmpty()) {
        row.add(cell.toString())
        rows.add(row)
    }
    return rows
}
