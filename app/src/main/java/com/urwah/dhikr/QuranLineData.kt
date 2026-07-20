package com.urwah.dhikr

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

data class WordItem(val text: String)

data class LineItem(
    val type: String,
    val words: List<WordItem>?,
    val text: String?,
    val centered: Boolean,
    val ayahRange: Pair<Int, Int>? = null
)

object QuranLineData {

    private val pageCache = mutableMapOf<Int, List<LineItem>>()

    fun getPageLines(pageNumber: Int, context: Context): List<LineItem> {
        pageCache[pageNumber]?.let { return it }
        val fname = "pages_hafs/page-${pageNumber.toString().padStart(3, '0')}.json"
        val json = try {
            readJsonFromAssets(context, fname)
        } catch (_: Exception) {
            return emptyList()
        }
        val root = JSONObject(json)
        val linesArr = root.optJSONArray("lines") ?: return emptyList()
        val lines = mutableListOf<LineItem>()
        for (i in 0 until linesArr.length()) {
            val obj = linesArr.getJSONObject(i)
            val tp = obj.getString("tp")
            val centered = obj.optBoolean("ce", false)
            val text = if (obj.has("text")) obj.getString("text") else null
            var words: List<WordItem>? = null
            val wordsArr = obj.optJSONArray("words")
            if (wordsArr != null) {
                words = mutableListOf()
                for (j in 0 until wordsArr.length()) {
                    words.add(WordItem(wordsArr.getString(j)))
                }
            }
            var ayahRange: Pair<Int, Int>? = null
            val rangeObj = obj.optJSONObject("ayah_range")
            if (rangeObj != null) {
                val start = rangeObj.optJSONObject("start")
                val end = rangeObj.optJSONObject("end")
                val startAyah = start?.optInt("ayah", 0) ?: 0
                val endAyah = end?.optInt("ayah", 0) ?: 0
                ayahRange = Pair(startAyah, endAyah)
            }
            lines.add(LineItem(tp, words, text, centered, ayahRange))
        }
        pageCache[pageNumber] = lines
        return lines
    }

    fun getLineCount(pageNumber: Int, context: Context): Int {
        return getPageLines(pageNumber, context).size
    }

    fun clearCache() {
        pageCache.clear()
    }

    private fun readJsonFromAssets(context: Context, fileName: String): String {
        val reader = BufferedReader(InputStreamReader(context.assets.open(fileName)))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line)
        }
        reader.close()
        return sb.toString()
    }
}
