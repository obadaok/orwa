package com.urwah.dhikr.calendar

import android.animation.AnimatorInflater
import android.content.Intent
import androidx.core.content.res.ResourcesCompat
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.urwah.dhikr.R
import com.urwah.dhikr.databinding.FragmentOrwaCalendarBinding
import java.util.Calendar
import java.util.GregorianCalendar

class OrwaCalendarFragment : Fragment() {

    private var _binding: FragmentOrwaCalendarBinding? = null
    private val binding get() = _binding!!
    private var currentState: OrwaCalendarUiState? = null

    private var hijriYear = 0
    private var hijriMonth = 0
    private var selectedGd = 0
    private var selectedGm = 0
    private var selectedGy = 0
    private var todayGd = 0
    private var todayGm = 0
    private var todayGy = 0

    private val weekdayNames = arrayOf("أحد", "إثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrwaCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buildWeekdayHeader()
        binding.btnPrevMonth.setOnClickListener { changeMonth(-1) }
        binding.btnNextMonth.setOnClickListener { changeMonth(1) }
        binding.tvTodayChip.setOnClickListener { goToToday() }
        applyCardPressMotion()
        runEntranceMotion()
    }

    override fun onResume() {
        super.onResume()
        val today = GregorianCalendar()
        todayGd = today.get(Calendar.DAY_OF_MONTH)
        todayGm = today.get(Calendar.MONTH) + 1
        todayGy = today.get(Calendar.YEAR)
        val (y, m) = OrwaCalendarData.todayHijriMonth()
        hijriYear = y
        hijriMonth = m
        selectDay(todayGd, todayGm, todayGy)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ──────────────────────────────────────────────
    //  الشبكة
    // ──────────────────────────────────────────────

    private fun buildWeekdayHeader() = with(binding.weekdayRow) {
        removeAllViews()
        weekdayNames.forEachIndexed { index, name ->
            val tv = TextView(context).apply {
                text = name
                textSize = 10f
                typeface = ResourcesCompat.getFont(context, R.font.alyamama_semibold)
                gravity = Gravity.CENTER
                setTextColor(
                    context.getColor(
                        if (index == 5 || index == 6) R.color.oc_primary else R.color.oc_text_secondary
                    )
                )
            }
            tv.layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f)
            addView(tv)
        }
    }

    private fun renderAll() {
        val grid = OrwaCalendarData.buildMonthGrid(hijriYear, hijriMonth)
        binding.tvHijriMonthHeader.text = "${grid.hijriMonthName} ${OrwaCalendarData.toArabicNum(hijriYear)} هـ"
        binding.tvGregorianMonthHeader.text = grid.gregorianLabel
        renderGrid(grid)

        val state = OrwaCalendarData.buildContentFor(requireContext(), selectedGd, selectedGm, selectedGy)
        currentState = state
        bindData(state)
    }

