package com.urwah.dhikr

data class DhikrItem(
    val id: Int,
    val category: String = "",
    val title: String = "",
    val arabic: String = "",
    val repeats: Int = 1,
    val virtue: String = "",
    val reference: String = "",
    val source: String = "",
    val hadithGrade: String = "",
    val notes: String = ""
)
