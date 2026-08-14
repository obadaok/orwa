package com.urwah.dhikr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * بيانات أسطر المصحف لكل صفحة (حفص) — المصدر الرسمي (QCF V2 / Quran.com):
 * كل صفحة → أسطرها (line_number)، وكل سطر → كلماته {text, surah, ayah, type, code}.
 * تُحزم في assets وتُحفظ في ذاكرة بعد أول تحميل (offline، تحميل واحد).
 */
object QuranPageLayouts {

    const val PAGE_COUNT = 604

    class Word(
        val text: String,
        val surah: Int,
        val ayah: Int,
        val line: Int,
        val type: String,
        val code: String,
    )

    class Line(
        val number: Int,
        val words: List<Word>,
    )

    class Page(
        val number: Int,
        val lines: List<Line>,
        val surahs: List<Int>,
    )

    private var cache: Map<Int, Page>? = null

    suspend fun ensureLoaded(context: Context): Map<Int, Page> {
        cache?.let { return it }
        return withContext(Dispatchers.IO) { ensureLoadedBlocking(context) }
    }

    /** تحميل متزامن يُستدعى من خيط خلفية (تدفئة الإقلاع) أو داخل Dispatchers.IO. */
    fun ensureLoadedBlocking(context: Context): Map<Int, Page> {
        synchronized(this) {
            cache?.let { return it }
            val root = JSONObject(readAsset(context, "quran_pages_layout.json"))
            val hafs = root.getJSONObject("hafs")
            val pages = HashMap<Int, Page>(PAGE_COUNT + 1)
            val it = hafs.keys()
            while (it.hasNext()) {
                val key = it.next()
                val pageNum = key.toInt()
                val pageObj = hafs.getJSONObject(key)
                val surahsArr = pageObj.getJSONArray("surahs")
                val surahs = ArrayList<Int>(surahsArr.length())
                for (i in 0 until surahsArr.length()) surahs.add(surahsArr.getInt(i))

                val linesArr = pageObj.getJSONArray("lines")
                val lines = ArrayList<Line>(linesArr.length())
                for (i in 0 until linesArr.length()) {
                    val lineArr = linesArr.getJSONArray(i)
                    val words = ArrayList<Word>(lineArr.length())
                    var lineNum = 1
                    for (j in 0 until lineArr.length()) {
                        val w = lineArr.getJSONObject(j)
                        val num = w.optInt("line", lineNum)
                        if (num != 0) lineNum = num
                        words.add(
                            Word(
                                text = w.optString("text"),
                                surah = w.optInt("surah"),
                                ayah = w.optInt("ayah"),
                                line = num,
                                type = w.optString("type", "word"),
                                code = w.optString("code"),
                            )
                        )
                    }
                    lines.add(Line(lineNum, words))
                }
                pages[pageNum] = Page(pageNum, lines, surahs)
            }
            cache = pages
            return pages
        }
    }

    fun cached(): Map<Int, Page>? = cache

    private fun readAsset(context: Context, name: String): String {
        val input = context.assets.open(name)
        val sb = StringBuilder()
        java.io.BufferedReader(java.io.InputStreamReader(input, "UTF-8")).forEachLine { sb.append(it) }
        return sb.toString()
    }
}
