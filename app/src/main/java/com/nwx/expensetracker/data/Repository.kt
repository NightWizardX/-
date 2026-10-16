package com.nwx.expensetracker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

private val DEFAULT_CATEGORIES = listOf(
    Triple("อาหาร", "🍜", 0xFFFF7043),
    Triple("เครื่องดื่ม", "☕", 0xFF8D6E63),
    Triple("เดินทาง", "🚗", 0xFF42A5F5),
    Triple("ช้อปปิ้ง", "🛍️", 0xFFEC407A),
    Triple("บิล/ค่าน้ำไฟ", "💡", 0xFFFFCA28),
    Triple("ที่พัก", "🏠", 0xFF7E57C2),
    Triple("สุขภาพ", "💊", 0xFF66BB6A),
    Triple("บันเทิง", "🎬", 0xFFAB47BC),
    Triple("การศึกษา", "📚", 0xFF26A69A),
    Triple("อื่นๆ", "📦", 0xFF78909C),
)

private class ExpenseDb(context: Context) : SQLiteOpenHelper(context, "expenses.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, " +
                "emoji TEXT NOT NULL, color INTEGER NOT NULL, hidden INTEGER NOT NULL DEFAULT 0, " +
                "sort INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, amount INTEGER NOT NULL, " +
                "category_id INTEGER NOT NULL, note TEXT NOT NULL DEFAULT '', time INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_expenses_time ON expenses(time)")
        DEFAULT_CATEGORIES.forEachIndexed { i, (name, emoji, color) ->
            db.insert("categories", null, ContentValues().apply {
                put("name", name)
                put("emoji", emoji)
                put("color", color.toInt())
                put("sort", i)
            })
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

class Repository private constructor(context: Context) {
    private val helper = ExpenseDb(context.applicationContext)
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _changes = MutableStateFlow(0)

    /** Bumped on every write so screens know to reload. */
    val changes: StateFlow<Int> = _changes

    private fun changed() = _changes.update { it + 1 }

    // ---------- categories ----------

    fun categories(): List<Category> =
        helper.readableDatabase.rawQuery(
            "SELECT id, name, emoji, color, hidden FROM categories ORDER BY sort, id", null
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Category(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4) != 0))
                }
            }
        }

    fun saveCategory(cat: Category): Long {
        val db = helper.writableDatabase
        val values = ContentValues().apply {
            put("name", cat.name)
            put("emoji", cat.emoji)
            put("color", cat.color)
            put("hidden", if (cat.hidden) 1 else 0)
        }
        val id = if (cat.id == 0L) {
            val nextSort = db.rawQuery("SELECT COALESCE(MAX(sort), 0) + 1 FROM categories", null)
                .use { if (it.moveToFirst()) it.getInt(0) else 0 }
            values.put("sort", nextSort)
            db.insert("categories", null, values)
        } else {
            db.update("categories", values, "id = ?", arrayOf(cat.id.toString()))
            cat.id
        }
        changed()
        return id
    }

    fun categoryUsage(id: Long): Int =
        helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM expenses WHERE category_id = ?", arrayOf(id.toString())
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /** Deletes an unused category. A category that is in use is hidden instead so history stays intact. Returns true if deleted. */
    fun deleteCategory(id: Long): Boolean {
        val db = helper.writableDatabase
        val deleted = if (categoryUsage(id) > 0) {
            db.update("categories", ContentValues().apply { put("hidden", 1) }, "id = ?", arrayOf(id.toString()))
            false
        } else {
            db.delete("categories", "id = ?", arrayOf(id.toString()))
            true
        }
        changed()
        return deleted
    }

    // ---------- expenses ----------

    private fun readExpenses(where: String, args: Array<String>?): List<Expense> =
        helper.readableDatabase.rawQuery(
            "SELECT id, amount, category_id, note, time FROM expenses $where ORDER BY time DESC, id DESC", args
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Expense(c.getLong(0), c.getLong(1), c.getLong(2), c.getString(3), c.getLong(4)))
                }
            }
        }

    /** Expenses with start <= time < end, newest first. */
    fun expenses(startMs: Long, endMs: Long): List<Expense> =
        readExpenses("WHERE time >= ? AND time < ?", arrayOf(startMs.toString(), endMs.toString()))

    fun allExpenses(): List<Expense> = readExpenses("", null)

    fun expense(id: Long): Expense? = readExpenses("WHERE id = ?", arrayOf(id.toString())).firstOrNull()

    fun total(startMs: Long, endMs: Long): Long =
        helper.readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE time >= ? AND time < ?",
            arrayOf(startMs.toString(), endMs.toString())
        ).use { if (it.moveToFirst()) it.getLong(0) else 0L }

    fun saveExpense(e: Expense): Long {
        val db = helper.writableDatabase
        val values = ContentValues().apply {
            put("amount", e.amount)
            put("category_id", e.categoryId)
            put("note", e.note)
            put("time", e.time)
        }
        val id = if (e.id == 0L) {
            db.insert("expenses", null, values)
        } else {
            db.update("expenses", values, "id = ?", arrayOf(e.id.toString()))
            e.id
        }
        changed()
        return id
    }

    fun deleteExpense(id: Long) {
        helper.writableDatabase.delete("expenses", "id = ?", arrayOf(id.toString()))
        changed()
    }

    // ---------- settings ----------

    var monthlyBudget: Long
        get() = prefs.getLong("monthly_budget", 0L)
        set(value) {
            prefs.edit().putLong("monthly_budget", value).apply()
            changed()
        }

    var lastCategoryId: Long
        get() = prefs.getLong("last_category", 0L)
        set(value) = prefs.edit().putLong("last_category", value).apply()

    var lastPeriodType: PeriodType
        get() = prefs.getString("last_period", null)
            ?.let { name -> PeriodType.entries.firstOrNull { it.name == name } }
            ?: PeriodType.MONTH
        set(value) = prefs.edit().putString("last_period", value.name).apply()

    // ---------- dashboard ----------

    fun dashboard(period: Period): DashboardData {
        val cats = categories().associateBy { it.id }
        val list = expenses(period.startMillis(), period.endMillisExclusive())
        val prev = period.shift(-1)
        return buildDashboard(
            period = period,
            expenses = list,
            prevTotal = total(prev.startMillis(), prev.endMillisExclusive()),
            categories = cats,
            monthlyBudget = monthlyBudget,
        )
    }

    // ---------- CSV backup ----------

    fun exportCsv(out: OutputStream): Int {
        val cats = categories().associateBy { it.id }
        val all = allExpenses().reversed()
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("﻿") // BOM so Excel / Google Sheets read Thai correctly
            w.write("date,time,amount,category,emoji,note\n")
            for (e in all) {
                val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.time), ZoneId.systemDefault())
                val cat = cats[e.categoryId]
                val row = listOf(
                    dt.toLocalDate().toString(),
                    dt.toLocalTime().format(TIME_FMT),
                    BigDecimal.valueOf(e.amount, 2).toPlainString(),
                    cat?.name ?: "",
                    cat?.emoji ?: "",
                    e.note,
                )
                w.write(row.joinToString(",") { csvEscape(it) })
                w.write("\n")
            }
        }
        return all.size
    }

    /** Imports rows exported by [exportCsv]. Rows that already exist are skipped. Returns the number of rows added. */
    fun importCsv(input: InputStream): Int {
        val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }.removePrefix("﻿")
        val rows = parseCsv(text).filter { r -> r.any { it.isNotBlank() } }
        val db = helper.writableDatabase
        val catsByName = categories().associateBy { it.name }.toMutableMap()
        var added = 0
        db.beginTransaction()
        try {
            for (r in rows) {
                if (r.size < 3 || r[0].trim().equals("date", ignoreCase = true)) continue
                val date = runCatching { LocalDate.parse(r[0].trim()) }.getOrNull() ?: continue
                val time = runCatching { LocalTime.parse(r[1].trim()) }.getOrDefault(LocalTime.NOON)
                val amount = Money.parse(r[2]) ?: continue
                val catName = r.getOrNull(3)?.trim().orEmpty().ifEmpty { "อื่นๆ" }
                val emoji = r.getOrNull(4)?.trim().orEmpty().ifEmpty { "📦" }
                val note = r.getOrNull(5).orEmpty()
                val cat = catsByName.getOrPut(catName) {
                    val c = Category(
                        name = catName,
                        emoji = emoji,
                        color = CATEGORY_COLORS[catsByName.size % CATEGORY_COLORS.size],
                    )
                    c.copy(id = saveCategory(c))
                }
                val ms = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val exists = db.rawQuery(
                    "SELECT 1 FROM expenses WHERE time = ? AND amount = ? AND category_id = ? AND note = ? LIMIT 1",
                    arrayOf(ms.toString(), amount.toString(), cat.id.toString(), note)
                ).use { it.moveToFirst() }
                if (exists) continue
                db.insert("expenses", null, ContentValues().apply {
                    put("amount", amount)
                    put("category_id", cat.id)
                    put("note", note)
                    put("time", ms)
                })
                added++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
        return added
    }

    companion object {
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

        @Volatile
        private var instance: Repository? = null

        fun get(context: Context): Repository =
            instance ?: synchronized(this) {
                instance ?: Repository(context).also { instance = it }
            }
    }
}

private fun csvEscape(s: String): String =
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
