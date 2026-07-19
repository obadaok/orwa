package com.urwah.dhikr

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

class SurahDetailActivity : AppCompatActivity() {

    private lateinit var surahInfo: SurahInfo
    private lateinit var ayahs: List<AyahData>
    private lateinit var scrollView: NestedScrollView
    private lateinit var containerAyahs: LinearLayout
    private lateinit var ayahRowMap: MutableMap<Int, LinearLayout>
    private val highlightedAyahs = mutableSetOf<Int>()
    private var actionPopup: PopupWindow? = null
    private var selectedColor = BookmarkManager.COLORS[0]
    private var isDark: Boolean = false
    private var isKhatmaMode: Boolean = false
    private var khatmaEndAyahIndex: Int = -1
    private var surahNumber: Int = 0
    private var verseCount: Int = 0
    private var isNavigating: Boolean = false
    private var selectedAyahNumber: Int = -1
    private var continuousViewRef: TextView? = null
    private var continuousAyahOffsets: List<Pair<Int, Int>>? = null
    private val basmalaText = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"

    private val quranPrefs by lazy {
        getSharedPreferences("urwah_quran", Context.MODE_PRIVATE)
    }

    private var autoScrollHandler: Handler? = null
    private var autoScrollRunnable: Runnable? = null
    private var isAutoScrolling = false
    private var autoScrollPixelsPerSecond = 5f
    private var autoScrollGeneration = 0L
    private var toastHelper: JuzHizbToastHelper? = null
    private var scrollHandler: android.os.Handler? = null
    private var scrollDebounce: Runnable? = null

    private var isPageMode = false
    private var viewPager: ViewPager2? = null
    private var firstPage: Int = 1
    private var lastPage: Int = 1
    private var pageModeRiwaya: String = "hafs"

    private val settingsPrefs by lazy {
        getSharedPreferences("urwah_settings", Context.MODE_PRIVATE)
    }

