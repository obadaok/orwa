package com.urwah.dhikr

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class PageAyah(
    val surah: Int,
    val ayah: Int,
    val text: String
)

class QuranPageData private constructor(
    val pageNumber: Int,
    val ayahs: List<PageAyah>
) {
    companion object {
        private var cache: Map<String, Map<Int, List<PageAyah>>>? = null

        fun getPageCount(riwaya: String, context: Context): Int {
            loadIfNeeded(context)
            return cache?.get(riwaya)?.size ?: 604
        }

        fun getPageAyahs(riwaya: String, pageNumber: Int, context: Context): List<PageAyah> {
            loadIfNeeded(context)
            return cache?.get(riwaya)?.get(pageNumber) ?: emptyList()
        }

        fun findPageForAyah(riwaya: String, surahNumber: Int, ayahNumber: Int, context: Context): Int {
            loadIfNeeded(context)
            val pages = cache?.get(riwaya) ?: return 1
            for ((pageNum, ayahs) in pages) {
                if (ayahs.any { it.surah == surahNumber && it.ayah == ayahNumber }) {
                    return pageNum
                }
            }
            return 1
        }

        fun findSurahPageRange(riwaya: String, surahNumber: Int, context: Context): Pair<Int, Int> {
            loadIfNeeded(context)
            val pages = cache?.get(riwaya) ?: return (1 to 604)
            var first = -1
            var last = -1
            for ((pageNum, ayahs) in pages) {
                if (ayahs.any { it.surah == surahNumber }) {
                    if (first < 0) first = pageNum
                    last = pageNum
                }
            }
            return (if (first < 0) 1 else first) to (if (last < 0) 604 else last)
        }

        fun getPageAyahText(riwaya: String, pageNumber: Int, context: Context): String {
            val ayahs = getPageAyahs(riwaya, pageNumber, context)
            return ayahs.joinToString(" ") { it.text }
        }

        private fun loadIfNeeded(context: Context) {
            if (cache != null) return
            val json = readJsonFromAssets(context, "quran_pages.json")
            val root = JSONObject(json)
            val result = mutableMapOf<String, Map<Int, List<PageAyah>>>()
            for (riwayaKey in listOf("hafs", "warsh")) {
                if (!root.has(riwayaKey)) continue
                val pagesObj = root.getJSONObject(riwayaKey)
                val pagesMap = mutableMapOf<Int, List<PageAyah>>()
                for (key in pagesObj.keys()) {
                    val pageNum = key.toIntOrNull() ?: continue
                    val ayahsArray = pagesObj.getJSONArray(key)
                    val ayahsList = mutableListOf<PageAyah>()
                    for (i in 0 until ayahsArray.length()) {
                        val obj = ayahsArray.getJSONObject(i)
                        ayahsList.add(PageAyah(
                            surah = obj.getInt("surah"),
                            ayah = obj.getInt("ayah"),
                            text = obj.getString("text")
                        ))
                    }
                    pagesMap[pageNum] = ayahsList
                }
                result[riwayaKey] = pagesMap
            }
            cache = result
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
}
