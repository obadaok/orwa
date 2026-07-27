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

    interface DownloadListener {
        fun onProgress(progress: Float)
        fun onComplete(success: Boolean, error: String? = null)
    }

    suspend fun downloadBook(
        context: Context,
        book: ShamelaBook,
        listener: DownloadListener? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val bookDir = ShamelaBookStorage.getBookDir(context, book.id)
            if (!bookDir.exists()) bookDir.mkdirs()

            // Download book_metadata.json
            listener?.onProgress(0f)
            downloadFile(book.metadataUrl, File(bookDir, "book_metadata.json"))
            listener?.onProgress(0.1f)

            // Download toc.jsonl
            downloadFile(book.tocUrl, File(bookDir, "toc.jsonl"))
            listener?.onProgress(0.2f)

            // Download pages.jsonl (large file - stream with progress)
            downloadFileWithProgress(book.pagesUrl, File(bookDir, "pages.jsonl")) { progress ->
                listener?.onProgress(0.2f + progress * 0.8f)
            }

            listener?.onProgress(1f)
            listener?.onComplete(true)
            true
        } catch (e: Exception) {
            // Cleanup on failure
            val bookDir = ShamelaBookStorage.getBookDir(context, book.id)
            if (bookDir.exists()) bookDir.deleteRecursively()
            listener?.onComplete(false, e.message)
            false
        }
    }

    private fun downloadFile(urlStr: String, outputFile: File) {
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
                    input.copyTo(output, bufferSize = 8192)
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadFileWithProgress(
        urlStr: String,
        outputFile: File,
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

            conn.inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead
                        if (totalSize > 0) {
                            onProgress(downloadedSize.toFloat() / totalSize)
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    fun cancelDownload() {
        // Future: track active connections for cancellation
    }
}
