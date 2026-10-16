package com.nwx.expensetracker.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nwx.expensetracker.data.Bar
import com.nwx.expensetracker.data.Category
import com.nwx.expensetracker.data.Money
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F0B8),
    onPrimaryContainer = Color(0xFF002204),
    secondaryContainer = Color(0xFFD5E8D0),
    onSecondaryContainer = Color(0xFF101F10),
    tertiary = Color(0xFF00796B),
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
    tertiary = Color(0xFF80CBC4),
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

/** Ease-out with a small overshoot, so bars "pop" into place. */
private val BackOut = Easing { x ->
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val t = x - 1f
    1f + c3 * t * t * t + c1 * t * t
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

/** Fades and slides its content up the first time it is shown. */
@Composable
fun Appear(delayMillis: Int = 0, enabled: Boolean = true, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(delayMillis.toLong())
            progress.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
        }
    }
    Box(
        Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 36.dp.toPx()
        }
    ) { content() }
}

/** A money amount that counts up/down to its new value. */
@Composable
fun AnimatedMoney(
    value: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    compact: Boolean = false,
) {
    // Last value drawn; plain holder (not state) so writing it during composition is harmless.
    val lastShown = remember { LongArray(1) }
    val from = remember { LongArray(1) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(value) {
        from[0] = lastShown[0]
        progress.snapTo(0f)
        progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val shown = from[0] + ((value - from[0]) * progress.value.toDouble()).roundToLong()
    lastShown[0] = shown
    Text(
        text = if (compact) Money.compact(shown) else Money.format(shown),
        style = style,
        color = color,
        modifier = modifier,
    )
}

/** Bars grow in with a stagger; tap a bar for a tooltip. Dashed line = average. */
@Composable
fun BarChart(bars: List<Bar>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    if (bars.isEmpty()) return
    var selected by remember(bars) { mutableStateOf<Int?>(null) }
    val grow = remember(bars) { Animatable(0f) }
    LaunchedEffect(bars) { grow.animateTo(1f, tween(1100, easing = LinearEasing)) }
    val haptic = LocalHapticFeedback.current

    val max = (bars.maxOfOrNull { it.value } ?: 0L).coerceAtLeast(1L)
    val avg = bars.sumOf { it.value } / bars.size
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val tipStyle = MaterialTheme.typography.labelMedium.copy(
        color = MaterialTheme.colorScheme.inverseOnSurface,
        fontWeight = FontWeight.SemiBold,
    )
    val tipBg = MaterialTheme.colorScheme.inverseSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val avgColor = MaterialTheme.colorScheme.tertiary

    Column(modifier) {
        val sel = selected?.let { bars.getOrNull(it) }
        Text(
            text = if (sel != null) "${sel.detail}: ${Money.format(sel.value)}"
            else "เส้นประ = ค่าเฉลี่ย ${Money.format(avg)} · แตะแท่งเพื่อดูยอด",
            style = MaterialTheme.typography.bodySmall,
            color = if (sel != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .pointerInput(bars) {
                    detectTapGestures { offset ->
                        val slot = size.width.toFloat() / bars.size
                        val idx = (offset.x / slot).toInt().coerceIn(0, bars.lastIndex)
                        selected = if (selected == idx) null else idx
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
        ) {
            val labelHeight = 18.dp.toPx()
            val topRoom = 28.dp.toPx() // space for the tooltip and the overshoot
            val chartHeight = size.height - labelHeight
            val usable = chartHeight - topRoom
            val slot = size.width / bars.size
            val barWidth = (slot * 0.68f).coerceAtMost(32.dp.toPx())
            val t = grow.value
            val n = bars.size

            drawLine(gridColor, Offset(0f, chartHeight), Offset(size.width, chartHeight), strokeWidth = 2f)
            drawLine(gridColor, Offset(0f, chartHeight - usable), Offset(size.width, chartHeight - usable), strokeWidth = 1f)

            bars.forEachIndexed { i, bar ->
                if (bar.value > 0) {
                    val delayFrac = if (n > 1) i.toFloat() / (n - 1) * 0.45f else 0f
                    val local = ((t - delayFrac) / 0.55f).coerceIn(0f, 1f)
                    val full = (bar.value.toFloat() / max * usable).coerceAtLeast(3.dp.toPx())
                    val h = full * BackOut.transform(local)
                    if (h > 0f) {
                        val x = i * slot + (slot - barWidth) / 2
                        val active = selected == null || selected == i
                        val c = if (active) color else color.copy(alpha = 0.3f)
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(c, c.copy(alpha = c.alpha * 0.55f)),
                                startY = chartHeight - h,
                                endY = chartHeight,
                            ),
                            topLeft = Offset(x, chartHeight - h),
                            size = Size(barWidth, h),
                            cornerRadius = CornerRadius(barWidth / 4, barWidth / 4),
                        )
                    }
                }
                if (bar.showLabel) {
                    val layout = measurer.measure(AnnotatedString(bar.label), labelStyle)
                    val lx = (i * slot + slot / 2 - layout.size.width / 2)
                        .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
                    drawText(layout, topLeft = Offset(lx, chartHeight + 3.dp.toPx()))
                }
            }

            if (avg > 0) {
                val y = chartHeight - avg.toFloat() / max * usable
                drawLine(
                    color = avgColor,
                    start = Offset(0f, y),
                    end = Offset(size.width * t, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                )
            }

            val si = selected
            if (si != null && bars[si].value > 0) {
                val layout = measurer.measure(AnnotatedString(Money.compact(bars[si].value)), tipStyle)
                val padH = 8.dp.toPx()
                val padV = 4.dp.toPx()
                val w = layout.size.width + padH * 2
                val h = layout.size.height + padV * 2
                val barTop = chartHeight - bars[si].value.toFloat() / max * usable
                val x = (si * slot + slot / 2 - w / 2).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
                val y = (barTop - h - 4.dp.toPx()).coerceAtLeast(0f)
                drawRoundRect(tipBg, Offset(x, y), Size(w, h), CornerRadius(6.dp.toPx(), 6.dp.toPx()))
                drawText(layout, topLeft = Offset(x + padH, y + padV))
            }
        }
    }
}

/**
 * Donut that sweeps in with a little swirl. Tap a slice to select it; the selected slice
 * gets thicker and the others fade.
 */
@Composable
fun DonutChart(
    slices: List<Pair<Color, Long>>,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onSliceClick: ((Int) -> Unit)? = null,
    center: @Composable () -> Unit = {},
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val sweep = remember(slices) { Animatable(0f) }
    LaunchedEffect(slices) { sweep.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    val dim by animateFloatAsState(if (selectedIndex != null) 1f else 0f, tween(350), label = "donutDim")
    val total = slices.sumOf { it.second }.toFloat()
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier.pointerInput(slices, onSliceClick) {
            val click = onSliceClick ?: return@pointerInput
            detectTapGestures { o ->
                if (total <= 0f) return@detectTapGestures
                val dx = o.x - size.width / 2f
                val dy = o.y - size.height / 2f
                val r = sqrt(dx * dx + dy * dy)
                val outer = min(size.width, size.height) / 2f
                if (r < outer * 0.55f || r > outer) return@detectTapGestures
                var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 90f
                if (angle < 0f) angle += 360f
                var acc = 0f
                slices.forEachIndexed { i, (_, v) ->
                    acc += v / total * 360f
                    if (angle <= acc) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        click(i)
                        return@detectTapGestures
                    }
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.14f
            val d = size.minDimension - stroke * 1.5f
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            val arcSize = Size(d, d)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (total > 0f) {
                val p = sweep.value
                var start = -90f - 120f * (1f - p)
                val gap = if (slices.size > 1) 1.5f else 0f
                slices.forEachIndexed { i, (c, v) ->
                    val angle = v / total * 360f * p
                    val isSelected = selectedIndex == i
                    val alpha = if (selectedIndex == null || isSelected) 1f else 1f - 0.7f * dim
                    val width = if (isSelected) stroke * (1f + 0.45f * dim) else stroke
                    if (angle > gap) {
                        drawArc(
                            c.copy(alpha = alpha), start + gap / 2, angle - gap, false, topLeft, arcSize,
                            style = Stroke(width),
                        )
                    }
                    start += angle
                }
            }
        }
        center()
    }
}
