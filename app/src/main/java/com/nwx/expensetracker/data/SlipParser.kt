package com.nwx.expensetracker.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs

/** Banks (and a wallet) whose transfer slips the app recognises. [code] is the one in the slip's QR. */
enum class Bank(val code: String?, val label: String, val color: Long, internal val keys: List<String>) {
    KBANK("004", "กสิกรไทย", 0xFF138F2D, listOf("กสิกร", "kasikorn", "kbank", "kplus")),
    SCB("014", "ไทยพาณิชย์", 0xFF4E2E7F, listOf("ไทยพาณิชย์", "scbeasy", "scb")),
    KTB("006", "กรุงไทย", 0xFF00A6E6, listOf("กรุงไทย", "krungthai", "ktb", "เป๋าตัง")),
    BBL("002", "กรุงเทพ", 0xFF1E4598, listOf("กรุงเทพ", "bangkokbank", "bualuang", "บัวหลวง")),
    BAY("025", "กรุงศรี", 0xFFC9A100, listOf("กรุงศรี", "krungsri", "อยุธยา")),
    TTB("011", "ทีทีบี", 0xFF0050F0, listOf("ทหารไทย", "ธนชาต", "ttb", "tmb")),
    GSB("030", "ออมสิน", 0xFFEB198D, listOf("ออมสิน", "gsb", "mymo")),
    BAAC("034", "ธ.ก.ส.", 0xFF4B9A2B, listOf("ธ.ก.ส", "เพื่อการเกษตร", "baac")),
    GHB("033", "ธอส.", 0xFFF57F20, listOf("อาคารสงเคราะห์", "ธ.อ.ส", "ghb")),
    CIMB("022", "ซีไอเอ็มบี", 0xFF7E2F36, listOf("ซีไอเอ็มบี", "cimb")),
    UOB("024", "ยูโอบี", 0xFF0B3B8C, listOf("ยูโอบี", "uob")),
    KKP("069", "เกียรตินาคินภัทร", 0xFF5A4E8C, listOf("เกียรตินาคิน", "kkp")),
    TISCO("067", "ทิสโก้", 0xFF12549B, listOf("ทิสโก้", "tisco")),
    LHB("073", "แลนด์ แอนด์ เฮ้าส์", 0xFF6D6E71, listOf("แลนด์แอนด์เฮ้าส์", "lhbank")),
    ICBC("070", "ไอซีบีซี", 0xFFC4161C, listOf("ไอซีบีซี", "icbc")),
    TCRB("071", "ไทยเครดิต", 0xFFFF8200, listOf("ไทยเครดิต", "thaicredit")),
    ISBT("066", "อิสลามแห่งประเทศไทย", 0xFF184D47, listOf("อิสลาม", "ibank")),
    TRUEMONEY(null, "ทรูมันนี่", 0xFFFF8300, listOf("ทรูมันนี่", "truemoney")),
    ;

    companion object {
        fun ofCode(code: String): Bank? = entries.firstOrNull { it.code == code }
    }
}

/** What could be read from a transfer slip; anything not found is null. */
data class SlipInfo(
    /** In satang. */
    val amount: Long?,
    val date: LocalDate?,
    val time: LocalTime?,
    /** The bank that issued the slip (the payer's bank). */
    val bank: Bank?,
    val receiver: String?,
    val memo: String?,
) {
    /** A note for the expense, e.g. "ค่าข้าว · นาย ปิยะ ทองดี · กสิกรไทย". */
    fun note(): String = listOfNotNull(memo, receiver, bank?.label).joinToString(" · ")
}

/**
 * Reads the amount, date/time, bank, receiver and memo from the OCR text of a Thai bank transfer
 * slip. OCR output is noisy (stray characters, merged columns, misplaced Thai vowels), so the
 * rules here are lenient about the surrounding text and strict about the values they accept.
 *
 * Regexes spell out [0-9] and spaces: Android's ICU engine gives \d and \s Unicode meanings.
 */
object SlipParser {