    private fun renderGrid(grid: OrwaMonthGrid) = with(binding.dayGrid) {
        removeAllViews()
        val total = grid.leadingBlanks + grid.cells.size
        var index = 0
        while (index < total) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            repeat(7) { col ->
                val gridIndex = index + col
                if (gridIndex < grid.leadingBlanks || gridIndex >= grid.leadingBlanks + grid.cells.size) {
                    val blank = TextView(context)
                    blank.layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
                    row.addView(blank)
                } else {
                    row.addView(buildDayCell(grid.cells[gridIndex - grid.leadingBlanks]))
                }
            }
            addView(row)
            index += 7
        }
    }

    private fun buildDayCell(cell: HijriDayCell): TextView {
        val isToday = cell.gregDay == todayGd && cell.gregMonth == todayGm && cell.gregYear == todayGy
        val isSelected = cell.gregDay == selectedGd && cell.gregMonth == selectedGm && cell.gregYear == selectedGy
        val tv = TextView(requireContext()).apply {
            text = OrwaCalendarData.toArabicNum(cell.hijriDay)
            textSize = 13f
            typeface = ResourcesCompat.getFont(context, R.font.alyamama_medium)
            gravity = Gravity.CENTER
            when {
                isToday -> {
                    setBackgroundResource(R.drawable.bg_day_today)
                    setTextColor(Color.WHITE)
                }
                isSelected -> {
                    setBackgroundResource(R.drawable.bg_day_selected)
                    setTextColor(context.getColor(R.color.oc_primary_deep))
                }
                else -> {
                    setBackgroundResource(R.drawable.bg_day_cell)
                    setTextColor(
                        context.getColor(
                            if (cell.isFriday || cell.isSaturday) R.color.oc_primary else R.color.oc_text_primary
                        )
                    )
                }
            }
        }
        tv.layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
        tv.setOnClickListener { selectDay(cell.gregDay, cell.gregMonth, cell.gregYear) }
        return tv
    }

    // ──────────────────────────────────────────────
    //  التنقل والاختيار
    // ──────────────────────────────────────────────

    private fun selectDay(gd: Int, gm: Int, gy: Int) {
        selectedGd = gd
        selectedGm = gm
        selectedGy = gy
        renderAll()
    }

    private fun changeMonth(delta: Int) {
        var y = hijriYear
        var m = hijriMonth + delta
        if (m < 1) {
            m = 12
            y--
        } else if (m > 12) {
            m = 1
            y++
        }
        hijriYear = y
        hijriMonth = m

        val hijriDay = OrwaCalendarData.hijriDayFor(selectedGd, selectedGm, selectedGy)
            .coerceAtMost(OrwaCalendarData.hijriMonthLength(y, m))
        val (gy, gm, gd) = OrwaCalendarData.hijriToGregorian(y, m, hijriDay)
        selectDay(gd, gm, gy)
    }

    private fun goToToday() {
        val (y, m) = OrwaCalendarData.todayHijriMonth()
        hijriYear = y
        hijriMonth = m
        selectDay(todayGd, todayGm, todayGy)
    }

    // ──────────────────────────────────────────────
    //  عرض المحتوى
    // ──────────────────────────────────────────────

    private fun bindData(state: OrwaCalendarUiState) = with(binding) {
        tvSelectedDate.text = "${state.dayName} · ${state.hijriDate} — ${state.gregorianDate}"

        val ramadanText = if (state.daysUntilRamadan == 0) {
            getString(R.string.oc_ramadan_today)
        } else {
            getString(R.string.oc_ramadan_countdown_format, state.daysUntilRamadan)
        }
        tvRamadanCountdown.text = ramadanText

        tvAsmaHusnaName.text = state.asmaHusnaName
        tvAsmaHusnaExplanation.text = state.asmaHusnaExplanation

        tvAyahText.text = state.ayahText
        tvSurahRef.text = getString(
            R.string.oc_surah_ref_format,
            state.surahName,
            state.ayahNumber
        )

        tvTafsirText.text = state.tafsirText

        tvHadithText.text = state.hadithText
        tvHadithNarrator.text = state.hadithNarrator
        tvHadithSource.text = state.hadithSource

        tvBenefitText.text = state.benefitOfTheDay
        tvScholarName.text = state.scholarName

        setupReadMoreButtons(state)
    }

    private fun setupReadMoreButtons(state: OrwaCalendarUiState) = with(binding) {
        postCheckTruncated(tvAyahText) { btnAyahMore.visibility = it }
        btnAyahMore.setOnClickListener {
            openDetail(
                title = getString(R.string.oc_ayah_title),
                subtitle = getString(R.string.oc_surah_ref_format, state.surahName, state.ayahNumber),
                content = state.ayahText
            )
        }

        postCheckTruncated(tvTafsirText) { btnTafsirMore.visibility = it }
        btnTafsirMore.setOnClickListener {
            openDetail(
                title = getString(R.string.oc_tafsir_title),
                subtitle = getString(R.string.oc_surah_ref_format, state.surahName, state.ayahNumber),
                content = state.tafsirText
            )
        }

        postCheckTruncated(tvHadithText) { btnHadithMore.visibility = it }
        btnHadithMore.setOnClickListener {
            openDetail(
                title = getString(R.string.oc_hadith_title),
                subtitle = "${state.hadithNarrator} · ${state.hadithSource}",
                content = state.hadithText
            )
        }

        postCheckTruncated(tvBenefitText) { btnBenefitMore.visibility = it }
        btnBenefitMore.setOnClickListener {
            openDetail(
                title = getString(R.string.oc_scholars_title),
                subtitle = state.scholarName,
                content = state.benefitOfTheDay
            )
        }
    }

    private fun postCheckTruncated(textView: TextView, onResult: (Int) -> Unit) {
        textView.post {
            val layout = textView.layout ?: return@post
            val isTruncated = layout.lineCount > 0 &&
                    (layout.getLineWidth(layout.lineCount - 1) > textView.width ||
                            textView.layout?.getEllipsisCount(layout.lineCount - 1) ?: 0 > 0)
            onResult(if (isTruncated) View.VISIBLE else View.GONE)
        }
    }

    private fun openDetail(title: String, subtitle: String, content: String) {
        val intent = Intent(requireContext(), CalendarDetailActivity::class.java).apply {
            putExtra(CalendarDetailActivity.EXTRA_TITLE, title)
            putExtra(CalendarDetailActivity.EXTRA_SUBTITLE, subtitle)
            putExtra(CalendarDetailActivity.EXTRA_CONTENT, content)
        }
        startActivity(intent)
    }

    private fun applyCardPressMotion() {
        val pressAnimator = AnimatorInflater.loadStateListAnimator(
            requireContext(),
            R.animator.oc_card_press_anim
        )
        listOf(
            binding.cardAsmaHusna,
            binding.cardAyah,
            binding.cardTafsir,
            binding.cardHadith,
            binding.cardBenefit
        ).forEach { card ->
            card.stateListAnimator = pressAnimator
        }
    }

    private fun runEntranceMotion() {
        val sections = listOf(
            binding.ocHero,
            binding.cardAsmaHusna,
            binding.cardAyah,
            binding.cardTafsir,
            binding.cardHadith,
            binding.cardBenefit
        )

        sections.forEachIndexed { index, section ->
            section.alpha = 0f
            section.translationY = 20f
            section.postDelayed({
                val animator = AnimatorInflater.loadAnimator(
                    requireContext(),
                    R.animator.oc_fade_slide_in
                )
                animator.setTarget(section)
                animator.start()
            }, index * 60L)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
