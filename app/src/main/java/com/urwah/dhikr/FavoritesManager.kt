package com.urwah.dhikr

import android.content.Context
import android.content.SharedPreferences

object FavoritesManager {

    private const val PREFS_NAME = "urwah_favorites"
    private const val FAVORITES_KEY = "favorite_ids"

    private var prefs: SharedPreferences? = null

    private var cachedIds: MutableSet<Int> = mutableSetOf()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        cachedIds = prefs?.getStringSet(FAVORITES_KEY, emptySet())
            ?.mapNotNull { it.toIntOrNull() }
            ?.toMutableSet() ?: mutableSetOf()
    }

    fun toggle(id: Int): Boolean {
        return if (cachedIds.contains(id)) {
            cachedIds.remove(id)
            save()
            false
        } else {
            cachedIds.add(id)
            save()
            true
        }
    }

    fun isFavorite(id: Int): Boolean = cachedIds.contains(id)

    fun getAllFavorites(): Set<Int> = cachedIds.toSet()

    private fun save() {
        prefs?.edit()?.putStringSet(FAVORITES_KEY, cachedIds.map { it.toString() }.toSet())?.apply()
    }
}
