package com.urwah.dhikr.calendar

import android.animation.AnimatorInflater
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import com.urwah.dhikr.R
import com.urwah.dhikr.databinding.FragmentOrwaCalendarBinding

class OrwaCalendarFragment : Fragment() {

    private var _binding: FragmentOrwaCalendarBinding? = null
    private val binding get() = _binding!!
    private var currentState: OrwaCalendarUiState? = null

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
        applyCardPressMotion()
        runEntranceMotion()
    }

    override fun onResume() {
        super.onResume()
        currentState = OrwaCalendarData.buildToday(requireContext())
        bindData(currentState!!)
    }

    private fun bindData(state: OrwaCalendarUiState) = with(binding) {
        tvDayName.text = state.dayName
        tvHijriDate.text = state.hijriDate
        tvGregorianDate.text = state.gregorianDate

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
        // Ayah read more
        postCheckTruncated(tvAyahText) { btnAyahMore.visibility = it }
        btnAyahMore.setOnClickListener {
            openDetail(
                title = getString(R.string.oc_ayah_title),
                subtitle = getString(R.string.oc_surah_ref_format, state.surahName, state.ayahNumber),
                content = state.ayahText
            )
        }

        // Tafsir read more
        postCheckTruncated(tvTafsirText) { btnTafsirMore.visibility = it }
        btnTafsirMore.setOnClickListener {
            openDetail(
                title = getString(R.string.oc_tafsir_title),
                subtitle = getString(R.string.oc_surah_ref_format, state.surahName, state.ayahNumber),
                content = state.tafsirText
            )
        }

        // Hadith read more
        postCheckTruncated(tvHadithText) { btnHadithMore.visibility = it }
        btnHadithMore.setOnClickListener {
            openDetail(
                title = getString(R.string.oc_hadith_title),
                subtitle = "${state.hadithNarrator} · ${state.hadithSource}",
                content = state.hadithText
            )
        }

        // Scholar quote read more
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
