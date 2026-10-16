package com.nwx.expensetracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The fixtures are real Tesseract output (tha+eng, PSM 6) for rendered slips in the layouts of
 * several Thai banking apps, junk lines from logos and misread characters included.
 */
class SlipParserTest {
    private val today = LocalDate.of(2026, 10, 16)

    private fun parse(text: String, qr: String? = null, ref: LocalDate = today) = SlipParser.parse(text, qr, ref, today)

    @Test
    fun kplusTransferWithLabelsAboveValues() {
        val info = parse(KPLUS, KPLUS_QR)
        assertEquals(456725L, info.amount) // not the 0.00 fee below it
        assertEquals(LocalDate.of(2026, 9, 15), info.date) // "15 กุย. 69": stray vowel, two-digit B.E. year
        assertEquals(LocalTime.of(16, 46), info.time)
        assertEquals(Bank.KBANK, info.bank)
        assertEquals("MS. ANNA LEE", info.receiver) // the second name; the first is the payer
        assertNull(info.memo)
    }

    @Test
    fun kplusPaymentWithMemo() {
        val info = parse(KPLUS_PAY, KPLUS_PAY_QR)
        assertEquals(12000L, info.amount)
        assertEquals(LocalDate.of(2025, 12, 23), info.date)
        assertEquals(LocalTime.of(23, 35), info.time)
        assertEquals("MS. ANNA LEE", info.receiver)
        assertEquals("ค่ากาแฟ", info.memo)
        assertEquals("ค่ากาแฟ · MS. ANNA LEE · กสิกรไทย", info.note())
    }

    @Test
    fun scbAmountBesideLabelAndReceiverAfterMarker() {
        val info = parse(SCB, SCB_QR)
        assertEquals(3500L, info.amount)
        assertEquals(LocalDateTime.of(2026, 3, 21, 11, 5), info.date!!.atTime(info.time))
        assertEquals(Bank.SCB, info.bank)
        assertTrue(info.receiver!!.startsWith("บริษัท"))
        // "ทรู" came out as "ng": worth a Thai-only pass.
        assertTrue(SlipParser.needsThaiPass(info))
    }

    @Test
    fun krungthaiDateAfterCaptionAndMemoBesideLabel() {
        val info = parse(KTB, KTB_QR)
        assertEquals(3500L, info.amount)
        assertEquals(LocalDateTime.of(2026, 10, 7, 8, 56), info.date!!.atTime(info.time))
        assertEquals(Bank.KTB, info.bank)
        assertEquals("ค่าเช่าห้อง", info.memo)
    }

    @Test
    fun krungsriNumericDateWithSeconds() {
        val info = parse(KRUNGSRI, KRUNGSRI_QR)
        assertEquals(1500000L, info.amount)
        assertEquals(LocalDateTime.of(2026, 1, 27, 14, 54), info.date!!.atTime(info.time))
        assertEquals(Bank.BAY, info.bank)
        assertEquals("MS. ANNA LEE", info.receiver)
        assertEquals("ค่าไฟ ต.ค.", info.memo)
    }

    @Test
    fun gsbFullMonthName() {
        val info = parse(GSB, GSB_QR)
        assertEquals(3500L, info.amount)
        assertEquals(LocalDateTime.of(2026, 1, 30, 11, 36), info.date!!.atTime(info.time))
        assertEquals(Bank.GSB, info.bank)
        assertEquals("บริษัท ทรู มูฟ เอช จำกัด", info.receiver) // "จํากัด" normalised to SARA AM
    }

    @Test
    fun ttbReceiverAfterMarker() {
        val info = parse(TTB, TTB_QR)
        assertEquals(456725L, info.amount)
        assertEquals(LocalDateTime.of(2026, 1, 15, 5, 30), info.date!!.atTime(info.time))
        assertEquals(Bank.TTB, info.bank)
        assertEquals("นาย ปิยะ ทองดี", info.receiver)
        assertEquals("ค่าข้าว", info.memo)
    }

