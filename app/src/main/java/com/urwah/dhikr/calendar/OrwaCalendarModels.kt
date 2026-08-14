package com.urwah.dhikr.calendar

data class OrwaCalendarUiState(
    val dayName: String,
    val hijriDate: String,
    val gregorianDate: String,
    val daysUntilRamadan: Int,
    val asmaHusnaName: String,
    val asmaHusnaExplanation: String,
    val ayahText: String,
    val surahName: String,
    val ayahNumber: Int,
    val tafsirText: String,
    val hadithText: String,
    val hadithNarrator: String,
    val hadithSource: String,
    val benefitOfTheDay: String,
    val scholarName: String
)

data class HijriDayCell(
    val hijriDay: Int,
    val gregDay: Int,
    val gregMonth: Int,
    val gregYear: Int,
    val dowIndex: Int,
    val isFriday: Boolean,
    val isSaturday: Boolean
)

data class OrwaMonthGrid(
    val hijriYear: Int,
    val hijriMonth: Int,
    val hijriMonthName: String,
    val gregorianLabel: String,
    val leadingBlanks: Int,
    val cells: List<HijriDayCell>,
    val todayHijriDay: Int
)
