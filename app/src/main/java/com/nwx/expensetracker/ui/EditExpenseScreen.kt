package com.nwx.expensetracker.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nwx.expensetracker.data.Bank
import com.nwx.expensetracker.data.Category
import com.nwx.expensetracker.data.Expense
import com.nwx.expensetracker.data.MAX_NOTE_LENGTH
import com.nwx.expensetracker.data.Money
import com.nwx.expensetracker.data.Repository
import com.nwx.expensetracker.data.SlipInfo
import com.nwx.expensetracker.data.SlipReadException
import com.nwx.expensetracker.data.SlipReader
import com.nwx.expensetracker.data.TIME_FORMAT
import com.nwx.expensetracker.data.ThaiDate
import com.nwx.expensetracker.data.safeTake
import com.nwx.expensetracker.data.toLocalDateTime
import com.nwx.expensetracker.data.toMillis
import com.nwx.expensetracker.data.toUtcMillis
import com.nwx.expensetracker.data.utcToLocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private val LocalDateTimeSaver = Saver<LocalDateTime, String>(
    save = { it.toString() },
    restore = { runCatching { LocalDateTime.parse(it) }.getOrNull() },
)

/** Keeps what was read from a slip across process death (the card and the duplicate check use it). */
private val SlipSaver = listSaver<SlipInfo?, String>(
    save = { info ->
        if (info == null) emptyList() else listOf(
            info.amount?.toString().orEmpty(), info.date?.toString().orEmpty(), info.time?.toString().orEmpty(),
            info.bank?.name.orEmpty(), info.receiver.orEmpty(), info.memo.orEmpty(),
        )
    },
    restore = { v ->
        if (v.size != 6) null else SlipInfo(
            amount = v[0].toLongOrNull(),
            date = runCatching { LocalDate.parse(v[1]) }.getOrNull(),
            time = runCatching { LocalTime.parse(v[2]) }.getOrNull(),
            bank = Bank.entries.firstOrNull { it.name == v[3] },
            receiver = v[4].ifEmpty { null },
            memo = v[5].ifEmpty { null },
        )
    },
)

