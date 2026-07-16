package com.urwah.dhikr

data class JuzBoundary(
    val juzNumber: Int,
    val startSurah: Int,
    val startAyah: Int,
    val endSurah: Int,
    val endAyah: Int
)

object JuzData {

    val JUZ_BOUNDARIES: List<JuzBoundary> = listOf(
        JuzBoundary(1,  1,   1,        2,   141),
        JuzBoundary(2,  2,   142,      2,   252),
        JuzBoundary(3,  2,   253,      3,   92),
        JuzBoundary(4,  3,   93,       4,   23),
        JuzBoundary(5,  4,   24,       4,   147),
        JuzBoundary(6,  4,   148,      5,   81),
        JuzBoundary(7,  5,   82,       6,   110),
        JuzBoundary(8,  6,   111,      7,   87),
        JuzBoundary(9,  7,   88,       8,   40),
        JuzBoundary(10, 8,   41,       9,   92),
        JuzBoundary(11, 9,   93,       11,  5),
        JuzBoundary(12, 11,  6,        12,  52),
        JuzBoundary(13, 12,  53,       14,  52),
        JuzBoundary(14, 15,  1,        16,  128),
        JuzBoundary(15, 17,  1,        18,  74),
        JuzBoundary(16, 18,  75,       20,  135),
        JuzBoundary(17, 21,  1,        22,  78),
        JuzBoundary(18, 23,  1,        25,  20),
        JuzBoundary(19, 25,  21,       27,  55),
        JuzBoundary(20, 27,  56,       29,  45),
        JuzBoundary(21, 29,  46,       33,  30),
        JuzBoundary(22, 33,  31,       36,  27),
        JuzBoundary(23, 36,  28,       39,  31),
        JuzBoundary(24, 39,  32,       41,  46),
        JuzBoundary(25, 41,  47,       45,  37),
        JuzBoundary(26, 46,  1,        51,  30),
        JuzBoundary(27, 51,  31,       57,  29),
        JuzBoundary(28, 58,  1,        66,  12),
        JuzBoundary(29, 67,  1,        77,  50),
        JuzBoundary(30, 78,  1,        114, 6)
    )

    private val JUZ_VERSE_COUNTS = listOf(
        148, 111, 126, 131, 124, 110, 149, 142, 159, 127,
        151, 170, 154, 227, 185, 269, 190, 202, 339, 171,
        178, 169, 357, 175, 246, 195, 399, 137, 431, 564
    )

    private val JUZ_CUMULATIVE = run {
        val cum = mutableListOf<Int>()
        var sum = 0
        for (vc in JUZ_VERSE_COUNTS) {
            sum += vc
            cum.add(sum)
        }
        cum
    }

    private val HAFS_TOTAL = JUZ_VERSE_COUNTS.sum()

    private fun buildSortedAyahList(allData: Map<Int, QuranSurah>): List<AyahData> {
        return allData.entries
            .sortedBy { it.key }
            .flatMap { (_, surah) -> surah.ayahs.sortedBy { it.number } }
    }

    private fun getJuzStartIndex(allAyahs: List<AyahData>, juzNumber: Int): Int {
        if (juzNumber <= 1) return 0
        val idx = juzNumber - 2
        if (idx >= JUZ_CUMULATIVE.size) return allAyahs.size
        val cumulativeBefore = JUZ_CUMULATIVE[idx]
        val fraction = cumulativeBefore.toDouble() / HAFS_TOTAL
        return (fraction * allAyahs.size).toInt().coerceIn(0, allAyahs.size - 1)
    }

    private fun findExactAyahIndex(allAyahs: List<AyahData>, surah: Int, ayah: Int): Int {
        return allAyahs.indexOfFirst { it.surahNumber == surah && it.number == ayah }
    }

    private fun isHafsData(allAyahs: List<AyahData>): Boolean {
        return allAyahs.size == HAFS_TOTAL
    }

    fun getDayAyahs(allData: Map<Int, QuranSurah>, startJuz: Int, totalDays: Int, currentDay: Int): List<AyahData> {
        if (totalDays <= 0 || currentDay < 0 || currentDay >= totalDays) return emptyList()

        val allAyahs = buildSortedAyahList(allData)
        val rangeStart = getJuzStartIndex(allAyahs, startJuz)
        if (rangeStart >= allAyahs.size) return emptyList()

        val remainingJuz = (31 - startJuz).coerceAtLeast(1)
        val totalHizbs = remainingJuz * 2

        if (totalDays <= remainingJuz) {
            return getPortionByJuz(allAyahs, startJuz, remainingJuz, totalDays, currentDay)
        } else if (totalDays <= totalHizbs) {
            return getPortionByHizb(allAyahs, startJuz, remainingJuz, totalDays, currentDay)
        } else {
            return getPortionByAyah(allAyahs, rangeStart, totalDays, currentDay)
        }
    }

