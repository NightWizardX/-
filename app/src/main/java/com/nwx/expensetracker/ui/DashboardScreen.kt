package com.nwx.expensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nwx.expensetracker.data.CategoryTotal
import com.nwx.expensetracker.data.DashboardData
import com.nwx.expensetracker.data.Expense
import com.nwx.expensetracker.data.Money
import com.nwx.expensetracker.data.Period
import com.nwx.expensetracker.data.PeriodType
import com.nwx.expensetracker.data.Repository
import com.nwx.expensetracker.data.ThaiDate
import com.nwx.expensetracker.data.categoryOf
import com.nwx.expensetracker.data.toLocalDateTime
import com.nwx.expensetracker.data.toUtcMillis
import com.nwx.expensetracker.data.utcToLocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
private val UP_COLOR = Color(0xFFE53935)
private val DOWN_COLOR = Color(0xFF43A047)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    period: Period,
    onPeriodChange: (Period) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val version by repo.changes.collectAsState()
    val data by produceState<DashboardData?>(null, period, version) {
        value = withContext(Dispatchers.IO) { repo.dashboard(period) }
    }
    var filterCategory by remember(period) { mutableStateOf<Long?>(null) }
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("รายจ่ายของฉัน", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "ตั้งค่า") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("บันทึกรายจ่าย") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "type") {
                PeriodTypeSelector(period.type) { type ->
                    onPeriodChange(
                        if (type == PeriodType.RANGE) Period(type, period.start, period.end)
                        else Period.of(type, period.anchor())
                    )
                }
            }
            item(key = "nav") {
                PeriodNavigator(
                    period = period,
                    onShift = { onPeriodChange(period.shift(it)) },
                    onPick = { showPicker = true },
                    onToday = { onPeriodChange(Period.of(period.type, LocalDate.now())) },
                )
            }

            val d = data
            if (d == null) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                item(key = "summary") { SummaryCard(d, period) }
                if (d.total > 0) {
                    item(key = "bars") {
                        SectionCard(title = barTitle(period)) { BarChart(d.bars) }
                    }
                    item(key = "categories") {
                        CategoryCard(d, filterCategory) { id ->
                            filterCategory = if (filterCategory == id) null else id
                        }
                    }
                }
                transactionList(d, filterCategory, onEdit, onClearFilter = { filterCategory = null })
            }
        }
    }

    if (showPicker) {
        PeriodPickerDialog(
            period = period,
            onDismiss = { showPicker = false },
            onPicked = {
                showPicker = false
                onPeriodChange(it)
            },
        )
    }
}

private fun barTitle(p: Period) = when {
    p.type == PeriodType.DAY -> "รายจ่ายตามช่วงเวลาของวัน"
    p.days <= 62 -> "รายจ่ายรายวัน"
    else -> "รายจ่ายรายเดือน"
}

@Composable
private fun PeriodTypeSelector(selected: PeriodType, onSelect: (PeriodType) -> Unit) {
    val types = PeriodType.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        types.forEachIndexed { i, type ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(i, types.size),
                icon = {},
            ) { Text(type.label, maxLines = 1) }
        }
    }
}

@Composable
private fun PeriodNavigator(period: Period, onShift: (Int) -> Unit, onPick: () -> Unit, onToday: () -> Unit) {
    val today = LocalDate.now()
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShift(-1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "ก่อนหน้า")
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onPick)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.DateRange,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                period.label(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { onShift(1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "ถัดไป")
        }
    }
    if (period.type != PeriodType.RANGE && today !in period) {
        TextButton(onClick = onToday, modifier = Modifier.fillMaxWidth()) {
            Text(
                when (period.type) {
                    PeriodType.DAY -> "กลับไปวันนี้"
                    PeriodType.WEEK -> "กลับไปสัปดาห์นี้"
                    else -> "กลับไปเดือนนี้"
                }
            )
        }
    }
}