    /**
     * Reads one OCR text. [ref] is a date near the slip's (the image file's date, or today); it
     * settles two-digit years and month abbreviations that OCR left ambiguous.
     */
    fun parse(text: String, qr: String? = null, ref: LocalDate = LocalDate.now(), today: LocalDate = LocalDate.now()): SlipInfo {
        val lines = normalize(text).lines().map { it.trim() }.filter { it.isNotEmpty() }
        val (date, time) = findDateTime(lines, ref, today)
        return SlipInfo(
            amount = findAmount(lines),
            date = date,
            time = time,
            bank = qr?.let { bankFromQr(it) } ?: findBank(lines),
            receiver = findReceiver(lines),
            memo = findMemo(lines),
        )
    }

    /**
     * Whether a Thai-only OCR pass may do better. With English enabled, OCR sometimes reads Thai
     * words as look-alike Latin letters ("ต.ค." → "A.A.", "มาลี" → "IA").
     */
    fun needsThaiPass(info: SlipInfo): Boolean =
        info.date == null || info.receiver == null || mixedScript(info.receiver) > 0 || (info.memo != null && mixedScript(info.memo) > 0)

    /** Combines a first reading with a second (Thai-only) one, field by field. */
    fun merge(first: SlipInfo, second: SlipInfo): SlipInfo {
        val date = first.date ?: second.date
        val time = when {
            first.date != null -> first.time ?: second.time?.takeIf { second.date == date }
            else -> second.time ?: first.time
        }
        fun better(a: String?, b: String?) = if (a == null || (b != null && mixedScript(b) < mixedScript(a))) b else a
        return SlipInfo(
            amount = first.amount ?: second.amount,
            date = date,
            time = time,
            bank = first.bank ?: second.bank,
            receiver = better(first.receiver, second.receiver),
            memo = better(first.memo, second.memo),
        )
    }

    /** Falls back on the image file's own date and time (when the slip was saved) for what OCR missed. */
    fun withFileTime(info: SlipInfo, fileTime: LocalDateTime?): SlipInfo {
        fileTime ?: return info
        val date = info.date ?: fileTime.toLocalDate()
        val time = info.time ?: fileTime.takeIf { it.toLocalDate() == date }?.toLocalTime()?.withSecond(0)?.withNano(0)
        return info.copy(date = date, time = time)
    }

    /** Letters in the minority script: a name or memo is normally all Thai or all English. */
    private fun mixedScript(s: String): Int {
        val thai = s.count { it in '\u0E01'..'\u0E4E' }
        val latin = s.count { it in 'A'..'Z' || it in 'a'..'z' }
        return minOf(thai, latin)
    }

    /**
     * The QR on Thai e-slips (the bank association's slip verification format) is a TLV string:
     * tag 00 holds {00: "000001", 01: sending bank code, 02: transaction reference}, tag 51 = "TH".
     */
    fun bankFromQr(payload: String): Bank? {
        val top = tlv(payload.trim()) ?: return null
        val inner = top["00"]?.let { tlv(it) } ?: return null
        if (inner["00"] != "000001" || top["51"] != "TH") return null
        return inner["01"]?.let { Bank.ofCode(it) }
    }

    private fun tlv(s: String): Map<String, String>? {
        val out = HashMap<String, String>()
        var i = 0
        while (i < s.length) {
            if (i + 4 > s.length) return null
            val tag = s.substring(i, i + 2)
            val len = s.substring(i + 2, i + 4).toIntOrNull() ?: return null
            if (!tag.all { it in '0'..'9' } || i + 4 + len > s.length) return null
            out[tag] = s.substring(i + 4, i + 4 + len)
            i += 4 + len
        }
        return out
    }

    // ---- Text clean-up ------------------------------------------------------------------------

    private val MULTI_SPACE = Regex(" {2,}")

