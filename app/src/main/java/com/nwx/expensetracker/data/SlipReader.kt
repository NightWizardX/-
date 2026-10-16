package com.nwx.expensetracker.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.googlecode.leptonica.android.ReadFile
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** [badImage]: the file couldn't be opened as an image (as opposed to OCR failing). */
class SlipReadException(val badImage: Boolean, message: String) : Exception(message)

/**
 * Reads a bank transfer slip image entirely on the device: the slip's QR code names the bank, and
 * Tesseract OCR (Thai + English models bundled in the APK) reads the text for [SlipParser].
 * Nothing is sent anywhere; the app has no internet permission.
 */
object SlipReader {
    /** Larger photos are scaled down to this many pixels (a phone screenshot is about 2.5 MP). */
    private const val MAX_PIXELS = 6_000_000
    /** OCR works best with text at least ~20 px tall, which a slip this wide has... */
    private const val MIN_WIDTH = 1080
    /** ...and wider only costs time (tested: no accuracy lost going from 1440 down to 1280). */
    private const val MAX_WIDTH = 1280
    /** Bump when the bundled traineddata changes, so devices copy the new files. */
    private const val MODEL_DIR = "ocr-1"
    private val LANGS = listOf("tha", "eng")

    private val copyLock = Mutex()

    suspend fun read(context: Context, uri: Uri): SlipInfo {
        // Only accept images handed over by a content provider (photo picker, gallery, share);
        // a file:// URI from another app could point at this app's own private files.
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) throw SlipReadException(true, "unsupported uri")
        val resolver = context.contentResolver
        val (bitmap, fileTime) = withContext(Dispatchers.IO) {
            val bmp = runCatching { decode(resolver, uri) }.getOrNull() ?: throw SlipReadException(true, "cannot decode")
            bmp to fileTime(resolver, uri)
        }
        val dataPath = modelPath(context)
        return withContext(Dispatchers.Default) {
            val (qr, page) = prepare(bitmap)
            ensureActive()
            val today = LocalDate.now()
            val ref = fileTime?.toLocalDate() ?: today
            val info = Ocr(dataPath).use { ocr ->
                val first = SlipParser.parse(ocr.read(page, "tha+eng"), qr, ref, today)
                if (!SlipParser.needsThaiPass(first)) first
                else SlipParser.merge(first, SlipParser.parse(ocr.read(page, "tha"), qr, ref, today))
            }
            SlipParser.withFileTime(info, fileTime)
        }
    }

    /** The QR payload, and the page for OCR. Recycles [bitmap]. */
    private fun prepare(bitmap: Bitmap): Pair<String?, Page> {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val (qr, qrBox) = findQr(pixels, bitmap.width, bitmap.height)
        val page = grayPage(bitmap.width, bitmap.height, pixels, qrBox)
        bitmap.recycle()
        return qr to page
    }

    // ---- Image --------------------------------------------------------------------------------

    private fun decode(resolver: ContentResolver, uri: Uri): Bitmap {
        val decoded = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val (w, h) = targetSize(info.size.width, info.size.height)
                if (w != info.size.width || h != info.size.height) decoder.setTargetSize(w, h)
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw SlipReadException(true, "not an image")
            var sample = 1
            while (bounds.outWidth.toLong() * bounds.outHeight / (sample.toLong() * sample) > MAX_PIXELS) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?.let { rotateByExif(resolver, uri, it) }
                ?: throw SlipReadException(true, "cannot decode")
            val (w, h) = targetSize(bitmap.width, bitmap.height)
            val scaled = if (w != bitmap.width || h != bitmap.height) Bitmap.createScaledBitmap(bitmap, w, h, true) else bitmap
            if (scaled !== bitmap) bitmap.recycle()
            scaled
        }
        val soft = if (decoded.config == Bitmap.Config.ARGB_8888) decoded else decoded.copy(Bitmap.Config.ARGB_8888, false)
        if (soft !== decoded) decoded.recycle()
        return soft
    }

    /** Scaled to between [MIN_WIDTH] and [MAX_WIDTH] wide and at most [MAX_PIXELS], keeping the aspect ratio. */
    private fun targetSize(w: Int, h: Int): Pair<Int, Int> {
        if (w <= 0 || h <= 0) throw SlipReadException(true, "empty image")
        val byWidth = when {
            w < MIN_WIDTH -> MIN_WIDTH.toDouble() / w
            w > MAX_WIDTH -> MAX_WIDTH.toDouble() / w
            else -> 1.0
        }
        val scale = min(byWidth, sqrt(MAX_PIXELS.toDouble() / (w.toDouble() * h)))
        return max(1, (w * scale).roundToInt()) to max(1, (h * scale).roundToInt())
    }

    private fun rotateByExif(resolver: ContentResolver, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: return bitmap
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    /**
     * When the image was taken or saved. Banking apps save the slip right after the transfer, so this
     * stands in for a date or time that OCR could not read.
     */
    private fun fileTime(resolver: ContentResolver, uri: Uri): LocalDateTime? {
        // Gallery and photo picker items have DATE_TAKEN; files from a document provider have
        // COLUMN_LAST_MODIFIED. Ask for one column at a time: providers reject unknown ones.
        val columns = listOf(
            MediaStore.Images.ImageColumns.DATE_TAKEN to 1L,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED to 1L,
            MediaStore.MediaColumns.DATE_MODIFIED to 1000L,
        )
        for ((column, toMillis) in columns) {
            val millis = runCatching {
                resolver.query(uri, arrayOf(column), null, null, null)?.use { c ->
                    val i = c.getColumnIndex(column)
                    if (i >= 0 && c.moveToFirst() && !c.isNull(i)) c.getLong(i) * toMillis else null
                }
            }.getOrNull()
            if (millis != null && millis in 946_684_800_000L..System.currentTimeMillis() + 86_400_000L) return millis.toLocalDateTime()
        }
        return null
    }

    /**
     * The slip QR (bank + reference) and where it is. ZXing decodes it from the full image; the
     * box is then blanked so OCR doesn't read the QR pattern as garbage text.
     */
    private fun findQr(pixels: IntArray, w: Int, h: Int): Pair<String?, Rect?> {
        val source = RGBLuminanceSource(w, h, pixels)
        val hints = mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))
        val reader = QRCodeReader()
        for (binarizer in listOf(HybridBinarizer(source), GlobalHistogramBinarizer(source))) {
            try {
                val result = reader.decode(BinaryBitmap(binarizer), hints)
                val points = result.resultPoints.orEmpty().filterNotNull()
                if (points.size < 3) return result.text to null
                val left = points.minOf { it.x }
                val right = points.maxOf { it.x }
                val top = points.minOf { it.y }
                val bottom = points.maxOf { it.y }
                // The points are finder-pattern centres, 3.5 modules in from the edges.
                val pad = ((right - left) * 0.25f).roundToInt() + 4
                return result.text to Rect(
                    (left.roundToInt() - pad).coerceAtLeast(0), (top.roundToInt() - pad).coerceAtLeast(0),
                    (right.roundToInt() + pad).coerceAtMost(w), (bottom.roundToInt() + pad).coerceAtMost(h),
                )
            } catch (_: ReaderException) {
                reader.reset()
            }
        }
        return null to null
    }

    private class Page(val gray: ByteArray, val width: Int, val height: Int)

    /** 8-bit grey image for OCR, with transparency shown as white and the QR blanked out. */
    private fun grayPage(w: Int, h: Int, pixels: IntArray, qrBox: Rect?): Page {
        val gray = ByteArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = p ushr 24
            val lum = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
            gray[i] = ((lum * a + 255 * (255 - a)) / 255).toByte()
        }
        qrBox?.let { box ->
            for (y in box.top until box.bottom) java.util.Arrays.fill(gray, y * w + box.left, y * w + box.right, 0xFF.toByte())
        }
        return Page(gray, w, h)
    }

    // ---- OCR ----------------------------------------------------------------------------------

    /** Copies the bundled traineddata out of the APK once (Tesseract reads real files only). */
    private suspend fun modelPath(context: Context): String = copyLock.withLock {
        withContext(Dispatchers.IO) {
            // noBackupFilesDir: 5 MB of models don't belong in the user's Google Drive backup.
            val root = File(context.noBackupFilesDir, MODEL_DIR)
            val tessdata = File(root, "tessdata").apply { mkdirs() }
            for (lang in LANGS) {
                val target = File(tessdata, "$lang.traineddata")
                if (target.isFile && target.length() > 0) continue
                val tmp = File(tessdata, "$lang.traineddata.tmp")
                context.assets.open("tessdata/$lang.traineddata").use { input -> tmp.outputStream().use { input.copyTo(it) } }
                if (!tmp.renameTo(target)) throw SlipReadException(false, "cannot install OCR data")
            }
            context.noBackupFilesDir.listFiles { f -> f.name.startsWith("ocr-") && f.name != MODEL_DIR }
                ?.forEach { it.deleteRecursively() }
            root.absolutePath
        }
    }

    /** One Tesseract instance; [read] can be stopped from another thread when the caller is cancelled. */
    private class Ocr(private val dataPath: String) : AutoCloseable {
        private val api = TessBaseAPI()
        private val lock = Any()
        private var closed = false

        suspend fun read(page: Page, langs: String): String = coroutineScope {
            val work = async(Dispatchers.Default) {
                if (!api.init(dataPath, langs, TessBaseAPI.OEM_LSTM_ONLY)) throw SlipReadException(false, "OCR init failed")
                api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
                val pix = ReadFile.readBytes8(page.gray, page.width, page.height)
                try {
                    api.setImage(pix)
                } finally {
                    pix.recycle()
                }
                // getHOCRText runs recognition in a way stop() can interrupt; the text is then cached.
                // (Check for cancellation around it: a stop() before recognition starts is lost, and
                // after a stop getUTF8Text could recognise the page all over again.)
                ensureActive()
                api.getHOCRText(0)
                ensureActive()
                api.getUTF8Text().orEmpty()
            }
            try {
                work.await()
            } catch (e: CancellationException) {
                synchronized(lock) { if (!closed) api.stop() }
                throw e
            }
        }

        override fun close() = synchronized(lock) {
            if (!closed) {
                closed = true
                api.recycle()
            }
        }
    }
}