    companion object {
        private val ENGLISH_NAMES = mapOf(
            1 to "Al-Fatiha", 2 to "Al-Baqarah", 3 to "Aal-e-Imran", 4 to "An-Nisa'",
            5 to "Al-Ma'idah", 6 to "Al-An'am", 7 to "Al-A'raf", 8 to "Al-Anfal",
            9 to "At-Tawbah", 10 to "Yunus", 11 to "Hud", 12 to "Yusuf",
            13 to "Ar-Ra'd", 14 to "Ibrahim", 15 to "Al-Hijr", 16 to "An-Nahl",
            17 to "Al-Isra'", 18 to "Al-Kahf", 19 to "Maryam", 20 to "Taha",
            21 to "Al-Anbiya'", 22 to "Al-Hajj", 23 to "Al-Mu'minun", 24 to "An-Nur",
            25 to "Al-Furqan", 26 to "Ash-Shu'ara'", 27 to "An-Naml", 28 to "Al-Qasas",
            29 to "Al-Ankabut", 30 to "Ar-Rum", 31 to "Luqman", 32 to "As-Sajdah",
            33 to "Al-Ahzab", 34 to "Saba'", 35 to "Fatir", 36 to "Ya-Sin",
            37 to "As-Saffat", 38 to "Sad", 39 to "Az-Zumar", 40 to "Ghafir",
            41 to "Fussilat", 42 to "Ash-Shura", 43 to "Az-Zukhruf", 44 to "Ad-Dukhan",
            45 to "Al-Jathiyah", 46 to "Al-Ahqaf", 47 to "Muhammad", 48 to "Al-Fath",
            49 to "Al-Hujurat", 50 to "Qaf", 51 to "Adh-Dhariyat", 52 to "At-Tur",
            53 to "An-Najm", 54 to "Al-Qamar", 55 to "Ar-Rahman", 56 to "Al-Waqi'ah",
            57 to "Al-Hadid", 58 to "Al-Mujadilah", 59 to "Al-Hashr", 60 to "Al-Mumtahanah",
            61 to "As-Saf", 62 to "Al-Jumu'ah", 63 to "Al-Munafiqun", 64 to "At-Taghabun",
            65 to "At-Talaq", 66 to "At-Tahrim", 67 to "Al-Mulk", 68 to "Al-Qalam",
            69 to "Al-Haqqah", 70 to "Al-Ma'arij", 71 to "Nuh", 72 to "Al-Jinn",
            73 to "Al-Muzzammil", 74 to "Al-Muddaththir", 75 to "Al-Qiyamah", 76 to "Al-Insan",
            77 to "Al-Mursalat", 78 to "An-Naba'", 79 to "An-Nazi'at", 80 to "Abasa",
            81 to "At-Takwir", 82 to "Al-Infitar", 83 to "Al-Mutaffifin", 84 to "Al-Inshiqaq",
            85 to "Al-Buruj", 86 to "At-Tariq", 87 to "Al-A'la", 88 to "Al-Ghashiyah",
            89 to "Al-Fajr", 90 to "Al-Balad", 91 to "Ash-Shams", 92 to "Al-Layl",
            93 to "Ad-Duha", 94 to "Ash-Sharh", 95 to "At-Tin", 96 to "Al-Alaq",
            97 to "Al-Qadr", 98 to "Al-Bayyinah", 99 to "Az-Zalzalah", 100 to "Al-'Adiyat",
            101 to "Al-Qari'ah", 102 to "At-Takathur", 103 to "Al-Asr", 104 to "Al-Humazah",
            105 to "Al-Fil", 106 to "Quraysh", 107 to "Al-Ma'un", 108 to "Al-Kawthar",
            109 to "Al-Kafirun", 110 to "An-Nasr", 111 to "Al-Masad", 112 to "Al-Ikhlas",
            113 to "Al-Falaq", 114 to "An-Nas"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_surah_detail)

        val settingsPrefs = getSharedPreferences("urwah_settings", Context.MODE_PRIVATE)
        if (settingsPrefs.getBoolean("keep_screen_on", false)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        val surahName = intent.getStringExtra("SURAH_NAME") ?: "العاديات"
        val lastAyah = intent.getIntExtra("LAST_AYAH", -1)
        isKhatmaMode = intent.getBooleanExtra("KHATMA_MODE", false)
        surahNumber = intent.getIntExtra("SURAH_NUMBER", 100)
        verseCount = intent.getIntExtra("VERSE_COUNT", 11)

        val data = QuranDataLoader.getSurah(this, surahNumber)
        ayahs = data?.ayahs ?: emptyList()

        val displayName = buildSurahDisplayName(surahName)
        surahInfo = SurahInfo(
            number = surahNumber,
            nameArabic = displayName,
            nameEnglish = ENGLISH_NAMES[surahNumber] ?: "",
            revelationPlace = data?.revelationPlace ?: "مكية",
            ayahCount = verseCount,
            ligatureCode = "surah$surahNumber"
        )

        findViewById<TextView>(R.id.tvSurahTitle).text = surahInfo.nameArabic
        findViewById<TextView>(R.id.tvSurahOrnamentalName).text = surahInfo.nameArabic
        findViewById<TextView>(R.id.tvSurahMeta).text =
            "${surahInfo.revelationPlace} • ${surahInfo.ayahCount} آيات"

        if (surahNumber == 9) {
            findViewById<View>(R.id.basmalaContainer).visibility = View.GONE
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<ImageButton>(R.id.btnAutoScroll).setOnClickListener {
            if (isAutoScrolling) {
                stopAutoScroll()
                updateAutoScrollButton(false)
            } else {
                showAutoScrollDialog()
            }
        }

        isPageMode = settingsPrefs.getBoolean("page_view_mode", false)
        pageModeRiwaya = QuranDataLoader.getQiraat(this)
        val (fp, lp) = QuranPageData.findSurahPageRange(pageModeRiwaya, surahNumber, this)
        firstPage = fp
        lastPage = lp
        viewPager = findViewById(R.id.pageViewPager)

        findViewById<ImageButton>(R.id.btnViewMode).visibility = View.VISIBLE
        findViewById<ImageButton>(R.id.btnViewMode).setOnClickListener {
            toggleViewMode()
        }
        if (isPageMode) {
            showPageMode()
        }

        scrollView = findViewById(R.id.scrollView)
        scrollView.setOnTouchListener { _, event ->
            if (isAutoScrolling && event.action == MotionEvent.ACTION_MOVE) {
                stopAutoScroll()
            }
            false
        }
        containerAyahs = findViewById(R.id.containerAyahs)
        isDark = isDarkMode()
        ayahRowMap = mutableMapOf()
        renderAyahsInSingleCard(containerAyahs, ayahs, isDark)

        if (!isKhatmaMode && surahNumber < 114) {
            addNextSurahButton()
        }

        if (isKhatmaMode) {
            val endIndex = intent.getIntExtra("KHATMA_END_AYAH_INDEX", -1)
            if (endIndex > 0 && endIndex < ayahRowMap.size - 1) {
                khatmaEndAyahIndex = endIndex
                hideAyahsAfter(endIndex)
                showContinueReadingButton()
            }
        }

        if (lastAyah > 0) {
            scrollView.post {
                scrollToAyah(lastAyah)
            }
        } else {
            val savedPos = ReadingTracker.getPosition(this)
            if (savedPos != null && savedPos.surahNumber == surahNumber) {
                scrollView.post {
                    scrollToAyah(savedPos.ayahNumber)
                }
            }
        }

        setupJuzHizbToast()

        findViewById<ImageButton>(R.id.btnJumpToAyah).setOnClickListener {
            showJumpToAyahDialog()
        }

        findViewById<ImageButton>(R.id.btnBookmarks).setOnClickListener {
            if (highlightedAyahs.isNotEmpty()) {
                showAddBookmarkDialog(highlightedAyahs.first())
            } else {
                val intent = Intent(this, BookmarksActivity::class.java)
                startActivity(intent)
            }
        }

        // Share feature removed
    }

    private fun toggleViewMode() {
        isPageMode = !isPageMode
        settingsPrefs.edit().putBoolean("page_view_mode", isPageMode).apply()
        if (isPageMode) {
            showPageMode()
        } else {
            showScrollMode()
        }
    }

    private fun showPageMode() {
        scrollView.visibility = View.GONE
        viewPager?.visibility = View.VISIBLE
        val adapter = viewPager?.adapter
        if (adapter == null) {
            viewPager?.adapter = object : FragmentStateAdapter(this) {
                override fun getItemCount(): Int = lastPage - firstPage + 1
                override fun createFragment(position: Int): Fragment {
                    val pageNum = firstPage + position
                    return PageViewFragment.newInstance(pageNum, pageModeRiwaya)
                }
            }
        }
        viewPager?.setCurrentItem(0, false)
    }

    private fun showScrollMode() {
        scrollView.visibility = View.VISIBLE
        viewPager?.visibility = View.GONE
    }

    override fun onPause() {
        super.onPause()
        stopAutoScroll()
        if (isKhatmaMode) return

        val quranPrefs = getSharedPreferences("urwah_quran", Context.MODE_PRIVATE)
        val singleLineMode = quranPrefs.getBoolean("ayah_single_line", true)

        if (singleLineMode) {
            val ayahViews = containerAyahs.findAyahViews()
            val scrollY = scrollView.scrollY
            var closestAyah = 1
            for (v in ayahViews) {
                val top = v.top
                if (top >= scrollY) {
                    closestAyah = (v.tag as? Int) ?: 1
                    break
                }
            }
            ReadingTracker.savePosition(this, surahInfo.number, closestAyah)
        } else {
            continuousViewRef?.let { tv ->
                val scrollY = scrollView.scrollY
                val tvTop = tv.top
                val visibleY = (scrollY - tvTop).coerceAtLeast(0)
                val layout = tv.layout
                if (layout != null && visibleY < layout.height) {
                    val line = layout.getLineForVertical(visibleY)
                    val offset = layout.getLineStart(line)
                    val idx = continuousAyahOffsets?.indexOfFirst { (s, e) -> offset >= s && offset < e } ?: -1
                    val closestAyah = if (idx >= 0) ayahs[idx].number else 1
                    ReadingTracker.savePosition(this, surahInfo.number, closestAyah)
                }
            }
        }
    }

    private fun isDarkMode(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun buildSurahDisplayName(surahName: String): String {
        return "سُورَةُ $surahName"
    }

    private fun renderAyahsInSingleCard(container: LinearLayout, ayahs: List<AyahData>, isDark: Boolean) {
        container.removeAllViews()
        val uthmanicTypeface = ResourcesCompat.getFont(this, QuranDataLoader.getUthmanicFontRes(this))
        val ayahColor = if (isDark) Color.parseColor("#e8e0d6") else Color.parseColor("#5E4B40")
        val dividerColor = Color.parseColor("#1A8B6F5E")
        val highlightColor = if (isDark) Color.parseColor("#338B6F5E") else Color.parseColor("#1A8B6F5E")

        val quranPrefs = getSharedPreferences("urwah_quran", Context.MODE_PRIVATE)
        val singleLineMode = quranPrefs.getBoolean("ayah_single_line", true)

        if (singleLineMode) {
            ayahs.forEachIndexed { index, ayah ->
                val row = LinearLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    tag = ayah.number
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        if (highlightedAyahs.contains(ayah.number)) {
                            clearHighlights()
                        }
                    }
                    setOnLongClickListener {
                        selectAyah(ayah.number, highlightColor)
                        true
                    }
                }

                val tvAyah = TextView(this).apply {
                    typeface = uthmanicTypeface
                    textSize = 29f
                    setTextColor(ayahColor)
                    textDirection = View.TEXT_DIRECTION_RTL
                    gravity = Gravity.START
                    setLineSpacing(4f, 1f)
                    letterSpacing = 0f
                    includeFontPadding = true
                    if (Build.VERSION.SDK_INT >= 26) {
                        justificationMode = 1
                    }
                    text = "${ayah.text} ${toHindiDigits(ayah.number)}"
                    setTextIsSelectable(false)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(tvAyah)

                container.addView(row)
                ayahRowMap[ayah.number] = row

                if (index != ayahs.lastIndex) {
                    val divider = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(0.8f)
                        ).apply {
                            topMargin = dpToPx(14f)
                            bottomMargin = dpToPx(14f)
                        }
                        setBackgroundColor(dividerColor)
                    }
                    container.addView(divider)
                }
            }
        } else {
            val sb = SpannableStringBuilder()
            val ayahOffsets = mutableListOf<Pair<Int, Int>>()
            ayahs.forEachIndexed { _, ayah ->
                val start = sb.length
                sb.append("${ayah.text} ${toHindiDigits(ayah.number)}")
                uthmanicTypeface?.let {
                    sb.setSpan(CustomTypefaceSpan(it), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                sb.append("  ")
                ayahOffsets.add(start to sb.length)
            }
            val rawSb = sb  // keep reference for highlight manipulation
            val continuousView = TextView(this).apply {
                text = sb
                tag = sb  // store original for clearHighlights
                typeface = uthmanicTypeface
                textSize = 29f
                setTextColor(ayahColor)
                textDirection = View.TEXT_DIRECTION_RTL
                gravity = Gravity.START
                setLineSpacing(4f, 1f)
                letterSpacing = 0f
                includeFontPadding = true
                if (Build.VERSION.SDK_INT >= 26) {
                    justificationMode = 1
                }
                setTextIsSelectable(false)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                isClickable = true
                isFocusable = true
                setOnClickListener { clearHighlights() }
            }

            var touchX = 0f
            var touchY = 0f
            continuousView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    touchX = event.x
                    touchY = event.y
                }
                false
            }
            continuousView.setOnLongClickListener {
                val layout = continuousView.layout ?: return@setOnLongClickListener true
                val line = layout.getLineForVertical(touchY.toInt())
                val offset = layout.getOffsetForHorizontal(line, touchX)
                val idx = ayahOffsets.indexOfFirst { (s, e) -> offset >= s && offset < e }
                val ayah = if (idx >= 0) ayahs[idx] else null
                if (ayah == null) return@setOnLongClickListener true

                clearHighlights()
                val copy = SpannableStringBuilder(rawSb)
                val (s, e) = ayahOffsets[idx]
                copy.setSpan(BackgroundColorSpan(highlightColor), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                continuousView.text = copy
                highlightedAyahs.add(ayah.number)
                selectedAyahNumber = ayah.number

                showActionPopup(continuousView)
                true
            }
            container.addView(continuousView)
            continuousViewRef = continuousView
            continuousAyahOffsets = ayahOffsets
        }
    }

    private fun selectAyah(ayahNumber: Int, highlightColor: Int) {
        clearHighlights()
        val row = ayahRowMap[ayahNumber] ?: return
        highlightedAyahs.add(ayahNumber)
        row.setBackgroundColor(highlightColor)
        showActionPopup(row)
    }

    private fun clearHighlights() {
        for (num in highlightedAyahs.toSet()) {
            ayahRowMap[num]?.setBackgroundColor(Color.TRANSPARENT)
        }
        highlightedAyahs.clear()
        selectedAyahNumber = -1
        actionPopup?.dismiss()
        actionPopup = null
        continuousViewRef?.let { tv ->
            val original = tv.tag as? SpannableStringBuilder
            if (original != null) tv.text = original
        }
    }

    private fun showActionPopup(anchor: View) {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.popup_ayah_action, null)
        actionPopup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        actionPopup?.isOutsideTouchable = true

        popupView.findViewById<View>(R.id.btnAddBookmark).setOnClickListener {
            val ayahNum = selectedAyahNumber
            if (ayahNum <= 0) return@setOnClickListener
            actionPopup?.dismiss()
            actionPopup = null
            clearHighlights()
            showAddBookmarkDialog(ayahNum)
        }

        popupView.findViewById<View>(R.id.btnClearHighlight).setOnClickListener {
            clearHighlights()
        }

        anchor.post {
            actionPopup?.showAtLocation(anchor, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, anchor.top + dpToPx(60f))
        }
    }

