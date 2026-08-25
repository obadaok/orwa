package com.urwah.dhikr

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.urwah.dhikr.calendar.OrwaCalendarData

class UrwahApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        applyNightMode()
        ReadingTimeTracker.registerLifecycleCallbacks(this)
        FavoritesManager.init(this)
        // تدفئة فهرس المكتبة (8589 كتابًا) في الخلفية فور الإقلاع حتى لا يُحلَّل
        // على الـ main thread عند أول دخول إلى «المكتبة».
        ShamelaCatalogReader.warmCache(this)
        // تحميل مسبق للقرآن (JSON بحجم عدة ميغابايت) في الخلفية
        QuranDataLoader.preloadAsync(this)
        // تحميل مسبق لبيانات التقويم (252KB)
        OrwaCalendarData.preloadAsync(this)
    }

    private fun applyNightMode() {
        val prefs = getSharedPreferences("urwah_settings", Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode_enabled", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
