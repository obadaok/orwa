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
