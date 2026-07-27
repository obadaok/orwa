package com.urwah.dhikr

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle

object ReadingTimeTracker {

    private const val PREFS = "urwah_reading_time"
    private const val KEY_TOTAL_APP_MS = "total_app_ms"
    private const val KEY_QURAN_MS = "quran_ms"
    private const val KEY_KHATMA_MS = "khatma_ms"
    private const val KEY_LAST_SESSION_START = "last_session_start"
    private const val KEY_SESSION_TYPE = "session_type"

    const val TYPE_NONE = 0
    const val TYPE_QURAN = 1
    const val TYPE_KHATMA = 2

    private var sessionStart = 0L
    private var sessionType = TYPE_NONE

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun startSession(context: Context, type: Int) {
        stopSession(context)
        sessionStart = System.currentTimeMillis()
        sessionType = type
        prefs(context).edit()
            .putLong(KEY_LAST_SESSION_START, sessionStart)
            .putInt(KEY_SESSION_TYPE, type)
            .apply()
    }

    fun stopSession(context: Context) {
        if (sessionStart > 0 && sessionType != TYPE_NONE) {
            val elapsed = System.currentTimeMillis() - sessionStart
            val p = prefs(context).edit()
            when (sessionType) {
                TYPE_QURAN -> p.putLong(KEY_QURAN_MS, prefs(context).getLong(KEY_QURAN_MS, 0) + elapsed)
                TYPE_KHATMA -> p.putLong(KEY_KHATMA_MS, prefs(context).getLong(KEY_KHATMA_MS, 0) + elapsed)
            }
            p.apply()
        }
        sessionStart = 0L
        sessionType = TYPE_NONE
    }

    fun getTotalAppMs(context: Context): Long = prefs(context).getLong(KEY_TOTAL_APP_MS, 0)
    fun getQuranMs(context: Context): Long = prefs(context).getLong(KEY_QURAN_MS, 0)
    fun getKhatmaMs(context: Context): Long = prefs(context).getLong(KEY_KHATMA_MS, 0)

    fun addAppUsage(context: Context, ms: Long) {
        prefs(context).edit().putLong(KEY_TOTAL_APP_MS, getTotalAppMs(context) + ms).apply()
    }

    fun registerLifecycleCallbacks(application: Application) {
        var lastPause = 0L
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                lastPause = 0L
            }
            override fun onActivityPaused(activity: Activity) {
                lastPause = System.currentTimeMillis()
            }
            override fun onActivityStopped(activity: Activity) {
                if (lastPause > 0) {
                    val elapsed = System.currentTimeMillis() - lastPause
                    addAppUsage(activity, elapsed)
                    lastPause = 0L
                }
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }

    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        if (totalSec < 60) return "${totalSec}ث"
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return when {
            hours > 0 -> "$hours س $minutes د"
            minutes > 0 -> "$minutes د $seconds ث"
            else -> "${seconds}ث"
        }
    }
}