@Composable
private fun SectionCard(title: String?, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

@Composable
private fun SummaryCard(d: DashboardData, period: Period) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("ใช้ไปทั้งหมด", style = MaterialTheme.typography.labelLarge)
            Text(
                Money.format(d.total),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append("${d.expenses.size} รายการ")
                    if (period.days > 1) append(" · เฉลี่ย ${Money.format(d.avgPerDay)}/วัน")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            CompareText(d.total, d.prevTotal, period)

            if (period.type == PeriodType.MONTH && d.monthlyBudget > 0) {
                Spacer(Modifier.height(14.dp))
                val ratio = d.total.toFloat() / d.monthlyBudget
                val over = d.total > d.monthlyBudget
                LinearProgressIndicator(
                    progress = { ratio.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = if (over) UP_COLOR else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (over) "เกินงบ ${Money.format(d.total - d.monthlyBudget)} (งบ ${Money.format(d.monthlyBudget)})"
                    else "งบเดือนนี้ ${Money.format(d.monthlyBudget)} · เหลือ ${Money.format(d.monthlyBudget - d.total)} (${(ratio * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (over) UP_COLOR else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun CompareText(total: Long, prev: Long, period: Period) {
    val prevName = when (period.type) {
        PeriodType.DAY -> "เมื่อวาน"
        PeriodType.WEEK -> "สัปดาห์ก่อน"
        PeriodType.MONTH -> "เดือนก่อน"
        PeriodType.RANGE -> "ช่วงก่อนหน้า"
    }
    if (prev == 0L) {
        Text("$prevName ไม่มีรายจ่าย", style = MaterialTheme.typography.bodySmall)
        return
    }
    val diff = total - prev
    val pct = diff * 100 / prev
    val (arrow, color) = when {
        diff > 0 -> "▲" to UP_COLOR
        diff < 0 -> "▼" to DOWN_COLOR
        else -> "•" to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Text(
        "$arrow ${kotlin.math.abs(pct)}% เทียบ$prevName (${Money.format(prev)})",
        style = MaterialTheme.typography.bodySmall,
        color = color,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun CategoryCard(d: DashboardData, selected: Long?, onSelect: (Long) -> Unit) {
    SectionCard(title = "แยกตามหมวดหมู่") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DonutChart(
                slices = d.byCategory.map { Color(it.category.color) to it.total },
                modifier = Modifier.size(170.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${d.byCategory.size} หมวด", style = MaterialTheme.typography.labelMedium)
                    Text(
                        Money.compact(d.total),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        d.byCategory.forEach { ct -> CategoryRow(ct, d.total, selected == ct.category.id) { onSelect(ct.category.id) } }
        Text(
            "แตะหมวดหมู่เพื่อกรองรายการด้านล่าง",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CategoryRow(ct: CategoryTotal, total: Long, selected: Boolean, onClick: () -> Unit) {
    val share = if (total > 0) ct.total.toFloat() / total else 0f
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(ct.category, 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ct.category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(Money.format(ct.total), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { share },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp),
                    color = Color(ct.category.color),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
                Text(
                    "  ${(share * 100).toInt()}% · ${ct.count} ครั้ง",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.transactionList(
    d: DashboardData,
    filter: Long?,
    onEdit: (Long) -> Unit,
    onClearFilter: () -> Unit,
) {
    val list = if (filter == null) d.expenses else d.expenses.filter { it.categoryId == filter }
    item(key = "list-title") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(
                if (filter == null) "รายการทั้งหมด"
                else "รายการ: ${categoryOf(filter, d.categories).name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (filter != null) {
                IconButton(onClick = onClearFilter) { Icon(Icons.Filled.Close, contentDescription = "ล้างตัวกรอง") }
            }
        }
    }
    if (list.isEmpty()) {
        item(key = "empty") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🧾", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "ยังไม่มีรายจ่ายในช่วงนี้\nกดปุ่ม \"บันทึกรายจ่าย\" เพื่อเริ่มจดได้เลย",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    val byDay = list.groupBy { it.time.toLocalDateTime().toLocalDate() }
    byDay.forEach { (day, items) ->
        item(key = "day-$day") {
            Row(Modifier.padding(top = 4.dp)) {
                Text(
                    ThaiDate.relative(day),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Money.format(items.sumOf { it.amount }),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        items(items, key = { it.id }) { e -> ExpenseRow(e, d, onEdit) }
    }
}

@Composable
private fun ExpenseRow(e: Expense, d: DashboardData, onEdit: (Long) -> Unit) {
    val cat = categoryOf(e.categoryId, d.categories)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit(e.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { CategoryBadge(cat) },
            headlineContent = {
                Text(e.note.ifBlank { cat.name }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text("${cat.name} · ${e.time.toLocalDateTime().format(TIME_FMT)} น.")
            },
            trailingContent = {
                Text(
                    Money.format(e.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodPickerDialog(period: Period, onDismiss: () -> Unit, onPicked: (Period) -> Unit) {
    if (period.type == PeriodType.RANGE) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = period.start.toUtcMillis(),
            initialSelectedEndDateMillis = period.end.toUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val s = state.selectedStartDateMillis?.utcToLocalDate() ?: return@TextButton
                        val e = state.selectedEndDateMillis?.utcToLocalDate() ?: s
                        onPicked(Period(PeriodType.RANGE, s, e))
                    },
                ) { Text("ตกลง") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } },
        ) {
            DateRangePicker(
                state = state,
                modifier = Modifier.weight(1f),
                title = { Text("เลือกช่วงวันที่", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
            )
        }
    } else {
        val state = rememberDatePickerState(initialSelectedDateMillis = period.start.toUtcMillis())
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPicked(Period.of(period.type, it.utcToLocalDate())) }
                        ?: onDismiss()
                }) { Text("ตกลง") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } },
        ) {
            DatePicker(state = state)
        }
    }
}
