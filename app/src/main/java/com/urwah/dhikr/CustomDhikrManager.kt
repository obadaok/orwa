package com.urwah.dhikr

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CustomDhikr(
    val id: Int,
    val category: String,
    val arabic: String,
    val repeats: Int,
    val virtue: String = ""
)

object CustomDhikrManager {

    private const val PREFS_NAME = "urwah_custom_dhikr"
    private const val KEY_ITEMS = "custom_items"

    fun getAll(context: Context): List<CustomDhikr> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<CustomDhikr>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(CustomDhikr(
                id = obj.getInt("id"),
                category = obj.getString("category"),
                arabic = obj.getString("arabic"),
                repeats = obj.getInt("repeats"),
                virtue = obj.optString("virtue", "")
            ))
        }
        return list
    }

    fun getByCategory(context: Context, category: String): List<CustomDhikr> {
        return getAll(context).filter { it.category == category }
    }

    private fun saveAll(context: Context, items: List<CustomDhikr>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        var nextId = 10000
        for (item in items) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("category", item.category)
            obj.put("arabic", item.arabic)
            obj.put("repeats", item.repeats)
            obj.put("virtue", item.virtue)
            arr.put(obj)
            if (item.id >= nextId) nextId = item.id + 1
        }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    fun add(context: Context, category: String, arabic: String, repeats: Int, virtue: String) {
        val items = getAll(context).toMutableList()
        val maxId = (items.maxOfOrNull { it.id } ?: 9999) + 1
        items.add(CustomDhikr(maxId, category, arabic, repeats, virtue))
        saveAll(context, items)
    }

    fun delete(context: Context, id: Int) {
        val items = getAll(context).toMutableList()
        items.removeAll { it.id == id }
        saveAll(context, items)
    }

    fun getAllDhikrItems(context: Context, category: String): List<DhikrItem> {
        val base = DhikrDataProvider.getDhikrs(category)
        val custom = getByCategory(context, category)
        val customItems = custom.map { c ->
            DhikrItem(
                id = c.id,
                category = c.category,
                title = "",
                arabic = c.arabic,
                repeats = c.repeats,
                virtue = c.virtue
            )
        }
        return base + customItems
    }
}