    internal fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                in '\u0E50'..'\u0E59' -> sb.append('0' + (ch - '\u0E50')) // Thai digits
                '\u200B', '\u200C', '\u200D', '\uFEFF', '\r' -> Unit
                '\u00A0', '\t' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
            // Tesseract writes SARA AM as NIKHAHIT + SARA AA, and SARA AE as two SARA E.
            .replace("\u0E4D\u0E32", "\u0E33")
            .replace("\u0E40\u0E40", "\u0E41")
            .replace(MULTI_SPACE, " ")
    }

    /** Lower case without spaces or dots, for matching labels that OCR spaces out ("จำ นวน:"). */
    private fun key(s: String) = buildString { s.lowercase().forEach { if (!it.isWhitespace() && it != '.') append(it) } }

    /** Lower case without spaces, for bank names: dots tell "ธ.ก.ส." from "ธ.กสิกรไทย". */
    private fun compact(s: String) = buildString { s.lowercase().forEach { if (!it.isWhitespace()) append(it) } }

    private fun namesBank(s: String) = compact(s).let { c -> Bank.entries.any { b -> b.keys.any { it in c } } }

    private fun isThaiConsonant(c: Char) = c in '\u0E01'..'\u0E2E'

    /** Letters, counting Thai vowel and tone marks (which Unicode doesn't class as letters). */
    private fun isWordChar(c: Char) = c.isLetter() || c in '\u0E01'..'\u0E4E'

    // ---- Amount -------------------------------------------------------------------------------

    private val AMOUNT_LABELS = listOf("จำนวน", "ำนวน", "ยอด", "amount", "total", "รวม", "ชำระ", "จ่าย")
    private val NOT_AMOUNT = listOf("ธรรมเนียม", "fee", "คงเหลือ", "balance", "ส่วนลด", "discount", "แต้ม", "point")

    /** 1,250.00 or 1250.00: slips always show two decimals. */
    private val MONEY = Regex("""(?<![0-9.,])([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,12})\.([0-9]{2})(?![0-9]|[.,][0-9]| ?%| ?น\.)""")
    private val SPACED_SEPARATOR = Regex("""(?<=[0-9]) ?([,.]) ?(?=[0-9])""")
    private val O_AS_ZERO = Regex("""(?<=[0-9,.])[Oo]|[Oo](?=[0-9,.][0-9])""")

    private class Money(val line: Int, val satang: Long, val currency: Boolean)

    private fun findAmount(lines: List<String>): Long? {
        val skip = BooleanArray(lines.size)
        val found = ArrayList<Money>()
        lines.forEachIndexed { i, raw ->
            val k = key(raw)
            val line = moneyForm(raw)
            if (NOT_AMOUNT.any { it in k }) {
                skip[i] = true
                // "ค่าธรรมเนียม:" on its own line labels the value on the next line.
                if (i + 1 < lines.size && !MONEY.containsMatchIn(line)) skip[i + 1] = true
            }
            for (m in MONEY.findAll(line)) {
                val whole = m.groupValues[1].replace(",", "")
                if (whole.length > 12) continue
                val satang = whole.toLong() * 100 + m.groupValues[2].toLong()
                val after = line.substring(m.range.last + 1).trimStart().lowercase()
                val before = line.substring(0, m.range.first).trimEnd().lowercase()
                val currency = after.startsWith("บาท") || after.startsWith("thb") || after.startsWith("฿") ||
                    before.endsWith("฿") || before.endsWith("thb") || "บาท" in k || "thb" in k
                found += Money(i, satang, currency)
            }
        }
        val usable = found.filter { !skip[it.line] && it.satang > 0 }
        fun labelled(i: Int) = i >= 0 && !skip[i] && AMOUNT_LABELS.any { it in key(lines[i]) }
        // The value beside its label ("จำนวนเงิน 1,250.00"), or just below it ("จำนวน:" / "1,250.00 บาท").
        usable.firstOrNull { labelled(it.line) }?.let { return it.satang }
        usable.firstOrNull { m -> (1..2).any { d -> labelled(m.line - d) && found.none { it.line == m.line - d } } }
            ?.let { return it.satang }
        return usable.filter { it.currency }.ifEmpty { usable }.maxOfOrNull { it.satang }
    }

    private fun moneyForm(line: String) = line.replace(O_AS_ZERO, "0").replace(SPACED_SEPARATOR, "$1")

    // ---- Date and time ------------------------------------------------------------------------

    /** 16 ต.ค. 69 · 16 ต.ค. 2569 · 16 ตุลาคม 2569 */
    private val TH_DATE = Regex("""(?<![0-9])([0-9]{1,2}) ?([\u0E01-\u0E4E][\u0E01-\u0E4E., ]{0,14}?) ?([0-9]{4}|[0-9]{2})(?![0-9])""")
    /** 16 Oct 2026 · 16-Oct-26 · 16 October, 2026 */
    private val EN_DATE = Regex("""(?<![0-9])([0-9]{1,2})[ \-/.]*([A-Za-z]{3,9})\.?,?[ \-/.]*([0-9]{4}|[0-9]{2})(?![0-9])""")
    /** Oct 16, 2026 */
    private val EN_DATE_US = Regex("""(?<![A-Za-z])([A-Za-z]{3,9})\.? ([0-9]{1,2}),? ([0-9]{4})(?![0-9])""")
    /** 16/10/2569 · 16-10-26 · 2026-10-16 */
    private val NUM_DATE = Regex("""(?<![0-9/.\-])([0-9]{1,4})[/.\-]([0-9]{1,2})[/.\-]([0-9]{2,4})(?![0-9/.\-])""")
    /** 14:23 · 14:23:05 · 2:23 PM · 14.23 น. */
    private val TIME = Regex("""(?<![0-9:.,])([01]?[0-9]|2[0-3]) ?([:.]) ?([0-5][0-9])(?: ?[:.] ?[0-5][0-9])?(?![0-9])( ?[AaPp]\.? ?[Mm]\.?| ?น)?""")

    private val TH_ABBR = mapOf(
        "มค" to 1, "กพ" to 2, "มย" to 4, "พค" to 5, "กค" to 7, "สค" to 8, "กย" to 9, "ตค" to 10, "พย" to 11, "ธค" to 12,
    )
    private val UNIQUE_FIRST = mapOf('ส' to 8, 'ต' to 10, 'ธ' to 12)
    /** Consonants of the full Thai month names (vowels are the part OCR gets wrong most). */
    private val TH_FULL = listOf(
        "มกรคม", "กมภพนธ", "มนคม", "มษยน", "พฤษภคม", "มถนยน", "กรกฎคม", "สงหคม", "กนยยน", "ตลคม", "พฤศจกยน", "ธนวคม",
    )
    private val EN_MONTHS = listOf(
        "january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december",
    )

    private class Found(val date: LocalDate, val line: Int, val end: Int)

    private fun findDateTime(lines: List<String>, ref: LocalDate, today: LocalDate): Pair<LocalDate?, LocalTime?> {
        val latest = today.plusDays(1)
        val found = lines.indices.firstNotNullOfOrNull { i ->
            dateIn(lines[i], i, ref)?.takeIf { it.date <= latest && it.date.year >= 2000 }
        }
        val nearDate = found?.let { f ->
            timeIn(lines[f.line].substring(f.end), dotted = true) ?: lines.getOrNull(f.line + 1)?.let { timeIn(it, dotted = true) }
        }
        return found?.date to (nearDate ?: lines.firstNotNullOfOrNull { timeIn(it, dotted = false) })
    }

    private fun dateIn(line: String, index: Int, ref: LocalDate): Found? {
        for (m in TH_DATE.findAll(line)) {
            val month = thaiMonth(m.groupValues[2], ref) ?: continue
            build(m.groupValues[3], month, m.groupValues[1], ref)?.let { return Found(it, index, m.range.last + 1) }
        }
        for (m in EN_DATE.findAll(line)) {
            val month = englishMonth(m.groupValues[2]) ?: continue
            build(m.groupValues[3], month, m.groupValues[1], ref)?.let { return Found(it, index, m.range.last + 1) }
        }
        for (m in EN_DATE_US.findAll(line)) {
            val month = englishMonth(m.groupValues[1]) ?: continue
            build(m.groupValues[3], month, m.groupValues[2], ref)?.let { return Found(it, index, m.range.last + 1) }
        }
        for (m in NUM_DATE.findAll(line)) {
            val (a, b, c) = m.destructured
            val date = when {
                a.length == 4 -> build(a, b.toInt(), c, ref)
                a.length <= 2 -> build(c, b.toInt(), a, ref)
                else -> null
            }
            date?.let { return Found(it, index, m.range.last + 1) }
        }
        return null
    }

    private fun build(year: String, month: Int, day: String, ref: LocalDate): LocalDate? {
        val y = year.toInt()
        val fullYear = when (year.length) {
            4 -> when {
                y >= 2400 -> y - 543 // Buddhist era
                y in 1900..2200 -> y
                else -> return null
            }
            // Two digits may be either era (69 = 2569 BE, 26 = 2026): take the one nearer the reference.
            2 -> if (abs(2000 + y - ref.year) <= abs(1957 + y - ref.year)) 2000 + y else 1957 + y
            else -> return null
        }
        return runCatching { LocalDate.of(fullYear, month, day.toInt()) }.getOrNull()
    }

    private fun thaiMonth(token: String, ref: LocalDate): Int? {
        val skeleton = token.filter(::isThaiConsonant)
        val upperVowel = token.any { it in '\u0E34'..'\u0E37' }
        when (skeleton) {
            // ม.ค./มี.ค. and เม.ย./มิ.ย. differ only by a vowel, which OCR may drop.
            "มค" -> return if (upperVowel) 3 else 1
            "มย" -> return when {
                '\u0E40' in token -> 4
                upperVowel -> 6
                abs(ref.monthValue - 4) <= abs(ref.monthValue - 6) -> 4
                else -> 6
            }
        }
        TH_ABBR[skeleton]?.let { return it }
        // ส.ค., ต.ค. and ธ.ค. are the only months starting with their letter ("ส.ุด." is still August).
        if (skeleton.length == 2) UNIQUE_FIRST[skeleton[0]]?.let { return it }
        if (skeleton.length < 4) return null
        val exact = TH_FULL.indexOf(skeleton)
        if (exact >= 0) return exact + 1
        return TH_FULL.withIndex().filter { editDistance(it.value, skeleton) <= 1 }.singleOrNull()?.index?.plus(1)
    }

    private fun englishMonth(word: String): Int? {
        val w = word.lowercase()
        val i = EN_MONTHS.indexOfFirst { it.startsWith(w.take(3)) }
        return if (i >= 0 && (EN_MONTHS[i].startsWith(w) || w == "sept")) i + 1 else null
    }

    private fun timeIn(text: String, dotted: Boolean): LocalTime? {
        for (m in TIME.findAll(text)) {
            val (h, sep, min, suffix) = m.destructured
            val marker = suffix.trim().lowercase()
            // A dot only separates hours and minutes beside the date or before "น." (otherwise it's money).
            if (sep == "." && !(dotted || marker.startsWith("น"))) continue
            var hour = h.toInt()
            if (marker.startsWith("p") && hour in 1..11) hour += 12
            if (marker.startsWith("a") && hour == 12) hour = 0
            return LocalTime.of(hour, min.toInt())
        }
        return null
    }

    private fun editDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    // ---- Bank, receiver, memo -----------------------------------------------------------------

    /** The first bank named on the slip; the payer's bank is normally listed before the receiver's. */
    private fun findBank(lines: List<String>): Bank? {
        val text = lines.joinToString("\n") { compact(it) }
        return Bank.entries
            .mapNotNull { bank -> bank.keys.map { text.indexOf(it) }.filter { it >= 0 }.minOrNull()?.let { bank to it } }
            .minByOrNull { it.second }?.first
    }

    private val TO_MARKERS = listOf("ไปยัง", "ไปที่", "ผู้รับเงิน", "ชื่อผู้รับ", "ผู้รับ", "ถึง", "payee", "receiver", "recipient")
    private val FROM_MARKERS = listOf("จาก", "ผู้โอน", "ผู้ส่ง", "ผู้จ่าย", "from", "sender")
    private val NAME_PREFIX = Regex(
        """(นางสาว|นาง|นาย|น\.ส\.?|ด\.ช\.?|ด\.ญ\.?|คุณ|บจก\.?|บมจ\.?|หจก\.?|บริษัท|ห้างหุ้นส่วน|ร้าน|(?<![A-Za-z])(?:mrs|mr|ms|miss)\.?(?= ))""",
        RegexOption.IGNORE_CASE,
    )
    private val NOT_NAME = listOf("รหัส", "เลขที่", "อ้างอิง", "ref", "ธรรมเนียม", "จำนวน", "สำเร็จ", "สแกน", "ตรวจสอบ", "คิวอาร์")
    /** A masked account or phone number ("xxx-x-x1234-x", "xxx-xxx-1234"). */
    private val ACCOUNT = Regex("""[xX*]{2,}-|[0-9]{4}-|-[0-9]{4}|[0-9]{3}-[0-9]""")
    /** Where a name ends: an account, phone or reference number. */
    private val NAME_END = Regex("""[xX*0-9]{3}-|[0-9]{4,}""")

    private fun findReceiver(lines: List<String>): String? {
        // "ไปยัง นาย ปิยะ", or a "To" line with the name below it (logos above the name read as junk).
        for ((i, line) in lines.withIndex()) {
            val k = key(line)
            val marker = TO_MARKERS.firstOrNull { k.startsWith(it) } ?: if (k == "to" || k == "to:") "to" else continue
            val near = listOf(line.substringAfter(':', "").ifBlank { dropMarker(line, marker) }) +
                lines.subList(i + 1, minOf(lines.size, i + 4))
            near.firstOrNull { NAME_PREFIX.containsMatchIn(it) }?.let { l -> cleanName(l)?.let { return it } }
            near.firstNotNullOfOrNull { l -> cleanName(l)?.takeIf { looksLikeWords(it) } }?.let { return it }
        }
        // Otherwise slips list the payer first and the receiver second.
        val hasFrom = lines.any { l -> FROM_MARKERS.any { key(l).startsWith(it) } }
        val names = lines.withIndex()
            .filter { (_, l) -> NAME_PREFIX.containsMatchIn(l) && NOT_NAME.none { it in key(l) } }
            .mapNotNull { (i, l) -> cleanName(l)?.let { i to it } }
        return when {
            names.size >= 2 -> names[1].second
            // A lone name that follows an account number is the receiver (the payer had no title).
            names.size == 1 && !hasFrom && lines.take(names[0].first).any { ACCOUNT.containsMatchIn(it) } -> names[0].second
            else -> null
        }
    }

    /** Not OCR junk: has a word of three or more letters. */
    private fun looksLikeWords(s: String) = s.split(' ').any { w -> w.count(::isWordChar) >= 3 }

    /** [line] after [marker] (in [key] form), skipping the spaces and dots OCR put inside it. */
    private fun dropMarker(line: String, marker: String): String {
        var matched = 0
        var cut = 0
        while (cut < line.length && matched < marker.length) {
            val c = line[cut].lowercaseChar()
            if (!c.isWhitespace() && c != '.') {
                if (c != marker[matched]) break
                matched++
            }
            cut++
        }
        return line.substring(cut)
    }

    private fun cleanName(raw: String): String? {
        val prefix = NAME_PREFIX.find(raw)
        var s = if (prefix != null) raw.substring(prefix.range.first) else raw
        NAME_END.find(s)?.let { s = s.substring(0, it.range.first) }
        s = s.trim { !isWordChar(it) }.replace(MULTI_SPACE, " ")
        if (s.count(::isWordChar) < 3 || NOT_NAME.any { it in key(s) }) return null
        // Without a title, a bank name or a long sentence is not a name.
        if (prefix == null && (s.length > 50 || namesBank(s))) return null
        return safeTake(s, 60)
    }

    private val MEMO_MARKERS = listOf("บันทึกช่วยจำ", "บันทึก", "หมายเหตุ", "memo", "note")

    /** Without junk around it, keeping a final dot ("ค่าไฟ ต.ค."). */
    private fun memoText(s: String) =
        s.trimStart { !isWordChar(it) && it !in '0'..'9' }.trimEnd { !isWordChar(it) && it !in '0'..'9' && it != '.' }

    private fun findMemo(lines: List<String>): String? {
        for ((i, line) in lines.withIndex()) {
            val k = key(line)
            val marker = MEMO_MARKERS.firstOrNull { k.startsWith(it) } ?: continue
            val rest = line.substringAfter(':', "").ifBlank { dropMarker(line, marker) }
            val memo = memoText(rest).ifBlank {
                memoText(lines.getOrNull(i + 1)?.takeIf { next -> !next.trimEnd().endsWith(':') && !MONEY.containsMatchIn(next) }.orEmpty())
            }
            if (memo.count(::isWordChar) >= 2) return safeTake(memo, 50)
        }
        return null
    }
}
