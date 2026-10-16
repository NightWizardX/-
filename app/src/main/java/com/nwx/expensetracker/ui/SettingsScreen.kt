package com.nwx.expensetracker.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nwx.expensetracker.data.CATEGORY_COLORS
import com.nwx.expensetracker.data.Category
import com.nwx.expensetracker.data.Money
import com.nwx.expensetracker.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onCategories: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val scope = rememberCoroutineScope()
    var budgetText by remember { mutableStateOf(repo.monthlyBudget.takeIf { it > 0 }?.let { Money.input(it) } ?: "") }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val msg = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { repo.exportCsv(it) } ?: 0
                }.fold({ "ส่งออก $it รายการเรียบร้อย" }, { "ส่งออกไม่สำเร็จ: ${it.message}" })
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val msg = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { repo.importCsv(it) } ?: 0
                }.fold({ "นำเข้า $it รายการใหม่เรียบร้อย" }, { "นำเข้าไม่สำเร็จ: ${it.message}" })
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ตั้งค่า") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "กลับ") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard("งบประมาณต่อเดือน", "แสดงแถบงบประมาณในหน้าสรุปรายเดือน (เว้นว่าง = ไม่ใช้งบ)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = budgetText,
                        onValueChange = { v -> budgetText = v.filter { it.isDigit() || it == '.' } },
                        prefix = { Text("฿ ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        val v = Money.parse(budgetText) ?: 0L
                        scope.launch(Dispatchers.IO) { repo.monthlyBudget = v }
                        Toast.makeText(
                            context,
                            if (v > 0) "ตั้งงบ ${Money.format(v)}/เดือน แล้ว" else "ปิดการใช้งบประมาณแล้ว",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }) { Icon(Icons.Filled.Check, contentDescription = "บันทึกงบ") }
                }
            }

            SettingsCard("หมวดหมู่", "เพิ่ม แก้ไข หรือซ่อนหมวดหมู่รายจ่าย") {
                OutlinedButton(onClick = onCategories, modifier = Modifier.fillMaxWidth()) { Text("จัดการหมวดหมู่") }
            }

            SettingsCard(
                "สำรองข้อมูล",
                "ข้อมูลเก็บไว้ในเครื่องเท่านั้น แนะนำให้ส่งออกไฟล์ CSV เก็บไว้ (เช่นใน Google Drive) เป็นระยะ " +
                    "ไฟล์นี้เปิดด้วย Excel / Google Sheets ได้ และนำเข้ากลับมาได้ (รายการซ้ำจะถูกข้าม)",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("expenses-${LocalDate.now()}.csv") },
                        modifier = Modifier.weight(1f),
                    ) { Text("ส่งออก CSV") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) },
                        modifier = Modifier.weight(1f),
                    ) { Text("นำเข้า CSV") }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val scope = rememberCoroutineScope()
    val version by repo.changes.collectAsState()
    val categories by produceState(emptyList<Category>(), version) {
        value = withContext(Dispatchers.IO) { repo.categories() }
    }
    var editing by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("จัดการหมวดหมู่") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "กลับ") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = Category(name = "", emoji = "🏷️", color = CATEGORY_COLORS[categories.size % CATEGORY_COLORS.size])
            }) { Icon(Icons.Filled.Add, contentDescription = "เพิ่มหมวดหมู่") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            items(categories, key = { it.id }) { cat ->
                ListItem(
                    modifier = Modifier.clickable { editing = cat },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { CategoryBadge(cat) },
                    headlineContent = {
                        Text(cat.name, color = if (cat.hidden) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified)
                    },
                    supportingContent = if (cat.hidden) {
                        { Text("ซ่อนอยู่ – ไม่แสดงตอนบันทึก") }
                    } else {
                        null
                    },
                )
                HorizontalDivider()
            }
        }
    }

    editing?.let { cat ->
        CategoryDialog(
            initial = cat,
            onDismiss = { editing = null },
            onSave = { updated ->
                editing = null
                scope.launch(Dispatchers.IO) { repo.saveCategory(updated) }
            },
            onDelete = {
                editing = null
                scope.launch {
                    val deleted = withContext(Dispatchers.IO) { repo.deleteCategory(cat.id) }
                    Toast.makeText(
                        context,
                        if (deleted) "ลบหมวด ${cat.name} แล้ว" else "หมวด ${cat.name} มีรายการอยู่ จึงซ่อนไว้แทนการลบ",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryDialog(
    initial: Category,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    var color by remember { mutableStateOf(initial.color) }
    var visible by remember { mutableStateOf(!initial.hidden) }
    val isNew = initial.id == 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "เพิ่มหมวดหมู่" else "แก้ไขหมวดหมู่") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it.take(8) },
                        label = { Text("อีโมจิ") },
                        singleLine = true,
                        modifier = Modifier.width(90.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("ชื่อหมวด") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("สี", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CATEGORY_COLORS.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(c), CircleShape)
                                .then(
                                    if (c == color) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape)
                                    else Modifier
                                )
                                .clickable { color = c },
                        )
                    }
                }
                if (!isNew) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("แสดงตอนบันทึกรายจ่าย", modifier = Modifier.weight(1f))
                        Switch(checked = visible, onCheckedChange = { visible = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && emoji.isNotBlank(),
                onClick = { onSave(initial.copy(name = name.trim(), emoji = emoji.trim(), color = color, hidden = !visible)) },
            ) { Text("บันทึก") }
        },
        dismissButton = {
            Row {
                if (!isNew) {
                    TextButton(onClick = onDelete) { Text("ลบ", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("ยกเลิก") }
            }
        },
    )
}
