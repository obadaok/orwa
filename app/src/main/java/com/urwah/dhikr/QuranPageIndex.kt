package com.urwah.dhikr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * فهرس الصفحات (حفص) لربط «وضع الصفحات» بالقرآن المحلي:
 *  - pageFor(surah, ayah): الصفحة التي تحتوي آية.
 *  - pageForSurah(surah): الصفحة التي تُفتح عند فتح سورة.
 *  - firstSurahOfPage / rangeOfPage: لتحديث السورة/الآية الحالية عند التنقل.
 * حدود الآيات هي نفسها في جميع الروايات (يختلف الرسم فقط)، لذا يعمل الفهرس
 * مع أي رواية معروضة في الوضع العادي.
 */
object QuranPageIndex {

    private const val PAGE_COUNT = 604

    private val cumulativeAyahs by lazy { buildCumulative() }
    private var pageFirstGlobal: IntArray? = null
    private var pageFirstSurah: IntArray? = null
    private var pageLastGlobal: IntArray? = null
    private var pageLastSurah: IntArray? = null
    private var globalToPage: IntArray? = null
    private var loaded = false

    private fun buildCumulative(): IntArray {
        val c = IntArray(115)
        var acc = 0
        for (s in 1..114) {
            acc += (SurahDataProvider.allSurahs.firstOrNull { it.number == s }?.verseCount ?: 0)
            c[s] = acc
        }
        return c
    }

    fun globalAyah(surah: Int, ayah: Int): Int = cumulativeAyahs[surah - 1] + ayah

    suspend fun ensureLoaded(context: Context) {
        if (loaded) return
        withContext(Dispatchers.IO) {
            synchronized(this) {
                if (loaded) return@withContext
                load(context)
                loaded = true
            }
        }
    }

    private fun load(context: Context) {
        val root = JSONObject(readAsset(context, "quran_pages.json"))
        val hafs = root.getJSONObject("hafs")

        val fGlobal = IntArray(PAGE_COUNT + 1)
        val lGlobal = IntArray(PAGE_COUNT + 1)
        val fSurah = IntArray(PAGE_COUNT + 1)
        val lSurah = IntArray(PAGE_COUNT + 1)
        val g2p = IntArray(cumulativeAyahs[114] + 1) { -1 }

        val it = hafs.keys()
        while (it.hasNext()) {
            val pageKey = it.next()
            val page = pageKey.toInt()
            val arr = hafs.getJSONArray(pageKey)
            var fg = Int.MAX_VALUE
            var lg = Int.MIN_VALUE
            var fs = -1
            var ls = -1
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val s = e.getInt("surah")
                val a = e.getInt("ayah")
                val g = globalAyah(s, a)
                if (fs == -1) fs = s
                ls = s
                if (g < fg) fg = g
                if (g > lg) lg = g
                if (g in g2p.indices) g2p[g] = page
            }
            fGlobal[page] = fg
            lGlobal[page] = lg
            fSurah[page] = fs
            lSurah[page] = ls
        }

        var lastKnown = 1
        for (g in 1 until g2p.size) {
            if (g2p[g] == -1) g2p[g] = lastKnown else lastKnown = g2p[g]
        }

        pageFirstGlobal = fGlobal
        pageLastGlobal = lGlobal
        pageFirstSurah = fSurah
        pageLastSurah = lSurah
        globalToPage = g2p
    }

    fun pageFor(context: Context, surah: Int, ayah: Int): Int {
        if (!loaded) runCatching { ensureLoadedBlocking(context) }
        val g = globalAyah(surah, ayah)
        return globalToPage?.getOrNull(g)?.takeIf { it in 1..PAGE_COUNT }
            ?: (surahStartFallback(surah) ?: 1)
    }

    private fun surahStartFallback(surah: Int): Int? {
        // صفحة أول آية في السورة (عند عدم اكتمال الفهرس)
        val gs = globalAyah(surah, 1)
        return globalToPage?.getOrNull(gs)?.takeIf { it in 1..PAGE_COUNT }
    }

    fun pageForSurah(context: Context, surah: Int): Int {
        return pageFor(context, surah, 1)
    }

    fun firstSurahOfPage(context: Context, page: Int): Int {
        if (!loaded) runCatching { ensureLoadedBlocking(context) }
        return pageFirstSurah?.getOrNull(page) ?: 1
    }

    fun rangeOfPage(context: Context, page: Int): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
        if (!loaded) runCatching { ensureLoadedBlocking(context) }
        val fs = pageFirstSurah?.getOrNull(page) ?: return null
        val ls = pageLastSurah?.getOrNull(page) ?: return null
        val fg = pageFirstGlobal?.getOrNull(page) ?: return null
        val lg = pageLastGlobal?.getOrNull(page) ?: return null
        return Pair(Pair(fs, localAyah(fs, fg)), Pair(ls, localAyah(ls, lg)))
    }

    fun ensureLoadedBlocking(context: Context) {
        synchronized(this) {
            if (!loaded) {
                load(context)
                loaded = true
            }
        }
    }

    private fun localAyah(surah: Int, g: Int): Int = (g - cumulativeAyahs[surah - 1]).coerceAtLeast(1)

    private fun readAsset(context: Context, name: String): String {
        val input = context.assets.open(name)
        val sb = StringBuilder()
        java.io.BufferedReader(java.io.InputStreamReader(input, "UTF-8")).forEachLine { sb.append(it) }
        return sb.toString()
    }
}
