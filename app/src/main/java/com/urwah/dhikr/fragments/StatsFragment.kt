package com.urwah.dhikr.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.urwah.dhikr.KhatmaManager
import com.urwah.dhikr.R
import com.urwah.dhikr.ReadingTimeTracker

class StatsFragment : Fragment() {

    private lateinit var root: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        root = inflater.inflate(R.layout.fragment_stats, container, false)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadStats()
    }

    override fun onResume() {
        super.onResume()
        loadStats()
    }

    private fun loadStats() {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("urwah_stats", Context.MODE_PRIVATE)
        val adhkarCount = prefs.getInt("total_completed", 0)

        val appTimeMs = ReadingTimeTracker.getTotalAppMs(ctx)
        val quranTimeMs = ReadingTimeTracker.getQuranMs(ctx)
        val khatmaTimeMs = ReadingTimeTracker.getKhatmaMs(ctx)

        val khatmas = KhatmaManager.getAll(ctx)
        val completedKhatmas = khatmas.count { !it.isActive }
        val activeKhatmas = khatmas.count { it.isActive }

        root.findViewById<TextView>(R.id.tv_adhkar_count).text = adhkarCount.toString()
        root.findViewById<TextView>(R.id.tv_app_time).text = ReadingTimeTracker.formatDuration(appTimeMs)
        root.findViewById<TextView>(R.id.tv_quran_time).text = ReadingTimeTracker.formatDuration(quranTimeMs)
        root.findViewById<TextView>(R.id.tv_khatma_time).text = ReadingTimeTracker.formatDuration(khatmaTimeMs)
        root.findViewById<TextView>(R.id.tv_completed_khatmas).text = completedKhatmas.toString()
        root.findViewById<TextView>(R.id.tv_active_khatmas).text = activeKhatmas.toString()
    }
}