    @Test
    fun englishSlips() {
        val bbl = parse(BBL_EN, BBL_EN_QR)
        assertEquals(1500000L, bbl.amount)
        assertEquals(LocalDateTime.of(2026, 6, 22, 15, 36), bbl.date!!.atTime(bbl.time))
        assertEquals(Bank.BBL, bbl.bank)
        assertEquals("MS ANNA LEE", bbl.receiver)

        val kplus = parse(KPLUS_EN, KPLUS_EN_QR)
        assertEquals(3500L, kplus.amount)
        assertEquals(LocalDateTime.of(2026, 2, 14, 10, 56), kplus.date!!.atTime(kplus.time))
        assertEquals("MS. ANNA LEE", kplus.receiver)
    }

    @Test
    fun bankFromTextWhenThereIsNoQr() {
        assertEquals(Bank.KBANK, parse(KPLUS).bank)
        assertEquals(Bank.BBL, parse(BBL_EN).bank)
        assertEquals(Bank.KBANK, parse(KPLUS_EN).bank)
        assertEquals(Bank.BAAC, parse("โอนเงินสำเร็จ\nธ.ก.ส.\nจำนวน 5.00").bank) // not "ธ.กสิกรไทย"
    }

    @Test
    fun thaiPassFillsTheMonthEnglishModeMisread() {
        // tha+eng read "ก.ค." as "A.A." but got the time; Thai-only read the month but not the time.
        val first = parse(TWO_PASS_MIXED)
        assertNull(first.date)
        assertEquals(LocalTime.of(4, 35), first.time)
        assertTrue(SlipParser.needsThaiPass(first))
        val merged = SlipParser.merge(first, parse(TWO_PASS_THAI))
        assertEquals(LocalDateTime.of(2026, 7, 9, 4, 35), merged.date!!.atTime(merged.time))
        assertEquals(456725L, merged.amount)
    }

    @Test
    fun mergePrefersNamesWrittenInOneScript() {
        val mixed = SlipInfo(100, null, null, null, "น.ส. ASws มั่นคง", null)
        val thai = SlipInfo(100, null, null, null, "น.ส. ศิริพร มั่นคง", null)
        assertEquals("น.ส. ศิริพร มั่นคง", SlipParser.merge(mixed, thai).receiver)
        val english = SlipInfo(100, null, null, null, "MS ANNA LEE", null)
        val junk = SlipInfo(100, null, null, null, "พพล แซซ", null)
        assertEquals("MS ANNA LEE", SlipParser.merge(english, junk).receiver)
    }

    @Test
    fun qrPayload() {
        assertEquals(Bank.KBANK, SlipParser.bankFromQr(KPLUS_QR))
        assertEquals(Bank.SCB, SlipParser.bankFromQr(SCB_QR))
        assertEquals(Bank.BBL, SlipParser.bankFromQr(BBL_EN_QR))
        assertNull(SlipParser.bankFromQr("https://example.com/slip"))
        // A PromptPay payment QR is not a slip.
        assertNull(SlipParser.bankFromQr("00020101021129370016A000000677010111011300668123456785802TH530376463041234"))
        assertNull(SlipParser.bankFromQr("0041000600000101030040220013260121813BTF012935102US9104ABCD"))
        assertNull(SlipParser.bankFromQr("004100060000"))
    }

    @Test
    fun yearsInEitherEra() {
        val be2 = parse("โอนเงินสำเร็จ\n3 ม.ค. 69 09:15 น.\nจำนวน: 50.00 บาท")
        assertEquals(LocalDate.of(2026, 1, 3), be2.date)
        val ce2 = parse("Transfer\n3 Jan 26 09:15\nAmount: 50.00 THB")
        assertEquals(LocalDate.of(2026, 1, 3), ce2.date)
        val be4 = parse("3 มีนาคม 2568 21:05\nจำนวนเงิน 50.00 บาท")
        assertEquals(LocalDate.of(2025, 3, 3), be4.date)
        val us = parse("Oct 2, 2026 2:23 PM\nAmount 50.00")
        assertEquals(LocalDateTime.of(2026, 10, 2, 14, 23), us.date!!.atTime(us.time))
    }

