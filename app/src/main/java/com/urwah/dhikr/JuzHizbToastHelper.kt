package com.urwah.dhikr

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast

class JuzHizbToastHelper(private val context: Context, allAyahs: List<AyahData>) {

    data class Point(
        val ayahIndex: Int, val title: String, val label: String, val icon: String
    )

    private val points: List<Point>
    private val handler = Handler(Looper.getMainLooper())
    private var lastIdx = -1
    private var activeToast: Toast? = null
    private var lastToastTime = 0L

    init {
        val hindi = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
        val h = { n: Int -> n.toString().map { hindi[it - '0'] }.joinToString("") }

        val pts = mutableListOf<Point>()
        for (juz in JuzData.JUZ_BOUNDARIES) {
            val si = allAyahs.indexOfFirst { it.surahNumber == juz.startSurah && it.number == juz.startAyah }
            val ei = allAyahs.indexOfFirst { it.surahNumber == juz.endSurah && it.number == juz.endAyah }
            if (si < 0 || ei <= si) continue
            val n = ei - si + 1
            if (n < 4) continue

            pts.add(Point(si, "الجزء ${h(juz.juzNumber)}", "الحزب ${h(juz.juzNumber * 2 - 1)}", h(juz.juzNumber)))

            val quarters = listOf(n / 4 to "ربع", n / 2 to "نصف", 3 * n / 4 to "ثلاثة أرباع")
            quarters.forEachIndexed { i, (off, who) ->
                val ai = si + off
                if (ai > si && ai < ei) {
                    val hib = if (i == 0) juz.juzNumber * 2 - 1 else juz.juzNumber * 2
                    val icon = when (who) { "ربع" -> "¼"; "نصف" -> "½"; else -> "¾" }
                    pts.add(Point(ai, "$who الحزب", "الحزب ${h(hib)} — جزء ${h(juz.juzNumber)}", icon))
                }
            }
        }
        points = pts.sortedBy { it.ayahIndex }
    }

    fun onPositionReached(globalAyahIndex: Int) {
        if (globalAyahIndex < 0) return
        val match = points.lastOrNull { it.ayahIndex <= globalAyahIndex } ?: return
        if (match.ayahIndex == lastIdx) return
        lastIdx = match.ayahIndex
        showToast(match.title, match.label, match.icon)
    }

    private fun showToast(title: String, label: String, icon: String) {
        val now = System.currentTimeMillis()
        if (now - lastToastTime < 1500) return
        lastToastTime = now

        activeToast?.cancel()
        val v = LayoutInflater.from(context).inflate(R.layout.toast_hizb_notification, null)
        v.findViewById<TextView>(R.id.tvToastTitle).text = title
        v.findViewById<TextView>(R.id.tvToastLabel).text = label
        v.findViewById<TextView>(R.id.tvToastIcon).text = icon

        val t = Toast(context).apply {
            duration = Toast.LENGTH_LONG
            @Suppress("DEPRECATION")
            view = v
            setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 120)
        }
        activeToast = t
        v.alpha = 0f
        t.show()
        v.animate().alpha(1f).setDuration(250).start()
        handler.postDelayed({
            v.animate().alpha(0f).setDuration(250).withEndAction { t.cancel(); activeToast = null }.start()
        }, 2000L)
    }

    fun detach() {
        handler.removeCallbacksAndMessages(null)
        activeToast?.cancel()
        activeToast = null
    }
}