    private fun getJuzIndices(allAyahs: List<AyahData>, startJuz: Int, remainingJuz: Int): List<Int> {
        val indices = mutableListOf<Int>()
        if (isHafsData(allAyahs)) {
            for (j in startJuz..(startJuz + remainingJuz - 1)) {
                val juz = JUZ_BOUNDARIES.find { it.juzNumber == j } ?: continue
                var idx = findExactAyahIndex(allAyahs, juz.startSurah, juz.startAyah)
                if (idx < 0) idx = getJuzStartIndex(allAyahs, j)
                indices.add(idx)
            }
        } else {
            for (j in startJuz..(startJuz + remainingJuz - 1)) {
                indices.add(getJuzStartIndex(allAyahs, j))
            }
        }
        return indices.distinct().sorted().take(remainingJuz)
    }

    private fun getPortionByJuz(
        allAyahs: List<AyahData>,
        startJuz: Int,
        remainingJuz: Int,
        totalDays: Int,
        currentDay: Int
    ): List<AyahData> {
        val juzIndices = getJuzIndices(allAyahs, startJuz, remainingJuz)
        if (juzIndices.isEmpty()) return emptyList()

        val portions = juzIndices.size
        val base = portions / totalDays
        val extra = portions % totalDays
        val startPortion = currentDay * base + minOf(currentDay, extra)
        val count = base + if (currentDay < extra) 1 else 0

        val fromIdx = juzIndices[startPortion]
        val toIdx = if (startPortion + count < juzIndices.size) juzIndices[startPortion + count] else allAyahs.size
        return allAyahs.subList(fromIdx, toIdx)
    }

    private fun getHizbIndices(allAyahs: List<AyahData>, startJuz: Int, remainingJuz: Int): List<Int> {
        val juzIndices = getJuzIndices(allAyahs, startJuz, remainingJuz)
        val hizbIndices = mutableListOf<Int>()
        for (i in juzIndices.indices) {
            val juzStart = juzIndices[i]
            val juzEnd = if (i + 1 < juzIndices.size) juzIndices[i + 1] else allAyahs.size
            val juzSize = juzEnd - juzStart
            val mid = juzStart + juzSize / 2
            hizbIndices.add(juzStart)
            hizbIndices.add(mid)
        }
        return hizbIndices.distinct().sorted()
    }

    private fun getPortionByHizb(
        allAyahs: List<AyahData>,
        startJuz: Int,
        remainingJuz: Int,
        totalDays: Int,
        currentDay: Int
    ): List<AyahData> {
        val hizbIndices = getHizbIndices(allAyahs, startJuz, remainingJuz)
        if (hizbIndices.isEmpty()) return emptyList()

        val portions = hizbIndices.size
        val base = portions / totalDays
        val extra = portions % totalDays
        val startPortion = currentDay * base + minOf(currentDay, extra)
        val count = base + if (currentDay < extra) 1 else 0

        val fromIdx = hizbIndices[startPortion]
        val toIdx = if (startPortion + count < hizbIndices.size) hizbIndices[startPortion + count] else allAyahs.size
        return allAyahs.subList(fromIdx, toIdx)
    }

    private fun getPortionByAyah(
        allAyahs: List<AyahData>,
        rangeStart: Int,
        totalDays: Int,
        currentDay: Int
    ): List<AyahData> {
        val rangeSize = allAyahs.size - rangeStart
        val base = rangeSize / totalDays
        val extra = rangeSize % totalDays
        val start = currentDay * base + minOf(currentDay, extra)
        val count = base + if (currentDay < extra) 1 else 0
        val fromIdx = rangeStart + start
        val toIdx = (fromIdx + count).coerceAtMost(allAyahs.size)
        return allAyahs.subList(fromIdx, toIdx)
    }

    fun formatDayRange(startJuz: Int, totalDays: Int, currentDay: Int): String {
        if (totalDays <= 0 || currentDay < 0) return ""
        val remainingJuz = 31 - startJuz
        val hindiDigits = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
        val toHindi = { n: Int -> n.toString().map { hindiDigits[it - '0'] }.joinToString("") }

        if (totalDays <= remainingJuz) {
            val perDay = remainingJuz / totalDays
            val remainder = remainingJuz % totalDays
            val desc = if (perDay > 0) {
                val parts = mutableListOf<String>()
                if (perDay >= 1) parts.add("$perDay ${if (perDay == 1) "جزء" else "أجزاء"}")
                if (remainder > 0) parts.add("و$remainder ${if (remainder == 1) "جزء" else "أجزاء"} الباقي يوزع")
                parts.joinToString(" ")
            } else ""
            return "من الجزء $startJuz إلى ٣٠ — $desc يوميًا لـ${toHindi(totalDays)} يومًا"
        } else if (totalDays <= remainingJuz * 2) {
            return "من الجزء $startJuz إلى ٣٠ — أنصاف أجزاء يوميًا لـ${toHindi(totalDays)} يومًا"
        } else {
            return "من الجزء $startJuz إلى ٣٠ — ${toHindi(totalDays)} يومًا (آيات)"
        }
    }

