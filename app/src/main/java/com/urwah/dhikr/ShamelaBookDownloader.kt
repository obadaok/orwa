package com.urwah.dhikr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object ShamelaBookDownloader {

    private const val BASE_URL = "https://huggingface.co/datasets/AuthenticIlm/Shamela4_Full_DB/resolve/main"

    /** إلغاء تعاوني حقيقي لكل كتاب على حدة (تحميلات متزامنة مستقلة). */
    private val cancelledByBook = java.util.concurrent.ConcurrentHashMap<Int, java.util.concurrent.atomic.AtomicBoolean>()

    private class DownloadCancelledException : Exception("تم إلغاء التحميل")

    interface DownloadListener {
        fun onProgress(progress: Float)
        fun onComplete(success: Boolean, error: String? = null)
    }

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
        try {
            // التنزيل في مجلد مؤقت أولًا ثم تبديله بالنسخة الجديدة حتى لا يتلف
            // الكتاب المحلي القديم إذا انقطع الاتصال في منتصف التحديث.
            if (!tmpDir.exists() && !tmpDir.isDirectory) tmpDir.mkdirs()

            // Download book_metadata.json
            listener?.onProgress(0f)
            downloadFile(book.metadataUrl, File(tmpDir, "book_metadata.json"), cancelled)
            listener?.onProgress(0.1f)

            // Download toc.jsonl
            downloadFile(book.tocUrl, File(tmpDir, "toc.jsonl"), cancelled)
            listener?.onProgress(0.2f)

            // Download pages.jsonl (large file - stream with progress)
            downloadFileWithProgress(book.pagesUrl, File(tmpDir, "pages.jsonl"), cancelled) { progress ->
                listener?.onProgress(0.2f + progress * 0.8f)
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

            listener?.onProgress(1f)
            listener?.onComplete(true)
            true
        } catch (e: DownloadCancelledException) {
            // عند الإلغاء/الفشل نُبقي النسخة القديمة سليمة ونحذف المؤقت فقط.
            if (!bookDir.exists() && backupDir.exists()) backupDir.renameTo(bookDir)
            if (tmpDir.exists()) tmpDir.deleteRecursively()
            listener?.onComplete(false, e.message)
            false
        } catch (e: Exception) {
            // عند الفشل نُبقي النسخة القديمة سليمة ونحذف المؤقت فقط.
            if (!bookDir.exists() && backupDir.exists()) backupDir.renameTo(bookDir)
            if (tmpDir.exists()) tmpDir.deleteRecursively()
            listener?.onComplete(false, e.message)
            false
        } finally {
            cancelled.set(false)
            cancelledByBook.remove(book.id, cancelled)
        }
    }

    private fun downloadFile(urlStr: String, outputFile: File, cancelled: java.util.concurrent.atomic.AtomicBoolean) {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("User-Agent", "OrwaApp/1.0")

        try {
            conn.connect()
            if (conn.responseCode != 200) {
                throw Exception("HTTP ${conn.responseCode}: ${conn.responseMessage}")
            }
            conn.inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (cancelled.get()) throw DownloadCancelledException()
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadFileWithProgress(
        urlStr: String,
        outputFile: File,
        cancelled: java.util.concurrent.atomic.AtomicBoolean,
        onProgress: (Float) -> Unit
    ) {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("User-Agent", "OrwaApp/1.0")

        try {
            conn.connect()
            if (conn.responseCode != 200) {
                throw Exception("HTTP ${conn.responseCode}: ${conn.responseMessage}")
            }

            val totalSize = conn.contentLength.toLong()
            var downloadedSize = 0L
            var lastReportedProgress = -1f
            var lastReportedAt = 0L

            conn.inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (cancelled.get()) throw DownloadCancelledException()
                        output.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead
                        if (totalSize > 0) {
                            val progress = downloadedSize.toFloat() / totalSize
                            val now = System.currentTimeMillis()
                            // تحديث الـ UI بتردد محدود (≈120ms أو فرق ≥1%) حتى لا
                            // يغرق السطر بإشعارات إعادة ربط أثناء التمرير.
                            if (progress - lastReportedProgress >= 0.01f ||
                                (now - lastReportedAt >= 120 && progress != lastReportedProgress)
                            ) {
                                lastReportedProgress = progress
                                lastReportedAt = now
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
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
