package com.nwx.expensetracker.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nwx.expensetracker.data.Bar
import com.nwx.expensetracker.data.Category
import com.nwx.expensetracker.data.Money

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F0B8),
    onPrimaryContainer = Color(0xFF002204),
    secondaryContainer = Color(0xFFD5E8D0),
    onSecondaryContainer = Color(0xFF101F10),
    surface = Color(0xFFF8FBF4),
    background = Color(0xFFF8FBF4),
    surfaceContainerLow = Color(0xFFF1F5EE),
    surfaceContainer = Color(0xFFECF0E8),
    surfaceContainerHigh = Color(0xFFE6EAE2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DD49D),
    onPrimary = Color(0xFF00390C),
    primaryContainer = Color(0xFF145218),
    onPrimaryContainer = Color(0xFFB9F0B8),
    secondaryContainer = Color(0xFF3A4B39),
    onSecondaryContainer = Color(0xFFD5E8D0),
    surface = Color(0xFF111410),
    background = Color(0xFF111410),
    surfaceContainerLow = Color(0xFF191D18),
    surfaceContainer = Color(0xFF1D211C),
    surfaceContainerHigh = Color(0xFF282B26),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Colored circle with the category emoji. */
@Composable
fun CategoryBadge(category: Category, size: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(Color(category.color).copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(category.emoji, fontSize = (size.value * 0.5f).sp)
    }
}

/** Tap a bar to see its exact value. */
@Composable
fun BarChart(bars: List<Bar>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    if (bars.isEmpty()) return
    var selected by remember(bars) { mutableStateOf<Int?>(null) }
    val max = (bars.maxOfOrNull { it.value } ?: 0L).coerceAtLeast(1L)
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier) {
        val sel = selected?.let { bars.getOrNull(it) }
        Text(
            text = if (sel != null) "${sel.detail}: ${Money.format(sel.value)}"
            else "สูงสุด ${Money.format(bars.maxOf { it.value })} · แตะแท่งเพื่อดูยอด",
            style = MaterialTheme.typography.bodySmall,
            color = if (sel != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .pointerInput(bars) {
                    detectTapGestures { offset ->
                        val slot = size.width.toFloat() / bars.size
                        val idx = (offset.x / slot).toInt().coerceIn(0, bars.lastIndex)
                        selected = if (selected == idx) null else idx
                    }
                },
        ) {
            val labelHeight = 18.dp.toPx()
            val chartHeight = size.height - labelHeight
            val slot = size.width / bars.size
            val barWidth = (slot * 0.68f).coerceAtMost(32.dp.toPx())
            for (frac in listOf(0.5f, 1f)) {
                val y = chartHeight - chartHeight * frac
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            drawLine(gridColor, Offset(0f, chartHeight), Offset(size.width, chartHeight), strokeWidth = 2f)
            bars.forEachIndexed { i, bar ->
                if (bar.value > 0) {
                    val h = (bar.value.toFloat() / max * chartHeight).coerceAtLeast(3.dp.toPx())
                    val x = i * slot + (slot - barWidth) / 2
                    val active = selected == null || selected == i
                    drawRoundRect(
                        color = if (active) color else color.copy(alpha = 0.3f),
                        topLeft = Offset(x, chartHeight - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(barWidth / 4, barWidth / 4),
                    )
                }
                if (bar.showLabel) {
                    val layout = measurer.measure(AnnotatedString(bar.label), labelStyle)
                    val lx = (i * slot + slot / 2 - layout.size.width / 2)
                        .coerceIn(0f, size.width - layout.size.width)
                    drawText(layout, topLeft = Offset(lx, chartHeight + 3.dp.toPx()))
                }
            }
        }
    }
}

@Composable
fun DonutChart(
    slices: List<Pair<Color, Long>>,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit = {},
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.15f
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            val arcSize = Size(d, d)
            val total = slices.sumOf { it.second }.toFloat()
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (total > 0f) {
                var start = -90f
                val gap = if (slices.size > 1) 1.2f else 0f
                slices.forEach { (c, v) ->
                    val sweep = v / total * 360f
                    if (sweep > gap) {
                        drawArc(c, start + gap / 2, sweep - gap, false, topLeft, arcSize, style = Stroke(stroke))
                    }
                    start += sweep
                }
            }
        }
        center()
    }
}
