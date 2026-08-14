package com.urwah.dhikr

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class UrwahApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        applyNightMode()
        ReadingTimeTracker.registerLifecycleCallbacks(this)
        // تدفئة فهرس المكتبة (8589 كتابًا) في الخلفية فور الإقلاع حتى لا يُحلَّل
        // على الـ main thread عند أول دخول إلى «المكتبة».
        ShamelaCatalogReader.warmCache(this)
        // تدفئة بيانات أسطر صفحات المصحف (~6MB JSON) فور الإقلاع حتى لا يتأخر
        // دخول «وضع الصفحات» على شاشة بيضاء.
        Thread {
            QuranPageLayouts.ensureLoadedBlocking(this)
            QuranPageIndex.ensureLoadedBlocking(this)
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun applyNightMode() {
        val prefs = getSharedPreferences("urwah_settings", Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode_enabled", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