    private fun showAddBookmarkDialog(ayahNumber: Int) {
        val ayah = ayahs.find { it.number == ayahNumber } ?: return
        val allBookmarks = BookmarkManager.getAll(this)

        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_bookmark, null)
        builder.setView(view)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val rvExisting = view.findViewById<RecyclerView>(R.id.rvExistingBookmarks)
        val etName = view.findViewById<EditText>(R.id.etBookmarkName)
        val colorPalette = view.findViewById<LinearLayout>(R.id.colorPalette)
        val btnSave = view.findViewById<Button>(R.id.btnSaveBookmark)
        val btnCancel = view.findViewById<Button>(R.id.btnCancelBookmark)
        val tvExistingLabel = view.findViewById<TextView>(R.id.tvExistingLabel)
        val dividerExisting = view.findViewById<View>(R.id.dividerExisting)

        val ayahHint = toHindiDigits(ayahNumber)
        etName.hint = "اسم العلامة (اختياري) - الآية $ayahHint"

        if (allBookmarks.isEmpty()) {
            tvExistingLabel.visibility = View.GONE
            rvExisting.visibility = View.GONE
            dividerExisting.visibility = View.GONE
        } else {
            tvExistingLabel.visibility = View.VISIBLE
            rvExisting.visibility = View.VISIBLE
            dividerExisting.visibility = View.VISIBLE
            rvExisting.layoutManager = LinearLayoutManager(this)
            rvExisting.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount() = allBookmarks.size
                override fun onCreateViewHolder(p: ViewGroup, vt: Int) = object : RecyclerView.ViewHolder(
                    LayoutInflater.from(p.context).inflate(android.R.layout.simple_list_item_1, p, false)
                ) {}
                override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
                    val bm = allBookmarks[pos]
                    (h.itemView as TextView).apply {
                        val prefix = if (bm.surahNumber == surahInfo.number && bm.ayahNumber == ayahNumber) "✓ " else ""
                        text = "$prefix${bm.name} - ${bm.surahName} (${toHindiDigits(bm.ayahNumber)})"
                        setTextColor(if (isDark) Color.parseColor("#e8e0d6") else Color.parseColor("#5E4B40"))
                        textSize = 14f
                        setPadding(dpToPx(8f), dpToPx(10f), dpToPx(8f), dpToPx(10f))
                        setOnClickListener {
                            BookmarkManager.update(this@SurahDetailActivity, bm.name, surahInfo.number, ayahNumber, surahInfo.nameArabic, ayah.text)
                            clearHighlights()
                            dialog.dismiss()
                            Toast.makeText(this@SurahDetailActivity, "تم تحديث ${bm.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        colorPalette.removeAllViews()
        BookmarkManager.COLORS.forEachIndexed { idx, color ->
            val container = LinearLayout(this).apply {
                val size = dpToPx(36f)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = dpToPx(10f)
                }
                gravity = Gravity.CENTER
                setOnClickListener {
                    selectedColor = color
                    updateColorSelection(colorPalette, idx)
                }
            }
            val circle = View(this).apply {
                val innerSize = dpToPx(28f)
                layoutParams = LinearLayout.LayoutParams(innerSize, innerSize)
                setBackgroundColor(color)
            }
            container.addView(circle)
            colorPalette.addView(container)
        }
        selectedColor = BookmarkManager.COLORS[0]
        updateColorSelection(colorPalette, 0)

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val finalName = if (name.isEmpty()) {
                "الآية $ayahHint - ${surahInfo.nameArabic}"
            } else name
            val surahNameDisplay = SurahDataProvider.allSurahs.find { it.number == surahInfo.number }?.name ?: ""
            BookmarkManager.add(this, SmartBookmark(
                name = finalName,
                color = selectedColor,
                surahNumber = surahInfo.number,
                ayahNumber = ayahNumber,
                surahName = surahNameDisplay,
                ayahText = ayah.text
            ))
            clearHighlights()
            dialog.dismiss()
            Toast.makeText(this, "تم حفظ العلامة \"$finalName\"", Toast.LENGTH_SHORT).show()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener { clearHighlights() }
        dialog.show()
    }

    private fun updateColorSelection(container: LinearLayout, selectedIndex: Int) {
        for (i in 0 until container.childCount) {
            val childContainer = container.getChildAt(i) as? LinearLayout ?: continue
            val isSelected = i == selectedIndex
            val pad = if (isSelected) dpToPx(4f) else 0
            childContainer.setPadding(pad, pad, pad, pad)
            if (isSelected) {
                childContainer.setBackgroundColor(BookmarkManager.COLORS.getOrElse(i) { BookmarkManager.COLORS[0] })
            } else {
                childContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        }
    }

    private fun hideAyahsAfter(endIndex: Int) {
        var visibleCount = 0
        for (i in 0 until containerAyahs.childCount) {
            val child = containerAyahs.getChildAt(i)
            if (child is LinearLayout && child.tag is Int) {
                visibleCount++
                if (visibleCount > endIndex + 1) {
                    child.visibility = View.GONE
                }
            }
        }
    }

    private fun showContinueReadingButton() {
        val btn = Button(this).apply {
            text = "متابعة القراءة"
            typeface = ResourcesCompat.getFont(this@SurahDetailActivity, R.font.alyamama)
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_primary_button)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48f)
            ).apply {
                topMargin = dpToPx(16f)
                marginStart = dpToPx(20f)
                marginEnd = dpToPx(20f)
            }
            setOnClickListener {
                revealAllAyahs()
                containerAyahs.removeView(this)
            }
        }
        containerAyahs.addView(btn)
    }