    fun getAyahsInJuzRange(allData: Map<Int, QuranSurah>, fromJuz: Int, toJuz: Int): List<AyahData> {
        val from = JUZ_BOUNDARIES.find { it.juzNumber == fromJuz } ?: JUZ_BOUNDARIES.first()
        val to = JUZ_BOUNDARIES.find { it.juzNumber == toJuz } ?: JUZ_BOUNDARIES.last()
        val result = mutableListOf<AyahData>()
        for (surahNum in from.startSurah..to.endSurah) {
            val surah = allData[surahNum] ?: continue
            val startA = if (surahNum == from.startSurah) from.startAyah else 1
            val endA = if (surahNum == to.endSurah) to.endAyah else surah.ayahs.lastOrNull()?.number ?: 0
            surah.ayahs.forEach { ayah ->
                if (ayah.number in startA..endA) {
                    result.add(ayah)
                }
            }
        }
        return result
    }

    fun getAyahsFromJuz(allData: Map<Int, QuranSurah>, fromJuz: Int): List<AyahData> =
        getAyahsInJuzRange(allData, fromJuz, 30)

    fun getAyahCountFromJuzToEnd(fromJuz: Int): Int {
        return JUZ_VERSE_COUNTS.drop(fromJuz - 1).sum()
    }

    fun describeDailyPortion(totalAyahs: Int, days: Int): String {
        if (days <= 0) return ""
        val perDay = totalAyahs.toFloat() / days
        var remaining = perDay
        val parts = mutableListOf<String>()
        var juzCount = 0
        var tempRemaining = remaining
        for (vc in JUZ_VERSE_COUNTS) {
            if (tempRemaining >= vc) {
                juzCount++
                tempRemaining -= vc
            } else break
        }
        if (juzCount > 0) {
            parts.add("$juzCount ${if (juzCount == 1) "جزء" else "أجزاء"}")
            remaining = tempRemaining
        }
        val avgHizbSize = remaining.coerceAtLeast(0f)
        val currentJuzIdx = juzCount
        if (currentJuzIdx < 30) {
            val juzSize = JUZ_VERSE_COUNTS[currentJuzIdx].toFloat()
            val hizbSize = juzSize / 2f
            val hizbCount = (remaining / hizbSize).toInt()
            if (hizbCount > 0) {
                parts.add("$hizbCount ${if (hizbCount == 1) "حزب" else "أحزاب"}")
                remaining -= hizbCount * hizbSize
            }
        }
        val pages = (remaining / 15f).toInt()
        if (pages > 0) {
            parts.add("$pages ${if (pages == 1) "صفحة" else "صفحات"}")
        }
        return parts.joinToString("، ").ifEmpty { "أقل من صفحة" }
    }

    fun findAyahIndexInRange(ayahs: List<AyahData>, targetSurah: Int, targetAyah: Int): Int {
        return ayahs.indexOfFirst { it.surahNumber == targetSurah && it.number == targetAyah }
    }

    fun findSurahNameForAyah(surahNumber: Int): String {
        return SurahDataProvider.allSurahs.find { it.number == surahNumber }?.name ?: ""
    }

    fun formatAyahRange(ayahs: List<AyahData>, startIndex: Int, endIndex: Int): String {
        if (ayahs.isEmpty() || startIndex >= ayahs.size) return ""
        val start = ayahs[startIndex]
        val end = ayahs[minOf(endIndex, ayahs.size - 1)]
        val startSurahName = findSurahNameForAyah(start.surahNumber)
        val endSurahName = findSurahNameForAyah(end.surahNumber)
        val hindiDigits = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
        val toHindi = { n: Int -> n.toString().map { hindiDigits[it - '0'] }.joinToString("") }
        return if (start.surahNumber == end.surahNumber) {
            "سورة $startSurahName من الآية ${toHindi(start.number)} إلى ${toHindi(end.number)}"
        } else {
            "من سورة $startSurahName (${toHindi(start.number)}) إلى سورة $endSurahName (${toHindi(end.number)})"
        }
    }

    private fun getAyahsInRegion(allData: Map<Int, QuranSurah>, region: JuzBoundary): List<AyahData> {
        val result = mutableListOf<AyahData>()
        for (surahNum in region.startSurah..region.endSurah) {
            val surah = allData[surahNum] ?: continue
            val startA = if (surahNum == region.startSurah) region.startAyah else 1
            val endA = if (surahNum == region.endSurah) region.endAyah else surah.ayahs.lastOrNull()?.number ?: 0
            surah.ayahs.forEach { ayah ->
                if (ayah.number in startA..endA) {
                    result.add(ayah)
                }
            }
        }
        return result
    }

    fun buildJuzBoundaries(allData: Map<Int, QuranSurah>): List<JuzBoundary> {
        return JUZ_BOUNDARIES
    }

    private fun getTotalAyat(allData: Map<Int, QuranSurah>, regions: List<JuzBoundary>): Int {
        var count = 0
        for (r in regions) {
            for (surahNum in r.startSurah..r.endSurah) {
                val surah = allData[surahNum] ?: continue
                val startA = if (surahNum == r.startSurah) r.startAyah else 1
                val endA = if (surahNum == r.endSurah) r.endAyah else surah.ayahs.lastOrNull()?.number ?: 0
                count += surah.ayahs.count { it.number in startA..endA }
            }
        }
        return count
    }
}
