package com.urwah.dhikr

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.urwah.dhikr.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Urwah)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NotificationHelper.createChannel(this)

        val navView: BottomNavigationView = binding.bottomNav
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        navView.setupWithNavController(navController)
    }

    override fun onResume() {
        super.onResume()
        rescheduleReminders()
    }

    private fun rescheduleReminders() {
        val prefs = getSharedPreferences("urwah_settings", Context.MODE_PRIVATE)
        val types = listOf(
            NotificationHelper.TYPE_MORNING,
            NotificationHelper.TYPE_EVENING,
            NotificationHelper.TYPE_BEDTIME
        )
        for (type in types) {
            val enabled = prefs.getBoolean("${type}_enabled", false)
            if (enabled) {
                val h = prefs.getInt("${type}_hour", 6)
                val m = prefs.getInt("${type}_min", 0)
                NotificationHelper.scheduleReminder(this, type, h, m)
            }
        }
    }
}
