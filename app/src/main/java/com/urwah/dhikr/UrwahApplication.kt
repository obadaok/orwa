package com.urwah.dhikr

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class UrwahApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        applyNightMode()
    }

    private fun applyNightMode() {
        val prefs = getSharedPreferences("urwah_settings", Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode_enabled", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
