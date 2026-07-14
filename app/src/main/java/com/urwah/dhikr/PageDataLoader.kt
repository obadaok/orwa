package com.urwah.dhikr

import android.content.Context
import org.json.JSONObject

data class PageAyah(val surah: Int, val ayah: Int, val text: String)

object PageDataLoader {

    private var pageCache: Map<String, Map<Int, List<PageAyah>>>? = null

    fun load(context: Context): Map<String, Map<Int, List<PageAyah>>> {
        pageCache?.let { return it }

        val jsonString = context.assets.open("quran_pages.json")
            .bufferedReader().use { it.readText() }
        val root = JSONObject(jsonString)

        val result = mutableMapOf<String, Map<Int, List<PageAyah>>>()

        for (qiraatKey in listOf("hafs", "warsh")) {
            val qiraatObj = root.getJSONObject(qiraatKey)
            val pageMap = mutableMapOf<Int, List<PageAyah>>()
            for (pageKey in qiraatObj.keys()) {
                val pageNum = pageKey.toInt()
                val ayahsArray = qiraatObj.getJSONArray(pageKey)
                val ayahs = mutableListOf<PageAyah>()
                for (i in 0 until ayahsArray.length()) {
                    val entry = ayahsArray.getJSONObject(i)
                    ayahs.add(
                        PageAyah(
                            surah = entry.getInt("surah"),
                            ayah = entry.getInt("ayah"),
                            text = entry.getString("text")
                        )
                    )
                }
                pageMap[pageNum] = ayahs
            }
            result[qiraatKey] = pageMap
        }

        pageCache = result
        return result
    }

    private fun getQiraatPageData(context: Context, qiraat: String): Map<Int, List<PageAyah>> {
        return load(context)[qiraat] ?: emptyMap()
    }

    fun getFirstSurahPage(context: Context, qiraat: String, surahNumber: Int): Int {
        val pageData = getQiraatPageData(context, qiraat)
        for (page in 1..604) {
            val ayahs = pageData[page] ?: continue
            if (ayahs.any { it.surah == surahNumber }) {
                return page
            }
        }
        return 1
    }

    fun getSurahPageRange(context: Context, qiraat: String, surahNumber: Int): Pair<Int, Int> {
        val pageData = getQiraatPageData(context, qiraat)
        var first = 604
        var last = 1
        for (page in 1..604) {
            val ayahs = pageData[page] ?: continue
            if (ayahs.any { it.surah == surahNumber }) {
                if (page < first) first = page
                if (page > last) last = page
            }
        }
        return first to last
    }

    fun getPages(context: Context, qiraat: String): Map<Int, List<PageAyah>> {
        return getQiraatPageData(context, qiraat)
    }

    fun getAyahPage(context: Context, qiraat: String, surahNumber: Int, ayahNumber: Int): Int {
        val pageData = getQiraatPageData(context, qiraat)
        for (page in 1..604) {
            val ayahs = pageData[page] ?: continue
            if (ayahs.any { it.surah == surahNumber && it.ayah == ayahNumber }) {
                return page
            }
        }
        return 1
    }
}
