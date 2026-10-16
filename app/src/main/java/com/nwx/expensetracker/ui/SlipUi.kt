package com.nwx.expensetracker.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nwx.expensetracker.data.Bank
import com.nwx.expensetracker.data.Expense
import com.nwx.expensetracker.data.Money
import com.nwx.expensetracker.data.SlipInfo
import com.nwx.expensetracker.data.TIME_FORMAT
import com.nwx.expensetracker.data.ThaiDate
import com.nwx.expensetracker.data.toLocalDateTime

/** Material's "Receipt" icon, which isn't in material-icons-core. */
val ReceiptIcon: ImageVector by lazy {
    materialIcon(name = "Filled.Receipt") {
        materialPath {
            moveTo(18f, 17f)
            horizontalLineTo(6f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(12f)
            verticalLineToRelative(2f)
            close()
            moveTo(18f, 13f)
            horizontalLineTo(6f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(12f)
            verticalLineToRelative(2f)
            close()
            moveTo(18f, 9f)
            horizontalLineTo(6f)
            verticalLineTo(7f)
            horizontalLineToRelative(12f)
            verticalLineToRelative(2f)
            close()
            moveTo(3f, 22f)
            lineToRelative(1.5f, -1.5f)
            lineTo(6f, 22f)
            lineToRelative(1.5f, -1.5f)
            lineTo(9f, 22f)
            lineToRelative(1.5f, -1.5f)
            lineTo(12f, 22f)
            lineToRelative(1.5f, -1.5f)
            lineTo(15f, 22f)
            lineToRelative(1.5f, -1.5f)
            lineTo(18f, 22f)
            lineToRelative(1.5f, -1.5f)
            lineTo(21f, 22f)
            verticalLineTo(2f)
            lineToRelative(-1.5f, 1.5f)
            lineTo(18f, 2f)
            lineToRelative(-1.5f, 1.5f)
            lineTo(15f, 2f)
            lineToRelative(-1.5f, 1.5f)
            lineTo(12f, 2f)
            lineToRelative(-1.5f, 1.5f)
            lineTo(9f, 2f)
            lineTo(7.5f, 3.5f)
            lineTo(6f, 2f)
            lineTo(4.5f, 3.5f)
            lineTo(3f, 2f)
            verticalLineToRelative(20f)
            close()
        }
    }
}

/** A receipt with a glowing line sweeping down and up it, while OCR runs. */
@Composable
private fun SlipScanAnimation() {
    val transition = rememberInfiniteTransition(label = "scan")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sweep",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    val color = MaterialTheme.colorScheme.primary
    Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
        Icon(
            ReceiptIcon,
            contentDescription = null,
            tint = color.copy(alpha = 0.8f),
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                },
        )
        Canvas(Modifier.size(88.dp)) {
            val y = size.height * (0.12f + 0.76f * sweep)
            val glow = 22.dp.toPx()
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = 0.3f), Color.Transparent), y - glow, y + glow),
                topLeft = Offset(0f, y - glow),
                size = Size(size.width, glow * 2),
            )
            drawLine(color, Offset(6.dp.toPx(), y), Offset(size.width - 6.dp.toPx(), y), 3.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
fun SlipScanDialog(onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { SlipScanAnimation() },
        title = { Text("กำลังอ่านสลิป…") },
        text = {
            Text(
                "อ่านในเครื่องนี้เท่านั้น ไม่มีการส่งรูปออกไปไหน",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = onCancel) { Text("ยกเลิก") } },
    )
}

@Composable
fun BankPill(bank: Bank) {
    val color = Color(bank.color)
    Text(
        bank.label,
        color = if (color.luminance() > 0.5f) Color.Black else Color.White,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** What was read from the slip, and whether the same payment seems to be recorded already. */
@Composable
fun SlipCard(info: SlipInfo, duplicate: Expense?, onPickAnother: () -> Unit, modifier: Modifier = Modifier) {
    val found = info.amount != null
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (found) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(ReceiptIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    if (found) "อ่านจากสลิปแล้ว" else "อ่านยอดเงินจากรูปนี้ไม่ได้",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                info.bank?.let { BankPill(it) }
                Box(Modifier.size(4.dp))
            }
            info.receiver?.let { Text("ผู้รับ: $it", style = MaterialTheme.typography.bodyMedium) }
            Text(
                if (found) "ตรวจยอดเงิน วันและเวลาอีกครั้งก่อนกดบันทึก" else "กรอกยอดเงินเองได้เลย หรือเลือกรูปสลิปที่ชัดกว่านี้",
                style = MaterialTheme.typography.bodySmall,
            )
            duplicate?.let { d ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    val t = d.time.toLocalDateTime()
                    Text(
                        "มีรายการ ${Money.format(d.amount)} ${ThaiDate.relative(t.toLocalDate())} ${t.format(TIME_FORMAT)} น. อยู่แล้ว อาจเป็นสลิปเดิม",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TextButton(onClick = onPickAnother, modifier = Modifier.align(Alignment.End)) { Text("เลือกรูปอื่น") }
        }
    }
}
