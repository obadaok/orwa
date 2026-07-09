package com.urwah.dhikr

sealed class CategoryGroupedItem {
    data class Header(val title: String) : CategoryGroupedItem()
    data class Item(val category: DhikrCategory) : CategoryGroupedItem()
}
