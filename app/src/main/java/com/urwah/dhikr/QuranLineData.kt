package com.urwah.dhikr

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class LineItem(
    val type: String,
    val text: String?,
    val surah: String?
)

object QuranLineData {

    private var rawJson: String? = null
    private var pageCache = mutableMapOf<Int, List<LineItem>>()

    fun getPageLines(pageNumber: Int, context: Context): List<LineItem> {
        pageCache[pageNumber]?.let { return it }
        val json = getRawJson(context)
        val key = "\"$pageNumber\":"
        val startIdx = json.indexOf(key)
        if (startIdx < 0) return emptyList()
        val arrayStart = json.indexOf('[', startIdx)
        if (arrayStart < 0) return emptyList()
        var depth = 0
        var arrayEnd = -1
        for (i in arrayStart until json.length) {
            when (json[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) { arrayEnd = i; break }
                }
            }
        }
        if (arrayEnd < 0) return emptyList()
        val pageJson = json.substring(arrayStart, arrayEnd + 1)
        val arr = JSONArray(pageJson)
        val lines = mutableListOf<LineItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val type = obj.getString("t")
            val text = if (obj.has("x")) obj.optString("x", "") else null
            val surah = if (obj.has("s")) obj.optString("s", "") else null
            lines.add(LineItem(type, text, surah))
        }
        pageCache[pageNumber] = lines
        return lines
    }

    fun getLineCount(pageNumber: Int, context: Context): Int {
        return getPageLines(pageNumber, context).size
    }

    fun clearCache() {
        pageCache.clear()
        rawJson = null
    }

    private fun getRawJson(context: Context): String {
        rawJson?.let { return it }
        val reader = BufferedReader(InputStreamReader(context.assets.open("quran_lines_hafs.json")))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line)
        }
        reader.close()
        val s = sb.toString()
        rawJson = s
        return s
    }
}
