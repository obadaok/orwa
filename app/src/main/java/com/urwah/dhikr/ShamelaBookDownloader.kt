package com.urwah.dhikr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object ShamelaBookDownloader {

    private const val BASE_URL = "https://huggingface.co/datasets/AuthenticIlm/Shamela4_Full_DB/resolve/main"

    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 120_000
    private const val MAX_PAGE_ATTEMPTS = 10
    private const val BUFFER_SIZE = 64 * 1024
    private const val PART_TAIL = ".pages.part"

    /** إلغاء تعاوني حقيقي لكل كتاب على حدة (تحميلات متزامنة مستقلة). */
    private val cancelledByBook = java.util.concurrent.ConcurrentHashMap<Int, java.util.concurrent.atomic.AtomicBoolean>()

    private class DownloadCancelledException : Exception("تم إلغاء التحميل")

    interface DownloadListener {
        fun onProgress(progress: Float)
        fun onComplete(success: Boolean, error: String? = null)
    }

    /** ملف صفحات جزئي خارج مجلد الكتاب ليُستأنف منه حتى عبر عدة محاولات، وحتى بعد فشل الاتصال. */
    private fun partFile(context: Context, bookId: Int): File {
        val parent = ShamelaBookStorage.getBookDir(context, bookId).parentFile
            ?: throw IllegalStateException("لا يوجد مجلد للكتب")
        return File(parent, "$bookId$PART_TAIL")
    }

    /** توقُّع الحجم/الصفوف من manifest.json — مصدر الحقيقة لمحتوى الكتاب. */
    private data class ManifestExpectation(val pagesBytes: Long, val pagesRows: Int)

    suspend fun downloadBook(
        context: Context,
        book: ShamelaBook,
        listener: DownloadListener? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val cancelled = cancelledByBook.getOrPut(book.id) { java.util.concurrent.atomic.AtomicBoolean(false) }
        cancelled.set(false)
        val bookDir = ShamelaBookStorage.getBookDir(context, book.id)
        val tmpDir = File(bookDir.parentFile, "${book.id}.tmp")
        val backupDir = File(bookDir.parentFile, "${book.id}.old")
        val part = partFile(context, book.id)
        try {
            // تنظيف مجلد مؤقت سابق (لا يُحذف الملف الجزئي لأن أجزاءً أقصر من الكتاب لا فائدة منها إلا بالاستئناف).
            if (tmpDir.exists()) tmpDir.deleteRecursively()
            if (!tmpDir.mkdirs()) throw Exception("تعذّر إنشاء مجلد التحميل")

            // Download book_metadata.json (ملف صغير)
            listener?.onProgress(0f)
            downloadFile(book.metadataUrl, File(tmpDir, "book_metadata.json"), cancelled)
            listener?.onProgress(0.1f)

            // Download toc.jsonl (ملف صغير)
            downloadFile(book.tocUrl, File(tmpDir, "toc.jsonl"), cancelled)
            listener?.onProgress(0.2f)

            // توقُّع المحتوى من manifest.json للتحقق فيما بعد (حجم + عدد الصفحات)
            val expectation = fetchManifestExpectation(book)

            // Download pages.jsonl — تنزيل قابل للاستئناف مع تحقق كامل:
            // - انقطاع الاتصال لا يبدأ من الصفر بل يُستأنف من آخر بايت مكتمل.
            // - إن وصل `read()` لنهاية الاتصال قبل الحجم المتوقع نعتبرها ملفًا ناقصًا
            //   لا يُعتمد عليه أبدًا (كان يظهر سابقًا كتاب مكتمل ببعض صفحاته فقط).
            var attempts = 0
            var verified = false
            while (!verified && attempts < MAX_PAGE_ATTEMPTS) {
                attempts++
                try {
                    downloadPages(book.pagesUrl, part, cancelled) { downloadedBytes ->
                        val total = if (expectation != null && expectation.pagesBytes > 0) {
                            expectation.pagesBytes.coerceAtLeast(downloadedBytes)
                        } else {
                            downloadedBytes
                        }
                        val ratio = if (total > 0) downloadedBytes.toFloat() / total else 0f
                        listener?.onProgress(0.2f + ratio.coerceIn(0f, 0.99f) * 0.8f)
                    }
                    verified = verifyPages(part, expectation)
                    if (!verified) throw Exception("بيانات الكتاب ناقصة، يُعاد الجلب من النقطة المتوقفة")
                } catch (e: DownloadCancelledException) {
                    throw e
                } catch (e: Exception) {
                    if (attempts >= MAX_PAGE_ATTEMPTS || cancelled.get()) throw e
                    delay(1200L * attempts)
                }
            }
            if (cancelled.get()) throw DownloadCancelledException()

            // نقل الملف الجزئي المكتمل إلى مكانه النهائي
            val pagesFile = File(tmpDir, "pages.jsonl")
            if (!part.renameTo(pagesFile)) {
                if (!part.copyTo(pagesFile, overwrite = false).exists()) {
                    throw Exception("تعذّر حفظ صفحات الكتاب على الجهاز")
                }
                part.delete()
            }

            // تبديل ذرّي: نُنقل النسخة القديمة جانبًا ثم نُدخل الجديدة.
            if (backupDir.exists()) backupDir.deleteRecursively()
            val hadOld = bookDir.exists()
            if (hadOld && !bookDir.renameTo(backupDir)) {
                throw Exception("تعذّر الاحتفاظ بنسخة الكتاب القديمة")
            }
            if (!tmpDir.renameTo(bookDir)) {
                // استرجاع النسخة القديمة عند الفشل.
                val restored = !hadOld || backupDir.renameTo(bookDir)
                if (!restored) throw IllegalStateException("فقدت نسخة الكتاب القديمة")
                throw Exception("تعذّر حفظ الكتاب على الجهاز")
            }
            if (backupDir.exists()) backupDir.deleteRecursively()

            part.delete()
            listener?.onProgress(1f)
            listener?.onComplete(true)
            true
        } catch (e: DownloadCancelledException) {
            // عند الإلغاء/الفشل نُبقي النسخة القديمة سليمة، ونُبقي الملف الجزئي للاستئناف لاحقًا
            // (من مسار أسرع مما لو بدأنا من الصفر)، ونحذف المؤقت فقط.
            if (!bookDir.exists() && backupDir.exists()) backupDir.renameTo(bookDir)
            if (tmpDir.exists()) tmpDir.deleteRecursively()
            listener?.onComplete(false, e.message)
            false
        } catch (e: Exception) {
            if (!bookDir.exists() && backupDir.exists()) backupDir.renameTo(bookDir)
            if (tmpDir.exists()) tmpDir.deleteRecursively()
            listener?.onComplete(false, e.message)
            false
        } finally {
            cancelled.set(false)
            cancelledByBook.remove(book.id, cancelled)
        }
    }

    private fun openConnection(urlStr: String): HttpURLConnection {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.setRequestProperty("User-Agent", "OrwaApp/1.0")
        return conn
    }

    /** تحميل ملف كامل مع رفض الملف المبتور (EOF قبل الحجم المتوقع). */
    private fun downloadFile(urlStr: String, outputFile: File, cancelled: java.util.concurrent.atomic.AtomicBoolean) {
        val conn = openConnection(urlStr)
        try {
            conn.connect()
            if (conn.responseCode != 200) {
                throw Exception("HTTP ${conn.responseCode}: ${conn.responseMessage}")
            }
            val total = conn.contentLength.toLong()
            var written = 0L
            conn.inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var n: Int
                    while (input.read(buffer).also { n = it } != -1) {
                        if (cancelled.get()) throw DownloadCancelledException()
                        output.write(buffer, 0, n)
                        written += n
                    }
                }
            }
            if (total > 0 && written < total) {
                outputFile.delete()
                throw IOException("ملف ناقص (تم استلام $written من $total بايت)")
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * تنزيل pages.jsonl مع استئناف عبر Range:
     * - يبدأ من نهاية الملف الجزئي إن وُجد ولا يعيد ما سبق تنزيله.
     * - أي انقطاع/مهلة يُستأنف تلقائيًا من آخر بايت مكتوب (بدل البدء من الصفر).
     * - يرفض الانتهاء ناقصًا: وصول EOF قبل الحجم المتوقع = اتصال مبتور = إعادة المحاولة.
     */
    private fun downloadPages(
        urlStr: String,
        outFile: File,
        cancelled: java.util.concurrent.atomic.AtomicBoolean,
        onProgress: (Long) -> Unit
    ) {
        var attempts = 0
        while (attempts < MAX_PAGE_ATTEMPTS) {
            attempts++
            var start = outFile.length()
            val conn = openConnection(urlStr)
            try {
                if (start > 0) conn.setRequestProperty("Range", "bytes=$start-")
                conn.connect()
                val code = conn.responseCode
                when (code) {
                    200 -> {
                        if (start > 0) {
                            // الخادم تجاهل Range — نبدأ من الصفر دفعة واحدة.
                            outFile.writeBytes(ByteArray(0))
                            start = 0
                        }
                    }
                    206 -> Unit
                    416 -> {
                        // الملف الجزئي يغطي الملف كاملًا بالفعل — لا شيء يُستأنف.
                        return
                    }
                    else -> throw Exception("HTTP $code: ${conn.responseMessage}")
                }

                val total = if (code == 206) start + conn.contentLength.toLong() else conn.contentLength.toLong()
                var added = 0L
                conn.inputStream.use { input ->
                    outFile.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            if (cancelled.get()) throw DownloadCancelledException()
                            val n = input.read(buffer)
                            if (n == -1) break
                            output.write(buffer, 0, n)
                            added += n
                            onProgress(start + added)
                        }
                    }
                }
                // EOF قبل الحجم المتوقع = وصلنا لنهاية اتصال مبتور، لا ملف مكتمل.
                if (total > start && start + added < total) {
                    throw IOException("انقطع الاتصال قبل اكتمال الملف (استُقبل $added من ${total - start} بايت)")
                }
                return
            } catch (e: DownloadCancelledException) {
                throw e
            } catch (e: Exception) {
                if (attempts >= MAX_PAGE_ATTEMPTS || cancelled.get()) throw e
                Thread.sleep(1200L * attempts)
            } finally {
                conn.disconnect()
            }
        }
    }

    /** التحقق من اكتمال ملف الصفحات بمطابقته لتوقُّع manifest.json (حجم + عدد الأبيات). */
    private fun verifyPages(part: File, expectation: ManifestExpectation?): Boolean {
        val size = part.length()
        if (expectation == null) return true
        if (expectation.pagesBytes > 0 && size != expectation.pagesBytes) return false
        if (expectation.pagesRows > 0) {
            var lines = 0
            part.forEachLine { if (it.isNotBlank()) lines++ }
            return lines == expectation.pagesRows
        }
        return true
    }

    /** جلب manifest.json (ملف صغير) لتوقُّع حجم/عدد صفحات الكتاب الحقيقية. */
    private fun fetchManifestExpectation(book: ShamelaBook): ManifestExpectation? {
        if (book.hfPath.isBlank()) return null
        val conn = openConnection("$BASE_URL/${book.hfPath.trim('/')}/manifest.json")
        try {
            conn.connect()
            if (conn.responseCode != 200) return null
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val obj = JSONObject(text)
            val files = obj.optJSONArray("files") ?: return null
            for (i in 0 until files.length()) {
                val f = files.optJSONObject(i) ?: continue
                if (f.optString("path") == "pages.jsonl") {
                    return ManifestExpectation(
                        pagesBytes = f.optLong("bytes", 0L),
                        pagesRows = f.optInt("rows", 0)
                    )
                }
            }
            return null
        } catch (_: Exception) {
            return null
        } finally {
            conn.disconnect()
        }
    }

    fun cancelDownload() {
        // يلغي كل التحميلات النشطة (سلوك آمن شامل)
        cancelledByBook.values.forEach { it.set(true) }
    }

    fun cancelDownload(bookId: Int) {
        cancelledByBook[bookId]?.set(true)
    }
}