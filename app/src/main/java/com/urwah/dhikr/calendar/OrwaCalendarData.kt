package com.urwah.dhikr.calendar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.GregorianCalendar
import kotlin.math.floor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object OrwaCalendarData {

    private var cachedState: OrwaCalendarUiState? = null
    private var cachedDayOfYear: Int = -1

    fun buildToday(context: Context): OrwaCalendarUiState {
        val today = GregorianCalendar()
        val dayOfYear = today.get(Calendar.DAY_OF_YEAR)

        if (cachedState != null && cachedDayOfYear == dayOfYear) {
            return cachedState!!
        }

        val state = buildContentFor(
            context,
            today.get(Calendar.DAY_OF_MONTH),
            today.get(Calendar.MONTH) + 1,
            today.get(Calendar.YEAR)
        )

        cachedState = state
        cachedDayOfYear = dayOfYear
        return state
    }

    fun buildContentFor(context: Context, gregDay: Int, gregMonth: Int, gregYear: Int): OrwaCalendarUiState {
        val gc = GregorianCalendar(gregYear, gregMonth - 1, gregDay)
        val dayOfYear = gc.get(Calendar.DAY_OF_YEAR)
        val hijri = gregorianToHijri(gc)
        val data = loadCalendarData(context)
        val dailyIndex = dayOfYear % minOf(
            data.ayahs.length(),
            data.hadiths.length(),
            data.scholarQuotes.length(),
            data.namesOfAllah.length()
        )

        val ayah = data.ayahs.getJSONObject(dailyIndex)
        val hadith = data.hadiths.getJSONObject(dailyIndex)
        val quote = data.scholarQuotes.getJSONObject(dailyIndex)
        val name = data.namesOfAllah.getJSONObject(dailyIndex)

        return OrwaCalendarUiState(
            dayName = arabicDayName(gc.get(Calendar.DAY_OF_WEEK)),
            hijriDate = formatHijriDate(hijri),
            gregorianDate = formatGregorianDate(gc),
            daysUntilRamadan = daysUntilRamadan(hijri),
            asmaHusnaName = name.getString("name"),
            asmaHusnaExplanation = name.getString("explanation"),
            ayahText = ayah.getString("text"),
            surahName = ayah.getString("surah_name_ar"),
            ayahNumber = ayah.getInt("ayah_number"),
            tafsirText = ayah.optString("tafsir", ""),
            hadithText = hadith.getString("text"),
            hadithNarrator = hadith.optString("narrator", ""),
            hadithSource = hadith.optString("source", ""),
            benefitOfTheDay = quote.getString("text"),
            scholarName = quote.getString("author")
        )
    }

    private data class CalendarData(
        val ayahs: JSONArray,
        val hadiths: JSONArray,
        val scholarQuotes: JSONArray,
        val namesOfAllah: JSONArray
    )

    @Volatile
    private var cachedCalendarData: CalendarData? = null

    fun preloadAsync(context: Context) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            try {
                loadCalendarData(context.applicationContext)
            } catch (_: Exception) {
            }
        }
    }

    private fun loadCalendarData(context: Context): CalendarData {
        cachedCalendarData?.let { return it }
        synchronized(this) {
            cachedCalendarData?.let { return it }
            val jsonStr = context.assets.open("calendar_data.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonStr)
            val data = CalendarData(
                ayahs = root.getJSONArray("ayahs"),
                hadiths = root.getJSONArray("hadiths"),
                scholarQuotes = root.getJSONArray("scholar_quotes"),
                namesOfAllah = root.getJSONArray("names_of_allah")
            )
            cachedCalendarData = data
            return data
        }
    }

    // ──────────────────────────────────────────────
    //  Hijri date conversion (tabular algorithm)
    // ──────────────────────────────────────────────

    private data class HijriDate(val year: Int, val month: Int, val day: Int)

    private fun gregorianToHijri(gc: GregorianCalendar): HijriDate {
        val jd = gregorianToJulianDay(gc)
        return julianDayToHijri(jd)
    }

    private fun gregorianToJulianDay(gc: GregorianCalendar): Int {
        val y = gc.get(Calendar.YEAR)
        val m = gc.get(Calendar.MONTH) + 1
        val d = gc.get(Calendar.DAY_OF_MONTH)
        val a = floor((14.0 - m) / 12.0).toInt()
        val yy = y + 4800 - a
        val mm = m + 12 * a - 3
        return d + floor((153.0 * mm + 2.0) / 5.0).toInt() +
                365 * yy + floor(yy / 4.0).toInt() -
                floor(yy / 100.0).toInt() + floor(yy / 400.0).toInt() - 32045
    }

    private fun julianDayToHijri(jd: Int): HijriDate {
        val l = jd - 1948440 + 10632
        val n = floor((l - 1.0) / 10631.0).toInt()
        var l2 = l - 10631 * n + 354
        val j = floor((10985.0 - l2) / 5316.0).toInt() * floor((50.0 * l2) / 17719.0).toInt() +
                floor(l2 / 5670.0).toInt() * floor((43.0 * l2) / 15238.0).toInt()
        l2 = l2 - floor((30.0 - j) / 15.0).toInt() * floor((17719.0 * j) / 50.0).toInt() -
                floor(j / 16.0).toInt() * floor((15238.0 * j) / 43.0).toInt() + 29
        val m = floor((24.0 * l2) / 709.0).toInt()
        val d = (l2 - floor((709.0 * m) / 24.0)).toInt()
        val y = 30 * n + j - 30
        return HijriDate(y, m, d)
    }

    private fun hijriToJulianDay(h: HijriDate): Int {
        return floor((11.0 * h.year + 3.0) / 30.0).toInt() +
                354 * h.year + 30 * h.month -
                floor((h.month - 1.0) / 2.0).toInt() + h.day + 1948440 - 385
    }

    private fun julianDayToGregorian(jd: Int): Triple<Int, Int, Int> {
        val a = jd + 32044
        val b = floor((4.0 * a + 3.0) / 146097.0).toInt()
        val c = a - floor((146097.0 * b) / 4.0).toInt()
        val d = floor((4.0 * c + 3.0) / 1461.0).toInt()
        val e = c - floor((1461.0 * d) / 4.0).toInt()
        val m = floor((5.0 * e + 2.0) / 153.0).toInt()
        val day = e - floor((153.0 * m + 2.0) / 5.0).toInt() + 1
        val month = m + 3 - 12 * floor(m / 10.0).toInt()
        val year = 100 * b + d - 4800 + floor(m / 10.0).toInt()
        return Triple(year, month, day)
    }

    private fun dayOfWeekIndex(gYear: Int, gMonth: Int, gDay: Int): Int {
        val gc = GregorianCalendar(gYear, gMonth - 1, gDay)
        return gc.get(Calendar.DAY_OF_WEEK) - 1
    }

    // ──────────────────────────────────────────────
    //  Month grid (public API for the calendar UI)
    // ──────────────────────────────────────────────

    fun todayHijriMonth(): Pair<Int, Int> {
        val h = gregorianToHijri(GregorianCalendar())
        return Pair(h.year, h.month)
    }

    fun hijriMonthLength(year: Int, month: Int): Int {
        val cur = hijriToJulianDay(HijriDate(year, month, 1))
        val nxt = if (month == 12) hijriToJulianDay(HijriDate(year + 1, 1, 1))
        else hijriToJulianDay(HijriDate(year, month + 1, 1))
        return nxt - cur
    }

    fun hijriToGregorian(year: Int, month: Int, day: Int): Triple<Int, Int, Int> {
        return julianDayToGregorian(hijriToJulianDay(HijriDate(year, month, day)))
    }

    fun hijriDayFor(gregDay: Int, gregMonth: Int, gregYear: Int): Int {
        return gregorianToHijri(GregorianCalendar(gregYear, gregMonth - 1, gregDay)).day
    }

    fun buildMonthGrid(hijriYear: Int, hijriMonth: Int): OrwaMonthGrid {
        val dayOneJd = hijriToJulianDay(HijriDate(hijriYear, hijriMonth, 1))
        val nextMonthJd = hijriToJulianDay(
            if (hijriMonth == 12) HijriDate(hijriYear + 1, 1, 1)
            else HijriDate(hijriYear, hijriMonth + 1, 1)
        )
        val monthLen = nextMonthJd - dayOneJd

        val todayHijri = gregorianToHijri(GregorianCalendar())
        val todayInMonth = if (todayHijri.year == hijriYear && todayHijri.month == hijriMonth) {
            todayHijri.day
        } else {
            0
        }

        var leadingBlanks = 0
        val cells = ArrayList<HijriDayCell>(monthLen)
        for (d in 1..monthLen) {
            val jd = dayOneJd + d - 1
            val (gY, gM, gD) = julianDayToGregorian(jd)
            val dow = dayOfWeekIndex(gY, gM, gD)
            if (d == 1) leadingBlanks = dow
            cells.add(HijriDayCell(d, gD, gM, gY, dow, dow == 5, dow == 6))
        }

        val (_, fM, _) = julianDayToGregorian(dayOneJd)
        val (lYear, lM, _) = julianDayToGregorian(dayOneJd + monthLen - 1)
        val gregLabel = if (fM == lM) {
            "${gregorianMonths[fM]} ${toArabicNum(lYear)} م"
        } else {
            "${gregorianMonths[fM]} — ${gregorianMonths[lM]} ${toArabicNum(lYear)} م"
        }

        return OrwaMonthGrid(
            hijriYear = hijriYear,
            hijriMonth = hijriMonth,
            hijriMonthName = hijriMonths[hijriMonth],
            gregorianLabel = gregLabel,
            leadingBlanks = leadingBlanks,
            cells = cells,
            todayHijriDay = todayInMonth
        )
    }

    // ──────────────────────────────────────────────
    //  Formatting
    // ──────────────────────────────────────────────

    private val hijriMonths = arrayOf(
        "", "محرّم", "صفر", "ربيع الأول", "ربيع الثاني",
        "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان",
        "رمضان", "شوّال", "ذو القعدة", "ذو الحجة"
    )

    private val gregorianMonths = arrayOf(
        "", "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    )

    private fun formatHijriDate(h: HijriDate): String {
        return "${toArabicNum(h.day)} ${hijriMonths[h.month]} ${toArabicNum(h.year)} هـ"
    }

    private fun formatGregorianDate(gc: GregorianCalendar): String {
        val d = gc.get(Calendar.DAY_OF_MONTH)
        val m = gc.get(Calendar.MONTH) + 1
        val y = gc.get(Calendar.YEAR)
        return "$d ${gregorianMonths[m]} $y م"
    }

    fun toArabicNum(n: Int): String {
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        return n.toString().map { if (it.isDigit()) arabicDigits[it - '0'] else it }.joinToString("")
    }

    private fun arabicDayName(dow: Int): String = when (dow) {
        Calendar.SUNDAY -> "الأحد"
        Calendar.MONDAY -> "الاثنين"
        Calendar.TUESDAY -> "الثلاثاء"
        Calendar.WEDNESDAY -> "الأربعاء"
        Calendar.THURSDAY -> "الخميس"
        Calendar.FRIDAY -> "الجمعة"
        Calendar.SATURDAY -> "السبت"
        else -> ""
    }

    // ──────────────────────────────────────────────
    //  Ramadan countdown
    // ──────────────────────────────────────────────

    private fun daysUntilRamadan(currentHijri: HijriDate): Int {
        val ramadanMonth = 9
        if (currentHijri.month == ramadanMonth) return 0
        var targetYear = currentHijri.year
        if (currentHijri.month > ramadanMonth) targetYear++
        val targetJd = hijriToJulianDay(HijriDate(targetYear, ramadanMonth, 1))
        val currentJd = hijriToJulianDay(currentHijri)
        return targetJd - currentJd
    }
}
