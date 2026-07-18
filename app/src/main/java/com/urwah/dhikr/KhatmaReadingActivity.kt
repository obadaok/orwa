package com.urwah.dhikr

import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.NestedScrollView
import kotlin.math.abs

class KhatmaReadingActivity : AppCompatActivity() {

    private lateinit var scrollView: NestedScrollView
    private lateinit var containerAyahs: LinearLayout
    private lateinit var tvKhatmaTitle: TextView
    private lateinit var tvProgress: TextView
    private lateinit var readingProgress: SeekBar
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
    private var autoScrollHandler: Handler? = null
    private var autoScrollRunnable: Runnable? = null
    private var isAutoScrolling = false
    private var autoScrollPixelsPerSecond = 5f
    private var autoScrollGeneration = 0L
    private var continuousKhatmaViewRef: TextView? = null
    private var khatmaAyahOffsets: List<Pair<Int, Int>>? = null
    private var toastHelper: JuzHizbToastHelper? = null
    private var allAyahsFlat: List<AyahData> = emptyList()
    private var khatmaRiwaya: String = "hafs"
    private val quranPrefs by lazy {
        getSharedPreferences("urwah_quran", Context.MODE_PRIVATE)
    }

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
            if (currentDayAyahs.isNotEmpty()) {
                var visibleGlobalIdx = -1
                val singleLine = true
                if (singleLine) {
                    for (i in 0 until containerAyahs.childCount) {
                        val child = containerAyahs.getChildAt(i)
                        if (child.visibility != View.VISIBLE) continue
                        if (child.tag is Int && child.top >= scrollY) {
                            val ayah = currentDayAyahs.getOrNull(child.tag as Int)
                            if (ayah != null) {
                                visibleGlobalIdx = allAyahsFlat.indexOfFirst { it.surahNumber == ayah.surahNumber && it.number == ayah.number }
                            }
                            break
                        }
                    }
                }
                if (visibleGlobalIdx >= 0) {
                    toastHelper?.onPositionReached(visibleGlobalIdx)
                }
            }
        }

        readingProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val content = scrollView.getChildAt(0) ?: return
                val maxScroll = (content.height - scrollView.height).coerceAtLeast(0)
                val targetY = (progress * maxScroll / 1000).coerceIn(0, maxScroll)
                scrollView.scrollTo(0, targetY)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

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

        khatmaRiwaya = khatma?.riwaya ?: QuranDataLoader.getQiraat(this)
        allQuran = if (khatmaRiwaya == "warsh") QuranDataLoader.loadWarsh(this) else QuranDataLoader.loadHafs(this)
        allAyahsFlat = allQuran.entries
            .sortedBy { it.key }
            .flatMap { (_, s) -> s.ayahs.sortedBy { it.number } }
        toastHelper = JuzHizbToastHelper(this, allAyahsFlat)
        loadDayAyahs()
        renderKhatma()

        scrollView.setOnTouchListener { _, event ->
            if (isAutoScrolling && event.action == MotionEvent.ACTION_MOVE) {
                stopAutoScroll()
            }
            false
        }

        findViewById<ImageButton>(R.id.btnAutoScroll).setOnClickListener {
            if (isAutoScrolling) {
                stopAutoScroll()
                updateAutoScrollButton(false)
            } else {
                showAutoScrollDialog()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopAutoScroll()
        if (!isFinishing) {
            saveScrollPosition()
        }
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
        val scrollOffset = scrollView.scrollY
        if (idx >= 0 && idx < currentDayAyahs.size) {
            val ayah = currentDayAyahs[idx]
            KhatmaManager.updatePosition(this, khatmaId, ayah.surahNumber, ayah.number, scrollOffset)
        }
    }

    private fun findVisibleAyahIndex(): Int {
        val scrollY = scrollView.scrollY
        val viewCenter = scrollY + scrollView.height / 3
        var bestIdx = -1
        var bestDist = Int.MAX_VALUE
        for (i in 0 until containerAyahs.childCount) {
            val child = containerAyahs.getChildAt(i)
            val tag = child.tag as? Int ?: continue
            val childCenter = child.top + child.height / 2
            val dist = abs(childCenter - viewCenter)
            if (dist < bestDist) {
                bestDist = dist
                bestIdx = tag
            }
        }
        if (bestIdx >= 0) return bestIdx
        val tv = continuousKhatmaViewRef ?: return -1
        val offsets = khatmaAyahOffsets ?: return -1
        val layout = tv.layout ?: return -1
        val visibleLine = layout.getLineForVertical((scrollY - tv.top).coerceAtLeast(0))
        val offset = layout.getLineStart(visibleLine)
        return offsets.indexOfFirst { (s, e) -> offset >= s && offset < e }
    }

    private fun renderKhatma() {
        containerAyahs.removeAllViews()
        val uthmanicTypeface = ResourcesCompat.getFont(this, if (khatmaRiwaya == "warsh") R.font.uthmanic_warsh else R.font.uthmanic_hafs)
        val ayahColor = if (isDark) Color.parseColor("#e8e0d6") else Color.parseColor("#5E4B40")
        val dividerColor = Color.parseColor("#1A8B6F5E")
        val singleLineMode = quranPrefs.getBoolean("ayah_single_line", true)

        val ayahs = currentDayAyahs

        if (singleLineMode) {
            var lastSurahNumber = -1
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
                    textSize = 29f
                    setTextColor(ayahColor)
                    textDirection = View.TEXT_DIRECTION_RTL
                    gravity = Gravity.START
                    setLineSpacing(4f, 1f)
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
        } else {
            val offsets = mutableListOf<Pair<Int, Int>>()
            var lastSurahNumber = -1
            var surahStartIdx = 0
            for (idx in ayahs.indices) {
                val ayah = ayahs[idx]

                if (ayah.surahNumber != lastSurahNumber) {
                    if (lastSurahNumber != -1) {
                        addContinuousSurahBlock(ayahs.subList(surahStartIdx, idx), offsets, uthmanicTypeface, ayahColor)
                    }
                    addSurahSeparator(ayah.surahNumber)
                    lastSurahNumber = ayah.surahNumber
                    surahStartIdx = idx
                    offsets.clear()
                }
            }
            if (surahStartIdx < ayahs.size) {
                addContinuousSurahBlock(ayahs.subList(surahStartIdx, ayahs.size), offsets, uthmanicTypeface, ayahColor)
            }
            val continuousView = containerAyahs.getChildAt(containerAyahs.childCount - 1) as? TextView
            continuousKhatmaViewRef = continuousView
            khatmaAyahOffsets = offsets
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
                typeface = ResourcesCompat.getFont(this@KhatmaReadingActivity, R.font.alyamama)
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
                typeface = ResourcesCompat.getFont(this@KhatmaReadingActivity, R.font.alyamama)
                textSize = 15f
                setTextColor(textColor)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }
            promptRow.addView(tvPrompt)

            val btnCheck = ImageView(this).apply {
                setImageResource(R.drawable.ic_confirm)
                setColorFilter(Color.parseColor("#8B6F5E"))
                val size = dpToPx(44f)
                layoutParams = LinearLayout.LayoutParams(size, size)
                setPadding(dpToPx(6f), dpToPx(6f), dpToPx(6f), dpToPx(6f))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    isDayCompleted = true
                    val lastIdx = findVisibleAyahIndex()
                    if (lastIdx >= 0 && lastIdx < currentDayAyahs.size) {
                        val last = currentDayAyahs[lastIdx]
                        KhatmaManager.updateEndOfWird(this@KhatmaReadingActivity, khatmaId, last.surahNumber, last.number)
                    }
                    rebuildCompletionUI(section)
                }
            }
            promptRow.addView(btnCheck)

            section.addView(promptRow)
        }
    }

    private fun scrollToSavedPosition() {
        val khatmas = KhatmaManager.getAll(this)
        val khatma = khatmas.find { it.id == khatmaId }
        val savedOffset = khatma?.lastScrollOffset ?: -1

        // أول مرة في هذا الورد → ابقَ في البداية
        if (savedSurah < 0 || savedAyah < 0) return

        // استخدم Scroll Offset المحفوظ مع ضبط الحدود
        if (savedOffset > 0) {
            scrollView.post {
                val content = scrollView.getChildAt(0)
                val maxScroll = ((content?.height ?: 0) - scrollView.height).coerceAtLeast(0)
                scrollView.scrollTo(0, savedOffset.coerceIn(0, maxScroll))
            }
            return
        }

        if (currentDayAyahs.isEmpty()) return
        val targetIdx = JuzData.findAyahIndexInRange(currentDayAyahs, savedSurah, savedAyah)
        if (targetIdx < 0) return
        scheduleScrollToIndex(targetIdx, 5)
    }

    private fun scheduleScrollToIndex(targetIdx: Int, retries: Int) {
        if (retries <= 0) return
        scrollView.postDelayed({
            if (continuousKhatmaViewRef != null && khatmaAyahOffsets != null) {
                val tv = continuousKhatmaViewRef ?: return@postDelayed
                val offsets = khatmaAyahOffsets ?: return@postDelayed
                if (targetIdx < 0 || targetIdx >= offsets.size) return@postDelayed
                val (start, _) = offsets[targetIdx]
                tv.post {
                    val layout = tv.layout ?: return@post
                    if (start >= layout.text.length) return@post
                    val line = layout.getLineForOffset(start)
                    var targetY = tv.top + layout.getLineTop(line)
                    val scrollContent = scrollView.getChildAt(0) ?: return@post
                    var p = tv.parent
                    while (p is View && p != scrollContent) {
                        targetY += (p as View).top
                        p = (p as View).parent
                    }
                    scrollView.scrollTo(0, (targetY - dpToPx(80f)).coerceAtLeast(0))
                }
                return@postDelayed
            }
            for (i in 0 until containerAyahs.childCount) {
                val child = containerAyahs.getChildAt(i)
                if (child.tag == targetIdx) {
                    val scrollContent = scrollView.getChildAt(0) ?: return@postDelayed
                    var offset = child.top
                    var p = child.parent
                    while (p is View && p != scrollContent) {
                        offset += (p as View).top
                        p = (p as View).parent
                    }
                    scrollView.scrollTo(0, (offset - dpToPx(80f)).coerceAtLeast(0))
                    return@postDelayed
                }
            }
            scheduleScrollToIndex(targetIdx, retries - 1)
        }, 100)
    }

    private fun addSurahSeparator(surahNumber: Int) {
        val surahName = JuzData.findSurahNameForAyah(surahNumber)
        val displayName = "سورة $surahName"
        val textColor = if (isDark) Color.parseColor("#e8e0d6") else Color.parseColor("#5E4B40")
        val uthmanicTypeface = ResourcesCompat.getFont(this, if (khatmaRiwaya == "warsh") R.font.uthmanic_warsh else R.font.uthmanic_hafs)

        val separator = LayoutInflater.from(this).inflate(R.layout.item_surah_separator, containerAyahs, false)
        separator.findViewById<TextView>(R.id.tvSeparatorName).apply {
            text = displayName
            setTextColor(textColor)
        }

        val tvBasmala = separator.findViewById<TextView>(R.id.tvSeparatorBasmala)
        if (surahNumber == 9) {
            tvBasmala.visibility = View.GONE
        } else {
            tvBasmala.visibility = View.VISIBLE
            tvBasmala.text = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"
            tvBasmala.typeface = uthmanicTypeface
        }
        containerAyahs.addView(separator)

        val margin = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(12f)
            )
        }
        containerAyahs.addView(margin)
    }

    private fun addContinuousSurahBlock(
        ayahs: List<AyahData>,
        offsets: MutableList<Pair<Int, Int>>,
        uthmanicTypeface: Typeface?,
        ayahColor: Int
    ) {
        val sb = SpannableStringBuilder()
        for (ayah in ayahs) {
            val start = sb.length
            sb.append("${ayah.text} ${toHindiDigits(ayah.number)}  ")
            uthmanicTypeface?.let {
                sb.setSpan(CustomTypefaceSpan(it), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            offsets.add(start to sb.length)
        }
        val textView = TextView(this).apply {
            text = sb
            typeface = uthmanicTypeface
            textSize = 29f
            setTextColor(ayahColor)
            textDirection = View.TEXT_DIRECTION_RTL
            gravity = Gravity.START
            setLineSpacing(4f, 1f)
            includeFontPadding = true
            if (Build.VERSION.SDK_INT >= 26) {
                justificationMode = 1
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        containerAyahs.addView(textView)
    }

    private fun showAutoScrollDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_auto_scroll, null)
        val slider = view.findViewById<android.widget.SeekBar>(R.id.scrollSpeedSlider)
        val tvSpeed = view.findViewById<TextView>(R.id.tvScrollSpeedValue)

        val savedSpeed = quranPrefs.getInt("auto_scroll_speed", 40)
        slider.progress = savedSpeed
        tvSpeed.text = "${savedSpeed}%"

        slider.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                tvSpeed.text = "${progress}%"
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar) {}
        })

        view.findViewById<TextView>(R.id.btnSlowPreset).setOnClickListener {
            slider.progress = 20; tvSpeed.text = "20%"
        }
        view.findViewById<TextView>(R.id.btnMediumPreset).setOnClickListener {
            slider.progress = 40; tvSpeed.text = "40%"
        }
        view.findViewById<TextView>(R.id.btnFastPreset).setOnClickListener {
            slider.progress = 70; tvSpeed.text = "70%"
        }

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<Button>(R.id.btnConfirmAutoScroll).setOnClickListener {
            val speed = slider.progress.coerceIn(5, 100)
            quranPrefs.edit().putInt("auto_scroll_speed", speed).apply()
            updateAutoScrollSpeed(speed)
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.btnCancelAutoScroll).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun calculatePixelsPerSecond(speedPercent: Int): Float {
        return (speedPercent.toFloat() * 0.8f + 5f).coerceIn(5f, 85f)
    }

    private fun startAutoScroll(speedPercent: Int) {
        autoScrollPixelsPerSecond = calculatePixelsPerSecond(speedPercent)
        if (isAutoScrolling) return
        isAutoScrolling = true
        autoScrollGeneration++
        updateAutoScrollButton(true)

        autoScrollRunnable = object : Runnable {
            private var lastTime = System.nanoTime()
            private var accumulator = 0f
            private val generation = autoScrollGeneration

            override fun run() {
                if (!isAutoScrolling || autoScrollGeneration != generation) return
                val now = System.nanoTime()
                val delta = ((now - lastTime) / 1_000_000f).coerceIn(1f, 50f)
                lastTime = now

                accumulator += autoScrollPixelsPerSecond * delta / 1000f
                val step = accumulator.toInt()
                if (step < 1) {
                    scrollView.postOnAnimation(this)
                    return
                }
                accumulator -= step

                val scrollContent = scrollView.getChildAt(0) ?: return
                val maxScroll = (scrollContent.height - scrollView.height).coerceAtLeast(0)
                val newScroll = scrollView.scrollY + step

                if (newScroll >= maxScroll) {
                    scrollView.scrollTo(0, maxScroll)
                    stopAutoScroll()
                    Toast.makeText(this@KhatmaReadingActivity, "وصلت لنهاية الورد", Toast.LENGTH_SHORT).show()
                    return
                }

                scrollView.scrollBy(0, step)
                scrollView.postOnAnimation(this)
            }
        }
        autoScrollRunnable?.let { scrollView.postOnAnimation(it) }
    }

    private fun updateAutoScrollSpeed(speedPercent: Int) {
        autoScrollPixelsPerSecond = calculatePixelsPerSecond(speedPercent)
        if (!isAutoScrolling) {
            startAutoScroll(speedPercent)
        }
    }

    private fun stopAutoScroll() {
        isAutoScrolling = false
        autoScrollRunnable = null
        updateAutoScrollButton(false)
    }

    private fun updateAutoScrollButton(isPlaying: Boolean) {
        val btn = findViewById<ImageButton>(R.id.btnAutoScroll)
        val targetRes = if (isPlaying) R.drawable.ic_scroll_pause else R.drawable.ic_scroll_play
        btn.animate().alpha(0f).setDuration(150).withEndAction {
            btn.setImageResource(targetRes)
            btn.animate().alpha(1f).setDuration(150).start()
        }.start()
    }

    private fun toHindiDigits(number: Int): String {
        val hindiDigits = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
        return number.toString().map { hindiDigits[it - '0'] }.joinToString("")
    }

    private fun dpToPx(dp: Float): Int =
        (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        toastHelper?.detach()
        toastHelper = null
    }
}
