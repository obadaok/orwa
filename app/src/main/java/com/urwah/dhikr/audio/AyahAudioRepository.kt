package com.urwah.dhikr.audio

import android.content.Context
import java.io.File

/**
 * مستودع ملفات التلاوة (Gapped per-ayah).
 *
 * مع القرار المعتمد لا حاجة لقواعد توقيتات الآيات إطلاقاً:
 * الملف الحالي = الآية الحالية. هذا المستودع يدير مسارات التخزين
 * المحلي والتحقق من وجود ملف محمل (للتشغيل دون إنترنت).
 */
class AyahAudioRepository(context: Context) {

    private val baseDir: File = File(context.filesDir, "recitations")

    fun dirForReciter(reciter: Reciter): File {
        return File(baseDir, reciter.cdnPath)
    }

    fun fileForAyah(reciter: Reciter, surah: Int, ayah: Int): File {
        val dir = File(dirForReciter(reciter), String.format("%03d", surah))
        return File(dir, String.format("%03d.mp3", ayah))
    }

    fun isDownloaded(reciter: Reciter, surah: Int, ayah: Int): Boolean {
        return fileForAyah(reciter, surah, ayah).exists()
    }

    /**
     * كل سور قارئ معين المحملة محلياً (مرتبة) + حجمها الكلي بالبايت.
     */
    fun downloadedSurahs(reciter: Reciter): Pair<List<Int>, Long> {
        val dir = dirForReciter(reciter)
        if (!dir.exists()) return emptyList<Int>() to 0L
        val surahs = mutableListOf<Int>()
        var totalBytes = 0L
        dir.listFiles()?.forEach { suraDir ->
            val surah = suraDir.name.toIntOrNull()
            if (surah != null && suraDir.isDirectory) {
                val bytes = suraDir.listFiles()?.sumOf { it.length() } ?: 0L
                if (bytes > 0) {
                    surahs.add(surah)
                    totalBytes += bytes
                }
            }
        }
        return surahs.sorted() to totalBytes
    }

    fun totalDownloadedBytes(): Long {
        var total = 0L
        baseDir.listFiles()?.forEach { reciterDir ->
            if (reciterDir.isDirectory) {
                reciterDir.listFiles()?.forEach { suraDir ->
                    if (suraDir.isDirectory) {
                        total += suraDir.listFiles()?.sumOf { it.length() } ?: 0L
                    }
                }
            }
        }
        return total
    }

    fun deleteReciter(reciter: Reciter): Long {
        val dir = dirForReciter(reciter)
        val bytes = totalReciterBytes(reciter)
        dir.deleteRecursively()
        return bytes
    }

    fun deleteAll(): Long {
        val bytes = totalDownloadedBytes()
        baseDir.deleteRecursively()
        return bytes
    }

    private fun totalReciterBytes(reciter: Reciter): Long {
        val dir = dirForReciter(reciter)
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