    @Test
    fun similarThaiMonthAbbreviations() {
        assertEquals(1, parse("5 ม.ค. 69 10:00").date!!.monthValue)
        assertEquals(3, parse("5 มี.ค. 69 10:00").date!!.monthValue)
        assertEquals(4, parse("5 เม.ย. 69 10:00").date!!.monthValue)
        assertEquals(6, parse("5 มิ.ย. 69 10:00").date!!.monthValue)
        assertEquals(4, parse("21 เม.ย., 69 06:25 น,").date!!.monthValue) // comma after the month
        assertEquals(8, parse("5 ส.ุด. 69 16:19 น.").date!!.monthValue) // ค misread, ส is still August
        assertEquals(11, parse("5 พฤศจิกายน 2568").date!!.monthValue)
        assertEquals(10, parse("5 ตุลาคบ 2569").date!!.monthValue) // one consonant off
    }

    @Test
    fun futureAndImpossibleDatesAreIgnored() {
        assertNull(parse("45 Jul 26, 17:34\nAmount 60.00").date)
        assertNull(parse("5 ธ.ค. 69 10:00").date) // December 2026 hasn't happened yet
        assertNull(parse("31 ก.พ. 69 10:00").date)
    }

    @Test
    fun fileTimeFillsWhatOcrMissed() {
        val noDate = parse("จำนวน: 120.00 บาท")
        val file = LocalDateTime.of(2026, 9, 11, 10, 34, 27)
        val filled = SlipParser.withFileTime(noDate, file)
        assertEquals(LocalDate.of(2026, 9, 11), filled.date)
        assertEquals(LocalTime.of(10, 34), filled.time)
        // OCR time kept with the file's date.
        val timeOnly = SlipParser.withFileTime(parse("16 A.A. 69 15:15 น\nจำนวน: 60.00 บาท"), file)
        assertEquals(LocalDateTime.of(2026, 9, 11, 15, 15), timeOnly.date!!.atTime(timeOnly.time))
        // A slip from another day doesn't take the file's time.
        val otherDay = SlipParser.withFileTime(parse("3 ม.ค. 69\nจำนวน: 60.00 บาท"), file)
        assertEquals(LocalDate.of(2026, 1, 3), otherDay.date)
        assertNull(otherDay.time)
        assertEquals(noDate, SlipParser.withFileTime(noDate, null))
    }

    @Test
    fun amountRules() {
        // Thai digits, OCR's O for 0, and a space after the comma.
        assertEquals(125000L, parse("จำนวนเงิน ๑,๒๕๐.๐๐ บาท").amount)
        assertEquals(125000L, parse("จำนวน: 1,25O.00 บาท").amount)
        assertEquals(125000L, parse("จำนวนเงิน 1, 250.00").amount)
        // Fee and balance lines never win; the fee label above its value counts too.
        assertEquals(9950L, parse("ค่าธรรมเนียม:\n10.00 บาท\nจำนวน:\n99.50 บาท").amount)
        assertEquals(9950L, parse("ยอดเงินคงเหลือ 15,000.00 บาท\nยอดโอน 99.50 บาท").amount)
        assertEquals(9950L, parse("ยอดเงินที่ใช้ได้ 15,000.00 บาท\nยอดชำระ 99.50 บาท").amount)
        assertEquals(9950L, parse("Available balance 15,000.00 THB\nAmount 99.50 THB").amount)
        // Without labels: the largest value in baht; dates and times are not money.
        assertEquals(30000L, parse("16.10.2569 14.23 น.\n300.00 บาท\n12.50").amount)
        assertNull(parse("ไม่มีตัวเลข").amount)
        assertNull(parse("ค่าธรรมเนียม 0.00 บาท").amount)
    }

