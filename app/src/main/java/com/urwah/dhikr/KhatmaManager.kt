package com.urwah.dhikr

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Khatma(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val startJuz: Int,
    val totalDays: Int,
    val currentDay: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSurah: Int = -1,
    val lastAyah: Int = -1,
    val color: Int = -1
) {
    companion object {
        val PALETTE = listOf(
            0xFF8B6F5E.toInt(), 0xFF5E8B6F.toInt(), 0xFF6F5E8B.toInt(),
            0xFF8B5E5E.toInt(), 0xFF5E7B8B.toInt(), 0xFF8B835E.toInt(),
            0xFF7B5E8B.toInt(), 0xFF5E8B8B.toInt()
        )
        fun pickColor(startJuz: Int): Int = PALETTE[(startJuz - 1) % PALETTE.size]
    }
}

object KhatmaManager {
    private const val PREFS_NAME = "khatma_prefs"
    private const val KEY_KHATMAS = "khatmas"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(context: Context): List<Khatma> {
        val json = prefs(context).getString(KEY_KHATMAS, null) ?: return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            Khatma(
                id = obj.getString("id"),
                name = obj.getString("name"),
                startJuz = obj.getInt("startJuz"),
                totalDays = obj.getInt("totalDays"),
                currentDay = obj.getInt("currentDay"),
                isActive = obj.getBoolean("isActive"),
                createdAt = obj.optLong("createdAt", 0L),
                lastSurah = obj.optInt("lastSurah", -1),
                lastAyah = obj.optInt("lastAyah", -1),
                color = obj.optInt("color", -1)
            )
        }
    }

    fun add(context: Context, khatma: Khatma) {
        val list = getAll(context).toMutableList()
        list.add(khatma)
        save(context, list)
    }

    fun updateDay(context: Context, id: String, day: Int) {
        val list = getAll(context).toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(
                currentDay = day,
                isActive = day < list[idx].totalDays
            )
            save(context, list)
        }
    }

    fun updatePosition(context: Context, id: String, surah: Int, ayah: Int) {
        val list = getAll(context).toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(lastSurah = surah, lastAyah = ayah)
            save(context, list)
        }
    }

    fun delete(context: Context, id: String) {
        val list = getAll(context).toMutableList()
        list.removeAll { it.id == id }
        save(context, list)
    }

    private fun save(context: Context, khatmas: List<Khatma>) {
        val arr = JSONArray()
        khatmas.forEach { k ->
            val obj = JSONObject()
            obj.put("id", k.id)
            obj.put("name", k.name)
            obj.put("startJuz", k.startJuz)
            obj.put("totalDays", k.totalDays)
            obj.put("currentDay", k.currentDay)
            obj.put("isActive", k.isActive)
            obj.put("createdAt", k.createdAt)
            obj.put("lastSurah", k.lastSurah)
            obj.put("lastAyah", k.lastAyah)
            obj.put("color", k.color)
            arr.put(obj)
        }
        prefs(context).edit().putString(KEY_KHATMAS, arr.toString()).apply()
    }
}
