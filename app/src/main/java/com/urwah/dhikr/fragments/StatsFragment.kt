package com.urwah.dhikr.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.urwah.dhikr.FavoritesManager
import com.urwah.dhikr.R
import java.util.Date
import java.util.Locale

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
        val totalCompleted = prefs.getInt("total_completed", 0)
        val completedToday = prefs.getInt("completed_today", 0)
        val favoriteCount = FavoritesManager.getAllFavorites().size

        root.findViewById<ImageView>(R.id.iv_settings_stats).setOnClickListener {
            findNavController().navigate(R.id.nav_settings)
        }
        root.findViewById<TextView>(R.id.tv_total_completed).text =
            "إجمالي الإكمالات: $totalCompleted"
        root.findViewById<TextView>(R.id.tv_completed_today).text =
            "إكمالات اليوم: $completedToday"
        root.findViewById<TextView>(R.id.tv_total_favorites).text =
            "المفضلة: $favoriteCount"
    }
}
