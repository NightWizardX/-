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

internal fun csvEscape(s: String): String =
    if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

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
