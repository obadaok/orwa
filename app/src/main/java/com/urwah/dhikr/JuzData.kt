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

    fun buildJuzBoundaries(allData: Map<Int, QuranSurah>): List<JuzBoundary> {
        if (JUZ_BOUNDARIES.size == 30) return JUZ_BOUNDARIES
        val allAyahs = allData.entries
            .sortedBy { it.key }
            .flatMap { (_, surah) -> surah.ayahs }
            .sortedBy { it.number }
        if (allAyahs.isEmpty()) return JUZ_BOUNDARIES
        val total = allAyahs.size
        val juzSize = total / 30
        return (0 until 30).map { j ->
            val startIdx = j * juzSize
            val endIdx = if (j < 29) ((j + 1) * juzSize - 1).coerceAtMost(total - 1) else total - 1
            val start = allAyahs[startIdx]
            val end = allAyahs[endIdx]
            JuzBoundary(j + 1, start.surahNumber, start.number, end.surahNumber, end.number)
        }
    }

    fun getDayAyahs(allData: Map<Int, QuranSurah>, startJuz: Int, totalDays: Int, currentDay: Int): List<AyahData> {
        if (totalDays <= 0 || currentDay < 0) return emptyList()
        val boundaries = buildJuzBoundaries(allData)
        val startRegion = (startJuz - 1).coerceIn(0, boundaries.lastIndex)
        val regions = boundaries.drop(startRegion)  // each region = a juz
        if (regions.isEmpty()) return emptyList()
        if (regions.size == 1) return getAyahsInRegion(allData, regions.first())
        val totalRegions = regions.size
        if (totalDays <= totalRegions * 60) {
            val hizbs = regions.flatMap { splitJuzIntoHizbs(allData, it) }
            return getDayPortionFromHizbs(allData, hizbs, totalDays, currentDay)
        }
        return getDayPortionFromAyahs(allData, regions, totalDays, currentDay)
    }

    private fun splitJuzIntoHizbs(allData: Map<Int, QuranSurah>, juz: JuzBoundary): List<Pair<Int, Int>> {
        val ayahsInJuz = getAyahsInRegion(allData, juz)
        if (ayahsInJuz.isEmpty()) return listOf(juz.startSurah to juz.startAyah)
        val half = ayahsInJuz.size / 2
        if (half <= 0) return listOf(juz.startSurah to juz.startAyah)
        val mid = ayahsInJuz[half]
        return listOf(
            juz.startSurah to juz.startAyah,
            mid.surahNumber to mid.number
        )
    }

    private fun getDayPortionFromHizbs(
        allData: Map<Int, QuranSurah>,
        hizbs: List<Pair<Int, Int>>,
        totalDays: Int,
        currentDay: Int
    ): List<AyahData> {
        val allAyahs = allData.entries.sortedBy { it.key }.flatMap { (_, s) ->
            s.ayahs.sortedBy { it.number }
        }
        val hizbAyahIndices = mutableListOf(0)
        for ((surah, ayah) in hizbs) {
            val idx = allAyahs.indexOfFirst { it.surahNumber == surah && it.number == ayah }
            if (idx >= 0) hizbAyahIndices.add(idx)
        }
        hizbAyahIndices.add(allAyahs.size)
        hizbAyahIndices.sort()
        hizbAyahIndices.distinct()
        val totalAyat = allAyahs.size
        val base = totalAyat / totalDays
        val extra = totalAyat % totalDays
        val start = currentDay * base + minOf(currentDay, extra)
        val cnt = base + if (currentDay < extra) 1 else 0
        val end = (start + cnt).coerceAtMost(totalAyat)
        return allAyahs.subList(start, end)
    }

    private fun getDayPortionFromAyahs(
        allData: Map<Int, QuranSurah>,
        regions: List<JuzBoundary>,
        totalDays: Int,
        currentDay: Int
    ): List<AyahData> {
        val totalAyat = getTotalAyat(allData, regions)
        if (totalAyat == 0) return emptyList()
        val base = totalAyat / totalDays
        val extra = totalAyat % totalDays
        val start = currentDay * base + minOf(currentDay, extra)
        val cnt = base + if (currentDay < extra) 1 else 0
        val allAyahs = allData.entries.sortedBy { it.key }.flatMap { (_, s) ->
            s.ayahs.sortedBy { it.number }
        }
        val fromRegion = regions.first()
        val toRegion = regions.last()
        val regionStart = allAyahs.indexOfFirst { it.surahNumber == fromRegion.startSurah && it.number == fromRegion.startAyah }.coerceAtLeast(0)
        val regionEnd = allAyahs.indexOfFirst { it.surahNumber == toRegion.endSurah && it.number >= toRegion.endAyah }.let { idx ->
            if (idx < 0) allAyahs.size else idx + 1
        }
        val inRange = allAyahs.subList(regionStart, regionEnd.coerceAtMost(allAyahs.size))
        return inRange.subList(
            start.coerceAtMost(inRange.size - 1),
            (start + cnt).coerceAtMost(inRange.size)
        )
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

    fun formatDayRange(startJuz: Int, totalDays: Int, currentDay: Int): String {
        if (totalDays <= 0 || currentDay < 0) return ""
        val totalJuz = 31 - startJuz
        val hindiDigits = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
        val toHindi = { n: Int -> n.toString().map { hindiDigits[it - '0'] }.joinToString("") }

        if (totalDays <= totalJuz * 60) {
            return "جزء $startJuz فما بعد — ${toHindi(totalDays)} يومًا"
        }
        return ""
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
}
