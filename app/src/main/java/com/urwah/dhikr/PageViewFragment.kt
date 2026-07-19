package com.urwah.dhikr

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment

class PageViewFragment : Fragment() {

    private var linesContainer: LinearLayout? = null
    private var tvPageNumber: TextView? = null
    private var typeface: Typeface? = null
    private var ayahColor = 0
    private var surahHeaderColor = 0
    private var isDark = false
    private var pageNumber = 1
    private var riwaya = "hafs"

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

        try {
            if (riwaya == "warsh") {
                renderAsPlainText()
            } else {
                renderLineByLine()
            }
        } catch (_: OutOfMemoryError) {
            QuranLineData.clearCache()
            renderAsPlainText()
        } catch (_: Exception) {
            renderAsPlainText()
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
            if (Build.VERSION.SDK_INT >= 26) {
                justificationMode = 1
            }
            textSize = 29f
        }
    }

    private fun renderLineByLine() {
        val lines = try {
            QuranLineData.getPageLines(pageNumber, requireContext())
        } catch (_: Exception) {
            emptyList()
        }
        if (lines.isEmpty()) {
            renderAsPlainText()
            return
        }

        val container = linesContainer ?: return
        val lineCount = lines.size

        // Measure line height ratio for this font at 1sp (in pixels, then convert)
        val tf = typeface
        val paint = Paint().apply {
            tf?.let { this.typeface = it }
            textSize = resources.displayMetrics.density  // 1sp in pixels
        }
        val fm = paint.fontMetrics
        val lineHeightAt1sp = fm.descent - fm.ascent + fm.leading
        val fontMetricsRatio = lineHeightAt1sp / resources.displayMetrics.density

        // Container will take weight=1 so we need to measure after layout
        // estimate using available screen height
        val screenHeight = resources.displayMetrics.heightPixels
        val estimatedAvailableHeight = screenHeight * 0.55f // rough estimate
        val maxFontSize = 72f
        val minFontSize = 12f

        var fontSize = (estimatedAvailableHeight / (lineCount * fontMetricsRatio))
            .coerceIn(minFontSize, maxFontSize)

        for (i in 0 until lineCount) {
            val ln = lines[i]
            val nextIsHeader = if (i + 1 < lineCount) lines[i + 1].type == "sh" else false
            val isShortLine = ln.type == "txt" && (ln.text?.length ?: 0) < 20

            val tv = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                typeface = tf
                setTextColor(ayahColor)
                textDirection = View.TEXT_DIRECTION_RTL
                includeFontPadding = false
                textSize = fontSize

                when (ln.type) {
                    "sh" -> {
                        text = ln.surah?.let { surahNameFromCode(it) } ?: ln.text ?: ""
                        gravity = Gravity.CENTER
                        setTextColor(surahHeaderColor)
                    }
                    "b" -> {
                        text = "\uFDFD"
                        gravity = Gravity.CENTER
                    }
                    "txt" -> {
                        text = ln.text ?: ""
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        if (nextIsHeader || isShortLine) {
                            gravity = Gravity.CENTER
                        }
                        if (Build.VERSION.SDK_INT >= 26) {
                            justificationMode = 1
                        }
                    }
                }
            }
            container.addView(tv)
        }

        // Refine font size after layout
        container.post {
            if (container.height <= 0) return@post
            val availableHeight = container.height
            var totalLineHeight = 0f
            val children = mutableListOf<TextView>()
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i) as? TextView ?: continue
                children.add(child)
                totalLineHeight += child.lineHeight.toFloat()
            }
            if (children.isEmpty()) return@post
            if (totalLineHeight <= 0f) return@post

            val targetHeight = availableHeight * 0.88f
            val scale = (targetHeight / totalLineHeight).coerceIn(0.4f, 2.5f)
            val currentSize = children[0].textSize / resources.displayMetrics.density
            val newSize = (currentSize * scale).coerceIn(minFontSize, maxFontSize)
            for (tv in children) {
                tv.textSize = newSize
            }
        }
    }

    private fun surahNameFromCode(code: String): String {
        val names = arrayOf(
            "", "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام",
            "الأعراف", "الأنفال", "التوبة", "يونس", "هود", "يوسف", "الرعد",
            "إبراهيم", "الحجر", "النحل", "الإسراء", "الكهف", "مريم", "طه",
            "الأنبياء", "الحج", "المؤمنون", "النور", "الفرقان", "الشعراء",
            "النمل", "القصص", "العنكبوت", "الروم", "لقمان", "السجدة",
            "الأحزاب", "سبأ", "فاطر", "يس", "الصافات", "ص", "الزمر",
            "غافر", "فصلت", "الشورى", "الزخرف", "الدخان", "الجاثية",
            "الأحقاف", "محمد", "الفتح", "الحجرات", "ق", "الذاريات",
            "الطور", "النجم", "القمر", "الرحمن", "الواقعة", "الحديد",
            "المجادلة", "الحشر", "الممتحنة", "الصف", "الجمعة", "المنافقون",
            "التغابن", "الطلاق", "التحريم", "الملك", "القلم", "الحاقة",
            "المعارج", "نوح", "الجن", "المزمل", "المدثر", "القيامة",
            "الإنسان", "المرسلات", "النبأ", "النازعات", "عبس", "التكوير",
            "الانفطار", "المطففين", "الانشقاق", "البروج", "الطارق",
            "الأعلى", "الغاشية", "الفجر", "البلد", "الشمس", "الليل",
            "الضحى", "الشرح", "التين", "العلق", "القدر", "البينة",
            "الزلزلة", "العاديات", "القارعة", "التكاثر", "العصر",
            "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر", "الكافرون",
            "النصر", "المسد", "الإخلاص", "الفلق", "الناس"
        )
        val idx = code.toIntOrNull() ?: return "سورة"
        return if (idx in names.indices) "سورة ${names[idx]}" else "سورة"
    }

    companion object {
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
}
