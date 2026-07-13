package com.urwah.dhikr

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class SmartBookmark(
    val name: String,
    val color: Int,
    val surahNumber: Int,
    val ayahNumber: Int,
    val surahName: String = "",
    val ayahText: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

object BookmarkManager {
    private const val PREFS_NAME = "smart_bookmarks"
    private const val KEY_BOOKMARKS = "bookmarks"

    val COLORS = listOf(
        0xFF8B6F5E.toInt(),
        0xFF5E8B6F.toInt(),
        0xFF6F5E8B.toInt(),
        0xFF8B5E5E.toInt(),
        0xFF5E7B8B.toInt(),
        0xFF8B835E.toInt(),
        0xFF7B5E8B.toInt(),
        0xFF5E8B8B.toInt()
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(context: Context): List<SmartBookmark> {
        val json = prefs(context).getString(KEY_BOOKMARKS, null) ?: return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            SmartBookmark(
                name = obj.getString("name"),
                color = obj.getInt("color"),
                surahNumber = obj.getInt("surahNumber"),
                ayahNumber = obj.getInt("ayahNumber"),
                surahName = obj.optString("surahName", ""),
                ayahText = obj.optString("ayahText", ""),
                timestamp = obj.optLong("timestamp", 0L)
            )
        }
    }

    fun getBySurah(context: Context, surahNumber: Int): List<SmartBookmark> =
        getAll(context).filter { it.surahNumber == surahNumber }

    fun add(context: Context, bookmark: SmartBookmark) {
        val list = getAll(context).toMutableList()
        list.add(bookmark)
        save(context, list)
    }

    fun update(context: Context, name: String, surahNumber: Int, ayahNumber: Int, surahName: String, ayahText: String) {
        val list = getAll(context).toMutableList()
        val idx = list.indexOfFirst { it.name == name }
        if (idx >= 0) {
            list[idx] = list[idx].copy(
                surahNumber = surahNumber,
                ayahNumber = ayahNumber,
                surahName = surahName,
                ayahText = ayahText,
                timestamp = System.currentTimeMillis()
            )
            save(context, list)
        }
    }

    fun delete(context: Context, name: String) {
        val list = getAll(context).toMutableList()
        list.removeAll { it.name == name }
        save(context, list)
    }

    fun deleteAllForSurah(context: Context, surahNumber: Int) {
        val list = getAll(context).toMutableList()
        list.removeAll { it.surahNumber == surahNumber }
        save(context, list)
    }

    private fun save(context: Context, bookmarks: List<SmartBookmark>) {
        val arr = JSONArray()
        bookmarks.forEach { b ->
            val obj = JSONObject()
            obj.put("name", b.name)
            obj.put("color", b.color)
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
