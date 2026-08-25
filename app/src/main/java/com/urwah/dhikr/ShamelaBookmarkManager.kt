package com.urwah.dhikr

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ShamelaBookmark(
    val bookId: Int,
    val page: Int,
    val charOffset: Int = -1,
    val bookTitle: String,
    val text: String,
    val time: Long
)

object ShamelaBookmarkManager {
    private const val PREFS_NAME = "urwah_shamela_bookmarks_v2"
    private const val KEY_BOOKMARKS = "bookmarks"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun migrateFromOldStorage(context: Context) {
        val oldPrefs = context.getSharedPreferences("urwah_shamela_bookmarks", Context.MODE_PRIVATE)
        if (prefs(context).contains(KEY_BOOKMARKS)) return
        val all = oldPrefs.all ?: return
        val bookmarks = all.mapNotNull { (key, value) ->
            if (key.startsWith("bm_") && value is String) {
                try {
                    val obj = JSONObject(value)
                    ShamelaBookmark(
                        bookId = obj.optInt("book_id", 0),
                        page = obj.optInt("page", 0),
                        charOffset = obj.optInt("char_offset", -1),
                        bookTitle = obj.optString("book_title", ""),
                        text = obj.optString("text", ""),
                        time = obj.optLong("time", 0L)
                    )
                } catch (_: Exception) { null }
            } else null
        }
        if (bookmarks.isNotEmpty()) {
            saveAll(context, bookmarks)
        }
        oldPrefs.edit().clear().apply()
    }

    fun getAll(context: Context): List<ShamelaBookmark> {
        val json = prefs(context).getString(KEY_BOOKMARKS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val obj = arr.getJSONObject(i)
                    ShamelaBookmark(
                        bookId = obj.getInt("book_id"),
                        page = obj.getInt("page"),
                        charOffset = obj.optInt("char_offset", -1),
                        bookTitle = obj.optString("book_title", ""),
                        text = obj.optString("text", ""),
                        time = obj.optLong("time", 0L)
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getByBookId(context: Context, bookId: Int): List<ShamelaBookmark> =
        getAll(context).filter { it.bookId == bookId }

    fun getBookmark(context: Context, bookId: Int, page: Int): ShamelaBookmark? =
        getAll(context).find { it.bookId == bookId && it.page == page }

    fun getBookmarkedBookIds(context: Context): Set<Int> =
        getAll(context).map { it.bookId }.toSet()

    fun hasBookmark(context: Context, bookId: Int, page: Int): Boolean =
        getBookmark(context, bookId, page) != null

    fun add(context: Context, bookmark: ShamelaBookmark) {
        val list = getAll(context).toMutableList()
        list.removeAll { it.bookId == bookmark.bookId && it.page == bookmark.page }
        list.add(bookmark)
        saveAll(context, list)
    }

    fun remove(context: Context, bookId: Int, page: Int) {
        val list = getAll(context).toMutableList()
        list.removeAll { it.bookId == bookId && it.page == page }
        saveAll(context, list)
    }

    private fun saveAll(context: Context, bookmarks: List<ShamelaBookmark>) {
        val arr = JSONArray()
        bookmarks.forEach { b ->
            val obj = JSONObject()
            obj.put("book_id", b.bookId)
            obj.put("page", b.page)
            obj.put("char_offset", b.charOffset)
            obj.put("book_title", b.bookTitle)
            obj.put("text", b.text)
            obj.put("time", b.time)
            arr.put(obj)
        }
        prefs(context).edit().putString(KEY_BOOKMARKS, arr.toString()).apply()
    }
}
