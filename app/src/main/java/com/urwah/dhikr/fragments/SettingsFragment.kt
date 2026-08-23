package com.urwah.dhikr.fragments

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import com.urwah.dhikr.QuranDataLoader
import com.urwah.dhikr.MurattalThemeManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TimePicker
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.urwah.dhikr.NotificationHelper
import com.urwah.dhikr.UrwahToast
import com.urwah.dhikr.R
import com.urwah.dhikr.ShamelaBook
import com.urwah.dhikr.ShamelaBookStorage
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

    private val themePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val theme = com.urwah.dhikr.MurattalThemeManager.current(requireContext())
        binding.tvMurattalTheme.text = theme.name
        applyThemeSwatches(theme)
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
        setupVibration(prefs)
        setupKeepScreenOn(prefs)

        setupReminder(
            switchView = binding.switchMorning,
            timeText = binding.tvMorningTime,
            type = NotificationHelper.TYPE_MORNING,
            defaultHour = 6, defaultMinute = 0,
            prefs = prefs
        ,
            rowView = binding.rowMorning
        )
        setupReminder(
            switchView = binding.switchEvening,
            timeText = binding.tvEveningTime,
            type = NotificationHelper.TYPE_EVENING,
            defaultHour = 17, defaultMinute = 0,
            prefs = prefs
        ,
            rowView = binding.rowEvening
        )
        setupReminder(
            switchView = binding.switchBedtime,
            timeText = binding.tvBedtimeTime,
            type = NotificationHelper.TYPE_BEDTIME,
            defaultHour = 22, defaultMinute = 0,
            prefs = prefs
        ,
            rowView = binding.rowBedtime
        )

        setupReminder(
            switchView = binding.switchKahf,
            timeText = binding.tvKahfTime,
            type = NotificationHelper.TYPE_KAHF,
            defaultHour = 6, defaultMinute = 0,
            prefs = prefs
        ,
            rowView = binding.rowKahf
        )
        setupReminder(
            switchView = binding.switchKahf,
            timeText = binding.tvKahfTime,
            type = NotificationHelper.TYPE_KAHF,
            defaultHour = 6, defaultMinute = 0,
            prefs = prefs
        ,
            rowView = binding.rowKahf
        )
        setupReminder(
            switchView = binding.switchKhatma,
            timeText = binding.tvKhatmaTime,
            type = NotificationHelper.TYPE_KHATMA,
            defaultHour = 8, defaultMinute = 0,
            prefs = prefs
        ,
            rowView = binding.rowKhatma
        )

        setupQuranSettings(quranPrefs)
        setupLibrarySection()
        setupAboutSection()
    }

    private fun setupLibrarySection() {
        binding.rowOpenLibrary.setOnClickListener {
            findNavController().navigate(R.id.nav_library)
        }
        binding.rowClearAllBooks.setOnClickListener {
            val ctx = requireContext()
            val downloaded = ShamelaBookStorage.getDownloadedBooks(ctx)
            if (downloaded.isEmpty()) {
                UrwahToast.show(ctx, "لا توجد كتب محملة")
                return@setOnClickListener
            }
            val totalSize = ShamelaBookStorage.getTotalStorageUsed(ctx)
            val sizeText = ShamelaBookStorage.formatFileSize(totalSize)
            val dialog = com.urwah.dhikr.BookCustomConfirmDialog(
                ctx,
                "حذف جميع الكتب",
                "سيتم حذف ${downloaded.size} كتاب ( $sizeText ). لا يمكن التراجع عن هذا الإجراء.",
                positiveText = "حذف الكل",
                negativeText = "إلغاء",
                onPositive = {
                    for (bookId in downloaded) {
                        ShamelaBookStorage.deleteBook(ctx, bookId)
                    }
                    UrwahToast.show(ctx, "تم حذف جميع الكتب")
                    updateStorageInfo()
                }
            )
            dialog.show()
        }
        updateStorageInfo()
    }

    private fun updateStorageInfo() {
        val ctx = requireContext()
        val downloadedIds = ShamelaBookStorage.getDownloadedBooks(ctx)
        val count = downloadedIds.size
        val size = ShamelaBookStorage.getTotalStorageUsed(ctx)
        binding.tvBooksCount.text = "$count كتاب"
        binding.tvStorageUsed.text = ShamelaBookStorage.formatFileSize(size)
    }

    private fun setupAboutSection() {
        binding.rowPrivacy.setOnClickListener {
            showPolicyDialog(requireContext(), "privacy")
        }
        binding.rowTerms.setOnClickListener {
            showPolicyDialog(requireContext(), "terms")
        }
        binding.rowArabicIdentity.setOnClickListener {
            com.urwah.dhikr.MainActivity.showArabicIdentityDialog(requireContext())
        }
        try {
            binding.tvVersionName.text = requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0).versionName
        } catch (_: Exception) {
            binding.tvVersionName.text = "1.0.0"
        }
    }

    private fun showPolicyDialog(context: Context, type: String) {
        com.urwah.dhikr.MainActivity.showPolicyDialog(context, type)
    }

    private fun setupQuranSettings(prefs: SharedPreferences) {
        val ayahDisplayMode = prefs.getBoolean("ayah_single_line", false)
        val qiraatMode = QuranDataLoader.getQiraat(requireContext())
        val alignment = prefs.getInt("quran_alignment", 3)

        binding.tvAyahDisplayMode.text = if (ayahDisplayMode) "كل آية في سطر مستقل" else "عرض متواصل"
        binding.tvQiraatMode.text = QuranDataLoader.getRiwayatInfo(qiraatMode).arabicName
        binding.tvAyahAlignment.text = alignmentLabel(alignment)

        binding.rowAyahDisplayMode.setOnClickListener {
            showViewModeDialog(prefs)
        }

        binding.rowQiraatMode.setOnClickListener {
            showRiwayaDialog()
        }

        binding.rowAyahAlignment.setOnClickListener {
            showAlignmentDialog(prefs)
        }

        val murattalTheme = MurattalThemeManager.current(requireContext())
        binding.tvMurattalTheme.text = murattalTheme.name
        binding.root.findViewById<View>(R.id.row_murattal_theme)
            .setOnClickListener { showMurattalThemeDialog() }
        applyThemeSwatches(murattalTheme)
    }

    private fun applyThemeSwatches(theme: com.urwah.dhikr.MurattalTheme) {
        val p = MurattalThemeManager.palette(requireContext(), theme)
        var drawable = binding.root.findViewById<View>(R.id.swatch_theme_bg).background
        drawable.mutate().setTint(p.background)
        drawable = binding.root.findViewById<View>(R.id.swatch_theme_surface).background
        drawable.mutate().setTint(p.surface)
        drawable = binding.root.findViewById<View>(R.id.swatch_theme_accent).background
        drawable.mutate().setTint(p.accent)
    }

    private fun showMurattalThemeDialog() {
        themePickerLauncher.launch(
            android.content.Intent(requireContext(), com.urwah.dhikr.MurattalThemePickerActivity::class.java)
        )
    }

    private fun alignmentLabel(alignment: Int): String = when (alignment) {
        0 -> "يمين"
        1 -> "وسط"
        2 -> "يسار"
        else -> "ضبط"
    }

    private fun showAlignmentDialog(prefs: SharedPreferences) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ayah_alignment, null)
        val current = prefs.getInt("quran_alignment", 3)

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val radios = mapOf(
            R.id.radioAlignRight to 0,
            R.id.radioAlignCenter to 1,
            R.id.radioAlignLeft to 2,
            R.id.radioAlignJustify to 3
        )
        val options = mapOf(
            R.id.optionAlignRight to 0,
            R.id.optionAlignCenter to 1,
            R.id.optionAlignLeft to 2,
            R.id.optionAlignJustify to 3
        )

        fun updateRadios(selected: Int) {
            radios.forEach { (radioId, value) ->
                val radio = view.findViewById<TextView>(radioId)
                radio.text = if (value == selected) "●" else "○"
            }
        }
        updateRadios(current)

        options.forEach { (optionId, value) ->
            view.findViewById<LinearLayout>(optionId).setOnClickListener {
                prefs.edit().putInt("quran_alignment", value).apply()
                binding.tvAyahAlignment.text = alignmentLabel(value)
                dialog.dismiss()
            }
        }

        view.findViewById<Button>(R.id.btnCancelAlignment).setOnClickListener { dialog.dismiss() }
        dialog.show()
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

        fun toggleDarkMode() {
            binding.switchDarkMode.isChecked = !binding.switchDarkMode.isChecked
        }
        binding.rowDarkMode.setOnClickListener { toggleDarkMode() }
        // الضغط على المفتاح نفسه يبقى مسموحاً ولا يتضاعف
        binding.switchDarkMode.setOnClickListener { it.isPressed = false; toggleDarkMode() }
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
    }

    private fun setupVibration(prefs: SharedPreferences) {
        val enabled = prefs.getBoolean(KEY_VIBRATION, true)
        binding.switchVibration.isChecked = enabled
        binding.rowVibration.setOnClickListener {
            binding.switchVibration.isChecked = !binding.switchVibration.isChecked
        }
        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_VIBRATION, isChecked).apply()
        }
    }

    private fun setupReminder(
        switchView: SwitchCompat, timeText: TextView, type: String,
        defaultHour: Int, defaultMinute: Int, prefs: SharedPreferences,
        rowView: View? = null
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
            showCustomTimePicker(prefs, timeText, switchView, type, hourKey, minKey, savedHour, savedMin)
        }

        // الصف بالكامل: الضغط في أي مكان يبدّل المفتاح (الوقت له مستمعه الخاص فيُستهلك الحدث هناك)
        rowView?.setOnClickListener {
            switchView.isChecked = !switchView.isChecked
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
        NotificationHelper.scheduleNext(requireContext(), data.type, data.hour, data.minute)
    }

    private fun showCustomTimePicker(
        prefs: SharedPreferences, timeText: TextView, switchView: SwitchCompat,
        type: String, hourKey: String, minKey: String,
        currentHour: Int, currentMin: Int
    ) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_time_picker, null)
        val picker = view.findViewById<TimePicker>(R.id.timePicker)
        picker.hour = currentHour
        picker.minute = currentMin
        picker.setIs24HourView(false)

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<Button>(R.id.btnConfirmTime).setOnClickListener {
            val h = picker.hour
            val m = picker.minute
            prefs.edit().putInt(hourKey, h).putInt(minKey, m).apply()
            timeText.text = formatTime(h, m)
            if (switchView.isChecked) {
                NotificationHelper.scheduleNext(requireContext(), type, h, m)
            }
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.btnCancelTime).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showViewModeDialog(prefs: SharedPreferences) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_view_mode, null)
        val current = prefs.getBoolean("ayah_single_line", false)

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val radioSingle = view.findViewById<TextView>(R.id.radioSingleLine)
        val radioContinuous = view.findViewById<TextView>(R.id.radioContinuous)
        val optionSingle = view.findViewById<LinearLayout>(R.id.optionSingleLine)
        val optionContinuous = view.findViewById<LinearLayout>(R.id.optionContinuous)

        fun updateRadio(isSingle: Boolean) {
            radioSingle.text = if (isSingle) "●" else "○"
            radioContinuous.text = if (isSingle) "○" else "●"
        }
        updateRadio(current)

        optionSingle.setOnClickListener {
            prefs.edit().putBoolean("ayah_single_line", true).apply()
            binding.tvAyahDisplayMode.text = "كل آية في سطر مستقل"
            dialog.dismiss()
        }
        optionContinuous.setOnClickListener {
            prefs.edit().putBoolean("ayah_single_line", false).apply()
            binding.tvAyahDisplayMode.text = "عرض متواصل"
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.btnCancelViewMode).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showRiwayaDialog() {
        val context = requireContext()
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_riwaya, null)
        val list = view.findViewById<LinearLayout>(R.id.riwayaList)
        val current = QuranDataLoader.getQiraat(context)

        val dialog = android.app.AlertDialog.Builder(context)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        fun dp(value: Int): Int = (resources.displayMetrics.density * value).toInt()

        fun buildRow(info: com.urwah.dhikr.RiwayatInfo): View {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                isClickable = info.available
                isFocusable = info.available
                background = resources.getDrawable(R.drawable.bg_dhikr_icon_action, null)
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }

            val radio = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
                background = resources.getDrawable(R.drawable.bg_ayah_number, null)
                gravity = android.view.Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
                textSize = 11f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                text = if (info.available && info.id == current) "●" else "○"
            }

            val nameColor = if (info.available) {
                ContextCompat.getColor(context, R.color.urwah_thread_dark)
            } else {
                ContextCompat.getColor(context, R.color.urwah_thread_light)
            }

            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(dp(12), 0, 0, 0)
            }
            texts.addView(TextView(context).apply {
                text = info.arabicName
                setTextColor(nameColor)
                textSize = 15f
                typeface = ResourcesCompat.getFont(context, R.font.alyamama)
            })
            texts.addView(TextView(context).apply {
                text = info.description
                setTextColor(ContextCompat.getColor(context, R.color.urwah_thread_light))
                textSize = 11f
                typeface = ResourcesCompat.getFont(context, R.font.alyamama)
            })

            row.addView(radio)
            row.addView(texts)

            if (info.available) {
                row.setOnClickListener {
                    QuranDataLoader.setQiraat(context, info.id)
                    QuranDataLoader.invalidateCache()
                    binding.tvQiraatMode.text = info.arabicName
                    dialog.dismiss()
                }
            } else {
                row.setOnClickListener {
                    UrwahToast.show(
                        context,
                        "رواية ${info.arabicName} ستتوفر قريباً في التحديث القادم"
                    )
                }
            }
            return row
        }

        QuranDataLoader.qiraaGroups.forEachIndexed { gi, (qiraaName, items) ->
            if (gi > 0) {
                val divider = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                    )
                    setBackgroundColor(ContextCompat.getColor(context, R.color.urwah_shadow_faint))
                }
                list.addView(divider)
            }

            list.addView(TextView(context).apply {
                text = qiraaName
                setTextColor(ContextCompat.getColor(context, R.color.urwah_thread_brown))
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, if (gi == 0) 0 else dp(14), 0, dp(6))
            })

            items.forEach { info ->
                list.addView(buildRow(info))
                if (items.last() != info) {
                    list.addView(View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                        )
                        setBackgroundColor(ContextCompat.getColor(context, R.color.urwah_shadow_faint))
                    })
                }
            }
        }

        view.findViewById<Button>(R.id.btnCancelRiwaya).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val ampm = if (hour < 12) "ص" else "م"
        val h = if (hour % 12 == 0) 12 else hour % 12
        return String.format("%02d:%02d %s", h, minute, ampm)
    }

    private fun setupKeepScreenOn(prefs: SharedPreferences) {
        val enabled = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false)
        binding.switchKeepScreenOn.isChecked = enabled
        applyKeepScreenOn(enabled)
        binding.rowKeepScreenOn.setOnClickListener {
            binding.switchKeepScreenOn.isChecked = !binding.switchKeepScreenOn.isChecked
        }
        binding.switchKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, isChecked).apply()
            applyKeepScreenOn(isChecked)
        }
    }

    private fun applyKeepScreenOn(enabled: Boolean) {
        requireActivity().window?.let { window ->
            if (enabled) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_DARK_MODE = "dark_mode_enabled"
        private const val KEY_VIBRATION = "vibration_enabled"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    }
}
