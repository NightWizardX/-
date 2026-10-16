package com.nwx.expensetracker.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    period: Period,
    listState: LazyListState,
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
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    // Rows already animated in for this period, so scrolling back doesn't replay the entrance.
    val seenRows = remember(period) { mutableSetOf<Long>() }
    var dragX by remember { mutableFloatStateOf(0f) }

    val shift: (Int) -> Unit = { n ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onPeriodChange(period.shift(n))
    }
    val onDelete: (Expense) -> Unit = { e ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            withContext(Dispatchers.IO) { repo.deleteExpense(e.id) }
            val result = snackbar.showSnackbar(
                message = "ลบ ${Money.format(e.amount)} แล้ว",
                actionLabel = "เลิกทำ",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                withContext(Dispatchers.IO) { repo.restoreExpense(e) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("รายจ่ายของฉัน", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "ตั้งค่า") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                expanded = listState.firstVisibleItemIndex < 2,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("บันทึกรายจ่าย") },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Swipe left/right anywhere (except on a transaction row) to change period.
                .pointerInput(period) {
                    val threshold = 72.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { dragX = 0f },
                        onDragEnd = {
                            when {
                                dragX > threshold -> shift(-1)
                                dragX < -threshold -> shift(1)
                            }
                            dragX = 0f
                        },
                        onDragCancel = { dragX = 0f },
                    ) { change, amount ->
                        change.consume()
                        dragX += amount
                    }
                },
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "type") {
                PeriodTypeSelector(period.type) { type ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPeriodChange(
                        if (type == PeriodType.RANGE) Period(type, period.start, period.end)
                        else Period.of(type, period.anchor())
                    )
                }
            }
            item(key = "nav") {
                Box(
                    Modifier.graphicsLayer {
                        translationX = dragX * 0.4f
                        alpha = 1f - (abs(dragX) / 500f).coerceAtMost(0.5f)
                    }
                ) {
                    PeriodNavigator(
                        period = period,
                        onShift = shift,
                        onPick = { showPicker = true },
                        onToday = { onPeriodChange(Period.of(period.type, LocalDate.now())) },
                    )
                }
            }

            val d = data
            if (d == null) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                item(key = "summary") {
                    Appear { SummaryCard(d, period) }
                }
                if (d.total > 0) {
                    item(key = "bars") {
                        Box(Modifier.animateItem()) {
                            Appear(delayMillis = 100) {
                                SectionCard(title = barTitle(period)) { BarChart(d.bars) }
                            }
                        }
                    }
                    item(key = "categories") {
                        Box(Modifier.animateItem()) {
                            Appear(delayMillis = 200) {
                                CategoryCard(d, filterCategory) { id ->
                                    filterCategory = if (filterCategory == id) null else id
                                }
                            }
                        }
                    }
                }
                transactionList(
                    d = d,
                    filter = filterCategory,
                    seenRows = seenRows,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onClearFilter = { filterCategory = null },
                )
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

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun PeriodNavigator(period: Period, onShift: (Int) -> Unit, onPick: () -> Unit, onToday: () -> Unit) {
    val today = LocalDate.now()
    Column {
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
                // The label slides in the direction you moved in time.
                AnimatedContent(
                    targetState = period,
                    transitionSpec = {
                        val forward = targetState.start > initialState.start
                        (slideInHorizontally(tween(300)) { w -> if (forward) w else -w } + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(tween(300)) { w -> if (forward) -w else w } + fadeOut(tween(200)))
                    },
                    label = "periodLabel",
                ) { p ->
                    Text(
                        p.label(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
}

@Composable
private fun SectionCard(title: String?, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
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

private fun prevName(type: PeriodType) = when (type) {
    PeriodType.DAY -> "เมื่อวาน"
    PeriodType.WEEK -> "สัปดาห์ก่อน"
    PeriodType.MONTH -> "เดือนก่อน"
    PeriodType.RANGE -> "ช่วงก่อนหน้า"
}

private fun moodEmoji(d: DashboardData, period: Period): String {
    if (period.type == PeriodType.MONTH && d.monthlyBudget > 0) {
        val ratio = d.total.toFloat() / d.monthlyBudget
        return when {
            ratio > 1f -> "😱"
            ratio > 0.8f -> "😬"
            ratio > 0.5f -> "🙂"
            else -> "😎"
        }
    }
    return when {
        d.total == 0L -> "🌱"
        d.prevTotal == 0L -> "💸"
        d.total < d.prevTotal -> "😊"
        d.total > d.prevTotal -> "😅"
        else -> "🙂"
    }
}

/** Gradient hero card with floating bubbles, a count-up total and an animated budget bar. */
@Composable
private fun SummaryCard(d: DashboardData, period: Period) {
    val cs = MaterialTheme.colorScheme
    val onHero = cs.onPrimary
    val bubbles = rememberInfiniteTransition(label = "bubbles")
    val drift by bubbles.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary)))
                .drawBehind {
                    val bubble = Color.White.copy(alpha = 0.10f)
                    drawCircle(
                        bubble,
                        radius = size.height * 0.75f,
                        center = Offset(size.width * (0.92f - 0.08f * drift), size.height * (0.05f + 0.15f * drift)),
                    )
                    drawCircle(
                        bubble,
                        radius = size.height * 0.35f,
                        center = Offset(size.width * (0.10f + 0.06f * drift), size.height * (1.05f - 0.1f * drift)),
                    )
                    drawCircle(
                        bubble,
                        radius = size.height * 0.12f,
                        center = Offset(size.width * (0.55f + 0.1f * drift), size.height * (0.25f + 0.2f * drift)),
                    )
                }
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "ใช้ไปทั้งหมด",
                        style = MaterialTheme.typography.labelLarge,
                        color = onHero,
                        modifier = Modifier.weight(1f),
                    )
                    MoodEmoji(moodEmoji(d, period))
                }
                AnimatedMoney(
                    value = d.total,
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = onHero,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append("${d.expenses.size} รายการ")
                        if (period.days > 1) append(" · เฉลี่ย ${Money.format(d.avgPerDay)}/วัน")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = onHero.copy(alpha = 0.9f),
                )
                Spacer(Modifier.height(10.dp))
                ComparePill(d.total, d.prevTotal, prevName(period.type), onHero)

                if (period.type == PeriodType.MONTH && d.monthlyBudget > 0) {
                    Spacer(Modifier.height(14.dp))
                    val ratio = d.total.toFloat() / d.monthlyBudget
                    val over = d.total > d.monthlyBudget
                    val animRatio by animateFloatAsState(
                        targetValue = ratio.coerceIn(0f, 1f),
                        animationSpec = tween(1000, easing = FastOutSlowInEasing),
                        label = "budget",
                    )
                    val barColor by animateColorAsState(
                        if (over) Color(0xFFFFCDD2) else onHero,
                        label = "budgetColor",
                    )
                    LinearProgressIndicator(
                        progress = { animRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp),
                        color = barColor,
                        trackColor = onHero.copy(alpha = 0.25f),
                        strokeCap = StrokeCap.Round,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (over) "เกินงบ ${Money.format(d.total - d.monthlyBudget)} (งบ ${Money.format(d.monthlyBudget)})"
                        else "งบเดือนนี้ ${Money.format(d.monthlyBudget)} · เหลือ ${Money.format(d.monthlyBudget - d.total)} (${(ratio * 100).toInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = onHero,
                        fontWeight = if (over) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/** Emoji that pops in with a springy bounce whenever the mood changes. */
@Composable
private fun MoodEmoji(emoji: String) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(emoji) {
        scale.snapTo(0.3f)
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow))
    }
    Text(emoji, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.scale(scale.value))
}

