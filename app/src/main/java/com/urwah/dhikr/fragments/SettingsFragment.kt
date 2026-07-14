package com.urwah.dhikr.fragments

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.SharedPreferences
import com.urwah.dhikr.QuranDataLoader
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
import androidx.navigation.fragment.findNavController
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
        val quranPrefs = requireContext().getSharedPreferences("urwah_quran", Context.MODE_PRIVATE)

        binding.ivBack.setOnClickListener {
            findNavController().popBackStack()
        }

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

        setupQuranSettings(quranPrefs)
    }

    private fun setupQuranSettings(prefs: SharedPreferences) {
        val ayahDisplayMode = prefs.getBoolean("ayah_single_line", true)
        val qiraatMode = QuranDataLoader.getQiraat(requireContext())

        binding.tvAyahDisplayMode.text = if (ayahDisplayMode) "كل آية في سطر مستقل" else "عرض متواصل"
        binding.tvQiraatMode.text = if (qiraatMode == "hafs") "حفص عن عاصم" else "ورش عن نافع"

        binding.tvAyahDisplayMode.setOnClickListener {
            val current = prefs.getBoolean("ayah_single_line", true)
            val options = arrayOf("كل آية في سطر مستقل", "عرض متواصل")
            val checked = if (current) 0 else 1
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("طريقة عرض الآيات")
                .setSingleChoiceItems(options, checked) { dialog, which ->
                    val newValue = which == 0
                    prefs.edit().putBoolean("ayah_single_line", newValue).apply()
                    binding.tvAyahDisplayMode.text = options[which]
                    dialog.dismiss()
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }

        binding.tvQiraatMode.setOnClickListener {
            val current = QuranDataLoader.getQiraat(requireContext())
            val options = arrayOf("حفص عن عاصم", "ورش عن نافع")
            val checked = if (current == "hafs") 0 else 1
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("رواية المصحف")
                .setSingleChoiceItems(options, checked) { dialog, which ->
                    val newValue = if (which == 0) "hafs" else "warsh"
                    QuranDataLoader.setQiraat(requireContext(), newValue)
                    QuranDataLoader.invalidateCache()
                    binding.tvQiraatMode.text = options[which]
                    dialog.dismiss()
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }
    }

    private fun setupDarkMode(prefs: SharedPreferences) {
        val actualIsDark = when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        if (!prefs.contains(KEY_DARK_MODE)) {
            prefs.edit().putBoolean(KEY_DARK_MODE, actualIsDark).apply()
        }
        binding.switchDarkMode.isChecked = actualIsDark

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
            TimePickerDialog(requireContext(), R.style.TimePickerTheme, { _, h, m ->
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
