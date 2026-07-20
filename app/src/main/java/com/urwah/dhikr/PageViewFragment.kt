package com.urwah.dhikr

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import kotlin.math.min

class PageViewFragment : Fragment() {

    private var linesContainer: LinearLayout? = null
    private var tvPageNumber: TextView? = null
    private var typeface: Typeface? = null
    private var ayahColor = 0
    private var surahHeaderColor = 0
    private var isDark = false
    private var pageNumber = 1
    private var riwaya = "hafs"

    companion object {
        private const val LINE_HEIGHT_MULT = 1.9f
        private const val CENTERED_GAP_FRACTION = 0.22f
        private const val MIN_INTER_WORD_GAP_FRACTION = 0.10f
        private const val FONT_SCALE_MIN = 0.85f
        private const val FONT_SCALE_MAX = 1.5f
        private const val WIDTH_DP_MIN = 260f
        private const val WIDTH_DP_MAX = 600f
        private const val FILL_THRESHOLD = 0.82f
        private const val ARG_PAGE_NUMBER = "page_number"
        private const val ARG_RIWAYA = "riwaya"

        fun newInstance(pageNumber: Int, riwaya: String): PageViewFragment {
            val args = Bundle().apply {
                putInt(ARG_PAGE_NUMBER, pageNumber)
                putString(ARG_RIWAYA, riwaya)
            }
            val fragment = PageViewFragment()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.item_quran_page, container, false)
        linesContainer = view.findViewById(R.id.linesContainer)
        tvPageNumber = view.findViewById(R.id.tvPageNumber)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pageNumber = arguments?.getInt(ARG_PAGE_NUMBER, 1) ?: 1
        riwaya = arguments?.getString(ARG_RIWAYA, "hafs") ?: "hafs"

        isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        ayahColor = if (isDark) Color.parseColor("#e8e0d6") else Color.parseColor("#5E4B40")
        surahHeaderColor = if (isDark) Color.parseColor("#D4B8A0") else Color.parseColor("#7A5F4E")
        val context = requireContext()
        val fontRes = if (riwaya == "warsh") R.font.uthmanic_warsh else R.font.uthmanic_hafs
        typeface = try { ResourcesCompat.getFont(context, fontRes) } catch (_: Exception) { null }

        val hindiDigits = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
        val pageNumStr = pageNumber.toString().map { hindiDigits[it - '0'] }.joinToString("")
        tvPageNumber?.text = "الصفحة $pageNumStr"
        tvPageNumber?.setTextColor(if (isDark) Color.parseColor("#C4AFA3") else Color.parseColor("#8B6F5E"))

        val container = linesContainer
        if (container != null) {
            container.post { buildPage(container) }
        }
    }