    private fun revealAllAyahs() {
        for (i in 0 until containerAyahs.childCount) {
            containerAyahs.getChildAt(i).visibility = View.VISIBLE
        }
        khatmaEndAyahIndex = -1
    }

    private fun LinearLayout.findAyahViews(): List<View> {
        val views = mutableListOf<View>()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is LinearLayout && child.tag is Int) {
                views.add(child)
            }
        }
        return views
    }

    private fun scrollToAyah(ayahNumber: Int) {
        if (continuousViewRef != null && continuousAyahOffsets != null) {
            scrollToAyahContinuous(ayahNumber)
            return
        }
        val target = containerAyahs.findAyahViews().find { it.tag == ayahNumber }
        if (target == null) {
            scheduleScrollToAyah(ayahNumber, 3)
            return
        }
        performScrollToView(target)
    }

    private fun scrollToAyahContinuous(ayahNumber: Int) {
        val tv = continuousViewRef ?: return
        val offsets = continuousAyahOffsets ?: return
        val idx = ayahs.indexOfFirst { it.number == ayahNumber }
        if (idx < 0 || idx >= offsets.size) return
        val (start, _) = offsets[idx]
        scrollView.post outer@{
            tv.post inner@{
                val layout = tv.layout ?: return@inner
                if (start >= layout.text.length) return@inner
                val line = layout.getLineForOffset(start)
                val targetY = tv.top + layout.getLineTop(line)
                val scrollContent = scrollView.getChildAt(0) ?: return@inner
                var p = tv.parent
                var offset = targetY
                while (p is View && p != scrollContent) {
                    offset += (p as View).top
                    p = (p as View).parent
                }
                scrollView.scrollTo(0, (offset - dpToPx(40f)).coerceAtLeast(0))
            }
        }
    }

    private fun scheduleScrollToAyah(ayahNumber: Int, retries: Int) {
        if (retries <= 0) return
        scrollView.postDelayed({
            if (continuousViewRef != null && continuousAyahOffsets != null) {
                scrollToAyahContinuous(ayahNumber)
                return@postDelayed
            }
            val target = containerAyahs.findAyahViews().find { it.tag == ayahNumber }
            if (target != null) {
                performScrollToView(target)
            } else {
                scheduleScrollToAyah(ayahNumber, retries - 1)
            }
        }, 100)
    }

    private fun performScrollToView(target: View) {
        val scrollContent = scrollView.getChildAt(0) ?: return
        fun accumulateParentOffsets(v: View): Int {
            var offset = v.top
            var p = v.parent
            while (p is View && p != scrollContent) {
                offset += (p as View).top
                p = (p as View).parent
            }
            return offset
        }
        val targetY = accumulateParentOffsets(target)
        scrollView.post {
            scrollView.scrollTo(0, (targetY - dpToPx(40f)).coerceAtLeast(0))
        }
    }

    private fun showJumpToAyahDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_jump_to_ayah, null)
        val etAyah = view.findViewById<EditText>(R.id.etAyahNumber)
        val tvRange = view.findViewById<TextView>(R.id.tvAyahRange)
        tvRange.text = "الآيات ١-${toHindiDigits(ayahs.size)}"
        etAyah.hint = "أدخل رقم الآية (١-${toHindiDigits(ayahs.size)})"

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<Button>(R.id.btnJumpGo).setOnClickListener {
            val num = etAyah.text.toString().toIntOrNull()
            if (num != null && num >= 1 && num <= ayahs.size) {
                scrollToAyah(num)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "رقم غير صحيح", Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<Button>(R.id.btnJumpCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun toHindiDigits(number: Int): String {
        val hindiDigits = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
        return number.toString().map { hindiDigits[it - '0'] }.joinToString("")
    }

    private fun dpToPx(dp: Float): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun showAutoScrollDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_auto_scroll, null)
        val slider = view.findViewById<SeekBar>(R.id.scrollSpeedSlider)
        val tvSpeed = view.findViewById<TextView>(R.id.tvScrollSpeedValue)

        val savedSpeed = quranPrefs.getInt("auto_scroll_speed", 40)
        slider.progress = savedSpeed
        tvSpeed.text = "${savedSpeed}%"

        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                tvSpeed.text = "${progress}%"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
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
                    Toast.makeText(this@SurahDetailActivity, "وصلت لنهاية السورة", Toast.LENGTH_SHORT).show()
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





    private fun addNextSurahButton() {
        val btn = Button(this).apply {
            text = "السورة التالية"
            compoundDrawablePadding = dpToPx(8f)
            setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_next_surah, 0)
            typeface = ResourcesCompat.getFont(this@SurahDetailActivity, R.font.alyamama)
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_button_next_surah)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48f)
            ).apply {
                topMargin = dpToPx(20f)
                marginStart = dpToPx(20f)
                marginEnd = dpToPx(20f)
            }
            setOnClickListener { navigateToNextSurah() }
        }
        containerAyahs.addView(btn)
    }

    private fun navigateToNextSurah() {
        if (isNavigating) return
        isNavigating = true
        val nextNum = surahNumber + 1
        val next = SurahDataProvider.allSurahs.find { it.number == nextNum } ?: return
        val intent = Intent(this, SurahDetailActivity::class.java).apply {
            putExtra("SURAH_NUMBER", next.number)
            putExtra("SURAH_NAME", next.name)
            putExtra("VERSE_COUNT", next.verseCount)
        }
        startActivity(intent)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("page_mode", isPageMode)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        isPageMode = savedInstanceState.getBoolean("page_mode", false)
    }

    private fun setupJuzHizbToast() {
        val qiraatData = QuranDataLoader.load(this)
        val allAyahs = qiraatData.entries
            .sortedBy { it.key }
            .flatMap { (_, s) -> s.ayahs.sortedBy { it.number } }

        toastHelper = JuzHizbToastHelper(this, allAyahs)

        scrollHandler = android.os.Handler(Looper.getMainLooper())

        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            scrollDebounce?.let { scrollHandler?.removeCallbacks(it) }
            scrollDebounce = Runnable {
                var visibleGlobalIdx = -1
                val singleLine = quranPrefs.getBoolean("ayah_single_line", true)
                if (singleLine) {
                    for (i in 0 until containerAyahs.childCount) {
                        val child = containerAyahs.getChildAt(i)
                        if (child.visibility != View.VISIBLE) continue
                        if (child.tag is Int && child.top >= scrollY) {
                            visibleGlobalIdx = allAyahs.indexOfFirst { it.surahNumber == surahNumber && it.number == child.tag as Int }
                            break
                        }
                    }
                } else {
                    val tv = continuousViewRef
                    if (tv != null) {
                        val layout = tv.layout ?: return@Runnable
                        val line = layout.getLineForVertical(scrollY - tv.top)
                        if (line >= 0) {
                            val offset = layout.getLineStart(line)
                            val offsets = continuousAyahOffsets ?: return@Runnable
                            val idx = offsets.indexOfLast { it.first <= offset }
                            if (idx >= 0) {
                                val ayah = ayahs.getOrNull(idx) ?: return@Runnable
                                visibleGlobalIdx = allAyahs.indexOfFirst { it.surahNumber == surahNumber && it.number == ayah.number }
                            }
                        }
                    }
                }
                if (visibleGlobalIdx >= 0) {
                    toastHelper?.onPositionReached(visibleGlobalIdx)
                }
            }
            scrollHandler?.postDelayed(scrollDebounce!!, 200L)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        toastHelper?.detach()
        toastHelper = null
        scrollHandler?.removeCallbacksAndMessages(null)
    }
}
