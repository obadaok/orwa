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
import android.text.Editable
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.urwah.dhikr.audio.AudioPlaybackState
import com.urwah.dhikr.audio.AudioPlayerService
import com.urwah.dhikr.audio.Reciter
import com.urwah.dhikr.audio.ReciterCatalog
import kotlinx.coroutines.launch
import kotlin.math.abs

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

    private val playerAutoHideHandler = Handler(Looper.getMainLooper())
    private val playerAutoHideRunnable = Runnable { hideAudioPlayer() }
    private val focusedAutoHideHandler = Handler(Looper.getMainLooper())
    private val focusedAutoHideRunnable = Runnable { hideFocusedTools() }
    private val focusedTools = mutableListOf<View>()
    private var playerUiVisible = false
    private var lastPlaybackActive = false

    private val audioPrefs by lazy {
        getSharedPreferences("urwah_audio", Context.MODE_PRIVATE)
    }
    private var currentReciterId: Int = 0
    private var playbackColorSpanActive = false
    private var lastPlaybackAyah = -1
    private var displayedRiwayatId: String = "hafs"
    private var lastSyncedReciterId: Int = -1
    private var userSeeking = false
    private var isFocusedMode = false
    private var lastFocusedAyah = -1
    private val readingPagers = HashMap<Int, com.urwah.dhikr.align.ReadingLinePager>()
    private var readingWindowKey: String? = null
    /** عرض البطاقة المتاح للنص (يُقاس مرة لكل تخطيط) لتكييف كلمات السطر. */
    private var readingCardTextWidth: Int = 0
    private var readingCardWaqfAware: Boolean? = null
    private var allAyahsGlobal: List<AyahData> = emptyList()
    private var juzAyahIndexes: Map<Int, Int> = emptyMap()

    companion object {
        private const val PLAYER_AUTO_HIDE_DELAY = 3000L
        private const val FOCUSED_AUTO_HIDE_DELAY = 10000L
        private const val REQUEST_RECITER_SELECT = 4101
        private const val REQUEST_MURATTAL_THEME = 4102
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
        displayedRiwayatId = QuranDataLoader.getQiraat(this)

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

        onBackPressedDispatcher.addCallback(this) {
            val circularMenu = findViewById<UrwahCircularMenu>(R.id.circularMenu)
            if (circularMenu.visibility == View.VISIBLE) {
                circularMenu.hide()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        setupCircularMenu()

        scrollView = findViewById(R.id.scrollView)
        scrollView.setOnTouchListener { _, event ->
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

        currentReciterId = audioPrefs.getInt("selected_reciter", 0)
        setupAudioPlayer()
        setupPlayWindowControls()

        // Share feature removed
    }

    override fun onResume() {
        super.onResume()
        ReadingTimeTracker.startSession(this, ReadingTimeTracker.TYPE_QURAN)
    }

    override fun onPause() {
        super.onPause()
        ReadingTimeTracker.stopSession(this)
        stopAutoScroll()
        if (isKhatmaMode) return

        val quranPrefs = getSharedPreferences("urwah_quran", Context.MODE_PRIVATE)
        val singleLineMode = quranPrefs.getBoolean("ayah_single_line", false)

        if (singleLineMode) {
            val ayahViews = containerAyahs.findAyahViews()
            val viewCenter = scrollView.scrollY + scrollView.height / 3
            var bestAyah = -1
            var bestDist = Int.MAX_VALUE
            for (v in ayahViews) {
                val ayahNum = v.tag as? Int ?: continue
                val childCenter = v.top + v.height / 2
                val dist = abs(childCenter - viewCenter)
                if (dist < bestDist) {
                    bestDist = dist
                    bestAyah = ayahNum
                }
            }
            if (bestAyah > 0) {
                ReadingTracker.savePosition(this, surahInfo.number, bestAyah)
            } else if (ayahs.isNotEmpty()) {
                ReadingTracker.savePosition(this, surahInfo.number, ayahs.last().number)
            }
        } else {
            continuousViewRef?.let { tv ->
                val scrollY = scrollView.scrollY
                // tv.top نسبي لـ containerAyahs فقط — يجب تجميع مواضع الآباء
                // (الترويسة والبسملة) حتى لا يُحفظ الموضع بعدّ أسطر زائدة.
                val tvTop = accumulateTop(tv)
                val visibleY = (scrollY - tvTop).coerceAtLeast(0)
                val layout = tv.layout
                val idx = if (layout != null && layout.height > 0) {
                    val clampedY = visibleY.coerceAtMost(layout.height - 1)
                    val line = layout.getLineForVertical(clampedY)
                    val offset = layout.getLineStart(line)
                    continuousAyahOffsets?.indexOfFirst { (s, e) -> offset >= s && offset < e } ?: -1
                } else -1
                val closestAyah = if (idx >= 0 && idx < ayahs.size) ayahs[idx].number
                                  else if (visibleY >= (tv.layout?.height ?: 0) && ayahs.isNotEmpty()) ayahs.last().number
                                  else -1
                if (closestAyah > 0) {
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
        val riwayatInfo = QuranDataLoader.getRiwayatInfo(QuranDataLoader.getQiraat(this))
        findViewById<TextView>(R.id.tvBasmala)?.apply {
            text = riwayatInfo.basmala
            typeface = uthmanicTypeface
        }
        val ayahColor = if (isDark) Color.parseColor("#e8e0d6") else Color.parseColor("#5E4B40")
        val dividerColor = Color.parseColor("#1A8B6F5E")
        val highlightColor = if (isDark) Color.parseColor("#338B6F5E") else Color.parseColor("#1A8B6F5E")

        val quranPrefs = getSharedPreferences("urwah_quran", Context.MODE_PRIVATE)
        val singleLineMode = quranPrefs.getBoolean("ayah_single_line", false)
        val alignment = quranPrefs.getInt("quran_alignment", 3)

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
                    applyAyahAlignment(this, alignment, continuous = false)
                    setLineSpacing(4f, 1f)
                    letterSpacing = 0f
                    includeFontPadding = true
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
                applyAyahAlignment(this, alignment, continuous = true)
                setLineSpacing(dpToPx(1f).toFloat(), 1f)
                letterSpacing = 0f
                includeFontPadding = true
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

    private fun clearHighlights() {
        for (num in highlightedAyahs.toSet()) {
            ayahRowMap[num]?.setBackgroundColor(Color.TRANSPARENT)
        }
        highlightedAyahs.clear()
        selectedAyahNumber = -1
        actionPopup?.dismiss()
        actionPopup = null
        val current = AudioPlaybackState.state.value
        if (playbackColorSpanActive && current.isActive && current.surahNumber == surahNumber &&
            (current.isPlaying || current.isBuffering)
        ) {
            applyPlaybackHighlight(current.currentAyah, scroll = false)
        } else {
            continuousViewRef?.let { tv ->
                val original = tv.tag as? SpannableStringBuilder
                if (original != null) tv.text = original
            }
            playbackColorSpanActive = false
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

        popupView.findViewById<View>(R.id.btnPlayFromHere).setOnClickListener {
            val ayahNum = selectedAyahNumber
            if (ayahNum <= 0) return@setOnClickListener
            actionPopup?.dismiss()
            actionPopup = null
            clearHighlights()
            startPlaybackFrom(currentReciterId, ayahNum)
            UrwahToast.show(this, "تشغيل التلاوة من الآية ${toHindiDigits(ayahNum)}")
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
        smoothScrollToCentered(accumulateTop(target), target.height)
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
                val targetY = accumulateTop(tv) + layout.getLineTop(line)
                val lineHeight = layout.getLineBottom(line) - layout.getLineTop(line)
                smoothScrollToCentered(targetY, lineHeight)
            }
        }
    }

    private fun accumulateTop(v: View): Int {
        val scrollContent = scrollView.getChildAt(0) ?: return v.top
        var offset = v.top
        var p = v.parent
        while (p is View && p != scrollContent) {
            offset += (p as View).top
            p = (p as View).parent
        }
        return offset
    }

    /**
     * ينقل الشاشة بسلاسة بحيث تكون الآية في منتصف الشاشة، مع مراعاة
     * نهاية المحتوى والمشغل السفلي (المهمة 121 + 122).
     */
    private fun smoothScrollToCentered(ayahTop: Int, ayahHeight: Int) {
        val content = scrollView.getChildAt(0) ?: return
        val viewHeight = scrollView.height
        val maxScroll = (content.height - viewHeight).coerceAtLeast(0)

        val playerBar = findViewById<View>(R.id.audioPlayerBar)
        val playerHeight = if (playerBar.visibility == View.VISIBLE && playerBar.height > 0) {
            playerBar.height
        } else {
            0
        }
        val safeBottom = dpToPx(20f)

        val effectiveHeight = viewHeight - playerHeight - safeBottom
        val centered = ayahTop - (effectiveHeight - ayahHeight) / 2
        val minScroll = (ayahTop + ayahHeight - effectiveHeight).coerceAtLeast(0)
        val target = centered.coerceAtLeast(minScroll).coerceIn(0, maxScroll)

        animateScrollTo(target)
    }

    private var scrollAnimator: android.animation.ValueAnimator? = null

    private fun animateScrollTo(targetY: Int) {
        scrollAnimator?.cancel()
        val startY = scrollView.scrollY
        if (Math.abs(targetY - startY) < 2) {
            scrollView.scrollTo(0, targetY)
            return
        }
        val animator = android.animation.ValueAnimator.ofInt(startY, targetY).apply {
            duration = 320L
            interpolator = android.view.animation.DecelerateInterpolator(1.6f)
            addUpdateListener {
                scrollView.scrollTo(0, it.animatedValue as Int)
            }
        }
        scrollAnimator = animator
        animator.start()
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
        smoothScrollToCentered(accumulateTop(target), target.height)
    }

    private fun showJumpToAyahDialog() {
        showJumpToAyahDialog { ayahNum ->
            scrollToAyah(ayahNum)
        }
    }

    private fun toHindiDigits(number: Int): String {
        val hindiDigits = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
        return number.toString().map { if (it.isDigit()) hindiDigits[it - '0'] else it }.joinToString("")
    }

    private fun dpToPx(dp: Float): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun withAlphaOf(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

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
            private var lastFollowedAyah = -1
            private val generation = autoScrollGeneration

            override fun run() {
                if (!isAutoScrolling || autoScrollGeneration != generation) return
                val now = System.nanoTime()
                val delta = ((now - lastTime) / 1_000_000f).coerceIn(1f, 50f)
                lastTime = now

                accumulator += autoScrollPixelsPerSecond * delta / 1000f
                val step = accumulator.toInt()
                accumulator -= step

                val content = scrollView.getChildAt(0) ?: return
                val maxScroll = (content.height - scrollView.height).coerceAtLeast(0)

                val playingAyah = AudioPlaybackState.state.value.currentAyah.takeIf {
                    AudioPlaybackState.state.value.isActive &&
                        AudioPlaybackState.state.value.isPlaying &&
                        AudioPlaybackState.state.value.surahNumber == surahNumber
                } ?: -1

                if (playingAyah > 0 && playingAyah != lastFollowedAyah) {
                    lastFollowedAyah = playingAyah
                    scrollToAyah(playingAyah)
                    scrollView.postOnAnimation(this)
                    return
                }

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
        if (isPlaying) {
            UrwahToast.show(this, "التشغيل الذاتي بدأ")
        }
    }

    private fun setupCircularMenu() {
        val circularMenu = findViewById<UrwahCircularMenu>(R.id.circularMenu)
        circularMenu.onMenuDismissed = {
            circularMenu.visibility = View.GONE
        }
        findViewById<ImageButton>(R.id.btnCircularMenu).setOnClickListener {
            if (circularMenu.visibility == View.VISIBLE) {
                circularMenu.hide()
            } else {
                setupCircularMenuItems()
                circularMenu.visibility = View.VISIBLE
                circularMenu.bringToFront()
                circularMenu.show()
            }
        }
    }

    private fun setupCircularMenuItems() {
        val circularMenu = findViewById<UrwahCircularMenu>(R.id.circularMenu)
        circularMenu.clearMenuItems()

        circularMenu.addMenuItem(R.drawable.ic_search, "بحث") {
            showSurahSearchDialog()
        }

        circularMenu.addMenuItem(R.drawable.ic_go_to_page, "آية") {
            showJumpToAyahDialog()
        }

        circularMenu.addMenuItem(R.drawable.ic_bookmark, "علامة") {
            if (highlightedAyahs.isNotEmpty()) {
                showAddBookmarkDialog(highlightedAyahs.first())
            } else {
                startActivity(Intent(this, BookmarksActivity::class.java))
            }
        }

        circularMenu.addMenuItem(R.drawable.ic_scroll_play, "تمرير تلقائي") {
            if (isAutoScrolling) {
                stopAutoScroll()
            } else {
                showAutoScrollDialog()
            }
        }

        circularMenu.addMenuItem(R.drawable.ic_play_arrow, "تلاوة") {
            startPlaybackFrom(currentReciterId, 1)
        }

        circularMenu.addMenuItem(R.drawable.ic_book_quran_24dp, getString(R.string.focused_mode_short)) {
            enterFocusedMode()
        }
    }

    private fun showSurahSearchDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_surah_search, null)
        val etSearch = view.findViewById<EditText>(R.id.etSurahSearch)
        val rvResults = view.findViewById<RecyclerView>(R.id.rvSurahSearchResults)
        val tvEmpty = view.findViewById<TextView>(R.id.tvSurahSearchEmpty)
        rvResults.layoutManager = LinearLayoutManager(this)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // فهرس مطابقة يُبنى مرة واحدة بفتح النافذة: النص العربي مُطبَّع (بلا
        // تشكيل/تنوين/تطويل/همزات/ألف مقصورة/تاء مربوطة) في طبقة البحث فقط
        // دون تغيير النص القرآني المعروض. هذا يحل أن «الحرف الثاني يُفقد
        // النتائج» لأن البحث الجديد لم يعد يعتمد على تطابق سلسلة مع تشكيل.
        val indexed = ayahs.map { it to normalizeArabicForSearch(it.text) }

        // Debounce على مدخل المستخدم: كل Kern-Knockback ينفّذ بحثًا واحدًا بأحدث
        // قيمة كاملة للنص، ولا توجد نتيجة سابقة تصل متأخرة (بحث متزامن على فهرس
        // السورة) فلا Race Condition بين الطلبات.
        val searchHandler = Handler(Looper.getMainLooper())
        val debounce = Runnable {
            val query = etSearch.text?.toString() ?: return@Runnable
            val q = normalizeArabicForSearch(query.trim())
            val results = if (q.isEmpty()) {
                emptyList<AyahData>()
            } else {
                indexed.filter { it.second.contains(q) }.map { it.first }
            }
            tvEmpty.visibility =
                if (q.isNotEmpty() && results.isEmpty()) View.VISIBLE else View.GONE
            rvResults.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount() = results.size
                override fun onCreateViewHolder(p: ViewGroup, vt: Int) = object : RecyclerView.ViewHolder(
                    LayoutInflater.from(p.context).inflate(R.layout.item_search_result, p, false)
                ) {}
                override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
                    val ayah = results[pos]
                    val pageNum = h.itemView.findViewById<TextView>(R.id.tvResultPageNum)
                    pageNum.text = "الآية ${toHindiDigits(ayah.number)}"
                    val snippet = h.itemView.findViewById<TextView>(R.id.tvResultSnippet)
                    snippet.typeface = ResourcesCompat.getFont(this@SurahDetailActivity, QuranDataLoader.getUthmanicFontRes(this@SurahDetailActivity))
                    snippet.textSize = 18f
                    snippet.text = ayah.text
                    h.itemView.setOnClickListener {
                        dialog.dismiss()
                        scrollToAyah(ayah.number)
                        UrwahToast.show(this@SurahDetailActivity, "الآية ${toHindiDigits(ayah.number)}")
                    }
                }
            }
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchHandler.removeCallbacks(debounce)
                searchHandler.postDelayed(debounce, 160L)
            }
        })

        view.findViewById<Button>(R.id.btnSurahSearchClose).setOnClickListener {
            searchHandler.removeCallbacks(debounce)
            dialog.dismiss()
        }
        dialog.setOnDismissListener { searchHandler.removeCallbacks(debounce) }
        etSearch.post { etSearch.requestFocus() }
        dialog.show()
    }

    /**
     * تطبيع النص العربي لطبقة البحث فقط: يزيل التشكيل/التنوين/التطويل وصغائر
     * الكرسي، ويوحّد الألفات/الهمزات والألف المقصورة والياء والتاء المربوطة،
     * كي يجد «الرحمن» الآية التي نصها «ٱلرَّحۡمَٰنِ» دون تغيير النص المعروض.
     */
    private fun normalizeArabicForSearch(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            val code = c.code
            sb.append(when {
                code in 0x064B..0x065F || code == 0x0670 || code == 0x0640 -> ""      // تشكيل + تطويل
                code in 0x06D6..0x06ED || code in 0x08F0..0x08FF -> ""               // ملحقات التشكيل
                c == '\u0622' || c == '\u0623' || c == '\u0625' || c == '\u0671' -> '\u0627' // ألف ممدودة/همزة/وصلة
                c == '\u0621' -> '\u0627'                                            // همزة على السطر
                c == '\u0624' -> '\u0648'                                            // واو همزة
                c == '\u0626' -> '\u064A'                                            // ياء همزة
                c == '\u0649' -> '\u064A'                                            // ألف مقصورة
                c == '\u0629' -> '\u0647'                                            // تاء مربوطة
                else -> c
            })
        }
        return sb.toString()
    }





    private fun addNextSurahButton() {
        val btn = Button(this).apply {
            text = "السورة التالية"
            contentDescription = "الانتقال إلى السورة التالية"
            typeface = ResourcesCompat.getFont(this@SurahDetailActivity, R.font.alyamama)
            textSize = 15f
            setTextColor(Color.WHITE)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_next_surah, 0, 0, 0)
            compoundDrawablePadding = dpToPx(8f)
            // في RTL يظهر الأيقونة على يمين النص طبيعياً
            compoundDrawablesRelative.firstOrNull()?.setTint(
                ContextCompat.getColor(this@SurahDetailActivity, android.R.color.white)
            )
            setBackgroundResource(R.drawable.bg_button_next_surah)
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(48f)
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                topMargin = dpToPx(24f)
                marginStart = dpToPx(20f)
                marginEnd = dpToPx(20f)
                // padding أفقي داخلي عبر minimumWidth
            }
            minWidth = dpToPx(180f)
            setPadding(dpToPx(24f), 0, dpToPx(24f), 0)
            // ظهور لطيف: انزلاق لأعلى مع شفافية
            alpha = 0f
            translationY = dpToPx(24f).toFloat()
            animate().alpha(1f).translationY(0f).setDuration(350L)
                .setStartDelay(150L)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
                .start()
            // استجابة لمسية خفيفة بدون مبالغة
            setOnTouchListener { v, ev ->
                when (ev.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN ->
                        v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90L).start()
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL ->
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
                }
                false
            }
            setOnClickListener { navigateToNextSurah() }
        }
        containerAyahs.addView(btn)
    }

    private fun navigateToNextSurah() {
        if (isNavigating) return
        isNavigating = true
        val nextNum = surahNumber + 1
        val next = SurahDataProvider.allSurahs.find { it.number == nextNum } ?: run {
            isNavigating = false
            return
        }
        val intent = Intent(this, SurahDetailActivity::class.java).apply {
            putExtra("SURAH_NUMBER", next.number)
            putExtra("SURAH_NAME", next.name)
            putExtra("VERSE_COUNT", next.verseCount)
            putExtra("NAVIGATING_TO_NEXT", true)
        }
        startActivity(intent)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.slide_in_up, R.anim.fade_out)
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
                val singleLine = quranPrefs.getBoolean("ayah_single_line", false)
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
        scrollAnimator?.cancel()
        toastHelper?.detach()
        toastHelper = null
        scrollHandler?.removeCallbacksAndMessages(null)
        playerAutoHideHandler.removeCallbacksAndMessages(null)
        focusedAutoHideHandler.removeCallbacksAndMessages(null)
        if (isFinishing && !isChangingConfigurations) {
            val st = AudioPlaybackState.state.value
            if (st.isActive && st.surahNumber == surahNumber) {
                val isNavigatingToNext = intent.getBooleanExtra("NAVIGATING_TO_NEXT", false)
                if (!isNavigatingToNext) {
                    AudioPlayerService.stop(this)
                }
            }
        }
        isFocusedMode = false
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (isFocusedMode) return super.dispatchTouchEvent(ev)
        if (ev.action == android.view.MotionEvent.ACTION_DOWN) {
            val playWindowRoot = findViewById<View>(R.id.playWindowRoot)
            if (playWindowRoot.visibility != View.VISIBLE) {
                val bar = findViewById<View>(R.id.audioPlayerBar)
                if (bar.visibility == View.VISIBLE) {
                    schedulePlayerAutoHide()
                } else if (AudioPlaybackState.state.value.isActive) {
                    showAudioPlayer()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun enterFocusedMode() {
        val overlay = findViewById<View>(R.id.focusedRecitationOverlay)
        if (overlay.visibility == View.VISIBLE) {
            exitFocusedMode()
            return
        }
        isFocusedMode = true
        lastFocusedAyah = -1

        buildGlobalAyahIndex()
        setupFocusedControls()
        applyMurattalTheme(MurattalThemeManager.current(this))

        overlay.alpha = 0f
        overlay.visibility = View.VISIBLE
        overlay.bringToFront()
        overlay.animate().alpha(1f).setDuration(250).start()

        focusedTools.clear()
        focusedTools.add(findViewById<View>(R.id.topBarLayout))
        focusedTools.add(findViewById<View>(R.id.focusedInfoCard))
        focusedTools.add(findViewById<View>(R.id.audioPlayerBar))
        showFocusedTools()

        val st = AudioPlaybackState.state.value
        if (st.isActive && st.surahNumber == surahNumber) {
            updateFocusedAyah(st.currentAyah)
            updateFocusedReciter(st.reciterId)
            updateFocusedReadingWindow(st.currentAyah, st.positionMs, st.durationMs)
        } else {
            UrwahToast.show(this, getString(R.string.focused_mode_enter_hint))
            // معاينة سطر القراءة لأول آية حتى لا تبقى البطاقة فارغة قبل التشغيل
            val first = ayahs.firstOrNull()
            if (first != null) {
                updateFocusedAyah(first.number)
                updateFocusedReadingWindow(first.number, 0L, 0L)
                updateFocusedReciter(currentReciterId)
            }
        }

        var downX = 0f
        var downY = 0f
        var moving = false
        val overlayTouch = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    moving = false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (dx * dx + dy * dy > dpToPx(12f) * dpToPx(12f)) moving = true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (!moving) handleFocusedOverlayTouch()
                }
            }
            false
        }
        overlay.setOnTouchListener(overlayTouch)
        val ayahArea = findViewById<View>(R.id.focusedAyahArea)
        if (ayahArea != null) {
            ayahArea.setOnTouchListener(overlayTouch)
        }
        scheduleFocusedAutoHide()
    }

    private fun hideFocusedTools() {
        val wasVisible = focusedTools.any { it.visibility == View.VISIBLE && it.alpha >= 1f }
        if (!wasVisible) return
        focusedTools.forEach { tool ->
            tool.animate().cancel()
            tool.visibility = View.VISIBLE
            tool.animate().alpha(0f).setDuration(200).withEndAction {
                tool.visibility = View.GONE
            }.start()
        }
        adjustFocusedAyahBottomPadding(restore = true)
        playerUiVisible = false
    }

    /**
     * يضمن أن شريط المشغّل العائم أسفل صفحة المرتّل لا يحجب نهاية النص القرآني:
     * حين يكون المشغّل ظاهرًا تُزاد حشوة سفلية لمنطقة الآية بقدر ارتفاعه،
     * فيكتمل التمرير حتى آخر سطر، وحين يختفي تُعاد الحشوة.
     */
    private fun adjustFocusedAyahBottomPadding(restore: Boolean) {
        val area = findViewById<View>(R.id.focusedAyahArea) ?: return
        val base = dpToPx(10f)
        if (restore) {
            area.setPadding(dpToPx(16f), dpToPx(10f), dpToPx(16f), base)
            return
        }
        val bar = findViewById<View>(R.id.audioPlayerBar)
        val extra = if (bar.visibility == View.VISIBLE) {
            (bar.measuredHeight.coerceAtLeast(dpToPx(96f))) + dpToPx(14f)
        } else {
            dpToPx(14f)
        }
        area.setPadding(dpToPx(16f), dpToPx(10f), dpToPx(16f), base + extra)
    }

    private fun showFocusedTools() {
        focusedTools.forEach { tool ->
            tool.animate().cancel()
            tool.visibility = View.VISIBLE
            tool.animate().alpha(1f).setDuration(200).start()
        }
        val playerBar = findViewById<View>(R.id.audioPlayerBar)
        playerBar.bringToFront()
        playerBar.post { adjustFocusedAyahBottomPadding(restore = false) }
        playerUiVisible = true
    }

    private fun scheduleFocusedAutoHide() {
        focusedAutoHideHandler.removeCallbacks(focusedAutoHideRunnable)
        focusedAutoHideHandler.postDelayed(focusedAutoHideRunnable, FOCUSED_AUTO_HIDE_DELAY)
    }

    private fun handleFocusedOverlayTouch() {
        val topBar = focusedTools.firstOrNull { it.id == R.id.topBarLayout } ?: return
        val visible = topBar.visibility == View.VISIBLE && topBar.alpha >= 1f
        if (visible) {
            hideFocusedTools()
        } else {
            showFocusedTools()
        }
        scheduleFocusedAutoHide()
    }

    private fun exitFocusedMode() {
        val overlay = findViewById<View>(R.id.focusedRecitationOverlay)
        if (overlay.visibility != View.VISIBLE) return
        isFocusedMode = false
        focusedAutoHideHandler.removeCallbacksAndMessages(null)
        findViewById<TextView>(R.id.tvPlayerTitle).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvPlayerMeta).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvPlayerAvatar).visibility = View.VISIBLE
        findViewById<View>(R.id.btnPlayerClose).visibility = View.VISIBLE
        // استعادة خلفية شريط المشغّل الافتراضية (كانت مُعاد تنسيقها للمرتّل)
        findViewById<View>(R.id.audioPlayerBar).background =
            androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_audio_player)
        overlay.animate().alpha(0f).setDuration(200).withEndAction {
            overlay.visibility = View.GONE
            val st = AudioPlaybackState.state.value
            if (st.isActive && (st.isPlaying || st.isBuffering)) {
                showAudioPlayer()
            } else {
                hideAudioPlayer()
            }
        }.start()
    }

    private fun buildGlobalAyahIndex() {
        val all = QuranDataLoader.load(this)
        val sorted = all.entries
            .sortedBy { it.key }
            .flatMap { (_, s) -> s.ayahs.sortedBy { it.number } }
        allAyahsGlobal = sorted

        val juzIndexes = mutableMapOf<Int, Int>()
        for (juz in JuzData.JUZ_BOUNDARIES) {
            val idx = sorted.indexOfFirst {
                it.surahNumber == juz.startSurah && it.number == juz.startAyah
            }
            if (idx >= 0) juzIndexes[juz.juzNumber] = idx
        }
        juzAyahIndexes = juzIndexes
    }

    private fun currentJuzHizb(surahNum: Int, ayahNum: Int): String {
        val idx = allAyahsGlobal.indexOfFirst {
            it.surahNumber == surahNum && it.number == ayahNum
        }
        if (idx < 0) return ""
        var juz = 1
        for ((j, startIdx) in juzAyahIndexes) {
            if (startIdx <= idx && j > juz) juz = j
        }
        val hizb = JuzData.getHizbNumberForAyah(allAyahsGlobal, idx)
        val hindi = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
        val h = { n: Int -> n.toString().map { if (it.isDigit()) hindi[it - '0'] else it }.joinToString("") }
        return "الجزء ${h(juz)} • الحزب ${h(hizb)}"
    }

    private fun setupFocusedControls() {
        findViewById<View>(R.id.btnFocusedClose).setOnClickListener { exitFocusedMode() }
        findViewById<View>(R.id.btnFocusedMinimize).setOnClickListener { exitFocusedMode() }
        findViewById<View>(R.id.btnFocusedTheme).setOnClickListener { showMurattalThemeDialog() }
    }

    private fun applyMurattalTheme(theme: MurattalTheme) {
        val p = MurattalThemeManager.palette(this, theme)
        val overlay = findViewById<View>(R.id.focusedRecitationOverlay)
        overlay.setBackgroundColor(p.background)

        // — أسلوب حديث مسطّح: بلا ظلال صلبة (لا Neubrutalism) —

        // الشريط العلوي: شفاف يذوب في الخلفية
        findViewById<View>(R.id.topBarLayout).apply {
            background = null
            val lp = layoutParams
            if (lp is ViewGroup.MarginLayoutParams) {
                lp.setMargins(0, 0, 0, 0)
                layoutParams = lp
            }
        }
        // شريط المعلومات السفلي: شفاف أيضاً
        findViewById<View>(R.id.focusedInfoCard).background = null

        // بطاقة سطر القراءة: سطح ناعم بحدّ شعري من اللوحة
        findViewById<View>(R.id.focusedReadingCard).apply {
            background = MurattalThemeManager.flatCardDrawable(
                this@SurahDetailActivity, p, radiusDp = 22f, strokeDp = 1f,
                fill = p.surface,
                stroke = withAlphaOf(p.accent, 0x40)
            )
        }
        findViewById<TextView>(R.id.tvFocusedReadingCard).setTextColor(p.textPrimary)
        listOf(
            R.id.focusedReadingOrnamentStart,
            R.id.focusedReadingOrnamentEnd
        ).forEach { id ->
            findViewById<ImageView>(id)?.setColorFilter(p.accent)
        }

        findViewById<TextView>(R.id.tvFocusedSurahMini).setTextColor(p.textPrimary)
        findViewById<TextView>(R.id.tvFocusedAyahCounter).apply {
            background = null
            setTextColor(p.textSecondary)
            setPadding(0, 0, 0, 0)
        }
        findViewById<TextView>(R.id.tvFocusedJuzHizb).setTextColor(p.textSecondary)
        findViewById<TextView>(R.id.tvFocusedReciter).setTextColor(p.textSecondary)
        findViewById<TextView>(R.id.tvFocusedPercent).setTextColor(p.accent)

        listOf(
            R.id.btnFocusedClose,
            R.id.btnFocusedTheme,
            R.id.btnFocusedMinimize
        ).forEach { id ->
            (findViewById<View>(id) as? ImageButton)?.let {
                it.imageTintList = android.content.res.ColorStateList.valueOf(p.textPrimary)
            }
        }

        findViewById<ProgressBar>(R.id.focusedProgress).apply {
            progressDrawable = MurattalThemeManager.seekbarProgressDrawable(
                this@SurahDetailActivity, p
            )
        }

        // — شريط المشغّل داخل المرتّل: سطح نظيف مدوّر بلا ظل صلب —
        findViewById<View>(R.id.audioPlayerBar).apply {
            background = MurattalThemeManager.flatCardDrawable(
                this@SurahDetailActivity, p, radiusDp = 24f, strokeDp = 1f,
                fill = p.surface,
                stroke = withAlphaOf(p.surfaceBorder, 0x26)
            )
        }
        findViewById<TextView>(R.id.tvPlayerAvatar).apply {
            background = MurattalThemeManager.circleDrawable(
                this@SurahDetailActivity, p, p.accent
            )
            setTextColor(p.accentText)
        }
        findViewById<TextView>(R.id.tvPlayerTitle).setTextColor(p.textPrimary)
        findViewById<TextView>(R.id.tvPlayerMeta).setTextColor(p.textSecondary)
        findViewById<TextView>(R.id.tvPlayerCurrentTime).setTextColor(p.textSecondary)
        findViewById<TextView>(R.id.tvPlayerRemainingTime).setTextColor(p.textSecondary)
        findViewById<TextView>(R.id.tvPlayerSpeed).setTextColor(p.accent)

        listOf(
            R.id.btnPlayerClose,
            R.id.btnPlayerPrevious,
            R.id.btnPlayerNext,
            R.id.btnPlayerRepeat,
            R.id.btnPlayerSpeed,
            R.id.btnPlayerReciters
        ).forEach { id ->
            (findViewById<View>(id) as? ImageButton)?.let {
                it.imageTintList = android.content.res.ColorStateList.valueOf(p.iconTint)
            }
        }

        val playBtn = findViewById<ImageButton>(R.id.btnPlayerPlayPause)
        playBtn.background = MurattalThemeManager.circleDrawable(
            this@SurahDetailActivity, p, p.accent
        )
        playBtn.imageTintList = android.content.res.ColorStateList.valueOf(p.accentText)

        findViewById<SeekBar>(R.id.playerSeekBar).apply {
            progressDrawable = MurattalThemeManager.seekbarProgressDrawable(
                this@SurahDetailActivity, p
            )
            thumb = MurattalThemeManager.seekbarThumbDrawable(
                this@SurahDetailActivity, p
            )
        }

        applyPaletteToPlayWindow(p)
        lastAppliedPalette = p
    }

    private var lastAppliedPalette: MurattalPalette? = null

    private fun applyPaletteToPlayWindow(p: MurattalPalette) {
        findViewById<View>(R.id.playWindowDim).setBackgroundColor(p.dim)
        // سطح ناعم مدوّر بلا ظل صلب (أسلوب حديث موحّد مع المرتّل)
        findViewById<View>(R.id.playWindowPanel).background =
            MurattalThemeManager.flatCardDrawable(
                this, p, radiusDp = 24f, strokeDp = 1f,
                fill = p.surface,
                stroke = withAlphaOf(p.surfaceBorder, 0x26)
            )

        findViewById<TextView>(R.id.tvPlayWindowReciterName).setTextColor(p.textPrimary)
        findViewById<TextView>(R.id.tvPlayWindowReciterMeta).apply {
            setTextColor(p.accent)
            background = MurattalThemeManager.circleDrawable(this@SurahDetailActivity, p, p.highlight)
        }
        findViewById<TextView>(R.id.tvPlayWindowSpeedValue).setTextColor(p.accent)
        findViewById<TextView>(R.id.tvPlayWindowHint).setTextColor(p.textSecondary)

        findViewById<ImageView>(R.id.btnPlayWindowReciters).imageTintList =
            android.content.res.ColorStateList.valueOf(p.iconTint)
        listOf(
            R.id.btnPlayWindowRepeatAyah,
            R.id.btnPlayWindowResume,
            R.id.btnPlayWindowSpeed
        ).forEach { id ->
            findViewById<View>(id).background = MurattalThemeManager.flatCardDrawable(
                this, p, radiusDp = 16f, strokeDp = 1f,
                fill = withAlphaOf(p.accent, 0x14),
                stroke = withAlphaOf(p.accent, 0x33)
            )
        }
    }

    private fun showMurattalThemeDialog() {
        val intent = Intent(this, MurattalThemePickerActivity::class.java)
        startActivityForResult(intent, REQUEST_MURATTAL_THEME)
        overridePendingTransition(R.anim.slide_in_up, R.anim.fade_out)
    }

    private fun updateFocusedAyah(ayahNumber: Int) {
        val idx = ayahs.indexOfFirst { it.number == ayahNumber }
        if (idx < 0) return
        if (ayahNumber == lastFocusedAyah) return
        lastFocusedAyah = ayahNumber
        readingWindowKey = null
        val ayah = ayahs[idx]

        // الآية لا تُعرض كاملةً في وضع المرتّل؛ العرض عبر سطر القراءة فقط.
        // توقّع: جهّز موجِّه هذه الآية والآية التالية مسبقاً لانتقال لحظي.
        val st = AudioPlaybackState.state.value
        obtainReadingPager(ayahNumber, ayah.text, st.durationMs.takeIf { st.surahNumber == surahNumber } ?: 0L)
        val next = ayahs.getOrNull(idx + 1)
        if (next != null && !readingPagers.containsKey(next.number)) {
            obtainReadingPager(next.number, next.text, 0L)
        }

        findViewById<TextView>(R.id.tvFocusedAyahCounter).text =
            "الآية ${toHindiDigits(ayah.number)} من ${toHindiDigits(ayahs.size)}"
        findViewById<TextView>(R.id.tvFocusedJuzHizb).text =
            currentJuzHizb(surahNumber, ayahNumber)
        findViewById<TextView>(R.id.tvFocusedSurahMini).text = surahInfo.nameArabic

        val percent = (ayahNumber * 100 / ayahs.size.coerceAtLeast(1)).coerceIn(0, 100)
        findViewById<TextView>(R.id.tvFocusedPercent).text = "${toHindiDigits(percent)}٪"
        findViewById<ProgressBar>(R.id.focusedProgress).progress = (percent * 10)
    }

    /**
     * سطر القراءة في وضع المرتّل: كل آية تُعرض أسطراً متتابعة غير متداخلة
     * (٤–٦ كلمات حسب عرض الشاشة وحجم الخط). الانتقال للسطر التالي يحدث
     * بدقة عندما يدخل موضع الصوت أول كلمة بعد نهاية السطر الظاهر، والنص
     * يُستبدل كسطرٍ كامل بانتقال ناعم — بلا أي تتبع بصري لكلمة.
     */
    private fun updateFocusedReadingWindow(ayahNumber: Int, positionMs: Long, durationMs: Long) {
        val card = findViewById<TextView>(R.id.tvFocusedReadingCard) ?: return
        val idx = ayahs.indexOfFirst { it.number == ayahNumber }
        if (idx < 0) {
            card.visibility = View.GONE
            return
        }
        val ayah = ayahs[idx]
        val pager = obtainReadingPager(ayahNumber, ayah.text, durationMs)
        fitReadingLine(pager, card)

        // اشتقاق مباشر من الموضع: مع تقدّم استباقي فقط أثناء التشغيل الفعلي
        // حتى لا يتقدم النص أثناء التوقف/التخزين المؤقت بسبب LEAD.
        val stPlay = AudioPlaybackState.state.value
        val effectivePos = if (stPlay.isPlaying && stPlay.surahNumber == surahNumber && stPlay.currentAyah == ayahNumber) {
            (positionMs + com.urwah.dhikr.align.ReadingLinePager.LEAD_MS).coerceAtMost(durationMs.coerceAtLeast(positionMs + com.urwah.dhikr.align.ReadingLinePager.LEAD_MS))
        } else {
            positionMs.coerceAtLeast(0L)
        }
        val lineIdx = pager.lineIndexAt(effectivePos)
        val line = pager.lineAt(lineIdx)
        if (line.words.isEmpty()) {
            card.visibility = View.GONE
            return
        }
        val key = "$ayahNumber:${line.startIndex}"
        if (key == readingWindowKey && card.visibility == View.VISIBLE) return
        readingWindowKey = key

        card.text = line.words.joinToString(" ")
        card.visibility = View.VISIBLE
        // انتقال ناعم قصير لتبديل السطر (بلا وميض ولا حركة كلمات داخلية)
        card.animate().cancel()
        card.alpha = 0f
        card.translationY = dpToPx(6f).toFloat()
        card.animate().alpha(1f).translationY(0f).setDuration(160).start()
    }

    /**
     * يجلب موجِّه سطور آية معينة ويحمله عند الحاجة. المدة صفر تعني تقديراً
     * استباقياً (للآية التالية) تُصحَّح حدوده تلقائياً بوصول المدة الحقيقية.
     */
    private fun obtainReadingPager(
        ayahNumber: Int,
        text: String,
        durationMs: Long
    ): com.urwah.dhikr.align.ReadingLinePager {
        val waqfAware = isWaqfAwareRiwaya(displayedRiwayatId)
        val existing = readingPagers[ayahNumber]
        if (existing != null) {
            existing.isWaqfAware = waqfAware
            if (durationMs > 0L && durationMs != existing.loadedDurationMs) {
                // لا تعِد توزيع الأزمنة في منتصف تلاوة الآية نفسها حتى لا تقفز الأسطر
                val st = AudioPlaybackState.state.value
                val midAyah = isFocusedMode && ayahNumber == lastFocusedAyah && st.isPlaying && st.positionMs > 800
                if (!midAyah) {
                    existing.load(text, durationMs)
                }
                existing.loadedDurationMs = durationMs
            }
            return existing
        }
        val pager = com.urwah.dhikr.align.ReadingLinePager()
        pager.isWaqfAware = waqfAware
        val effective = if (durationMs > 0L) durationMs
        else com.urwah.dhikr.align.ReadingLinePager.estimateDuration(text)
        pager.load(text, effective)
        pager.loadedDurationMs = if (durationMs > 0L) durationMs else 0L
        readingPagers[ayahNumber] = pager
        return pager
    }

    /**
     * يكيّف عدد كلمات السطر (٦ ← ٥ ← ٤ ← ٣) حتى يتسع النص في سطرٍ واحد
     * فعلياً داخل البطاقة — بالقياس لا بالتخمين، مع تخزين العرض المتاح.
     */
    private fun fitReadingLine(
        pager: com.urwah.dhikr.align.ReadingLinePager,
        card: TextView
    ) {
        if (card.width <= 0 && readingCardTextWidth <= 0) {
            card.post { readingCardTextWidth = 0 } // إعادة المحاولة بعد التخطيط
            return
        }
        if (card.width > 0) {
            val hpad = card.paddingLeft + card.paddingRight +
                dpToPx(24f) // هوامش TextView الجانبية داخل البطاقة
            val avail = card.width - hpad
            if (avail > 0) readingCardTextWidth = avail
        }
        if (readingCardTextWidth <= 0) return
        // حدّث وعي الوقف قبل الملاءمة (قد تغيّر الرواية)
        val waqfAware = isWaqfAwareRiwaya(displayedRiwayatId)
        pager.isWaqfAware = waqfAware
        if (pager.fittedForWidth == readingCardTextWidth &&
            pager.lineCount > 0 && readingCardWaqfAware == waqfAware
        ) return

        var k = com.urwah.dhikr.align.ReadingLinePager.MAX_WORDS_PER_LINE
        val paint = card.paint
        while (k > com.urwah.dhikr.align.ReadingLinePager.MIN_WORDS_PER_LINE) {
            // قياس مستقل عن تقسيم الوقف الحالي — خذ أول k كلمات من النص الأصلي
            val candidateWords = pager.displayWordsForTest().take(k)
            val candidate = candidateWords.joinToString(" ")
            if (candidateWords.isEmpty() || paint.measureText(candidate) <= readingCardTextWidth) break
            k--
        }
        pager.setWordsPerLine(k)
        pager.fittedForWidth = readingCardTextWidth
        readingCardWaqfAware = waqfAware
    }

    private fun updateFocusedReciter(reciterId: Int) {
        val reciter = ReciterCatalog.getById(reciterId)
        findViewById<TextView>(R.id.tvFocusedReciter).text =
            "${reciter.nameArabic} • ${reciter.bitrateDisplay()}"
    }

    private fun showPlayWindow() {
        val root = findViewById<View>(R.id.playWindowRoot)
        if (root.visibility == View.VISIBLE) {
            hidePlayWindow()
            return
        }

        playerAutoHideHandler.removeCallbacks(playerAutoHideRunnable)

        val reciter = ReciterCatalog.getById(currentReciterId)
        findViewById<TextView>(R.id.tvPlayWindowReciterName).text = reciter.nameArabic
        findViewById<TextView>(R.id.tvPlayWindowReciterMeta).text = reciter.riwaya
        findViewById<TextView>(R.id.tvPlayWindowSpeedValue).text = formatSpeed(
            AudioPlaybackState.state.value.speed
        )

        val st = AudioPlaybackState.state.value
        findViewById<TextView>(R.id.tvPlayWindowHint).text = when {
            st.isActive && st.surahNumber == surahNumber && st.isPlaying -> "قيد التشغيل الآن"
            st.isActive && st.surahNumber == surahNumber -> "التلاوة متوقفة مؤقتاً"
            else -> "التلاوة متوقفة حالياً"
        }

        val playerBar = findViewById<View>(R.id.audioPlayerBar)
        playerBar.visibility = View.GONE

        root.bringToFront()
        val panel = findViewById<View>(R.id.playWindowPanel)
        root.visibility = View.VISIBLE
        panel.translationY = dpToPx(120f).toFloat()
        root.alpha = 0f
        root.animate().alpha(1f).setDuration(200).start()
        panel.animate().translationY(0f).setDuration(250).start()
    }

    private fun hidePlayWindow() {
        val root = findViewById<View>(R.id.playWindowRoot)
        if (root.visibility != View.VISIBLE) return
        val panel = findViewById<View>(R.id.playWindowPanel)
        panel.animate().translationY(dpToPx(120f).toFloat()).setDuration(200)
            .withEndAction {
                root.visibility = View.GONE
                val st = AudioPlaybackState.state.value
                if (st.isActive && (st.isPlaying || st.isBuffering)) {
                    showAudioPlayer()
                }
            }
            .start()
        root.animate().alpha(0f).setDuration(200).start()
    }

    private fun setupPlayWindowControls() {
        findViewById<View>(R.id.playWindowDim).setOnClickListener { hidePlayWindow() }

        findViewById<View>(R.id.btnPlayWindowReciters).setOnClickListener {
            showRecitersDialog()
        }

        findViewById<View>(R.id.btnPlayWindowFull).setOnClickListener {
            startPlaybackFrom(currentReciterId, 1)
            hidePlayWindow()
        }

        findViewById<View>(R.id.btnPlayWindowFromAyah).setOnClickListener {
            hidePlayWindow()
            showJumpToAyahDialog { ayahNum ->
                startPlaybackFrom(currentReciterId, ayahNum)
                UrwahToast.show(this, "تشغيل من الآية ${toHindiDigits(ayahNum)}")
            }
        }

        findViewById<View>(R.id.btnPlayWindowRange).setOnClickListener {
            hidePlayWindow()
            showRangePlaybackDialog()
        }

        findViewById<View>(R.id.btnPlayWindowRepeatAyah).setOnClickListener {
            cycleRepeatMode()
            UrwahToast.show(this, "تم ضبط وضع التكرار")
        }

        findViewById<View>(R.id.btnPlayWindowResume).setOnClickListener {
            val savedPos = ReadingTracker.getPosition(this)
            if (savedPos != null && savedPos.surahNumber == surahNumber) {
                startPlaybackFrom(currentReciterId, savedPos.ayahNumber)
                UrwahToast.show(this, "استكمال من الآية ${toHindiDigits(savedPos.ayahNumber)}")
            } else {
                startPlaybackFrom(currentReciterId, 1)
                UrwahToast.show(this, "لا يوجد موضع سابق، بدأ من البداية")
            }
            hidePlayWindow()
        }

        findViewById<View>(R.id.btnPlayWindowSpeed).setOnClickListener {
            cyclePlaybackSpeed()
            findViewById<TextView>(R.id.tvPlayWindowSpeedValue).text = formatSpeed(
                AudioPlaybackState.state.value.speed
            )
        }
    }

    private fun showJumpToAyahDialog(onJump: (Int) -> Unit) {
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
                dialog.dismiss()
                onJump(num)
            } else {
                Toast.makeText(this, "رقم غير صحيح", Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<Button>(R.id.btnJumpCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showRangePlaybackDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_range_playback, null)
        val etFrom = view.findViewById<EditText>(R.id.etRangeFrom)
        val etTo = view.findViewById<EditText>(R.id.etRangeTo)
        val tvHint = view.findViewById<TextView>(R.id.tvRangeHint)
        tvHint.text = "الآيات ١-${toHindiDigits(ayahs.size)}"
        etFrom.hint = "من ١"
        etTo.hint = "إلى ${toHindiDigits(ayahs.size)}"

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<Button>(R.id.btnRangePlay).setOnClickListener {
            val from = etFrom.text.toString().toIntOrNull()
            val to = etTo.text.toString().toIntOrNull()
            if (from != null && to != null && from >= 1 && to >= from && to <= ayahs.size) {
                AudioPlayerService.playRange(
                    this, surahNumber, from, to, currentReciterId
                )
                dialog.dismiss()
                showAudioPlayer()
                UrwahToast.show(this, "تشغيل من الآية ${toHindiDigits(from)} إلى ${toHindiDigits(to)}")
            } else {
                Toast.makeText(this, "نطاق غير صحيح", Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<Button>(R.id.btnRangeCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupAudioPlayer() {
        findViewById<View>(R.id.audioPlayerBar).setOnClickListener {
            showPlayWindow()
        }
        findViewById<View>(R.id.audioPlayerBar).setOnLongClickListener {
            enterFocusedMode()
            true
        }

        findViewById<View>(R.id.btnPlayerPlayPause).setOnClickListener {
            val state = AudioPlaybackState.state.value
            if (state.isActive && state.surahNumber == surahNumber) {
                if (state.isPlaying) {
                    AudioPlayerService.pause(this)
                } else {
                    AudioPlayerService.resume(this)
                }
            } else {
                startPlaybackFrom(currentReciterId, 1)
            }
        }

        findViewById<View>(R.id.btnPlayerPrevious).setOnClickListener {
            if (isFocusedMode) {
                val cur = AudioPlaybackState.state.value.currentAyah
                val idx = ayahs.indexOfFirst { it.number == cur }
                val prev = if (idx > 0) ayahs[idx - 1].number else null
                if (prev != null) {
                    readingWindowKey = null
                    lastFocusedAyah = -1
                    updateFocusedAyah(prev)
                    updateFocusedReadingWindow(prev, 0L, 0L)
                }
            }
            AudioPlayerService.previous(this)
        }

        findViewById<View>(R.id.btnPlayerNext).setOnClickListener {
            if (isFocusedMode) {
                val cur = AudioPlaybackState.state.value.currentAyah
                val idx = ayahs.indexOfFirst { it.number == cur }
                val next = if (idx >= 0 && idx + 1 < ayahs.size) ayahs[idx + 1].number else null
                if (next != null) {
                    readingWindowKey = null
                    lastFocusedAyah = -1
                    updateFocusedAyah(next)
                    updateFocusedReadingWindow(next, 0L, 0L)
                }
            }
            AudioPlayerService.next(this)
        }

        // في الواجهة العربية (RTL) «السابقة» تقع يمين الشاشة ويجب أن يشير سهمها
        // نحو اليمين و«التالية» نحو اليسار — نعكس الرمزين أفقيًا ليطابقا الاتجاه.
        val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        findViewById<ImageButton>(R.id.btnPlayerPrevious).rotationY = if (isRtl) 180f else 0f
        findViewById<ImageButton>(R.id.btnPlayerNext).rotationY = if (isRtl) 180f else 0f

        findViewById<View>(R.id.btnPlayerClose).setOnClickListener {
            AudioPlayerService.stop(this)
            hideAudioPlayer()
        }

        findViewById<View>(R.id.btnPlayerSpeed).setOnClickListener {
            showSpeedDialog()
        }
        findViewById<View>(R.id.btnPlayerSpeed).setOnLongClickListener {
            cyclePlaybackSpeedDown()
            true
        }

        findViewById<View>(R.id.btnPlayerRepeat).setOnClickListener {
            cycleRepeatMode()
        }

        findViewById<View>(R.id.btnPlayerReciters).setOnClickListener {
            showRecitersDialog()
        }

        val seekBar = findViewById<SeekBar>(R.id.playerSeekBar)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val st = AudioPlaybackState.state.value
                    if (st.isActive && st.durationMs > 0) {
                        val pos = (progress * st.durationMs / 1000L).toLong()
                        findViewById<TextView>(R.id.tvPlayerCurrentTime).text = formatTime(pos)
                    }
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar) {
                userSeeking = true
            }

            override fun onStopTrackingTouch(sb: SeekBar) {
                val st = AudioPlaybackState.state.value
                if (st.isActive && st.durationMs > 0) {
                    val pos = (sb.progress * st.durationMs / 1000L).toLong()
                    AudioPlayerService.seek(this@SurahDetailActivity, pos)
                }
                userSeeking = false
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AudioPlaybackState.state.collect { state ->
                    if (!lastPlaybackActive && state.isActive &&
                        (state.isPlaying || state.isBuffering)
                    ) {
                        showAudioPlayer()
                    }
                    lastPlaybackActive = state.isActive
                    if (state.isActive && state.surahNumber == surahNumber &&
                        state.reciterId != lastSyncedReciterId
                    ) {
                        lastSyncedReciterId = state.reciterId
                        syncRiwayatWithReciter(state.reciterId, state.currentAyah)
                    } else if (!state.isActive) {
                        lastSyncedReciterId = -1
                    }
                    updateAudioPlayerUi(state)
                    if (isFocusedMode) {
                        if (state.isActive && state.surahNumber == surahNumber) {
                            updateFocusedAyah(state.currentAyah)
                            updateFocusedReciter(state.reciterId)
                            updateFocusedReadingWindow(
                                state.currentAyah, state.positionMs, state.durationMs
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startPlaybackFrom(reciterId: Int, startAyah: Int) {
        currentReciterId = reciterId
        lastPlaybackAyah = -1
        audioPrefs.edit().putInt("selected_reciter", reciterId).apply()
        syncRiwayatWithReciter(reciterId, startAyah)
        // في وضع المرتل: اعرض النص فوراً قبل بدء الصوت حتى لا يسبق الصوت النص
        if (isFocusedMode) {
            readingWindowKey = null
            lastFocusedAyah = -1
            updateFocusedAyah(startAyah)
            updateFocusedReadingWindow(startAyah, 0L, 0L)
        }
        AudioPlayerService.play(
            this,
            surahNumber,
            startAyah,
            ayahs.size,
            reciterId
        )
        showAudioPlayer()
    }

    /** هل تُطبَّق قواعد الوقف (حفص) على هذه الرواية؟ */
    private fun isWaqfAwareRiwaya(id: String?): Boolean {
        return id == "hafs" || id == "shouba"
    }

    private fun syncRiwayatWithReciter(reciterId: Int, currentAyah: Int) {
        if (isKhatmaMode) return
        val reciter = ReciterCatalog.getById(reciterId)
        val targetId = QuranDataLoader.riwayatIdForArabicName(reciter.riwaya) ?: return
        if (targetId == displayedRiwayatId) return

        val currentInfo = QuranDataLoader.getRiwayatInfo(displayedRiwayatId)
        if (!QuranDataLoader.isAvailable(targetId)) {
            UrwahToast.show(
                this,
                "رواية ${reciter.riwaya} غير متوفرة حالياً، تم الإبقاء على ${currentInfo.arabicName}"
            )
            return
        }

        displayedRiwayatId = targetId
        QuranDataLoader.setQiraat(this, targetId)
        QuranDataLoader.invalidateCache()

        val newAyahs = QuranDataLoader.getSurah(this, surahNumber)?.ayahs ?: emptyList()
        ayahs = newAyahs
        ayahRowMap.clear()
        highlightedAyahs.clear()
        continuousViewRef = null
        continuousAyahOffsets = null
        playbackColorSpanActive = false
        lastPlaybackAyah = -1
        readingPagers.clear()
        readingCardTextWidth = 0
        readingWindowKey = null

        renderAyahsInSingleCard(containerAyahs, ayahs, isDark)

        surahInfo = surahInfo.copy(ayahCount = ayahs.size)
        findViewById<TextView>(R.id.tvSurahMeta).text =
            "${surahInfo.revelationPlace} • ${surahInfo.ayahCount} آيات"

        UrwahToast.show(this, "تم تحويل المصحف إلى رواية ${reciter.riwaya}")
        if (currentAyah > 0) {
            scrollView.post { scrollToAyah(currentAyah) }
        }
    }

    private fun showAudioPlayer() {
        if (isFocusedMode) {
            showFocusedTools()
            scheduleFocusedAutoHide()
            return
        }
        playerUiVisible = true
        val bar = findViewById<View>(R.id.audioPlayerBar)
        if (bar.visibility != View.VISIBLE) {
            bar.alpha = 0f
            bar.translationY = dpToPx(40f).toFloat()
            bar.visibility = View.VISIBLE
            bar.animate().alpha(1f).translationY(0f).setDuration(250).start()
        }
        schedulePlayerAutoHide()
    }

    private fun hideAudioPlayer() {
        playerAutoHideHandler.removeCallbacks(playerAutoHideRunnable)
        if (isFocusedMode) {
            hideFocusedTools()
            return
        }
        if (!playerUiVisible) return
        playerUiVisible = false
        val bar = findViewById<View>(R.id.audioPlayerBar)
        bar.animate().alpha(0f).translationY(dpToPx(40f).toFloat()).setDuration(200)
            .withEndAction { bar.visibility = View.GONE }
            .start()
    }

    private fun schedulePlayerAutoHide() {
        playerAutoHideHandler.removeCallbacks(playerAutoHideRunnable)
        playerAutoHideHandler.postDelayed(playerAutoHideRunnable, PLAYER_AUTO_HIDE_DELAY)
    }

    private fun updateAudioPlayerUi(state: AudioPlaybackState.PlaybackUiState) {
        if (!state.isActive) return

        val bar = findViewById<View>(R.id.audioPlayerBar)
        val playWindowRoot = findViewById<View>(R.id.playWindowRoot)
        if (state.isPlaying || state.isBuffering) {
            if (playerUiVisible && bar.visibility != View.VISIBLE &&
                playWindowRoot.visibility != View.VISIBLE
            ) {
                showAudioPlayer()
            }
        }

        val tvTitle = findViewById<TextView>(R.id.tvPlayerTitle)
        val tvMeta = findViewById<TextView>(R.id.tvPlayerMeta)
        val btnPlay = findViewById<ImageView>(R.id.btnPlayerPlayPause)
        val tvAvatar = findViewById<TextView>(R.id.tvPlayerAvatar)

        val reciter = ReciterCatalog.getById(if (state.isActive) state.reciterId else currentReciterId)
        tvTitle.text = "سورة ${SurahDataProvider.allSurahs.find { it.number == surahNumber }?.name ?: ""}"
        tvMeta.text = "الآية ${toHindiDigits(state.currentAyah)} من ${toHindiDigits(state.totalAyahs)} • ${reciter.nameArabic}"
        tvAvatar.text = reciter.nameArabic.take(1)
        btnPlay.setImageResource(
            if (state.isPlaying) R.drawable.ic_media_pause else R.drawable.ic_play_arrow
        )

        if (isFocusedMode) {
            tvTitle.visibility = View.GONE
            tvMeta.visibility = View.GONE
            tvAvatar.visibility = View.GONE
            findViewById<View>(R.id.btnPlayerClose).visibility = View.GONE
        } else {
            tvTitle.visibility = View.VISIBLE
            tvMeta.visibility = View.VISIBLE
            tvAvatar.visibility = View.VISIBLE
            findViewById<View>(R.id.btnPlayerClose).visibility = View.VISIBLE
        }

        findViewById<TextView>(R.id.tvPlayerSpeed).text = formatSpeed(state.speed)

        val repeatBtn = findViewById<ImageView>(R.id.btnPlayerRepeat)
        val isRepeatOn = state.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF
        repeatBtn.alpha = if (isRepeatOn) 1f else 0.35f

        updatePlayerProgress(state)

        highlightPlayingAyah(state.currentAyah)
    }

    private fun updatePlayerProgress(state: AudioPlaybackState.PlaybackUiState) {
        val seekBar = findViewById<SeekBar>(R.id.playerSeekBar)
        val tvCurrent = findViewById<TextView>(R.id.tvPlayerCurrentTime)
        val tvRemaining = findViewById<TextView>(R.id.tvPlayerRemainingTime)

        if (userSeeking) return

        if (state.durationMs > 0) {
            val progress = (state.positionMs * 1000L / state.durationMs).toInt().coerceIn(0, 1000)
            seekBar.progress = progress
            tvCurrent.text = formatTime(state.positionMs)
            tvRemaining.text = "-${formatTime((state.durationMs - state.positionMs).coerceAtLeast(0L))}"
        } else {
            seekBar.progress = 0
            tvCurrent.text = "00:00"
            tvRemaining.text = "-00:00"
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format("%02d:%02d", m, s)
    }

    private fun highlightPlayingAyah(ayahNumber: Int) {
        if (ayahNumber == lastPlaybackAyah && playbackColorSpanActive) return
        val current = AudioPlaybackState.state.value
        if (!current.isPlaying && !current.isBuffering) return
        lastPlaybackAyah = ayahNumber
        applyPlaybackHighlight(ayahNumber, scroll = true)
    }

    private fun applyPlaybackHighlight(ayahNumber: Int, scroll: Boolean) {
        if (continuousViewRef != null && continuousAyahOffsets != null) {
            val idx = ayahs.indexOfFirst { it.number == ayahNumber }
            val offsets = continuousAyahOffsets ?: return
            if (idx < 0 || idx >= offsets.size) return
            val (s, e) = offsets[idx]
            val rawSb = continuousViewRef?.tag as? SpannableStringBuilder ?: return
            val copy = SpannableStringBuilder(rawSb)
            val highlight = if (isDark) Color.parseColor("#338B6F5E") else Color.parseColor("#1A8B6F5E")
            copy.setSpan(BackgroundColorSpan(highlight), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            continuousViewRef?.text = copy
            playbackColorSpanActive = true
            if (scroll) scrollToAyah(ayahNumber)
        } else {
            val row = ayahRowMap[ayahNumber]
            if (row != null) {
                val highlight = if (isDark) Color.parseColor("#338B6F5E") else Color.parseColor("#1A8B6F5E")
                row.setBackgroundColor(highlight)
                playbackColorSpanActive = true
                if (scroll) scrollToAyah(ayahNumber)
            }
        }
    }

    private val playbackSpeeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    private fun showSpeedDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_playback_speed, null)
        val grid = view.findViewById<LinearLayout>(R.id.speedGrid)
        val current = AudioPlaybackState.state.value.speed
        val currentIdx = playbackSpeeds.indexOf(current)

        playbackSpeeds.forEachIndexed { idx, speed ->
            val chip = TextView(this).apply {
                text = formatSpeed(speed)
                textSize = 14f
                gravity = Gravity.CENTER
                val selected = idx == currentIdx
                setTextColor(resources.getColor(
                    if (selected) R.color.urwah_surface else R.color.urwah_thread_brown
                ))
                setBackgroundResource(
                    if (selected) R.drawable.bg_primary_button else R.drawable.bg_chip_neo
                )
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(52f), dpToPx(44f)
                ).apply { marginEnd = dpToPx(8f) }
                setOnClickListener {
                    applySpeed(speed)
                    (it as TextView).parent.let { p ->
                        for (i in 0 until (p as LinearLayout).childCount) {
                            val c = p.getChildAt(i)
                            val sel = (c as TextView).text == formatSpeed(speed)
                            c.setBackgroundResource(if (sel) R.drawable.bg_primary_button else R.drawable.bg_chip_neo)
                            c.setTextColor(resources.getColor(
                                if (sel) R.color.urwah_surface else R.color.urwah_thread_brown
                            ))
                        }
                    }
                }
            }
            grid.addView(chip)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun cyclePlaybackSpeed() {
        val current = AudioPlaybackState.state.value
        val idx = playbackSpeeds.indexOf(current.speed)
        val next = if (idx >= 0 && idx < playbackSpeeds.size - 1) {
            playbackSpeeds[idx + 1]
        } else {
            playbackSpeeds.first()
        }
        applySpeed(next)
    }

    private fun cyclePlaybackSpeedDown() {
        val current = AudioPlaybackState.state.value
        val idx = playbackSpeeds.indexOf(current.speed)
        val next = if (idx > 0) {
            playbackSpeeds[idx - 1]
        } else {
            playbackSpeeds.last()
        }
        applySpeed(next)
    }

    private fun applySpeed(speed: Float) {
        AudioPlaybackState.update { it.copy(speed = speed) }
        AudioPlayerService.setSpeed(this, speed)
        UrwahToast.show(this, "السرعة ${formatSpeed(speed)}")
    }

    private fun cycleRepeatMode() {
        val current = AudioPlaybackState.state.value
        val next = when (current.repeatMode) {
            androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ONE
            androidx.media3.common.Player.REPEAT_MODE_ONE -> androidx.media3.common.Player.REPEAT_MODE_ALL
            else -> androidx.media3.common.Player.REPEAT_MODE_OFF
        }
        AudioPlaybackState.update { it.copy(repeatMode = next) }
        AudioPlayerService.setRepeatMode(this, next)
    }

    private fun formatSpeed(speed: Float): String {
        val s = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()
        return "${s}x"
    }

    private fun showRecitersDialog() {
        val intent = Intent(this, ReciterSelectionActivity::class.java)
        startActivityForResult(intent, REQUEST_RECITER_SELECT)
        overridePendingTransition(R.anim.slide_in_up, R.anim.fade_out)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_RECITER_SELECT && resultCode == RESULT_OK) {
            val reciterId = data?.getIntExtra(
                ReciterSelectionActivity.EXTRA_RECITER_ID, currentReciterId
            ) ?: currentReciterId
            if (reciterId != currentReciterId) {
                currentReciterId = reciterId
                audioPrefs.edit().putInt("selected_reciter", reciterId).apply()
                val st = AudioPlaybackState.state.value
                if (st.isActive && st.surahNumber == surahNumber) {
                    startPlaybackFrom(reciterId, st.currentAyah)
                } else {
                    UrwahToast.show(this, "تم اختيار ${ReciterCatalog.getById(reciterId).nameArabic}")
                }
            }
        } else if (requestCode == REQUEST_MURATTAL_THEME && isFocusedMode) {
            applyMurattalTheme(MurattalThemeManager.current(this))
        }
    }
}
