package com.urwah.dhikr.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.urwah.dhikr.DhikrDataProvider
import com.urwah.dhikr.FavoritesManager
import com.urwah.dhikr.R

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

        val prefs = requireContext().getSharedPreferences("urwah_stats", Context.MODE_PRIVATE)
        val completedToday = prefs.getInt("completed_today", 0)

        root.findViewById<TextView>(R.id.tv_total_categories).text =
            "عدد التصنيفات: ${DhikrDataProvider.getCategoryCount()}"
        root.findViewById<TextView>(R.id.tv_total_dhikrs).text =
            "إجمالي الأذكار: ${DhikrDataProvider.getTotalDhikrCount()}"
        root.findViewById<TextView>(R.id.tv_total_favorites).text =
            "عدد المفضلة: ${FavoritesManager.getAllFavorites().size}"
        root.findViewById<TextView>(R.id.tv_total_completed).text =
            "تم إكمالها اليوم: $completedToday"
    }
}