/** [slipUri]: a slip image to read as soon as the screen opens (picked on the dashboard or shared from another app). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditExpenseScreen(expenseId: Long?, defaultDate: LocalDate, slipUri: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val scope = rememberCoroutineScope()
    val isNew = expenseId == null

    // The form survives the app being killed in the background (rememberSaveable), so a
    // half-typed entry is still there when the user comes back.
    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf(0L) }
    var note by rememberSaveable { mutableStateOf("") }
    var dateTime by rememberSaveable(stateSaver = LocalDateTimeSaver) {
        mutableStateOf(LocalDateTime.of(defaultDate, LocalTime.now().withSecond(0).withNano(0)))
    }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
    // Guards against a double tap (or IME Done + button) inserting the same expense twice.
    var saving by remember { mutableStateOf(false) }
    // Slip reading: the last result, the note it filled in (replaced by the next slip unless the
    // user edited it), whether [slipUri] has been read, and the running OCR job.
    var slip by rememberSaveable(stateSaver = SlipSaver) { mutableStateOf<SlipInfo?>(null) }
    var slipNote by rememberSaveable { mutableStateOf("") }
    var slipUriRead by rememberSaveable { mutableStateOf(slipUri == null) }
    var reading by remember { mutableStateOf<Job?>(null) }
    var duplicate by remember { mutableStateOf<Expense?>(null) }

    val categories by produceState(emptyList<Category>()) {
        value = withContext(Dispatchers.IO) { repo.categories() }
    }
    val amountFocus = remember { FocusRequester() }

    LaunchedEffect(expenseId) {
        if (expenseId != null) {
            // Already loaded means the form was restored with the user's unsaved edits; keep them.
            if (loaded) return@LaunchedEffect
            val e = withContext(Dispatchers.IO) { repo.expense(expenseId) }
            if (e == null) {
                Toast.makeText(context, "ไม่พบรายการนี้แล้ว", Toast.LENGTH_SHORT).show()
                onDone()
                return@LaunchedEffect
            }
            amountText = Money.input(e.amount)
            categoryId = e.categoryId
            note = e.note
            dateTime = e.time.toLocalDateTime()
            loaded = true
        } else if (slipUri == null) {
            delay(150)
            runCatching { amountFocus.requestFocus() }
        }
    }
    // Default to the most recently used category for new entries.
    LaunchedEffect(categories) {
        if (isNew && categories.isNotEmpty() && categories.none { it.id == categoryId && !it.hidden }) {
            val last = repo.lastCategoryId
            categoryId = categories.firstOrNull { it.id == last && !it.hidden }?.id
                ?: categories.firstOrNull { !it.hidden }?.id ?: 0L
        }
    }

    suspend fun applySlip(info: SlipInfo) {
        info.amount?.let {
            amountText = Money.input(it)
            amountError = false
        }
        val date = info.date
        when {
            date != null -> dateTime = LocalDateTime.of(date, info.time ?: dateTime.toLocalTime())
            info.time != null -> dateTime = LocalDateTime.of(dateTime.toLocalDate(), info.time)
        }
        val slipText = safeTake(info.note(), MAX_NOTE_LENGTH)
        if (note.isBlank() || note == slipNote) note = slipText
        slipNote = slipText
        // Paid this receiver before? Use the same category as last time.
        info.receiver?.let { r -> withContext(Dispatchers.IO) { repo.categoryForNote(r) } }?.let { categoryId = it }
        slip = info
    }

    fun readSlip(uri: Uri) {
        if (reading != null) return
        reading = scope.launch {
            try {
                applySlip(SlipReader.read(context, uri))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Throwable: an unusual device could also fail to load the OCR library itself.
                val message = if (e is SlipReadException && e.badImage) "เปิดรูปนี้ไม่ได้ ลองเลือกรูปอีกครั้ง" else "อ่านสลิปไม่สำเร็จ"
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } finally {
                reading = null
                slipUriRead = true
            }
        }
    }

    val slipPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { readSlip(it) }
    }
    fun pickSlip() = slipPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    LaunchedEffect(Unit) {
        if (!slipUriRead && slipUri != null) readSlip(Uri.parse(slipUri))
    }
    // Warn when the slip seems to be recorded already (same amount within two minutes).
    LaunchedEffect(slip, amountText, dateTime) {
        val amount = Money.parse(amountText)
        duplicate = if (slip == null || amount == null) null else withContext(Dispatchers.IO) {
            repo.similarExpense(amount, dateTime.toMillis(), expenseId ?: 0L)
        }
    }

    fun save() {
        if (saving || !loaded || reading != null) return
        val amount = Money.parse(amountText)
        if (amount == null) {
            amountError = true
            return
        }
        if (categoryId == 0L) {
            Toast.makeText(context, "กรุณาเลือกหมวดหมู่", Toast.LENGTH_SHORT).show()
            return
        }
        val expense = Expense(
            id = expenseId ?: 0L,
            amount = amount,
            categoryId = categoryId,
            note = note.trim(),
            time = dateTime.toMillis(),
        )
        saving = true
        scope.launch {
            withContext(Dispatchers.IO) {
                repo.saveExpense(expense)
                repo.lastCategoryId = expense.categoryId
            }
            Toast.makeText(context, "บันทึก ${Money.format(amount)} แล้ว", Toast.LENGTH_SHORT).show()
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "บันทึกรายจ่าย" else "แก้ไขรายจ่าย") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "กลับ")
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "ลบ")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = { save() },
                enabled = loaded && !saving && reading == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp)
                    .height(56.dp),
            ) { Text("บันทึก", style = MaterialTheme.typography.titleMedium) }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isNew) {
                AnimatedContent(
                    targetState = slip,
                    transitionSpec = { (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically()) },
                    contentKey = { it != null },
                    label = "slip",
                    modifier = Modifier.padding(top = 8.dp),
                ) { info ->
                    if (info == null) {
                        FilledTonalButton(onClick = ::pickSlip, modifier = Modifier.fillMaxWidth()) {
                            Icon(ReceiptIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("อ่านจากรูปสลิปโอนเงิน")
                        }
                    } else {
                        SlipCard(info, duplicate, onPickAnother = ::pickSlip)
                    }
                }
            }
            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    val cleaned = input.filter { it.isDigit() || it == '.' }
                    val parts = cleaned.split('.')
                    if (parts.size <= 2 && (parts.getOrNull(1)?.length ?: 0) <= 2 && cleaned.length <= 12) {
                        amountText = cleaned
                        amountError = false
                    }
                },
                label = { Text("จำนวนเงิน (บาท)") },
                prefix = { Text("฿ ", style = MaterialTheme.typography.headlineMedium) },
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                isError = amountError,
                supportingText = { if (amountError) Text("กรุณาใส่จำนวนเงินที่มากกว่า 0") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (isNew) 0.dp else 8.dp)
                    .focusRequester(amountFocus),
            )

            Text("หมวดหมู่", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                categories.filter { !it.hidden || it.id == categoryId }.forEach { cat ->
                    FilterChip(
                        selected = cat.id == categoryId,
                        onClick = { categoryId = cat.id },
                        label = { Text("${cat.emoji} ${cat.name}") },
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = safeTake(it, MAX_NOTE_LENGTH) },
                label = { Text("บันทึกช่วยจำ (ไม่บังคับ)") },
                placeholder = { Text("เช่น ข้าวมันไก่, ค่า BTS") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("วันและเวลา", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { showDate = true },
                    label = { Text(ThaiDate.relative(dateTime.toLocalDate())) },
                    leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                )
                AssistChip(
                    onClick = { showTime = true },
                    label = { Text("${dateTime.format(TIME_FORMAT)} น.") },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val today = LocalDate.now()
                TextButton(onClick = { dateTime = dateTime.with(today) }) { Text("วันนี้") }
                TextButton(onClick = { dateTime = dateTime.with(today.minusDays(1)) }) { Text("เมื่อวาน") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (reading != null) SlipScanDialog(onCancel = { reading?.cancel() })

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dateTime.toLocalDate().toUtcMillis())
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { dateTime = dateTime.with(it.utcToLocalDate()) }
                    showDate = false
                }) { Text("ตกลง") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("ยกเลิก") } },
        ) { DatePicker(state = state) }
    }

    if (showTime) {
        val state = rememberTimePickerState(initialHour = dateTime.hour, initialMinute = dateTime.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    dateTime = dateTime.withHour(state.hour).withMinute(state.minute)
                    showTime = false
                }) { Text("ตกลง") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("ยกเลิก") } },
            title = { Text("เลือกเวลา") },
            text = { TimePicker(state = state) },
        )
    }

    if (confirmDelete && expenseId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("ลบรายการนี้?") },
            text = { Text("${Money.parse(amountText)?.let { Money.format(it) } ?: ""} จะถูกลบถาวร") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.deleteExpense(expenseId) }
                        onDone()
                    }
                }) { Text("ลบ", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("ยกเลิก") } },
        )
    }
}