@Composable
private fun ComparePill(total: Long, prev: Long, prevName: String, onHero: Color) {
    val text = if (prev == 0L) {
        "$prevName ไม่มีรายจ่าย"
    } else {
        val diff = total - prev
        val pct = abs(diff * 100 / prev)
        when {
            diff > 0 -> "▲ ใช้มากกว่า$prevName $pct% (${Money.format(prev)})"
            diff < 0 -> "▼ ใช้น้อยกว่า$prevName $pct% เก่งมาก!"
            else -> "เท่ากับ$prevName"
        }
    }
    Box(
        Modifier
            .background(onHero.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = onHero, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun CategoryCard(d: DashboardData, selected: Long?, onSelect: (Long) -> Unit) {
    val selectedIndex = d.byCategory.indexOfFirst { it.category.id == selected }.takeIf { it >= 0 }
    SectionCard(title = "แยกตามหมวดหมู่") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DonutChart(
                slices = d.byCategory.map { Color(it.category.color) to it.total },
                modifier = Modifier.size(190.dp),
                selectedIndex = selectedIndex,
                onSliceClick = { i -> onSelect(d.byCategory[i].category.id) },
            ) {
                AnimatedContent(
                    targetState = selectedIndex?.let { d.byCategory[it] },
                    transitionSpec = {
                        (fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.8f)) togetherWith
                            (fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.8f))
                    },
                    label = "donutCenter",
                ) { ct ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (ct == null) {
                            Text("${d.byCategory.size} หมวด", style = MaterialTheme.typography.labelMedium)
                            AnimatedMoney(
                                value = d.total,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                compact = true,
                            )
                        } else {
                            Text(ct.category.emoji, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                ct.category.name,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                Money.compact(ct.total),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        d.byCategory.forEachIndexed { i, ct ->
            CategoryRow(ct, d.total, selected == ct.category.id, index = i) { onSelect(ct.category.id) }
        }
        Text(
            "แตะกราฟหรือหมวดหมู่เพื่อกรองรายการด้านล่าง",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CategoryRow(ct: CategoryTotal, total: Long, selected: Boolean, index: Int, onClick: () -> Unit) {
    val share = if (total > 0) ct.total.toFloat() / total else 0f
    // Bars fill from 0 shortly after the card appears, one after another.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val animShare by animateFloatAsState(
        targetValue = if (started) share else 0f,
        animationSpec = tween(900, delayMillis = 250 + index * 80, easing = FastOutSlowInEasing),
        label = "share",
    )
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "rowBg",
    )
    val badgeScale by animateFloatAsState(
        if (selected) 1.15f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "badge",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.scale(badgeScale)) { CategoryBadge(ct.category, 36.dp) }
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
                    progress = { animShare },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp),
                    color = Color(ct.category.color),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
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

private fun LazyListScope.transactionList(
    d: DashboardData,
    filter: Long?,
    seenRows: MutableSet<Long>,
    onEdit: (Long) -> Unit,
    onDelete: (Expense) -> Unit,
    onClearFilter: () -> Unit,
) {
    val list = if (filter == null) d.expenses else d.expenses.filter { it.categoryId == filter }
    item(key = "list-title") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .animateItem()
                .padding(top = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (filter == null) "รายการทั้งหมด"
                    else "รายการ: ${categoryOf(filter, d.categories).name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (list.isNotEmpty()) {
                    Text(
                        "ปัดรายการไปทางซ้ายเพื่อลบ · ปัดที่ว่างเพื่อเปลี่ยนช่วงเวลา",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (filter != null) {
                IconButton(onClick = onClearFilter) { Icon(Icons.Filled.Close, contentDescription = "ล้างตัวกรอง") }
            }
        }
    }
    if (list.isEmpty()) {
        item(key = "empty") { EmptyState(Modifier.animateItem()) }
        return
    }
    var rowIndex = 0
    val byDay = list.groupBy { it.time.toLocalDateTime().toLocalDate() }
    byDay.forEach { (day, dayItems) ->
        item(key = "day-$day") {
            Row(
                Modifier
                    .animateItem()
                    .padding(top = 4.dp)
            ) {
                Text(
                    ThaiDate.relative(day),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Money.format(dayItems.sumOf { it.amount }),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        dayItems.forEach { e ->
            val stagger = rowIndex.coerceAtMost(8) * 50
            rowIndex++
            item(key = e.id) {
                Box(Modifier.animateItem()) {
                    Appear(delayMillis = stagger, enabled = seenRows.add(e.id)) {
                        SwipeableExpenseRow(e, d, onEdit, onDelete)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val bounce = rememberInfiniteTransition(label = "empty")
    val dy by bounce.animateFloat(
        initialValue = 0f,
        targetValue = -14f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bounce",
    )
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "🧾",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.graphicsLayer { translationY = dy.dp.toPx() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "ยังไม่มีรายจ่ายในช่วงนี้\nกดปุ่ม \"บันทึกรายจ่าย\" เพื่อเริ่มจดได้เลย",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableExpenseRow(
    e: Expense,
    d: DashboardData,
    onEdit: (Long) -> Unit,
    onDelete: (Expense) -> Unit,
) {
    // confirmValueChange may be called more than once per swipe; delete only once.
    val fired = remember { BooleanArray(1) }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                if (!fired[0]) {
                    fired[0] = true
                    onDelete(e)
                }
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val armed = state.targetValue == SwipeToDismissBoxValue.EndToStart
            val bg by animateColorAsState(
                if (armed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.errorContainer,
                label = "swipeBg",
            )
            val iconScale by animateFloatAsState(
                if (armed) 1.3f else 0.9f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "swipeIcon",
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(bg, RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "ลบ",
                    tint = if (armed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.scale(iconScale),
                )
            }
        },
    ) {
        ExpenseRow(e, d, onEdit)
    }
}

@Composable
private fun ExpenseRow(e: Expense, d: DashboardData, onEdit: (Long) -> Unit) {
    val cat = categoryOf(e.categoryId, d.categories)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit(e.id) },
        shape = RoundedCornerShape(12.dp),
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