    private fun buildPage(container: LinearLayout) {
        val lines = try {
            QuranLineData.getPageLines(pageNumber, requireContext())
        } catch (_: Exception) { emptyList() }
        if (lines.isEmpty()) {
            renderAsPlainText()
            return
        }

        val density = resources.displayMetrics.density
        val availWidthPx = container.width.toFloat() - (16 * density)
        val availHeightPx = container.height.toFloat()
        val numAyahLines = lines.count { it.type == "ayah" }

        if (availWidthPx <= 0 || availHeightPx <= 0) {
            renderAsPlainText()
            return
        }

        // --- Calculate base font size from screen width ---
        val availWidthDp = availWidthPx / density
        val screenScale = FONT_SCALE_MIN + (availWidthDp - WIDTH_DP_MIN) /
                (WIDTH_DP_MAX - WIDTH_DP_MIN) * (FONT_SCALE_MAX - FONT_SCALE_MIN)
        val clampedScreenScale = screenScale.coerceIn(FONT_SCALE_MIN, FONT_SCALE_MAX)

        val testSizeSp = 24f
        val paint = Paint().apply {
            typeface = this@PageViewFragment.typeface
            textSize = testSizeSp * density
        }

        // --- QuranApp-style page-wide scale ---
        val baseGapPx = paint.textSize * CENTERED_GAP_FRACTION
        val minGapPx = paint.textSize * MIN_INTER_WORD_GAP_FRACTION

        val wideLineRatios = mutableListOf<Float>()
        for (line in lines) {
            if (line.type != "ayah" || line.words == null || line.words.isEmpty()) continue
            var measuredW = 0f
            for (w in line.words) {
                measuredW += paint.measureText(w.text)
            }
            if (line.words.size > 1) {
                val gap = if (line.centered) maxOf(baseGapPx, minGapPx) else minGapPx
                measuredW += gap * (line.words.size - 1)
            }
            val fillRatio = measuredW / availWidthPx
            if (!line.centered && fillRatio >= FILL_THRESHOLD) {
                wideLineRatios.add((availWidthPx / measuredW).coerceAtLeast(0f))
            }
        }

        val medianRatio = if (wideLineRatios.isNotEmpty()) {
            val sorted = wideLineRatios.sorted()
            sorted[sorted.size / 2]
        } else {
            1f
        }
        val pageScale = medianRatio.coerceIn(FONT_SCALE_MIN, min(clampedScreenScale, FONT_SCALE_MAX))
        val baseFontSizeSp = testSizeSp * pageScale

        // --- Height constraint ---
        val lineHeightPx = baseFontSizeSp * density * LINE_HEIGHT_MULT
        val totalContentPx = lineHeightPx * numAyahLines
        val heightScale = if (totalContentPx > availHeightPx) {
            // Need to shrink
            (availHeightPx / totalContentPx).coerceIn(0.5f, 1f)
        } else if (numAyahLines > 1) {
            // Can expand to fill
            (availHeightPx / totalContentPx).coerceIn(1f, 1.5f)
        } else {
            1f
        }

        val finalFontSizeSp = baseFontSizeSp * heightScale

        // --- Recalculate at final size ---
        paint.textSize = finalFontSizeSp * density
        val finalGapPx = maxOf(
            paint.textSize * CENTERED_GAP_FRACTION,
            paint.textSize * MIN_INTER_WORD_GAP_FRACTION
        )

        val finalLineHeightPx = paint.textSize * LINE_HEIGHT_MULT
        val totalFinalPx = finalLineHeightPx * numAyahLines
        val extraGapPx = if (numAyahLines > 1) {
            (availHeightPx - totalFinalPx).coerceAtLeast(0f) / (numAyahLines - 1)
        } else 0f

        // --- Build lines ---
        val containerWidth = container.width

        for (line in lines) {
            when (line.type) {
                "surah_name" -> {
                    val tv = TextView(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (finalLineHeightPx + extraGapPx).toInt()
                        )
                        text = line.text ?: ""
                        typeface = this@PageViewFragment.typeface
                        setTextColor(surahHeaderColor)
                        gravity = Gravity.CENTER
                        textSize = finalFontSizeSp * 1.1f
                        includeFontPadding = false
                        textDirection = View.TEXT_DIRECTION_RTL
                    }
                    container.addView(tv)
                }
                "basmallah" -> {
                    val tv = TextView(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (finalLineHeightPx + extraGapPx).toInt()
                        )
                        text = "\uFDFD"
                        typeface = this@PageViewFragment.typeface
                        setTextColor(ayahColor)
                        gravity = Gravity.CENTER
                        textSize = finalFontSizeSp * 1.1f
                        includeFontPadding = false
                        textDirection = View.TEXT_DIRECTION_RTL
                    }
                    container.addView(tv)
                }
                "ayah" -> {
                    if (line.words == null || line.words.isEmpty()) continue
                    val wordTexts = line.words.map { it.text }
                    val wordWidthsPx = wordTexts.map { paint.measureText(it) }
                    val totalWPx = wordWidthsPx.sumOf { it.toDouble() }.toFloat()
                    val gapCount = wordTexts.size - 1

                    // Center the line if it fits; else distribute remaining space as gaps
                    val remainingPx = availWidthPx - totalWPx
                    val isShort = remainingPx > finalGapPx * gapCount * 2f
                    val wordGapPx = if (gapCount > 0 && !isShort) {
                        remainingPx.coerceAtLeast(0f) / gapCount
                    } else {
                        finalGapPx
                    }

                    val lineLayout = LinearLayout(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (finalLineHeightPx + extraGapPx).toInt()
                        )
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        layoutDirection = View.LAYOUT_DIRECTION_RTL
                    }

                    for ((i, wText) in wordTexts.withIndex()) {
                        val isVerseNum = wText.all { it in '\u0660'..'\u0669' }
                        val tv = TextView(requireContext()).apply {
                            val lp = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                            if (i > 0 && !isShort) {
                                lp.marginEnd = wordGapPx.toInt()
                            } else if (i > 0 && isShort) {
                                lp.marginEnd = finalGapPx.toInt()
                            }
                            layoutParams = lp
                            text = wText
                            typeface = this@PageViewFragment.typeface
                            setTextColor(ayahColor)
                            textSize = finalFontSizeSp * (if (isVerseNum) 0.7f else 1f)
                            includeFontPadding = false
                            textDirection = View.TEXT_DIRECTION_RTL
                            gravity = Gravity.CENTER
                        }
                        lineLayout.addView(tv)
                    }

                    if (isShort) {
                        lineLayout.gravity = Gravity.CENTER_HORIZONTAL
                    }
                    container.addView(lineLayout)
                }
            }
        }
    }

    private fun renderAsPlainText() {
        val ayahText = QuranPageData.getPageAyahText(riwaya, pageNumber, requireContext())
        val tv = linesContainer?.let { container ->
            val textView = TextView(requireContext())
            textView.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            container.addView(textView)
            textView
        }
        tv?.apply {
            text = ayahText
            typeface = this@PageViewFragment.typeface
            setTextColor(ayahColor)
            textSize = 29f
            textDirection = View.TEXT_DIRECTION_RTL
            gravity = Gravity.CENTER
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        linesContainer = null
        tvPageNumber = null
    }
}
