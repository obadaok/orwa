package com.urwah.dhikr.fragments

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.urwah.dhikr.NotificationHelper
import com.urwah.dhikr.R
import com.urwah.dhikr.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private var pendingReminder: ReminderData? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingReminder?.let { enableReminder(it) }
        }
        pendingReminder = null
    }

    private data class ReminderData(
        val switchView: SwitchCompat,
        val timeText: TextView,
        val type: String,
        val hour: Int,
        val minute: Int,
        val prefs: SharedPreferences
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("urwah_settings", Context.MODE_PRIVATE)

        setupDarkMode(prefs)

        setupReminder(
            switchView = binding.switchMorning,
            timeText = binding.tvMorningTime,
            type = NotificationHelper.TYPE_MORNING,
            defaultHour = 6, defaultMinute = 0,
            prefs = prefs
        )
        setupReminder(
            switchView = binding.switchEvening,
            timeText = binding.tvEveningTime,
            type = NotificationHelper.TYPE_EVENING,
            defaultHour = 17, defaultMinute = 0,
            prefs = prefs
        )
        setupReminder(
            switchView = binding.switchBedtime,
            timeText = binding.tvBedtimeTime,
            type = NotificationHelper.TYPE_BEDTIME,
            defaultHour = 22, defaultMinute = 0,
            prefs = prefs
        )
    }

    private fun setupDarkMode(prefs: SharedPreferences) {
        val isDark = prefs.getBoolean(KEY_DARK_MODE, false)
        binding.switchDarkMode.isChecked = isDark

        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
    }

    private fun setupReminder(
        switchView: SwitchCompat, timeText: TextView, type: String,
        defaultHour: Int, defaultMinute: Int, prefs: SharedPreferences
    ) {
        val hourKey = "${type}_hour"
        val minKey = "${type}_min"
        val enabledKey = "${type}_enabled"

        val savedHour = prefs.getInt(hourKey, defaultHour)
        val savedMin = prefs.getInt(minKey, defaultMinute)
        val isEnabled = prefs.getBoolean(enabledKey, false)

        timeText.text = formatTime(savedHour, savedMin)
        switchView.isChecked = isEnabled

        timeText.setOnClickListener {
            TimePickerDialog(requireContext(), { _, h, m ->
                prefs.edit().putInt(hourKey, h).putInt(minKey, m).apply()
                timeText.text = formatTime(h, m)
                if (switchView.isChecked) {
                    NotificationHelper.scheduleReminder(requireContext(), type, h, m)
                }
            }, savedHour, savedMin, false).show()
        }

        switchView.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(enabledKey, isChecked).apply()
            if (isChecked) {
                val h = prefs.getInt(hourKey, defaultHour)
                val m = prefs.getInt(minKey, defaultMinute)
                if (requestNotificationPermissionIfNeeded(
                        switchView, timeText, type, h, m, prefs
                    )
                ) {
                    enableReminder(ReminderData(switchView, timeText, type, h, m, prefs))
                }
            } else {
                NotificationHelper.cancelReminder(requireContext(), type)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded(
        switchView: SwitchCompat, timeText: TextView, type: String,
        hour: Int, minute: Int, prefs: SharedPreferences
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                pendingReminder = ReminderData(switchView, timeText, type, hour, minute, prefs)
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return false
            }
        }
        return true
    }

    private fun enableReminder(data: ReminderData) {
        NotificationHelper.createChannel(requireContext())
        NotificationHelper.scheduleReminder(requireContext(), data.type, data.hour, data.minute)
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val ampm = if (hour < 12) "ص" else "م"
        val h = if (hour % 12 == 0) 12 else hour % 12
        return String.format("%02d:%02d %s", h, minute, ampm)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_DARK_MODE = "dark_mode_enabled"
    }
}
