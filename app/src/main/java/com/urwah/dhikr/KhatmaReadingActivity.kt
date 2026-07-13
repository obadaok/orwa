package com.urwah.dhikr

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.NestedScrollView
import kotlin.math.abs

class KhatmaReadingActivity : AppCompatActivity() {

    private lateinit var scrollView: NestedScrollView
    private lateinit var containerAyahs: LinearLayout
    private lateinit var tvKhatmaTitle: TextView
    private lateinit var tvProgress: TextView
    private lateinit var readingProgress: ProgressBar
    private var allQuran = mapOf<Int, QuranSurah>()
    private var khatmaId = ""
    private var currentDay = 0
    private var totalDays = 0
    private var startJuz = 1
    private var savedSurah = -1
    private var savedAyah = -1
    private var isDayCompleted = false
    private var isDark = false
    private var currentDayAyahs = listOf<AyahData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_khatma_reading)

        startJuz = intent.getIntExtra("START_JUZ", 1)
        totalDays = intent.getIntExtra("TOTAL_DAYS", 30)
        currentDay = intent.getIntExtra("CURRENT_DAY", 0)
        khatmaId = intent.getStringExtra("KHATMA_ID") ?: ""
        isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        tvKhatmaTitle = findViewById(R.id.tvKhatmaTitle)
        tvProgress = findViewById(R.id.tvProgress)
        scrollView = findViewById(R.id.scrollView)
        containerAyahs = findViewById(R.id.containerAyahs)
        readingProgress = findViewById(R.id.readingProgress)

        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val contentHeight = scrollView.getChildAt(0)?.height ?: return@setOnScrollChangeListener
            val viewportHeight = scrollView.height
            val maxScroll = (contentHeight - viewportHeight).coerceAtLeast(1)
            readingProgress.progress = (scrollY * 1000 / maxScroll).coerceIn(0, 1000)
        }

        val khatmas = KhatmaManager.getAll(this)
        val khatma = khatmas.find { it.id == khatmaId }
        tvKhatmaTitle.text = khatma?.name ?: "ختمة من الجزء $startJuz"
        tvProgress.text = "اليوم ${currentDay + 1} من $totalDays"
        savedSurah = khatma?.lastSurah ?: -1
        savedAyah = khatma?.lastAyah ?: -1

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            saveScrollPosition()
            onBackPressedDispatcher.onBackPressed()
        }

        allQuran = QuranDataLoader.load(this)
        loadDayAyahs()
        renderKhatma()
    }

    override fun onPause() {
        super.onPause()
        saveScrollPosition()
    }

    override fun onBackPressed() {
        saveScrollPosition()
        super.onBackPressed()
    }

    private fun loadDayAyahs() {
        currentDayAyahs = JuzData.getDayAyahs(allQuran, startJuz, totalDays, currentDay)
    }

    private fun saveScrollPosition() {
        if (khatmaId.isEmpty() || currentDayAyahs.isEmpty() || containerAyahs.childCount == 0) return
        val idx = findVisibleAyahIndex()
        if (idx >= 0 && idx < currentDayAyahs.size) {
            val ayah = currentDayAyahs[idx]
            KhatmaManager.updatePosition(this, khatmaId, ayah.surahNumber, ayah.number)
        }
    }

    private fun findVisibleAyahIndex(): Int {
        val scrollY = scrollView.scrollY
        val viewCenter = scrollY + scrollView.height / 3
        var bestMatch = -1
        var bestDist = Int.MAX_VALUE
        for (i in 0 until containerAyahs.childCount) {
            val child = containerAyahs.getChildAt(i)
            val tag = child.tag as? Int ?: continue
            val childCenter = child.top + child.height / 2
            val dist = abs(childCenter - viewCenter)
            if (dist < bestDist) {
                bestDist = dist
                bestMatch = tag
            }
        }
        return bestMatch
    }

    private fun renderKhatma() {
        containerAyahs.removeAllViews()
        val uthmanicTypeface = ResourcesCompat.getFont(this, R.font.uthmanic_hafs)
        val ayahColor = if (isDark) Color.parseColor("#e8e0d6") else Color.parseColor("#5E4B40")
        val dividerColor = Color.parseColor("#1A8B6F5E")

        var lastSurahNumber = -1

        val ayahs = currentDayAyahs
        for (idx in ayahs.indices) {
            val ayah = ayahs[idx]

            if (ayah.surahNumber != lastSurahNumber) {
                addSurahSeparator(ayah.surahNumber)
                lastSurahNumber = ayah.surahNumber
            }

            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                tag = idx
            }

            val tvAyah = TextView(this).apply {
                typeface = uthmanicTypeface
                textSize = 22f
                setTextColor(ayahColor)
                textDirection = View.TEXT_DIRECTION_RTL
                gravity = Gravity.START
                setLineSpacing(8f, 1f)
                includeFontPadding = true
                if (Build.VERSION.SDK_INT >= 26) {
                    justificationMode = 1
                }
                text = "${ayah.text} ${toHindiDigits(ayah.number)}"
                setTextIsSelectable(false)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(tvAyah)
            containerAyahs.addView(row)

            if (idx < ayahs.size - 1) {
                val nextAyah = ayahs[idx + 1]
                val isSurahBreak = nextAyah.surahNumber != ayah.surahNumber
                if (!isSurahBreak) {
                    val divider = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(0.8f)
                        ).apply {
                            topMargin = dpToPx(14f)
                            bottomMargin = dpToPx(14f)
                        }
                        setBackgroundColor(dividerColor)
                    }
                    containerAyahs.addView(divider)
                }
            }
        }

        if (currentDay < totalDays - 1) {
            addWirdCompletionSection()
        }

        scrollToSavedPosition()
    }

    private fun addWirdCompletionSection() {
        val section = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            id = View.generateViewId()
        }
        containerAyahs.addView(section)
        rebuildCompletionUI(section)
    }

    private fun rebuildCompletionUI(section: LinearLayout) {
        section.removeAllViews()

        val marginColor = Color.parseColor("#1A8B6F5E")
        val textColor = if (isDark) Color.parseColor("#e8e0d6") else Color.parseColor("#5E4B40")

        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(0.8f)
            ).apply {
                topMargin = dpToPx(24f)
                bottomMargin = dpToPx(16f)
            }
            setBackgroundColor(marginColor)
        }
        section.addView(divider)

        if (isDayCompleted) {
            val actionBtn = Button(this).apply {
                text = "تقبل الله منكم"
                typeface = ResourcesCompat.getFont(this@KhatmaReadingActivity, R.font.stc_forward)
                textSize = 15f
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.bg_primary_button)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48f)
                ).apply {
                    marginStart = dpToPx(20f)
                    marginEnd = dpToPx(20f)
                    topMargin = dpToPx(8f)
                }
                setOnClickListener {
                    if (khatmaId.isNotEmpty()) {
                        KhatmaManager.updateDay(this@KhatmaReadingActivity, khatmaId, currentDay + 1)
                    }
                    finish()
                }
            }
            section.addView(actionBtn)
        } else {
            val promptRow = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            val tvPrompt = TextView(this).apply {
                text = "هل أكملت هذا الورد؟"
                typeface = ResourcesCompat.getFont(this@KhatmaReadingActivity, R.font.stc_forward)
                textSize = 15f
                setTextColor(textColor)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }
            promptRow.addView(tvPrompt)

            val btnCheck = ImageView(this).apply {
                setImageResource(R.drawable.ic_check_circle_24dp)
                setColorFilter(Color.parseColor("#8B6F5E"))
                val size = dpToPx(44f)
                layoutParams = LinearLayout.LayoutParams(size, size)
                setPadding(dpToPx(6f), dpToPx(6f), dpToPx(6f), dpToPx(6f))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    isDayCompleted = true
                    rebuildCompletionUI(section)
                }
            }
            promptRow.addView(btnCheck)

            section.addView(promptRow)
        }
    }

    private fun scrollToSavedPosition() {
        if (savedSurah < 0 || savedAyah < 0 || currentDayAyahs.isEmpty()) return
        val targetIdx = JuzData.findAyahIndexInRange(currentDayAyahs, savedSurah, savedAyah)
        if (targetIdx < 0) return

        for (i in 0 until containerAyahs.childCount) {
            val child = containerAyahs.getChildAt(i)
            if (child.tag == targetIdx) {
                scrollView.post { scrollView.scrollTo(0, child.top) }
                break
            }
        }
    }

    private fun addSurahSeparator(surahNumber: Int) {
        val surahName = JuzData.findSurahNameForAyah(surahNumber)
        val displayName = "سُورَةُ $surahName"

        val separator = LayoutInflater.from(this).inflate(R.layout.item_surah_separator, containerAyahs, false)
        separator.findViewById<TextView>(R.id.tvSeparatorName).text = displayName

        val tvBasmala = separator.findViewById<TextView>(R.id.tvSeparatorBasmala)
        if (surahNumber == 9) {
            tvBasmala.visibility = View.GONE
        } else {
            tvBasmala.text = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"
        }
        containerAyahs.addView(separator)

        val margin = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(12f)
            )
        }
        containerAyahs.addView(margin)
    }

    private fun toHindiDigits(number: Int): String {
        val hindiDigits = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
        return number.toString().map { hindiDigits[it - '0'] }.joinToString("")
    }

    private fun dpToPx(dp: Float): Int =
        (dp * resources.displayMetrics.density).toInt()
}
