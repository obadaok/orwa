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
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
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
    private lateinit var tvTimeRemaining: TextView
    private lateinit var tvSurahName: TextView
    private lateinit var tvJuzName: TextView
    private lateinit var tvHizbName: TextView
    private lateinit var tvSeparator1: TextView
    private lateinit var tvSeparator2: TextView
    private lateinit var tvSeparator3: TextView
    private lateinit var readingProgress: SeekBar
    private lateinit var progressMarkers: FrameLayout
    private lateinit var topToolbar: View
    private lateinit var progressContainer: View
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
    private var continuousBlocks = listOf<ContinuousBlock>()
    private var toastHelper: JuzHizbToastHelper? = null
    private var allAyahsFlat: List<AyahData> = emptyList()
    private var khatmaRiwaya: String = "hafs"
    private var singleLineMode = true
    private var ayahAlignment = 3
    private var isUiHidden = false

    private class ContinuousBlock(
        val textView: TextView,
        val baseIndex: Int,
        val offsets: List<Pair<Int, Int>>
    ) {
        fun containsAyah(idx: Int) = idx >= baseIndex && idx < baseIndex + offsets.size
        fun ayahForOffset(offset: Int): Int {
            val matchIdx = offsets.indexOfFirst { (s, e) -> offset >= s && offset < e }
            return if (matchIdx >= 0) baseIndex + matchIdx else -1
        }
    }

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
        tvTimeRemaining = findViewById(R.id.tvTimeRemaining)
        tvSurahName = findViewById(R.id.tvSurahName)
        tvJuzName = findViewById(R.id.tvJuzName)
        tvHizbName = findViewById(R.id.tvHizbName)
        tvSeparator1 = findViewById(R.id.tvSeparator1)
        tvSeparator2 = findViewById(R.id.tvSeparator2)
        tvSeparator3 = findViewById(R.id.tvSeparator3)
        scrollView = findViewById(R.id.scrollView)
        containerAyahs = findViewById(R.id.containerAyahs)
        readingProgress = findViewById(R.id.readingProgress)
        progressMarkers = findViewById(R.id.progressMarkers)
        topToolbar = findViewById(R.id.topToolbar)
        progressContainer = findViewById(R.id.progressContainer)
        singleLineMode = quranPrefs.getBoolean("ayah_single_line", false)
        ayahAlignment = quranPrefs.getInt("quran_alignment", 3)

        // Keep screen on
        applyKeepScreenOnIfEnabled()

        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val contentHeight = scrollView.getChildAt(0)?.height ?: return@setOnScrollChangeListener
            val viewportHeight = scrollView.height
            val maxScroll = (contentHeight - viewportHeight).coerceAtLeast(1)
            readingProgress.progress = (scrollY * 1000 / maxScroll).coerceIn(0, 1000)
            updateTimeRemaining(scrollY, maxScroll)
            findAndNotifyPosition(scrollY)
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
        isDayCompleted = khatma?.confirmedDay == currentDay

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            // الحفظ يتم في onBackPressed — تجنّب الكتابة المزدوجة
            onBackPressedDispatcher.onBackPressed()
        }

        khatmaRiwaya = khatma?.riwaya ?: QuranDataLoader.getQiraat(this)
        allQuran = QuranDataLoader.loadWithQiraat(this, khatmaRiwaya)
        allAyahsFlat = allQuran.entries
            .sortedBy { it.key }
            .flatMap { (_, s) -> s.ayahs.sortedBy { it.number } }
        toastHelper = JuzHizbToastHelper(this, allAyahsFlat)
        loadDayAyahs()
        renderKhatma()

        // Set initial toolbar info
        if (currentDayAyahs.isNotEmpty()) {
            val firstAyah = currentDayAyahs.first()
            val initialIdx = allAyahsFlat.indexOfFirst {
                it.surahNumber == firstAyah.surahNumber && it.number == firstAyah.number
            }
            if (initialIdx >= 0) updateToolbarInfo(initialIdx)
        }

        // Touch listener: detect tap for UI toggle (never stops auto-scroll)
        var touchDownX = 0f
        var touchDownY = 0f
        var touchDownTime = 0L
        scrollView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.x
                    touchDownY = event.y
                    touchDownTime = System.currentTimeMillis()
                }
                MotionEvent.ACTION_UP -> {
                    val dx = abs(event.x - touchDownX)
                    val dy = abs(event.y - touchDownY)
                    val dt = System.currentTimeMillis() - touchDownTime
                    if (dx < 20f && dy < 20f && dt < 300L) {
                        toggleUiVisibility()
                    }
                }
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

    override fun onResume() {
        super.onResume()
        applyKeepScreenOnIfEnabled()
        ReadingTimeTracker.startSession(this, ReadingTimeTracker.TYPE_KHATMA)
    }

    override fun onPause() {
        super.onPause()
        ReadingTimeTracker.stopSession(this)
        stopAutoScroll()
        if (!isFinishing) {
            saveScrollPosition()
        }
    }

    override fun onBackPressed() {
        saveScrollPosition()
        super.onBackPressed()
    }

    private fun applyKeepScreenOnIfEnabled() {
        val prefs = getSharedPreferences("urwah_settings", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("keep_screen_on", false)
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private var uiToggleGeneration = 0

    private fun toggleUiVisibility() {
        isUiHidden = !isUiHidden
        val targetAlpha = if (isUiHidden) 0f else 1f
        val duration = 250L
        val generation = ++uiToggleGeneration

        val applyInteractivity: (View) -> Unit = { v ->
            v.animate().alpha(targetAlpha).setDuration(duration).withEndAction {
                if (generation == uiToggleGeneration) {
                    v.visibility = if (isUiHidden) View.INVISIBLE else View.VISIBLE
                }
            }.start()
        }
        applyInteractivity(topToolbar)
        applyInteractivity(progressContainer)
        applyInteractivity(findViewById(R.id.bottomDivider))
    }

    private fun findAndNotifyPosition(scrollY: Int) {
        if (currentDayAyahs.isEmpty()) return
        var visibleGlobalIdx = -1

        if (singleLineMode) {
            for (i in 0 until containerAyahs.childCount) {
                val child = containerAyahs.getChildAt(i)
                if (child.visibility != View.VISIBLE) continue
                if (child.tag is Int && child.top >= scrollY) {
                    val ayah = currentDayAyahs.getOrNull(child.tag as Int)
                    if (ayah != null) {
                        visibleGlobalIdx = allAyahsFlat.indexOfFirst {
                            it.surahNumber == ayah.surahNumber && it.number == ayah.number
                        }
                    }
                    break
                }
            }
        } else {
            if (continuousBlocks.isEmpty()) return
            var ayahIdx = -1
            for (block in continuousBlocks) {
                val tv = block.textView
                val layout = tv.layout ?: continue
                // إحداثية بداية البلوك داخل محتوى الـ ScrollView كاملة
                val blockTop = topOfViewWithinScrollContent(tv)
                val blockBottom = blockTop + layout.height
                if (scrollY >= blockTop && scrollY < blockBottom) {
                    val visibleLine = layout.getLineForVertical((scrollY - blockTop).coerceAtLeast(0))
                    ayahIdx = block.ayahForOffset(layout.getLineStart(visibleLine))
                    break
                }
            }
            if (ayahIdx < 0) {
                // موضع التمرير في منطقة فاصل سورة: خذ آخر آية من أقرب بلوك سابق
                for (block in continuousBlocks.asReversed()) {
                    val layout = block.textView.layout ?: continue
                    if (topOfViewWithinScrollContent(block.textView) <= scrollY) {
                        ayahIdx = block.baseIndex + block.offsets.size - 1
                        break
                    }
                }
            }
            if (ayahIdx >= 0) {
                val ayah = currentDayAyahs.getOrNull(ayahIdx)
                if (ayah != null) {
                    visibleGlobalIdx = allAyahsFlat.indexOfFirst {
                        it.surahNumber == ayah.surahNumber && it.number == ayah.number
                    }
                }
            }
        }

        if (visibleGlobalIdx >= 0) {
            toastHelper?.onPositionReached(visibleGlobalIdx)
            updateToolbarInfo(visibleGlobalIdx)
        }
    }

    private fun updateToolbarInfo(globalIndex: Int) {
        if (globalIndex < 0 || globalIndex >= allAyahsFlat.size) return
        val ayah = allAyahsFlat[globalIndex]

        val surahName = JuzData.findSurahNameForAyah(ayah.surahNumber)
        if (surahName.isNotEmpty()) {
            tvSurahName.text = surahName
            tvSurahName.visibility = View.VISIBLE
            tvSeparator1.visibility = View.VISIBLE
        }

        val juzNum = JuzData.getJuzNumberForAyah(allAyahsFlat, globalIndex)
        val hindi = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
        val toHindi = { n: Int -> n.toString().map { if (it.isDigit()) hindi[it - '0'] else it }.joinToString("") }
        tvJuzName.text = "جزء ${toHindi(juzNum)}"
        tvJuzName.visibility = View.VISIBLE
        tvSeparator2.visibility = View.VISIBLE

        val hizbNum = JuzData.getHizbNumberForAyah(allAyahsFlat, globalIndex)
        tvHizbName.text = "حزب ${toHindi(hizbNum)}"
        tvHizbName.visibility = View.VISIBLE
        tvSeparator3.visibility = View.VISIBLE
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

        if (singleLineMode) {
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
        } else {
            var nearestBestDist = Int.MAX_VALUE
            for (block in continuousBlocks) {
                val tv = block.textView
                val layout = tv.layout ?: continue
                val blockTop = topOfViewWithinScrollContent(tv)
                val blockBottom = blockTop + layout.height
                if (viewCenter in blockTop until blockBottom) {
                    val localY = (viewCenter - blockTop).coerceIn(0, (layout.height - 1).coerceAtLeast(0))
                    val line = layout.getLineForVertical(localY)
                    val idx = block.ayahForOffset(layout.getLineStart(line))
                    if (idx >= 0) return idx
                }
                val dist = if (viewCenter < blockTop) blockTop - viewCenter else viewCenter - (blockBottom - 1)
                if (dist < nearestBestDist) {
                    nearestBestDist = dist
                    bestIdx = if (viewCenter < blockTop) block.baseIndex else block.baseIndex + block.offsets.size - 1
                }
            }
        }
        return bestIdx
    }

    private fun topOfViewWithinScrollContent(view: View): Int {
        var offset = view.top
        val scrollContent = scrollView.getChildAt(0)
        var p = view.parent
        while (p is View && p !== scrollContent) {
            offset += p.top
            p = p.getParent()
        }
        return offset
    }

    private fun renderKhatma() {
        containerAyahs.removeAllViews()
        continuousBlocks = emptyList()
        val uthmanicTypeface = ResourcesCompat.getFont(this, QuranDataLoader.fontResFor(khatmaRiwaya))
        val ayahColor = if (isDark) Color.parseColor("#e8e0d6") else Color.parseColor("#5E4B40")
        val dividerColor = Color.parseColor("#1A8B6F5E")

        val ayahs = currentDayAyahs

        if (ayahs.isEmpty()) {
            val tvEmpty = TextView(this).apply {
                text = "تعذّر تحميل ورد هذا اليوم. تأكد من صحة إعدادات الختمة."
                typeface = ResourcesCompat.getFont(this@KhatmaReadingActivity, R.font.alyamama)
                textSize = 16f
                setTextColor(ayahColor)
                gravity = Gravity.CENTER
                setPadding(dpToPx(24f), dpToPx(48f), dpToPx(24f), dpToPx(48f))
            }
            containerAyahs.addView(tvEmpty)
            return
        }

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
                    applyAyahAlignment(this, ayahAlignment, continuous = false)
                    setLineSpacing(4f, 1f)
                    includeFontPadding = true
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
            continuousBlocks = emptyList()
            var lastSurahNumber = -1
            var surahStartIdx = 0
            for (idx in ayahs.indices) {
                val ayah = ayahs[idx]
                if (ayah.surahNumber != lastSurahNumber) {
                    if (lastSurahNumber != -1) {
                        addContinuousSurahBlock(ayahs.subList(surahStartIdx, idx), surahStartIdx, uthmanicTypeface, ayahColor)
                    }
                    addSurahSeparator(ayah.surahNumber)
                    lastSurahNumber = ayah.surahNumber
                    surahStartIdx = idx
                }
            }
            if (surahStartIdx < ayahs.size) {
                addContinuousSurahBlock(ayahs.subList(surahStartIdx, ayahs.size), surahStartIdx, uthmanicTypeface, ayahColor)
            }
        }

        if (currentDay < totalDays - 1) {
            addWirdCompletionSection()
        }

        scrollToSavedPosition()
        updateProgressMarkers()
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
                        KhatmaManager.updateEndOfWird(this@KhatmaReadingActivity, khatmaId, last.surahNumber, last.number, currentDay)
                    } else if (currentDayAyahs.isNotEmpty()) {
                        val last = currentDayAyahs.last()
                        KhatmaManager.updateEndOfWird(this@KhatmaReadingActivity, khatmaId, last.surahNumber, last.number, currentDay)
                    }
                    rebuildCompletionUI(section)
                }
            }
            promptRow.addView(btnCheck)

            section.addView(promptRow)
        }
    }

    private fun scrollToSavedPosition() {
        if (savedSurah < 0 || savedAyah < 0) return
        if (currentDayAyahs.isEmpty()) return

        val targetIdx = JuzData.findAyahIndexInRange(currentDayAyahs, savedSurah, savedAyah)

        val khatmas = KhatmaManager.getAll(this)
        val khatma = khatmas.find { it.id == khatmaId }
        val savedOffset = khatma?.lastScrollOffset ?: -1

        if (savedOffset > 0 && targetIdx >= 0) {
            scheduleScrollWithOffsetOrAyah(savedOffset, targetIdx, 5)
            return
        }

        if (targetIdx < 0) return
        scheduleScrollToIndex(targetIdx, 5)
    }

    private fun scheduleScrollWithOffsetOrAyah(savedOffset: Int, targetIdx: Int, retries: Int) {
        if (retries <= 0) {
            scheduleScrollToIndex(targetIdx, 3)
            return
        }
        scrollView.postDelayed({
            val content = scrollView.getChildAt(0)
            val contentHeight = content?.height ?: 0
            val viewportHeight = scrollView.height
            if (contentHeight <= 0 || viewportHeight <= 0) {
                scheduleScrollWithOffsetOrAyah(savedOffset, targetIdx, retries - 1)
                return@postDelayed
            }
            val maxScroll = (contentHeight - viewportHeight).coerceAtLeast(0)
            if (maxScroll <= 0) {
                scheduleScrollWithOffsetOrAyah(savedOffset, targetIdx, retries - 1)
                return@postDelayed
            }
            val target = savedOffset.coerceIn(0, maxScroll)
            scrollView.scrollTo(0, target)
            scrollView.post {
                if (scrollView.scrollY == 0 && target > 0) {
                    scheduleScrollToIndex(targetIdx, 3)
                }
            }
        }, 100)
    }

    private fun scheduleScrollToIndex(targetIdx: Int, retries: Int) {
        if (retries <= 0) return
        scrollView.postDelayed({
            if (continuousBlocks.isNotEmpty()) {
                val block = continuousBlocks.firstOrNull { it.containsAyah(targetIdx) }
                    ?: (if (targetIdx < 0) null else continuousBlocks.lastOrNull { it.baseIndex <= targetIdx })
                    ?: return@postDelayed
                val localIdx = (targetIdx - block.baseIndex).coerceIn(0, block.offsets.size - 1)
                val (start, _) = block.offsets[localIdx]
                val tv = block.textView
                tv.post {
                    val layout = tv.layout ?: return@post
                    if (start >= layout.text.length) return@post
                    val line = layout.getLineForOffset(start)
                    val targetY = topOfViewWithinScrollContent(tv) + layout.getLineTop(line)
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
        val uthmanicTypeface = ResourcesCompat.getFont(this, QuranDataLoader.fontResFor(khatmaRiwaya))

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
        baseIndex: Int,
        uthmanicTypeface: Typeface?,
        ayahColor: Int
    ) {
        val offsets = mutableListOf<Pair<Int, Int>>()
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
            applyAyahAlignment(this, ayahAlignment, continuous = true)
            setLineSpacing(dpToPx(1f).toFloat(), 1f)
            includeFontPadding = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        containerAyahs.addView(textView)
        continuousBlocks = continuousBlocks + ContinuousBlock(textView, baseIndex, offsets)
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
        updateTimeRemainingVisibility()
        refreshTimeRemaining()

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
        updateTimeRemainingVisibility()
        tvTimeRemaining.text = ""
    }

    private fun updateAutoScrollButton(isPlaying: Boolean) {
        val btn = findViewById<ImageButton>(R.id.btnAutoScroll)
        val targetRes = if (isPlaying) R.drawable.ic_scroll_pause else R.drawable.ic_scroll_play
        btn.animate().alpha(0f).setDuration(150).withEndAction {
            btn.setImageResource(targetRes)
            btn.animate().alpha(1f).setDuration(150).start()
        }.start()
    }

    // ─── Remaining time ────────────────────────────────────────────

    private fun updateTimeRemainingVisibility() {
        tvTimeRemaining.visibility = if (isAutoScrolling) View.VISIBLE else View.GONE
    }

    private fun updateTimeRemaining(scrollY: Int, maxScroll: Int) {
        if (!isAutoScrolling || maxScroll <= 0) {
            tvTimeRemaining.text = ""
            return
        }
        val remainingPixels = (maxScroll - scrollY).coerceAtLeast(0)
        if (remainingPixels <= 0 || autoScrollPixelsPerSecond <= 0f) {
            tvTimeRemaining.text = ""
            return
        }
        val remainingSeconds = (remainingPixels / autoScrollPixelsPerSecond.toDouble())
        if (remainingSeconds <= 0.5) {
            tvTimeRemaining.text = ""
            return
        }
        val totalSec = remainingSeconds.toInt()
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        tvTimeRemaining.text = if (minutes > 0) {
            "متبقي: $minutes د و ${seconds}ث"
        } else {
            "متبقي: ${seconds}ث"
        }
    }

    private fun refreshTimeRemaining() {
        val content = scrollView.getChildAt(0) ?: return
        val maxScroll = (content.height - scrollView.height).coerceAtLeast(1)
        if (isAutoScrolling) {
            tvTimeRemaining.visibility = View.VISIBLE
            updateTimeRemaining(scrollView.scrollY, maxScroll)
        }
    }

    // ─── Progress markers ──────────────────────────────────────────

    private fun updateProgressMarkers() {
        if (currentDayAyahs.isEmpty()) return
        progressMarkers.removeAllViews()

        val scrollContent = scrollView.getChildAt(0) ?: return

        val firstAyah = currentDayAyahs.first()
        val lastAyah = currentDayAyahs.last()
        val globalStart = allAyahsFlat.indexOfFirst {
            it.surahNumber == firstAyah.surahNumber && it.number == firstAyah.number
        }
        val globalEnd = allAyahsFlat.indexOfFirst {
            it.surahNumber == lastAyah.surahNumber && it.number == lastAyah.number
        }
        if (globalStart < 0 || globalEnd <= globalStart) return

        val dayLength = globalEnd - globalStart + 1

        val width = progressMarkers.width
        if (width <= 0) {
            progressMarkers.post { updateProgressMarkers() }
            return
        }

        val seekBarPadding = dpToPx(2f)
        val thumbOffset = dpToPx(10f)
        val markersPadding = dpToPx(4f)
        val trackStart = seekBarPadding + thumbOffset
        val trackWidth = width - 2 * seekBarPadding - 2 * thumbOffset

        val points = mutableListOf<Pair<Float, Boolean>>()

        // Add juz/hizb boundaries that fall within this day
        for (juz in JuzData.JUZ_BOUNDARIES) {
            val si = allAyahsFlat.indexOfFirst { it.surahNumber == juz.startSurah && it.number == juz.startAyah }
            val ei = allAyahsFlat.indexOfFirst { it.surahNumber == juz.endSurah && it.number == juz.endAyah }
            if (si < 0 || ei <= si) continue
            val n = ei - si + 1

            if (si in globalStart..globalEnd) {
                val frac = (si - globalStart).toFloat() / dayLength
                points.add(frac to true)
            }

            if (si < globalEnd && ei > globalStart) {
                val hizbHalf = si + n / 2
                if (hizbHalf in (globalStart + 1) until globalEnd) {
                    val frac = (hizbHalf - globalStart).toFloat() / dayLength
                    points.add(frac to true)
                }

                for (q in listOf(n / 4, 3 * n / 4)) {
                    val ai = si + q
                    if (ai in (globalStart + 1) until globalEnd) {
                        val frac = (ai - globalStart).toFloat() / dayLength
                        points.add(frac to false)
                    }
                }

                val juzEnd = si + n - 1
                if (juzEnd in (globalStart + 1) until globalEnd) {
                    val frac = (juzEnd - globalStart).toFloat() / dayLength
                    points.add(frac to true)
                }
            }
        }

        // Always add day-quarter markers so markers appear even for short days
        val dayQuarters = listOf(0.25f, 0.5f, 0.75f)
        for (qf in dayQuarters) {
            val exists = points.any { abs(it.first - qf) < 0.001f }
            if (!exists) {
                points.add(qf to false)
            }
        }

        val epsilon = 0.001f
        val sorted = points.distinctBy { (it.first / epsilon).toInt() }.sortedBy { it.first }
        sorted.forEach { (fraction, isJuz) ->
            val size = if (isJuz) dpToPx(5f) else dpToPx(3f)
            val markerColor = if (isJuz) "#8B6F5E" else "#C9A182"

            val markerCenterX = trackStart + (trackWidth * fraction).toInt()
            val tx = (markerCenterX - markersPadding - size / 2f)
                .coerceIn(0f, (width - markersPadding - size).toFloat())

            val marker = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(size, dpToPx(16f))
                setBackgroundColor(Color.parseColor(markerColor))
                translationX = tx
                translationY = dpToPx(4f).toFloat()
                isClickable = false
                isFocusable = false
            }
            progressMarkers.addView(marker)
        }
    }

    private fun toHindiDigits(number: Int): String {
        val hindiDigits = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
        return number.toString().map { if (it.isDigit()) hindiDigits[it - '0'] else it }.joinToString("")
    }

    /**
     * يطبّق المحاذاة المختارة (يمين/وسط/يسار/ضبط) على TextView من آيات المصحف.
     * alignment: 0=يمين، 1=وسط، 2=يسار، 3=ضبط.
     * الضبط يُفعّل في العرض المتواصل فقط حتى لا تظهر فراغات كبيرة عند عرض آية
     * واحدة في سطر مستقل.
     */
    private fun applyAyahAlignment(tv: TextView, alignment: Int, continuous: Boolean) {
        tv.textDirection = View.TEXT_DIRECTION_RTL
        when (alignment) {
            1 -> {
                tv.gravity = Gravity.CENTER
                tv.textAlignment = View.TEXT_ALIGNMENT_CENTER
                if (Build.VERSION.SDK_INT >= 26) tv.justificationMode = Layout.JUSTIFICATION_MODE_NONE
            }
            2 -> {
                tv.gravity = Gravity.END
                tv.textAlignment = View.TEXT_ALIGNMENT_TEXT_END
                if (Build.VERSION.SDK_INT >= 26) tv.justificationMode = Layout.JUSTIFICATION_MODE_NONE
            }
            3 -> {
                tv.gravity = Gravity.START
                tv.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
                if (Build.VERSION.SDK_INT >= 26) {
                    tv.justificationMode = if (continuous) {
                        Layout.JUSTIFICATION_MODE_INTER_WORD
                    } else {
                        Layout.JUSTIFICATION_MODE_NONE
                    }
                }
            }
            else -> {
                tv.gravity = Gravity.START
                tv.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
                if (Build.VERSION.SDK_INT >= 26) tv.justificationMode = Layout.JUSTIFICATION_MODE_NONE
            }
        }
    }

    private fun dpToPx(dp: Float): Int =
        (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        toastHelper?.detach()
        toastHelper = null
    }
}
