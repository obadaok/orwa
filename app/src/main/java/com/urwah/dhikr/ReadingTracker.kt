package com.urwah.dhikr

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class ReadingPosition(
    val surahNumber: Int,
    val ayahNumber: Int,
    val surahName: String = ""
)

data class Bookmark(
    val id: String,
    val surahNumber: Int,
    val ayahNumber: Int,
    val surahName: String,
    val ayahText: String,
    val timestamp: Long = System.currentTimeMillis()
)

object ReadingTracker {
    private const val PREFS_NAME = "reading_tracker"
    private const val KEY_LAST_SURAH = "last_surah"
    private const val KEY_LAST_AYAH = "last_ayah"
    private const val KEY_BOOKMARKS = "bookmarks"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun savePosition(context: Context, surahNumber: Int, ayahNumber: Int) {
        prefs(context).edit().apply {
            putInt(KEY_LAST_SURAH, surahNumber)
            putInt(KEY_LAST_AYAH, ayahNumber)
            apply()
        }
    }

    fun getPosition(context: Context): ReadingPosition? {
        val surah = prefs(context).getInt(KEY_LAST_SURAH, -1)
        val ayah = prefs(context).getInt(KEY_LAST_AYAH, -1)
        if (surah < 0 || ayah < 0) return null
        val name = SurahDataProvider.allSurahs.find { it.number == surah }?.name ?: ""
        return ReadingPosition(surah, ayah, name)
    }

    fun clearPosition(context: Context) {
        prefs(context).edit().apply {
            remove(KEY_LAST_SURAH)
            remove(KEY_LAST_AYAH)
            apply()
        }
    }

    fun toggleBookmark(context: Context, surahNumber: Int, ayahNumber: Int, surahName: String, ayahText: String): Boolean {
        val id = "${surahNumber}:${ayahNumber}"
        val bookmarks = getAllBookmarks(context).toMutableList()
        val existing = bookmarks.indexOfFirst { it.id == id }
        return if (existing >= 0) {
            bookmarks.removeAt(existing)
            saveBookmarks(context, bookmarks)
            false
        } else {
            bookmarks.add(Bookmark(id, surahNumber, ayahNumber, surahName, ayahText))
            saveBookmarks(context, bookmarks.sortedBy { "${it.surahNumber}:${it.ayahNumber}" })
            true
        }
    }

    fun isBookmarked(context: Context, surahNumber: Int, ayahNumber: Int): Boolean {
        val id = "${surahNumber}:${ayahNumber}"
        return getAllBookmarks(context).any { it.id == id }
    }

    fun getAllBookmarks(context: Context): List<Bookmark> {
        val json = prefs(context).getString(KEY_BOOKMARKS, null) ?: return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            Bookmark(
                id = obj.getString("id"),
                surahNumber = obj.getInt("surahNumber"),
                ayahNumber = obj.getInt("ayahNumber"),
                surahName = obj.optString("surahName", ""),
                ayahText = obj.optString("ayahText", ""),
                timestamp = obj.optLong("timestamp", 0L)
            )
        }
    }

    private fun saveBookmarks(context: Context, bookmarks: List<Bookmark>) {
        val arr = JSONArray()
        bookmarks.forEach { b ->
            val obj = JSONObject()
            obj.put("id", b.id)
            obj.put("surahNumber", b.surahNumber)
            obj.put("ayahNumber", b.ayahNumber)
            obj.put("surahName", b.surahName)
            obj.put("ayahText", b.ayahText)
            obj.put("timestamp", b.timestamp)
            arr.put(obj)
        }
        prefs(context).edit().putString(KEY_BOOKMARKS, arr.toString()).apply()
    }
}
