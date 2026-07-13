package com.urwah.dhikr

data class JuzBoundary(
    val juzNumber: Int,
    val startSurah: Int,
    val startAyah: Int,
    val endSurah: Int,
    val endAyah: Int
)

data class HizbBoundary(
    val hizbNumber: Int,
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

    val HIZB_BOUNDARIES: List<HizbBoundary> = listOf(
        HizbBoundary(1,  1,  1,  1,   2,  74),
        HizbBoundary(2,  1,  2,  75,  2,  141),
        HizbBoundary(3,  2,  2,  142, 2,  202),
        HizbBoundary(4,  2,  2,  203, 2,  252),
        HizbBoundary(5,  3,  2,  253, 3,  14),
        HizbBoundary(6,  3,  3,  15,  3,  92),
        HizbBoundary(7,  4,  3,  93,  3,  170),
        HizbBoundary(8,  4,  3,  171, 4,  23),
        HizbBoundary(9,  5,  4,  24,  4,  87),
        HizbBoundary(10, 5,  4,  88,  4,  147),
        HizbBoundary(11, 6,  4,  148, 5,  26),
        HizbBoundary(12, 6,  5,  27,  5,  81),
        HizbBoundary(13, 7,  5,  82,  6,  35),
        HizbBoundary(14, 7,  6,  36,  6,  110),
        HizbBoundary(15, 8,  6,  111, 6,  165),
        HizbBoundary(16, 8,  7,  1,   7,  87),
        HizbBoundary(17, 9,  7,  88,  7,  170),
        HizbBoundary(18, 9,  7,  171, 8,  40),
        HizbBoundary(19, 10, 8,  41,  9,  33),
        HizbBoundary(20, 10, 9,  34,  9,  92),
        HizbBoundary(21, 11, 9,  93,  10, 25),
        HizbBoundary(22, 11, 10, 26,  11, 5),
        HizbBoundary(23, 12, 11, 6,   11, 83),
        HizbBoundary(24, 12, 11, 84,  12, 52),
        HizbBoundary(25, 13, 12, 53,  13, 18),
        HizbBoundary(26, 13, 13, 19,  15, 1),
        HizbBoundary(27, 14, 15, 2,   16, 50),
        HizbBoundary(28, 14, 16, 51,  16, 128),
        HizbBoundary(29, 15, 17, 1,   17, 98),
        HizbBoundary(30, 15, 17, 99,  18, 74),
        HizbBoundary(31, 16, 18, 75,  19, 98),
        HizbBoundary(32, 16, 20, 1,   20, 135),
        HizbBoundary(33, 17, 21, 1,   21, 112),
        HizbBoundary(34, 17, 22, 1,   22, 78),
        HizbBoundary(35, 18, 23, 1,   24, 20),
        HizbBoundary(36, 18, 24, 21,  25, 20),
        HizbBoundary(37, 19, 25, 21,  26, 110),
        HizbBoundary(38, 19, 26, 111, 27, 55),
        HizbBoundary(39, 20, 27, 56,  28, 50),
        HizbBoundary(40, 20, 28, 51,  29, 45),
        HizbBoundary(41, 21, 29, 46,  31, 21),
        HizbBoundary(42, 21, 31, 22,  33, 30),
        HizbBoundary(43, 22, 33, 31,  34, 23),
        HizbBoundary(44, 22, 34, 24,  36, 27),
        HizbBoundary(45, 23, 36, 28,  37, 144),
        HizbBoundary(46, 23, 37, 145, 39, 31),
        HizbBoundary(47, 24, 39, 32,  40, 40),
        HizbBoundary(48, 24, 40, 41,  41, 46),
        HizbBoundary(49, 25, 41, 47,  43, 23),
        HizbBoundary(50, 25, 43, 24,  45, 37),
        HizbBoundary(51, 26, 46, 1,   48, 17),
        HizbBoundary(52, 26, 48, 18,  51, 30),
        HizbBoundary(53, 27, 51, 31,  54, 55),
        HizbBoundary(54, 27, 55, 1,   57, 29),
        HizbBoundary(55, 28, 58, 1,   61, 14),
        HizbBoundary(56, 28, 62, 1,   66, 12),
        HizbBoundary(57, 29, 67, 1,   71, 28),
        HizbBoundary(58, 29, 72, 1,   77, 50),
        HizbBoundary(59, 30, 78, 1,   86, 17),
        HizbBoundary(60, 30, 87, 1,   114, 6)
    )

    private val JUZ_VERSE_COUNTS = listOf(
        148, 111, 126, 131, 124, 110, 149, 142, 159, 127,
        151, 170, 154, 227, 185, 269, 190, 202, 339, 171,
        178, 169, 357, 175, 246, 195, 399, 137, 431, 564
    )

    /** تقسم الختمة على الأيام حسب حدود الأجزاء الكاملة */
    fun getDayAyahs(allData: Map<Int, QuranSurah>, startJuz: Int, totalDays: Int, currentDay: Int): List<AyahData> {
        if (totalDays <= 0 || currentDay < 0) return emptyList()
        val totalJuz = 31 - startJuz

        if (totalDays <= totalJuz) {
            val base = totalJuz / totalDays
            val extra = totalJuz % totalDays
            var cur = startJuz
            for (d in 0 until totalDays) {
                val cnt = base + if (d < extra) 1 else 0
                if (d == currentDay) {
                    return getAyahsInJuzRange(allData, cur, cur + cnt - 1)
                }
                cur += cnt
            }
        }
        return getAyahsFromJuz(allData, startJuz)
    }

    /** نطاق الورد اليومي المقسّم حسب الأجزاء */
    fun formatDayRange(startJuz: Int, totalDays: Int, currentDay: Int): String {
        if (totalDays <= 0 || currentDay < 0) return ""
        val totalJuz = 31 - startJuz
        val hindiDigits = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
        val toHindi = { n: Int -> n.toString().map { hindiDigits[it - '0'] }.joinToString("") }

        if (totalDays <= totalJuz) {
            val base = totalJuz / totalDays
            val extra = totalJuz % totalDays
            var cur = startJuz
            for (d in 0 until totalDays) {
                val cnt = base + if (d < extra) 1 else 0
                if (d == currentDay) {
                    val end = cur + cnt - 1
                    val startB = JUZ_BOUNDARIES.find { it.juzNumber == cur }!!
                    val endB = JUZ_BOUNDARIES.find { it.juzNumber == end }!!
                    val sName = findSurahNameForAyah(startB.startSurah)
                    val eName = findSurahNameForAyah(endB.endSurah)
                    return if (cur == end) {
                        "الورد: سورة $sName ← سورة $eName"
                    } else {
                        "الورد: سورة $sName ← سورة $eName (الأجزاء $cur-$end)"
                    }
                }
                cur += cnt
            }
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
            val endA = if (surahNum == to.endSurah) to.endAyah else surah.ayahs.last().number
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