    @Test
    fun receiverRules() {
        // Junk lines from logos between the marker and the name are skipped.
        assertEquals("นาย ปิยะ ทองดี", parse("ไปยัง\na ๕ ev\nนาย ปิยะ ทองดี xxx-x-x1234-x").receiver)
        assertEquals("การไฟฟ้านครหลวง", parse("ผู้รับเงิน: การไฟฟ้านครหลวง").receiver)
        // A lone titled name after an account number is the receiver; before one, the payer.
        assertEquals("นาง มาลี แสงทอง", parse("SOMCHAI JAIDEE\nxxx-x-x1234-x\nนาง มาลี แสงทอง").receiver)
        assertEquals("ร้านกาแฟ", parse("นาย สมชาย ใจดี\nxxx-x-x1234-x\nKSHOP ร้านกาแฟ").receiver)
        assertNull(parse("นาย สมชาย ใจดี\nxxx-x-x1234-x\nKSHOP COFFEE").receiver)
        // A footer sentence is not a name.
        assertNull(parse("ผู้รับเงินสามารถสแกนคิวอาร์โค้ดนี้\nเพื่อตรวจสอบสถานะการโอนเงิน").receiver)
    }

    @Test
    fun noteSkipsMissingParts() {
        assertEquals("", SlipInfo(1, null, null, null, null, null).note())
        assertEquals("ไทยพาณิชย์", SlipInfo(1, null, null, Bank.SCB, null, null).note())
        assertFalse(SlipParser.needsThaiPass(parse(GSB, GSB_QR)))
    }

    private val KPLUS = """
โอนเจินสําเร็อ
15 กุย. 69 16:46 u.
๑  นางสาว กมลวรรณ ศรีสุข
ธ.กสิกรไทย
2๐๐-%-%9340-%
ป
๑  MS. ANNA LEE
ธ.กรุงศรีอยุธยา
XXX-XXX-9915
เลขที่รายการ:
026258724063060BTF99292
จํานวน:
4,567.25 บาท
ค่าธรรมเนียม:
0.00 บาท
สแกนตรวจสอบสลิป
"""

    private val KPLUS_QR = "0044000600000101030040223026258724063060BTF992925102TH9104BAC8"

    private val KPLUS_PAY = """
๐      =   ๐   ๕
ชาระเงนสาเรจ
23 ธ.ค. 68 23:35 น.
๑ นาย ธนพล วงศ์ใหญ่
ธ.กสิกรไทย
2๐๐-%-«4404-%
MS. ANNA LEE
รหัสร้านค้า 8818558460
เลขที่รายการ:
02535770364552281177862
จํานวน:
120.00 บาท
ค่าธรรมเนียม:
0.00 บาท
บันทึกช่วยจํา:
ค่ากาแฟ
สแกนตรวจสอบสลิป
"""

    private val KPLUS_PAY_QR = "0044000600000101030040223025357703645522BTF778625102TH91043500"

    private val SCB = """
a   ๐   ๕
โอนเงินสําเร็จ
21 มี.ค. 2569 - 11:05
รหัสอ้างอิง: 2026032111053290088
จาก
๑  นาง มาลี แสงทอง
XXX-XXX471-5
ไปยัง
  บริษัท ng มูฟ เอช จํากัด
XXX-X-X7929-x
จํานวนเงิน                                                 35.00
ผู้รับเงินสามารถสแกนคิวอาร์โค้ดนี
เพือตรวจสอบสถานะการโอนเงิน
"""

    private val SCB_QR = "004000060000010103014021920260321110532900885102TH91043F13"

    private val KTB = """
Krungthai NEXT
a    ๐   เวว
โอนเงินสําเรือ
วันทีทํารายการ 7 ต.ค. 2569 - 08:56

จาก
๑  นาย สมชาย ใจดี

ธ-กรุงไทย

XXX-X-X1594-x
ไปยัง
๑  wig วีระ ส-

ธ+กรุงศรีอยุธยา

XXX-X-X1068-x
จํานวนเงิน                                                               35.00 บาท
ค่าธรรมเนียม                                                         0.00 บาท
รหัสอ้างอิง                                                      A7329259711707
บันทึกช่วยจํา                                                          ค่าเช่าห้อง
"""

    private val KTB_QR = "0035000600000101030060214A73292597117075102TH9104566A"

    private val KRUNGSRI = """
โอนเงินสําเร็จ
วันที่ 27/01/2569 เวลา 14:54:57

จาก
© MR. SOMCHAI JAIDEE

ธ.กรุงศรีอยุธยา

XXX-X-X3885-x
ไปยัง
๑ MS. ANNA LEE

ธ.ออมสิน

Xxx-xxx-3400
จํานวนเงิน                         15,000.00 บาท
ค่าธรรมเนียม                               0.00 บาท
รหัสอ้างอิง                             491147653221
บันทึกช่วยจํา                              ค่าไฟ ต.ค.
"""

    private val KRUNGSRI_QR = "00330006000001010302502124911476532215102TH91042647"

    private val GSB = """
MyMo
โอนเงินสําเร็จ
30 มกราคม 2569 11:36 uw.

จาก
@๑@ นาง มาลี แสงทอง

ธ.ออมสิน

XXX-X-X4892-x
ไปยัง
๑  บริษัท ทรู มูฟ เอช จํากัด

ธ.กรุงไทย

XXX-X-X3478-x
จํานวนเงิน                                     35.00 บาท
ค่าธรรมเนียม                                           0.00 บาท
รหัสอ้างอิง                                 626114284201
บันทึกช่วยจํา                                      ค่าเช่าห้อง
"""

    private val GSB_QR = "00330006000001010303002126261142842015102TH9104654C"

    private val TTB = """
๐         ๐  ๕
ทารายการสาเรจ
15 ม.ค. 69 05:30 น.
จาก
๑ MR. SOMCHAI JAIDEE
ธ.ทหารไทยธนชาต
2๐๐-%-%8706-%
ไปยัง
© นาย ปิยะ ทองดี
ธ.กรุงศรีอยุธยา
XXX-XXX-9158
จํานวนเงิน                        4,567.25 บาท
ค่าธรรมเนียม                           0.00 บาท
รหัสอ้างอิง                          778710784339
บันทึกช่วยจํา                              ค่าข้าว
"""

    private val TTB_QR = "00330006000001010301102127787107843395102TH91048B88"

    private val BBL_EN = """
Bangkok Bank
Successful transfer
22 Jun 26, 15:36

From
  MR. SOMCHAI JAIDEE

Bangkok Bank

XXX-X-X9359-x
To
๑  MS ANNA LEE

ธ.กรุงไทย

XXX-X-X689 3-x
Amount (THB)                                           15,000.00
Fee (THB)                                                             0.00
Transaction reference                                  658932859298
"""

    private val BBL_EN_QR = "00330006000001010300202126589328592985102TH910465CC"

    private val KPLUS_EN = """
Transfer Completed
14 Feb 26 10:56
๑  MR. SOMCHAI J
KASIKORNBANK
XXX-X-X4808-x
๑  MS. ANNA LEE
SCB
XXX-XXX-2205
Transaction ID:
026045781907343BTF40363
Amount:
35.00 THB
Fee:
0.00 THB
"""

    private val KPLUS_EN_QR = "0044000600000101030040223026045781907343BTF403635102TH9104EFAB"

    private val TWO_PASS_MIXED = """
๐         ๐  ๕
ทารายการสาเรจ
9 A.A. 69 04:35 น.
จาก
๑ นางสาว กมลวรรณ ศรีสุข
ธ.ทหารไทยธนชาต
XXX-X-X7 193-x
ไปยัง
 นาย ปิยะ ทองดิ
ธ.กรุงเทพ
XXX-XXX-6607
จํานวนเงิน                          4,567.25 บาท
ค่าธรรมเนียม                           0.00 บาท
รหัสอ้างอิง                          393059242603
"""

    private val TWO_PASS_THAI = """
๐         ๐  ๕
ทารายการสาเรจ
9 ก.ค. 69 04135 น.
จาก
๑ นางสาว กมลวรรณ ศรีสุข
ธ.ทหารไทยธนชาต
2๐๐-%-%๕7193-%
ไปยัง
 นาย ปิยะ ทองดิ
ธ.กรุงเทพ
2๐๐-%๐๐-6607
จํานวนเงิน                          4,567.25 บาท
ค่าธรรมเนียม                           0.00 บาท
รหัสอ้างอิง                          393059242603
"""
}
