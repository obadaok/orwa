package com.urwah.dhikr

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.ceil
import org.json.JSONArray
import org.json.JSONObject

private object PageChromeMetrics {
    const val CARD_MARGIN_HORIZONTAL_DP = 10f
    const val CARD_MARGIN_VERTICAL_DP = 14f
    const val CARD_PADDING_END_DP = 2f
    const val PROGRESS_COLUMN_WIDTH_DP = 3f
    const val SCROLL_PADDING_HORIZONTAL_DP = 20f
    const val SCROLL_PADDING_TOP_DP = 16f
    const val SCROLL_PADDING_BOTTOM_DP = 10f
    const val BOOK_TITLE_TEXT_SIZE_SP = 11f
    const val BOOK_TITLE_MARGIN_BOTTOM_DP = 6f
    const val PAGE_NUMBER_TEXT_SIZE_SP = 11f
    const val PAGE_NUMBER_MARGIN_TOP_DP = 8f
    const val TOC_DRAWER_WIDTH_DP = 288f
    const val MIN_ONLINE_PROGRESS_PAGES = 50
    const val ONLINE_REFRESH_INTERVAL_MS = 800L
}

class ShamelaBookReaderActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var gestureLayout: ReaderGestureLayout
    private lateinit var tvPageInfo: TextView
    private lateinit var tvBookTitle: TextView
    private lateinit var loadingView: View
    private lateinit var circularMenu: UrwahCircularMenu

    private lateinit var overlayManager: ReaderOverlayManager

    private var bookId = 0
    private var bookTitle = ""
    private var bookContent: ShamelaBookContent? = null
    private var fullText: String = ""
    private var displayPages: List<BookTextPaginator.Page> = emptyList()
    private var pageAdapter: BookPageAdapter? = null

    private var onlineMode = false
    private var onlineHfPath = ""
    private var onlineLoadedPages = mutableListOf<ShamelaPage>()
    private var onlineFirstShowDone = false

    private var fontSize = 18f
    private var lineSpacing = 1.6f
    private var paraSpacing = 1.0f
    private var textAlign = Layout.Alignment.ALIGN_NORMAL
    private var marginSize = 20f
    private var readingWidth = 0.92f
    private var fontFile = "amiri_regular.ttf"
    private var currentTypeface: Typeface? = null

    private var scrollSaveHandler: Handler? = null
    private var scrollSaveRunnable: Runnable? = null
    private var repaginateHandler: Handler? = null
    private var repaginateRunnable: Runnable? = null
    private var searchDebounceHandler: Handler? = null
    private var searchDebounceRunnable: Runnable? = null

    private var lastKnownViewPagerWidth = 0
    private var lastKnownViewPagerHeight = 0

    private val tocDrawerWidthPx: Int by lazy {
        dp(PageChromeMetrics.TOC_DRAWER_WIDTH_DP).toInt()
    }

    private val isRtlLayout: Boolean
        get() = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

    private lateinit var searchAdapter: ReaderSearchAdapter
    private var searchResults = mutableListOf<ReaderSearchAdapter.SearchResult>()
    private var currentSearchIndex = -1

    // إلغاء وإصدارة لأي بحث قديم: تصل نتيجة أحدث استعلام فقط، ولا تُطبَّق
    // نتيجة قديمة بعد كتابة أحدث (منع قفزات الصفحات الخاطئة).
    private var searchJob: Job? = null
    private var searchGeneration = 0

    // إلغاء إعادة الترقيم السابقة إذا بدأت أخرى أثناء تنفيذها.
    private var repaginateJob: Job? = null

    /** نص مطبَّع (بلا تشكيل) مع خريطة موضع: normalized[i] -> original index. */
    private data class NormalizedSearchText(
        val text: String,
        val offsetMap: IntArray
    )

    // Single source of truth for current position — updated IMMEDIATELY on any navigation,
    // never stale. repaginate() uses this instead of viewPager.currentItem.
    private var lastIntendedPage: Int = 0

    // When true, onPageSelected will NOT overwrite lastIntendedPage
    // (it's already set to the target by navigateToPage).
    private var isProgrammaticScroll: Boolean = false

    // All page changes go through this helper to keep lastIntendedPage consistent.
    private fun navigateToPage(pageIndex: Int, smoothScroll: Boolean = true) {
        cancelScheduledRepaginate()
        val idx = pageIndex.coerceIn(0, (displayPages.size - 1).coerceAtLeast(0))
        lastIntendedPage = idx
        isProgrammaticScroll = true
        viewPager.setCurrentItem(idx, smoothScroll)
        updatePageInfo(idx)
    }

    private var pendingSearchQuery: String? = null
    private var pendingSearchMatchPage: Int = -1
    private var pendingSearchRetries: Int = 0
    private var searchHighlightRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shamela_book_reader)

        bookId = intent.getIntExtra("BOOK_ID", 0)
        bookTitle = intent.getStringExtra("BOOK_TITLE") ?: ""
        onlineMode = intent.getBooleanExtra("ONLINE_MODE", false)
        onlineHfPath = intent.getStringExtra("BOOK_HF_PATH") ?: ""

        viewPager = findViewById(R.id.viewPager)
        gestureLayout = findViewById(R.id.gestureLayout)
        tvPageInfo = findViewById(R.id.tvPageInfo)
        tvBookTitle = findViewById(R.id.tvBookTitle)
        loadingView = findViewById(R.id.loadingView)
        circularMenu = findViewById(R.id.circularMenu)

        tvBookTitle.text = bookTitle
        scrollSaveHandler = Handler(Looper.getMainLooper())
        repaginateHandler = Handler(Looper.getMainLooper())
        searchDebounceHandler = Handler(Looper.getMainLooper())

        viewPager.setPageTransformer(BookPageTransformer())
        viewPager.offscreenPageLimit = 3

        gestureLayout.setup { direction ->
            val current = lastIntendedPage
            val target = current + direction
            if (target in 0 until (viewPager.adapter?.itemCount ?: 0)) {
                navigateToPage(target)
            }
        }
        // التقليب أصبح أصليًا عبر ViewPager2 (ناعم ومستجيب دائمًا)؛
        // هذه الطبقة اليدوية معطّلة نهائيًا حتى لا تعترض اللمس.
        gestureLayout.setSwipeEnabled(false)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (!isProgrammaticScroll) {
                    lastIntendedPage = position
                }
                updatePageInfo(position)
                scheduleScrollSave(position)
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    isProgrammaticScroll = false
                }
            }
        })

        viewPager.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val w = viewPager.width
                val h = viewPager.height
                if (w <= 0 || h <= 0 || bookContent == null) return
                // Only repaginate on the very first layout (when lastKnown is 0).
                // After that, TextView auto-reflow handles size changes from panels.
                // Repagination from settings changes is triggered explicitly.
                val isFirstLayout = lastKnownViewPagerWidth == 0 && lastKnownViewPagerHeight == 0
                if (!isFirstLayout) return
                lastKnownViewPagerWidth = w
                lastKnownViewPagerHeight = h
                repaginate()
            }
        })

        ShamelaBookmarkManager.migrateFromOldStorage(this)
        setupOverlayManager()
        setupBackHandling()
        setupRetryButton()
        loadSettings()
        loadBookContent()
        setupToolbar()
        setupCircularMenu()
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    // ------------------------------------------------------------------
    // Overlay Manager
    // ------------------------------------------------------------------

    private fun setupOverlayManager() {
        overlayManager = ReaderOverlayManager()

        overlayManager.onTocShow = {
            val tocDrawer = findViewById<View>(R.id.tocDrawer)
            val tocDim = findViewById<View>(R.id.tocDimBackground)
            tocDrawer.visibility = View.VISIBLE
            tocDim.visibility = View.VISIBLE
            tocDim.alpha = 0f
            tocDrawer.translationX = hiddenTocTranslationX()
            ObjectAnimator.ofFloat(tocDim, "alpha", 0f, 1f).setDuration(250).start()
            ObjectAnimator.ofFloat(tocDrawer, "translationX", hiddenTocTranslationX(), 0f).setDuration(250).start()
        }
        overlayManager.onTocHideAnimated = { onDone ->
            val tocDrawer = findViewById<View>(R.id.tocDrawer)
            val tocDim = findViewById<View>(R.id.tocDimBackground)
            val dimAnim = ObjectAnimator.ofFloat(tocDim, "alpha", 1f, 0f).setDuration(200)
            val drawerAnim = ObjectAnimator.ofFloat(tocDrawer, "translationX", 0f, hiddenTocTranslationX()).setDuration(200)
            dimAnim.start()
            drawerAnim.start()
            dimAnim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    tocDrawer.visibility = View.GONE
                    tocDim.visibility = View.GONE
                    onDone()
                }
            })
        }
        overlayManager.onTocHide = {
            findViewById<View>(R.id.tocDrawer).visibility = View.GONE
            findViewById<View>(R.id.tocDimBackground).visibility = View.GONE
        }

        overlayManager.onSettingsPanelShow = { showSettingsPanel() }
        overlayManager.onSettingsPanelHideAnimated = { onDone -> hidePanelAnimated(R.id.settingsPanel, R.id.settingsDimBackground, onDone) }
        overlayManager.onSettingsPanelHide = { hidePanelImmediate(R.id.settingsPanel, R.id.settingsDimBackground) }

        overlayManager.onJumpPanelShow = { showJumpPanel() }
        overlayManager.onJumpPanelHideAnimated = { onDone -> hidePanelAnimated(R.id.jumpPanel, R.id.jumpDimBackground, onDone) }
        overlayManager.onJumpPanelHide = { hidePanelImmediate(R.id.jumpPanel, R.id.jumpDimBackground) }

        overlayManager.onSearchPanelShow = { showSearchPanel() }
        overlayManager.onSearchPanelHideAnimated = { onDone -> hidePanelAnimated(R.id.searchPanel, R.id.searchDimBackground, onDone) }
        overlayManager.onSearchPanelHide = { hidePanelImmediate(R.id.searchPanel, R.id.searchDimBackground) }

        overlayManager.onFontPickerShow = { showFontPickerPanel() }
        overlayManager.onFontPickerHideAnimated = { onDone -> hidePanelAnimated(R.id.fontPickerPanel, R.id.fontPickerDimBackground, onDone) }
        overlayManager.onFontPickerHide = { hidePanelImmediate(R.id.fontPickerPanel, R.id.fontPickerDimBackground) }

        overlayManager.onCircularMenuShow = {
            setupCircularMenuItems()
            circularMenu.visibility = View.VISIBLE
            circularMenu.bringToFront()
            circularMenu.show()
        }
        overlayManager.onCircularMenuHide = {
            circularMenu.clearMenuItems()
            circularMenu.visibility = View.GONE
        }

        overlayManager.onOverlayActiveChanged = { _ ->
            gestureLayout.setSwipeEnabled(false)
        }
    }

    private fun hidePanelAnimated(panelId: Int, dimId: Int, onDone: () -> Unit) {
        val panel = findViewById<View>(panelId)
        val dim = findViewById<View>(dimId)
        val distance = if (panel.height > 0) panel.height.toFloat() else dp(400f)
        val dimAnim = ObjectAnimator.ofFloat(dim, "alpha", 1f, 0f).setDuration(200)
        val panelAnim = ObjectAnimator.ofFloat(panel, "translationY", 0f, distance).setDuration(200)
        dimAnim.start()
        panelAnim.start()
        dimAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                panel.visibility = View.GONE
                dim.visibility = View.GONE
                panel.translationY = 0f
                onDone()
            }
        })
    }

    private fun hidePanelImmediate(panelId: Int, dimId: Int) {
        findViewById<View>(panelId).visibility = View.GONE
        findViewById<View>(dimId).visibility = View.GONE
        findViewById<View>(panelId).translationY = 0f
    }

    // ------------------------------------------------------------------
    // Back handling
    // ------------------------------------------------------------------

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this) {
            if (overlayManager.isActive) {
                overlayManager.closeCurrent()
            } else {
                saveReadingProgress()
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    // ------------------------------------------------------------------
    // Toolbar
    // ------------------------------------------------------------------

    private fun setupToolbar() {
        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        findViewById<ImageView>(R.id.ivMenu).setOnClickListener {
            overlayManager.open(ReaderOverlayManager.Overlay.CIRCULAR_MENU)
        }
        findViewById<View>(R.id.ivCloseToc).setOnClickListener {
            overlayManager.closeCurrent()
        }
        findViewById<View>(R.id.tocDimBackground).setOnClickListener {
            overlayManager.closeCurrent()
        }
    }

    // ------------------------------------------------------------------
    // Circular Menu
    // ------------------------------------------------------------------

    private fun setupCircularMenu() {
        circularMenu.onMenuDismissed = {
            if (overlayManager.currentOverlay == ReaderOverlayManager.Overlay.CIRCULAR_MENU) {
                overlayManager.closeCurrent(closeAnimated = false)
            }
        }
    }

    private fun setupCircularMenuItems() {
        circularMenu.clearMenuItems()

        circularMenu.addMenuItem(R.drawable.ic_table_of_contents, "الفهرس") {
            overlayManager.open(ReaderOverlayManager.Overlay.TOC)
        }

        circularMenu.addMenuItem(R.drawable.ic_search, "بحث") {
            overlayManager.open(ReaderOverlayManager.Overlay.SEARCH_PANEL)
        }

        circularMenu.addMenuItem(R.drawable.ic_reading_settings, "إعدادات") {
            overlayManager.open(ReaderOverlayManager.Overlay.SETTINGS_PANEL)
        }

        circularMenu.addMenuItem(R.drawable.ic_bookmark_add, "حفظ") {
            toggleBookmark()
        }

        circularMenu.addMenuItem(R.drawable.ic_share, "مشاركة") {
            shareCurrentPage()
        }

        circularMenu.addMenuItem(R.drawable.ic_copy, "نسخ") {
            copyCurrentPage()
        }

        circularMenu.addMenuItem(R.drawable.ic_go_to_page, "صفحة") {
            overlayManager.open(ReaderOverlayManager.Overlay.JUMP_PANEL)
        }

        circularMenu.addMenuItem(R.drawable.ic_save_from_editor, "حفظ كصورة") {
            openQuoteEditor()
        }
    }

    private fun shareCurrentPage() {
        val pageText = displayPages.getOrNull(lastIntendedPage)?.text ?: return
        val shareText = "$bookTitle\n\n$pageText"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, "مشاركة اقتباس"))
    }

    private fun openQuoteEditor() {
        if (displayPages.isEmpty()) return
        val cur = lastIntendedPage.coerceIn(0, displayPages.lastIndex)
        val content = bookContent ?: return
        val meta = content.metadata

        val winStart = (cur - PAGE_CONTEXT_HALF).coerceAtLeast(0)
        val winEnd = (cur + PAGE_CONTEXT_HALF).coerceAtMost(displayPages.lastIndex)
        val window = displayPages.subList(winStart, winEnd + 1)

        // نص متصل للنوافذ المجاورة: يعطي المحرر سياقًا للاختيار عبر الصفحات،
        // مع علامات فاصلة «≪ صفحة N ≫» تُفهم من المحرر وتُحذف عند التصدير.
        val sb = StringBuilder()
        window.forEachIndexed { i, p ->
            val pn = p.originalPageNum ?: (winStart + i + 1)
            sb.append("≪ صفحة $pn ≫\n")
            sb.append(p.text)
            sb.append("\n")
        }

        val intent = Intent(this, QuoteEditorActivity::class.java).apply {
            putExtra("PAGE_TEXT", displayPages[cur].text)
            putExtra("WINDOW_TEXT", sb.toString())
            putExtra("PAGE_NUMBER", displayPages[cur].originalPageNum ?: (cur + 1))
            putExtra("BOOK_TITLE", bookTitle)
            putExtra("AUTHOR", meta.displayAuthor)
            putExtra("EDITION", "")
            putExtra("FONT_FILE", fontFile)
            putExtra("FONT_SIZE", fontSize)
            putExtra("LINE_SPACING", lineSpacing)
            putExtra("PARA_SPACING", paraSpacing)
            putExtra("TEXT_ALIGN", textAlign.ordinal)
        }
        startActivity(intent)
    }

    private fun copyCurrentPage() {
        val pageText = displayPages.getOrNull(lastIntendedPage)?.text ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("book_page", pageText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "تم نسخ النص", Toast.LENGTH_SHORT).show()
    }

    private fun showBookInfo() {
        val content = bookContent ?: return
        val meta = content.metadata
        val pageCount = content.pages.size
        val tocCount = content.toc.size
        val msg = "العنوان: ${meta.title}\n" +
                "المؤلف: ${meta.displayAuthor}\n" +
                "النوع: ${meta.bookType}\n" +
                "عدد الصفحات: $pageCount\n" +
                "عدد الفصول: $tocCount"
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_jump_to_page_reader, null)
        val panel = findViewById<FrameLayout>(R.id.jumpPanel)
        panel.removeAllViews()
        panel.addView(view)

        view.findViewById<TextView>(R.id.tvPageRange).text = msg
        view.findViewById<EditText>(R.id.etPageNumber).visibility = View.GONE
        view.findViewById<TextView>(R.id.tvPageRange).setTextColor(getColor(R.color.urwah_thread_dark))
        view.findViewById<TextView>(R.id.btnJumpGo).text = "إغلاق"
        view.findViewById<TextView>(R.id.btnJumpGo).setOnClickListener {
            overlayManager.closeCurrent()
        }
        view.findViewById<TextView>(R.id.btnJumpCancel).visibility = View.GONE
    }

    // ------------------------------------------------------------------
    // Settings Panel
    // ------------------------------------------------------------------

    private fun showSettingsPanel() {
        val panel = findViewById<FrameLayout>(R.id.settingsPanel)
        val dim = findViewById<View>(R.id.settingsDimBackground)
        panel.removeAllViews()
        val view = LayoutInflater.from(this).inflate(R.layout.panel_reader_settings, null)
        panel.addView(view)

        dim.visibility = View.VISIBLE
        dim.alpha = 0f
        ObjectAnimator.ofFloat(dim, "alpha", 0f, 1f).setDuration(220).start()

        panel.visibility = View.VISIBLE
        panel.post {
            val startY = if (panel.height > 0) panel.height.toFloat() else dp(400f)
            panel.translationY = startY
            ObjectAnimator.ofFloat(panel, "translationY", startY, 0f).setDuration(220).start()
        }

        setupSettingsControls(view)
    }

    private fun setupSettingsControls(view: View) {
        val fontSizeSlider = view.findViewById<SeekBar>(R.id.fontSizeSlider)
        val lineSpacingSlider = view.findViewById<SeekBar>(R.id.lineSpacingSlider)
        val paraSpacingSlider = view.findViewById<SeekBar>(R.id.paraSpacingSlider)
        val marginSlider = view.findViewById<SeekBar>(R.id.marginSlider)
        val readingWidthSlider = view.findViewById<SeekBar>(R.id.readingWidthSlider)
        val tvFontSizeValue = view.findViewById<TextView>(R.id.tvFontSizeValue)
        val tvLineSpacingValue = view.findViewById<TextView>(R.id.tvLineSpacingValue)
        val tvParaSpacingValue = view.findViewById<TextView>(R.id.tvParaSpacingValue)
        val tvMarginValue = view.findViewById<TextView>(R.id.tvMarginValue)
        val tvReadingWidthValue = view.findViewById<TextView>(R.id.tvReadingWidthValue)
        val tvCurrentFontName = view.findViewById<TextView>(R.id.tvCurrentFontName)
        val btnDecrease = view.findViewById<TextView>(R.id.btnFontDecrease)
        val valIncrease = view.findViewById<TextView>(R.id.btnFontIncrease)
        val tvAlignmentValue = view.findViewById<TextView>(R.id.tvAlignmentValue)
        val btnRight = view.findViewById<TextView>(R.id.btnAlignRight)
        val btnCenter = view.findViewById<TextView>(R.id.btnAlignCenter)
        val btnJustify = view.findViewById<TextView>(R.id.btnAlignJustify)

        tvCurrentFontName.text = FontManager.getDisplayName(fontFile)

        tvFontSizeValue.text = "${fontSize.toInt()}sp"
        fontSizeSlider.progress = ((fontSize - 12f) / (32f - 12f) * 100).toInt()

        tvLineSpacingValue.text = "${String.format("%.1f", lineSpacing)}x"
        lineSpacingSlider.progress = ((lineSpacing - 1.0f) / (3.0f - 1.0f) * 100).toInt()

        tvParaSpacingValue.text = "${String.format("%.1f", paraSpacing)}x"
        paraSpacingSlider.progress = ((paraSpacing - 0.5f) / (3.0f - 0.5f) * 100).toInt()

        tvMarginValue.text = "${marginSize.toInt()}dp"
        marginSlider.progress = ((marginSize - 10f) / (40f - 10f) * 100).toInt()

        tvReadingWidthValue.text = "${(readingWidth * 100).toInt()}%"
        readingWidthSlider.progress = ((readingWidth - 0.7f) / (1.0f - 0.7f) * 100).toInt()

        view.findViewById<View>(R.id.btnFontPicker).setOnClickListener {
            overlayManager.open(ReaderOverlayManager.Overlay.FONT_PICKER)
        }

        tvFontSizeValue.text = "${fontSize.toInt()}sp"
        fontSizeSlider.progress = ((fontSize - 12f) / (32f - 12f) * 100).toInt()

        tvLineSpacingValue.text = "${String.format("%.1f", lineSpacing)}x"
        lineSpacingSlider.progress = ((lineSpacing - 1.0f) / (3.0f - 1.0f) * 100).toInt()

        tvParaSpacingValue.text = "${String.format("%.1f", paraSpacing)}x"
        paraSpacingSlider.progress = ((paraSpacing - 0.5f) / (3.0f - 0.5f) * 100).toInt()

        fun updateAlignmentUI() {
            val label = when (textAlign) {
                Layout.Alignment.ALIGN_OPPOSITE -> "يمين"
                Layout.Alignment.ALIGN_CENTER -> "وسط"
                else -> "ممتزج"
            }
            tvAlignmentValue.text = label
            btnRight.setBackgroundResource(if (textAlign == Layout.Alignment.ALIGN_OPPOSITE) R.drawable.bg_segment_active_reader else R.drawable.bg_segment_inactive_reader)
            btnCenter.setBackgroundResource(if (textAlign == Layout.Alignment.ALIGN_CENTER) R.drawable.bg_segment_active_reader else R.drawable.bg_segment_inactive_reader)
            btnJustify.setBackgroundResource(if (textAlign == Layout.Alignment.ALIGN_NORMAL) R.drawable.bg_segment_active_reader else R.drawable.bg_segment_inactive_reader)
            val activeColor = getColor(R.color.urwah_card_bg)
            val inactiveColor = getColor(R.color.urwah_thread_dark)
            btnRight.setTextColor(if (textAlign == Layout.Alignment.ALIGN_OPPOSITE) activeColor else inactiveColor)
            btnCenter.setTextColor(if (textAlign == Layout.Alignment.ALIGN_CENTER) activeColor else inactiveColor)
            btnJustify.setTextColor(if (textAlign == Layout.Alignment.ALIGN_NORMAL) activeColor else inactiveColor)
        }
        updateAlignmentUI()

        btnDecrease.setOnClickListener {
            fontSize = (fontSize - 1f).coerceAtLeast(12f)
            tvFontSizeValue.text = "${fontSize.toInt()}sp"
            fontSizeSlider.progress = ((fontSize - 12f) / (32f - 12f) * 100).toInt()
            saveSettings()
            repaginate()
        }

        valIncrease.setOnClickListener {
            fontSize = (fontSize + 1f).coerceAtMost(32f)
            tvFontSizeValue.text = "${fontSize.toInt()}sp"
            fontSizeSlider.progress = ((fontSize - 12f) / (32f - 12f) * 100).toInt()
            saveSettings()
            repaginate()
        }

        fontSizeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                fontSize = 12f + (progress / 100f) * (32f - 12f)
                tvFontSizeValue.text = "${fontSize.toInt()}sp"
                saveSettings()
                scheduleRepaginate()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                cancelScheduledRepaginate()
                repaginate()
            }
        })

        lineSpacingSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                lineSpacing = 1.0f + (progress / 100f) * (3.0f - 1.0f)
                tvLineSpacingValue.text = "${String.format("%.1f", lineSpacing)}x"
                saveSettings()
                scheduleRepaginate()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                cancelScheduledRepaginate()
                repaginate()
            }
        })

        paraSpacingSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                paraSpacing = 0.5f + (progress / 100f) * (3.0f - 0.5f)
                tvParaSpacingValue.text = "${String.format("%.1f", paraSpacing)}x"
                saveSettings()
                scheduleRepaginate()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                cancelScheduledRepaginate()
                repaginate()
            }
        })

        btnRight.setOnClickListener {
            textAlign = Layout.Alignment.ALIGN_OPPOSITE
            updateAlignmentUI()
            saveSettings()
            repaginate()
        }
        btnCenter.setOnClickListener {
            textAlign = Layout.Alignment.ALIGN_CENTER
            updateAlignmentUI()
            saveSettings()
            repaginate()
        }
        btnJustify.setOnClickListener {
            textAlign = Layout.Alignment.ALIGN_NORMAL
            updateAlignmentUI()
            saveSettings()
            repaginate()
        }

        marginSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                marginSize = 10f + (progress / 100f) * (40f - 10f)
                tvMarginValue.text = "${marginSize.toInt()}dp"
                saveSettings()
                scheduleRepaginate()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                cancelScheduledRepaginate()
                repaginate()
            }
        })

        readingWidthSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                readingWidth = 0.7f + (progress / 100f) * (1.0f - 0.7f)
                tvReadingWidthValue.text = "${(readingWidth * 100).toInt()}%"
                saveSettings()
                scheduleRepaginate()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                cancelScheduledRepaginate()
                repaginate()
            }
        })

        view.findViewById<TextView>(R.id.btnCloseSettings).setOnClickListener {
            overlayManager.closeCurrent()
        }

        findViewById<View>(R.id.settingsDimBackground).setOnClickListener { overlayManager.closeCurrent() }
    }

    private fun scheduleRepaginate(delayMs: Long = 180L) {
        repaginateRunnable?.let { repaginateHandler?.removeCallbacks(it) }
        repaginateRunnable = Runnable { repaginate() }
        repaginateHandler?.postDelayed(repaginateRunnable!!, delayMs)
    }

    private fun cancelScheduledRepaginate() {
        repaginateRunnable?.let { repaginateHandler?.removeCallbacks(it) }
    }

    // ------------------------------------------------------------------
    // Font Picker Panel
    // ------------------------------------------------------------------

    private fun showFontPickerPanel() {
        val panel = findViewById<FrameLayout>(R.id.fontPickerPanel)
        val dim = findViewById<View>(R.id.fontPickerDimBackground)
        panel.removeAllViews()
        val view = LayoutInflater.from(this).inflate(R.layout.panel_font_picker, null)
        panel.addView(view)

        dim.visibility = View.VISIBLE
        dim.alpha = 0f
        ObjectAnimator.ofFloat(dim, "alpha", 0f, 1f).setDuration(220).start()

        panel.visibility = View.VISIBLE
        panel.post {
            val startY = if (panel.height > 0) panel.height.toFloat() else dp(400f)
            panel.translationY = startY
            ObjectAnimator.ofFloat(panel, "translationY", startY, 0f).setDuration(220).start()
        }

        val rvFonts = view.findViewById<RecyclerView>(R.id.rvFonts)
        val fonts = FontManager.getFonts()
        val selected = FontManager.getSelectedFont(this)
        var pickerAdapter: FontPickerAdapter? = null
        pickerAdapter = FontPickerAdapter(fonts, selected) { newFont ->
            fontFile = newFont
            currentTypeface = FontManager.loadTypeface(this, newFont)
            FontManager.setSelectedFont(this, newFont)
            saveSettings()
            pickerAdapter?.setSelected(newFont)
            repaginate()
        }
        rvFonts.layoutManager = LinearLayoutManager(this)
        rvFonts.adapter = pickerAdapter

        view.findViewById<TextView>(R.id.btnCloseFontPicker).setOnClickListener {
            overlayManager.closeCurrent()
        }
        dim.setOnClickListener { overlayManager.closeCurrent() }
    }

    // ------------------------------------------------------------------
    // Jump to Page Panel
    // ------------------------------------------------------------------

    private fun showJumpPanel() {
        val panel = findViewById<FrameLayout>(R.id.jumpPanel)
        val dim = findViewById<View>(R.id.jumpDimBackground)
        panel.removeAllViews()
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_jump_to_page_reader, null)
        panel.addView(view)

        dim.visibility = View.VISIBLE
        dim.alpha = 0f
        ObjectAnimator.ofFloat(dim, "alpha", 0f, 1f).setDuration(220).start()

        panel.visibility = View.VISIBLE
        panel.post {
            val startY = if (panel.height > 0) panel.height.toFloat() else dp(300f)
            panel.translationY = startY
            ObjectAnimator.ofFloat(panel, "translationY", startY, 0f).setDuration(220).start()
        }

        val totalOrig = bookContent?.pages?.size?.coerceAtLeast(1) ?: displayPages.size
        view.findViewById<TextView>(R.id.tvPageRange).text = "من 1 إلى $totalOrig"
        val etPage = view.findViewById<EditText>(R.id.etPageNumber)
        etPage.requestFocus()
        etPage.postDelayed({
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etPage, InputMethodManager.SHOW_IMPLICIT)
        }, 200)

        view.findViewById<TextView>(R.id.btnJumpGo).setOnClickListener {
            val pageNum = etPage.text.toString().toIntOrNull() ?: return@setOnClickListener
            val index = displayPages.indexOfFirst { it.originalPageNum == pageNum }
                .takeIf { it >= 0 } ?: (pageNum - 1).coerceIn(0, displayPages.size - 1)
            navigateToPage(index)
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(etPage.windowToken, 0)
            overlayManager.closeCurrent()
        }

        view.findViewById<TextView>(R.id.btnJumpCancel).setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(etPage.windowToken, 0)
            overlayManager.closeCurrent()
        }

        dim.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(etPage.windowToken, 0)
            overlayManager.closeCurrent()
        }
    }

    // ------------------------------------------------------------------
    // Search Panel
    // ------------------------------------------------------------------

    private fun showSearchPanel() {
        val panel = findViewById<FrameLayout>(R.id.searchPanel)
        val dim = findViewById<View>(R.id.searchDimBackground)
        panel.removeAllViews()
        val view = LayoutInflater.from(this).inflate(R.layout.panel_reader_search, null)
        panel.addView(view)

        dim.visibility = View.VISIBLE
        dim.alpha = 0f
        ObjectAnimator.ofFloat(dim, "alpha", 0f, 1f).setDuration(220).start()

        panel.visibility = View.VISIBLE
        panel.post {
            val startY = if (panel.height > 0) panel.height.toFloat() else dp(400f)
            panel.translationY = startY
            ObjectAnimator.ofFloat(panel, "translationY", startY, 0f).setDuration(220).start()
        }

        val etSearch = view.findViewById<EditText>(R.id.etReaderSearch)
        val ivClear = view.findViewById<ImageView>(R.id.ivClearReaderSearch)
        val navRow = view.findViewById<View>(R.id.searchNavRow)
        val tvCount = view.findViewById<TextView>(R.id.tvSearchResultCount)
        val rvResults = view.findViewById<RecyclerView>(R.id.rvSearchResults)
        val tvNoResults = view.findViewById<TextView>(R.id.tvNoResults)
        val stateArea = view.findViewById<View>(R.id.searchStateArea)
        val progress = view.findViewById<ProgressBar>(R.id.searchProgress)

        searchAdapter = ReaderSearchAdapter(emptyList()) { pageIndex ->
            navigateToPage(pageIndex)
            closeSearchPanel()
            scrollToSearchMatch(pageIndex, etSearch.text.toString())
        }
        rvResults.layoutManager = LinearLayoutManager(this)
        rvResults.adapter = searchAdapter

        searchResults.clear()
        currentSearchIndex = -1
        searchJob?.cancel()
        searchGeneration++
        navRow.visibility = View.GONE
        rvResults.visibility = View.GONE
        showSearchState(stateArea, progress, tvNoResults, message = "ابدأ الكتابة للبحث...", loading = false)

        etSearch.requestFocus()
        etSearch.postDelayed({
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
        }, 200)

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                ivClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                searchDebounceRunnable?.let { searchDebounceHandler?.removeCallbacks(it) }
                searchDebounceRunnable = Runnable {
                    performReaderSearch(query, rvResults, navRow, tvCount, tvNoResults, stateArea, progress)
                }
                searchDebounceHandler?.postDelayed(searchDebounceRunnable!!, 250L)
            }
        })

        ivClear.setOnClickListener { etSearch.text?.clear() }

        view.findViewById<ImageView>(R.id.ivSearchPrev).setOnClickListener {
            if (searchResults.isEmpty()) return@setOnClickListener
            currentSearchIndex = (currentSearchIndex - 1 + searchResults.size) % searchResults.size
            searchAdapter.setActivePosition(currentSearchIndex)
            val result = searchResults[currentSearchIndex]
            navigateToPage(result.pageIndex)
            rvResults.scrollToPosition(currentSearchIndex)
            tvCount.text = "${currentSearchIndex + 1} / ${searchResults.size}"
            closeSearchPanel()
            scrollToSearchMatch(result.pageIndex, etSearch.text.toString())
        }

        view.findViewById<ImageView>(R.id.ivSearchNext).setOnClickListener {
            if (searchResults.isEmpty()) return@setOnClickListener
            currentSearchIndex = (currentSearchIndex + 1) % searchResults.size
            searchAdapter.setActivePosition(currentSearchIndex)
            val result = searchResults[currentSearchIndex]
            navigateToPage(result.pageIndex)
            rvResults.scrollToPosition(currentSearchIndex)
            tvCount.text = "${currentSearchIndex + 1} / ${searchResults.size}"
            closeSearchPanel()
            scrollToSearchMatch(result.pageIndex, etSearch.text.toString())
        }

        view.findViewById<View>(R.id.btnCloseSearch).setOnClickListener {
            closeSearchPanel()
        }

        dim.setOnClickListener {
            closeSearchPanel()
        }
    }

    private fun closeSearchPanel() {
        val panel = findViewById<FrameLayout>(R.id.searchPanel)
        val et = panel?.findViewById<EditText>(R.id.etReaderSearch)
        if (et != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(et.windowToken, 0)
        }
        overlayManager.closeCurrent()
    }

    /**
     * يعرض منطقة الحالة (تحميل/رسالة) في لوحة البحث.
     * أثناء LOADING يُعرض مؤشر واضح ولا تظهر «لا توجد نتائج».
     */
    private fun showSearchState(
        stateArea: View,
        progress: View,
        tvNoResults: TextView,
        message: String? = null,
        loading: Boolean = false
    ) {
        stateArea.visibility = View.VISIBLE
        if (loading) {
            progress.visibility = View.VISIBLE
            tvNoResults.visibility = View.GONE
        } else {
            progress.visibility = View.GONE
            tvNoResults.visibility = View.VISIBLE
            tvNoResults.text = message ?: ""
        }
    }

    private fun hideSearchState(stateArea: View, progress: View, tvNoResults: TextView) {
        stateArea.visibility = View.GONE
        progress.visibility = View.GONE
        tvNoResults.visibility = View.GONE
    }

    private fun performReaderSearch(
        query: String,
        rvResults: RecyclerView,
        navRow: View,
        tvCount: TextView,
        tvNoResults: TextView,
        stateArea: View,
        progress: View
    ) {
        searchJob?.cancel()
        val generation = ++searchGeneration

        if (query.isBlank() || displayPages.isEmpty()) {
            searchResults.clear()
            searchAdapter.updateResults(emptyList())
            navRow.visibility = View.GONE
            rvResults.visibility = View.GONE
            showSearchState(stateArea, progress, tvNoResults, message = "ابدأ الكتابة للبحث...")
            return
        }

        val normalizedQuery = normalizeSearchQuery(query.trim())
        // البحث عن أرقام الأحاديث قصير جدًا (12/25/40/101) فلا يُمنع بالحد النصي؛
        // أما الاستعلام النصي العادي فيبقى مطبقًا عليه حد الثلاثة أحرف.
        val isNumericQuery = normalizedQuery.isNotEmpty() && normalizedQuery.all { it.isDigit() }
        if (!isNumericQuery && normalizedQuery.length < 3) {
            searchResults.clear()
            searchAdapter.updateResults(emptyList())
            navRow.visibility = View.GONE
            rvResults.visibility = View.GONE
            showSearchState(stateArea, progress, tvNoResults, message = "اكتب ثلاثة أحرف على الأقل لبدء البحث")
            return
        }

        // LOADING: مؤشر واضح أثناء المسح، ولا «لا توجد نتائج» قبل اكتماله.
        rvResults.visibility = View.GONE
        navRow.visibility = View.GONE
        showSearchState(stateArea, progress, tvNoResults, loading = true)

        searchJob = lifecycleScope.launch {
            val results = withContext(Dispatchers.Default) {
                val found = mutableListOf<ReaderSearchAdapter.SearchResult>()
                for ((index, page) in displayPages.withIndex()) {
                    coroutineContext.ensureActive()
                    val normalized = buildNormalizedSearchText(page.text)
                    var searchFrom = 0
                    while (true) {
                        val matchIdx = normalized.text.indexOf(normalizedQuery, searchFrom, ignoreCase = true)
                        if (matchIdx < 0) break

                        // المطابقة تُمثَّل على النص المطبَّع لكن المواضع تُعاد إلى
                        // النص الأصلي عبر خريطة المواضع — فيُبنى المعاينة حول
                        // MATCH_START..MATCH_END الحقيقي ويقع الـ highlight على
                        // الكلمة التي بحث عنها المستخدم فعلاً (إصلاح P0).
                        val origStart = normalized.offsetMap[matchIdx]
                        val origEnd = normalized.offsetMap[matchIdx + normalizedQuery.length - 1] + 1

                        val snippetStart = (origStart - 40).coerceAtLeast(0)
                        val snippetEnd = (origEnd + 40).coerceAtMost(page.text.length)
                        var snippet = page.text.substring(snippetStart, snippetEnd)
                        var prefixOffset = 0
                        if (snippetStart > 0) {
                            snippet = "…$snippet"
                            prefixOffset = 1
                        }
                        if (snippetEnd < page.text.length) snippet = "$snippet…"

                        val localMatchStart = origStart - snippetStart + prefixOffset
                        val localMatchEnd = origEnd - snippetStart + prefixOffset
                        val origNum = page.originalPageNum ?: (index + 1)

                        found.add(
                            ReaderSearchAdapter.SearchResult(
                                pageIndex = index,
                                pageNumber = origNum,
                                snippet = snippet,
                                matchStart = localMatchStart,
                                matchEnd = localMatchEnd
                            )
                        )
                        searchFrom = matchIdx + normalizedQuery.length
                    }
                }
                found
            }
            if (generation != searchGeneration) return@launch // نتيجة قديمة بعد أحدث استعلام

            searchResults.clear()
            searchResults.addAll(results)
            currentSearchIndex = -1
            searchAdapter.updateResults(results)

            if (results.isEmpty()) {
                navRow.visibility = View.GONE
                rvResults.visibility = View.GONE
                showSearchState(stateArea, progress, tvNoResults, message = "لا توجد نتائج")
            } else {
                navRow.visibility = View.VISIBLE
                rvResults.visibility = View.VISIBLE
                hideSearchState(stateArea, progress, tvNoResults)
                currentSearchIndex = 0
                searchAdapter.setActivePosition(0)
                tvCount.text = "1 / ${results.size}"
                // لا نقلب الصفحات تلقائيًا أثناء الكتابة؛ ينتقل المستخدم عند
                // الضغط على نتيجة أو عبر التالي/السابق.
            }
        }
    }

    private fun scrollToSearchMatch(pageIndex: Int, query: String) {
        pendingSearchQuery = query
        pendingSearchMatchPage = pageIndex
        pendingSearchRetries = 0
        viewPager.post {
            performPendingSearchScroll()
        }
    }

    private fun clearPendingSearch() {
        pendingSearchQuery = null
        pendingSearchMatchPage = -1
        pendingSearchRetries = 0
    }

    /**
     * السبب الجذري لمشكلة الكتب الكبيرة: الاعتماد على تأخير ثابت (80ms) لإعادة المحاولة،
     * وهو غير كافٍ عندما يستغرق تخطيط الصفحات وقتًا طويلاً، وقد يتكرر بلا نهاية.
     * الحل: حد أقصى للمحاولات + انتقال event-driven عند اكتمال layout النص (doOnLayout-style)
     * بدل انتظار أعمى.
     */
    private fun performPendingSearchScroll() {
        val query = pendingSearchQuery ?: return
        val targetPage = pendingSearchMatchPage
        if (targetPage < 0) return

        val rv = viewPager.getChildAt(0) as? RecyclerView ?: run {
            if (++pendingSearchRetries > MAX_SEARCH_SCROLL_RETRIES) { clearPendingSearch(); return }
            viewPager.post { performPendingSearchScroll() }
            return
        }
        val vh = rv.findViewHolderForAdapterPosition(targetPage) as? BookPageAdapter.PageViewHolder
        if (vh == null) {
            // VH غير مرتبط بعد (offscreen) — انتقل للصفحة مرة واحدة ثم انتظر التصاقها.
            if (viewPager.currentItem != targetPage) {
                viewPager.setCurrentItem(targetPage, true)
                pendingSearchRetries = 0 // الانتقال نفسه يُعيد المحاولة عبر onPageSelected
                return
            }
            if (++pendingSearchRetries > MAX_SEARCH_SCROLL_RETRIES) { clearPendingSearch(); return }
            viewPager.postDelayed({ performPendingSearchScroll() }, SEARCH_RETRY_DELAY_MS)
            return
        }

        val tv = vh.tvContent
        // event-driven: لا نمرّر إلا بعد اكتمال layout النص الفعلي — لا تأخيرات ثابتة.
        tv.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View, l: Int, t: Int, r: Int, b: Int,
                ol: Int, ot: Int, or_: Int, ob: Int
            ) {
                v.removeOnLayoutChangeListener(this)
                if (pendingSearchMatchPage != targetPage) return
                scrollAndHighlightMatch(vh, query)
            }
        })
        if (tv.layout != null) {
            // التخطيط جاهز بالفعل — نفّذ فورًا (المستمع لن يُطلق إن لم يتغير شيء).
            tv.post { if (pendingSearchMatchPage == targetPage) scrollAndHighlightMatch(vh, query) }
        }
    }

    /** يمرر إلى أول تطابق داخل صفحة النتيجة ويبرزه مؤقتًا (3 ثوانٍ). */
    private fun scrollAndHighlightMatch(vh: BookPageAdapter.PageViewHolder, query: String) {
        val tv = vh.tvContent
        val layout = tv.layout ?: run { clearPendingSearch(); return }
        val fullText = tv.text.toString()
        val normalizedQuery = normalizeSearchQuery(query)
        val normalized = buildNormalizedSearchText(fullText)
        val matchIdx = normalized.text.indexOf(normalizedQuery, ignoreCase = true)
        if (matchIdx < 0) { clearPendingSearch(); return }
        val origOffset = normalized.offsetMap[matchIdx] ?: 0

        val line = layout.getLineForOffset(origOffset)
        val y = layout.getLineTop(line)
        vh.scrollView.smoothScrollTo(0, y)

        // Highlight مؤقت: تمييز التطابق بلون خفيف يزول تلقائيًا بعد 3 ثوانٍ.
        searchHighlightRunnable?.let { tv.removeCallbacks(it) }
        val endOffset = normalized.offsetMap.getOrNull(matchIdx + normalizedQuery.length)
            ?: (origOffset + normalizedQuery.length).coerceAtMost(fullText.length)
        if (endOffset > origOffset && tv.text is Spannable) {
            val sp = tv.text as Spannable
            val span = BackgroundColorSpan(SEARCH_HIGHLIGHT_COLOR)
            sp.setSpan(span, origOffset, endOffset, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            searchHighlightRunnable = Runnable { sp.removeSpan(span) }.also {
                tv.postDelayed(it, SEARCH_HIGHLIGHT_MS)
            }
        }
        clearPendingSearch()
    }

    /** يزيل التشكيل من استعلام البحث (لا يشترط الخريطة — الاستعلام مُدخل مستخدم). */
    private fun stripDiacritics(text: String): String {
        return text.replace(Regex("[\u064B-\u065F\u0670\u06D6-\u06ED]"), "")
    }

    /** يطبع استعلام البحث: بلا تشكيل، همزات موحّدة (آ/أ/إ/ٱ→ا، ؤ→و، ئ→ي، ى→ي، ة→ه)،
     *  وأرقام عربية-هندية/فارسية موحّدة إلى أرقام ASCII ليُطابق «12» نص «١٢». */
    private fun normalizeSearchQuery(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            val code = c.code
            sb.append(when {
                code in 0x064B..0x065F || code == 0x0670 || code in 0x06D6..0x06ED -> ""
                c == '\u0622' || c == '\u0623' || c == '\u0625' || c == '\u0671' || c == '\u0621' -> '\u0627'
                c == '\u0624' -> '\u0648'
                c == '\u0626' -> '\u064A'
                c == '\u0649' -> '\u064A'
                c == '\u0629' -> '\u0647'
                code in 0x0660..0x0669 -> ('0'.code + code - 0x0660).toChar()
                code in 0x06F0..0x06F9 -> ('0'.code + code - 0x06F0).toChar()
                else -> c
            })
        }
        return sb.toString()
    }

    /**
     * نبني النص المطبَّع للصفحة مع خريطة مواضع (normalized index -> original
     * index) حتى تُحسب المعاينة والـ highlight على النص الأصلي مباشرة.
     */
    private fun buildNormalizedSearchText(text: String): NormalizedSearchText {
        val sb = StringBuilder(text.length)
        val map = IntArray(text.length)
        var originalIdx = 0
        for (c in text) {
            val code = c.code
            val keep = !(code in 0x064B..0x065F || code == 0x0670 ||
                code in 0x06D6..0x06ED || code in 0x08F0..0x08FF)
            if (keep) {
                map[sb.length] = originalIdx
                val mapped = when {
                    code in 0x0660..0x0669 -> ('0'.code + code - 0x0660).toChar()
                    code in 0x06F0..0x06F9 -> ('0'.code + code - 0x06F0).toChar()
                    else -> c
                }
                sb.append(mapped)
            }
            originalIdx++
        }
        return NormalizedSearchText(sb.toString(), IntArray(sb.length) { map[it] })
    }

    // ------------------------------------------------------------------
    // TOC drawer
    // ------------------------------------------------------------------

    private fun hiddenTocTranslationX(): Float =
        if (isRtlLayout) tocDrawerWidthPx.toFloat() else -tocDrawerWidthPx.toFloat()

    // ------------------------------------------------------------------
    // Pagination
    // ------------------------------------------------------------------

    private fun loadBookContent() {
        loadingView.visibility = View.VISIBLE
        viewPager.visibility = View.GONE
        findViewById<View>(R.id.errorState)?.visibility = View.GONE

        lifecycleScope.launch {
            val content = try {
                withContext(Dispatchers.IO) {
                    ShamelaBookStorage.loadBookContent(this@ShamelaBookReaderActivity, bookId)
                }
            } catch (e: Exception) {
                showLoadError("تعذّر قراءة بيانات الكتاب", e.message)
                return@launch
            }

            if (content == null) {
                if (onlineMode && onlineHfPath.isNotBlank()) {
                    loadBookContentOnline()
                    return@launch
                }
                showLoadError(
                    "الكتاب غير محمّل على هذا الجهاز",
                    "حمّله من المكتبة أولاً ثم أعد المحاولة"
                )
                return@launch
            }
            bookContent = content
            ShamelaBookStorage.saveLastReadTime(this@ShamelaBookReaderActivity, bookId)

            // كل المعالجة الثقيلة (نزع وسوم HTML + تجميع النص الكامل + الترقيم)
            // خارج الـ main thread — كان تجميع النص يجري على واجهة المستخدم
            // ويُجمّد التطبيق قبل فتح الكتاب الضخم.
            val pages = try {
                withContext(Dispatchers.Default) {
                    fullText = content.pages.joinToString("\n\n") { stripHtml(it.body) }
                    paginateBook()
                }
            } catch (e: Exception) {
                showLoadError("تعذّر تجهيز صفحات الكتاب", e.message)
                return@launch
            }
            displayPages = pages

            try {
                buildAdapterAndBind()
            } catch (e: Exception) {
                showLoadError("تعذّر عرض صفحات الكتاب", e.message)
                return@launch
            }

            val startFromBeginning = intent.getBooleanExtra("START_FROM_BEGINNING", false)
            val targetPage = if (startFromBeginning) 0 else restoreReadingPosition()
            if (targetPage > 0) {
                navigateToPage(targetPage, smoothScroll = false)
            } else {
                lastIntendedPage = 0
                updatePageInfo(0)
            }

            loadingView.visibility = View.GONE
            viewPager.visibility = View.VISIBLE

            setupToc()
        }
    }

    /**
     * خطأ محلي في صفحة القراءة (لا يخرج المستخدم من الكتاب): يعرض رسالة
     * مع زر «إعادة المحاولة» مع بقاء بيانات الكتاب الحالية كما هي.
     */
    private fun showLoadError(title: String, detail: String?) {
        if (!isFinishing && !isDestroyed) {
            loadingView.visibility = View.GONE
            viewPager.visibility = View.GONE
            findViewById<View>(R.id.errorState)?.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvErrorMessage)?.text = title
            findViewById<TextView>(R.id.tvErrorDetail)?.text = detail ?: ""
        }
    }

    private fun setupRetryButton() {
        findViewById<View>(R.id.btnRetryLoad)?.setOnClickListener {
            findViewById<View>(R.id.errorState)?.visibility = View.GONE
            loadBookContent()
        }
    }

    /**
     * قراءة مباشرة من الإنترنت دون تحميل مسبق:
     * يحمّل أولًا البيانات الموجزة (متاح/فهرست) ثم يبثّ الصفحات سطرًا-سطرًا،
     * ويعرض أول الصفحات بمجرد وصولها قبل اكتمال الجلب.
     */
    private fun loadBookContentOnline() {
        if (!ShamelaOnlineReader.isNetworkAvailable(this)) {
            Toast.makeText(this, "لا يوجد اتصال بالإنترنت لقراءة الكتاب مباشرة", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        loadingView.visibility = View.VISIBLE
        viewPager.visibility = View.GONE

        lifecycleScope.launch {
            val pendingRequest = withContext(Dispatchers.IO) {
                try {
                    val metadata = ShamelaOnlineReader.fetchMetadata(onlineHfPath)
                    val toc = ShamelaOnlineReader.fetchToc(onlineHfPath)
                    metadata to toc
                } catch (e: Exception) {
                    null
                }
            }
            if (pendingRequest == null) {
                Toast.makeText(this@ShamelaBookReaderActivity, "تعذّر جلب بيانات الكتاب من المكتبة", Toast.LENGTH_LONG).show()
                loadingView.visibility = View.GONE
                finish()
                return@launch
            }
            val (metadata, toc) = pendingRequest
            onlineLoadedPages = mutableListOf()

            // مقارنة حداثة الإصدار قبل/أثناء القراءة (مهمة #5): تُعرَض فحسب،
            // فالقراءة المباشرة تعتمد دائمًا على آخر إصدار متاح من المصدر.
            var lastRefreshAt = 0L
            val streamError = withContext(Dispatchers.IO) {
                try {
                    ShamelaOnlineReader.streamPages(onlineHfPath) { page ->
                        onlineLoadedPages.add(page)
                        // تحديث تدريجي محدود: يُعاد الترقيم كلما وصلت دفعة كافية.
                        val now = SystemClock.elapsedRealtime()
                        if (onlineLoadedPages.size >= PageChromeMetrics.MIN_ONLINE_PROGRESS_PAGES && now - lastRefreshAt >= PageChromeMetrics.ONLINE_REFRESH_INTERVAL_MS) {
                            lastRefreshAt = now
                            applyOnlineSnapshot(metadata, toc)
                        }
                    }
                    null
                } catch (e: Exception) {
                    e
                }
            }

            // الدفعة النهائية — تُضمن إنهاء الترقيم وتجهيز الفهرس.
            applyOnlineSnapshot(metadata, toc)

            ShamelaBookStorage.saveLastReadTime(this@ShamelaBookReaderActivity, bookId)

            loadingView.visibility = View.GONE
            viewPager.visibility = View.VISIBLE

            setupToc()

            if (streamError != null) {
                Toast.makeText(
                    this@ShamelaBookReaderActivity,
                    "انقطع الاتصال أثناء الجلب، عُرضت ${onlineLoadedPages.size} صفحة",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /** يعيد الترقيم من الدفعة الحالية من الصفحات المرتجعة. */
    private fun applyOnlineSnapshot(metadata: ShamelaBook, toc: List<ShamelaTocEntry>) {
        lifecycleScope.launch {
            val snapshot = onlineLoadedPages.toList()
            val pendingContent = ShamelaBookContent(metadata, toc, snapshot)
            val ft = snapshot.joinToString("\n\n") { stripHtml(it.body) }

            val anchorCharOffset = displayPages.getOrNull(lastIntendedPage)?.startOffset
                ?.takeIf { it >= 0 }
                ?: findCharOffset(lastIntendedPage)
            val onlineRendered = displayPages.mapNotNull { it.startOffset }
            val restoredIndex = onlineRendered.indexOfFirst { it == anchorCharOffset }
                .let { if (it < 0) -1 else it }

            bookContent = pendingContent
            fullText = ft
            val pages = withContext(Dispatchers.Default) { paginateBook() }
            displayPages = pages
            buildAdapterAndBind()

            if (!onlineFirstShowDone) {
                onlineFirstShowDone = true
                val startFromBeginning = intent.getBooleanExtra("START_FROM_BEGINNING", false)
                val targetPage = if (startFromBeginning) 0 else restoreReadingPosition()
                if (targetPage > 0) navigateToPage(targetPage, smoothScroll = false) else {
                    lastIntendedPage = 0
                    updatePageInfo(0)
                }
            } else if (restoredIndex >= 0 && restoredIndex < displayPages.size) {
                navigateToPage(restoredIndex, smoothScroll = false)
            } else {
                lastIntendedPage = 0
                updatePageInfo(0)
            }
        }
    }

    private fun buildAdapterAndBind() {
        pageAdapter = BookPageAdapter(
            pages = displayPages,
            bookTitle = bookTitle,
            originalTotalPages = bookContent?.pages?.size ?: displayPages.size,
            fontSize = fontSize,
            lineSpacing = lineSpacing,
            typeface = currentTypeface,
            onPageScrollState = { },
            onScrollViewReady = { position, scrollView ->
                runOnUiThread {
                    gestureLayout.updateScrollState(scrollView)
                    if (position == pendingSearchMatchPage) {
                        performPendingSearchScroll()
                    }
                }
            },
            pageLayoutRes = R.layout.item_book_page_tf
        )
        viewPager.adapter = pageAdapter
    }

    private fun paginateBook(): List<BookTextPaginator.Page> {
        val content = bookContent ?: return listOf(BookTextPaginator.Page(0, ""))
        val ft = fullText.ifEmpty { content.pages.joinToString("\n\n") { stripHtml(it.body) } }

        val rawWidth = if (viewPager.width > 0) viewPager.width
            else (resources.displayMetrics.widthPixels * readingWidth).toInt()
        val rawHeight = if (viewPager.height > 0) viewPager.height
            else (resources.displayMetrics.heightPixels * 0.85f).toInt()

        val horizontalChromePx = dp(
            PageChromeMetrics.CARD_MARGIN_HORIZONTAL_DP * 2 +
                PageChromeMetrics.CARD_PADDING_END_DP +
                PageChromeMetrics.PROGRESS_COLUMN_WIDTH_DP +
                marginSize * 2
        )

        val bookTitleRowPx = singleLineHeightPx(sp(PageChromeMetrics.BOOK_TITLE_TEXT_SIZE_SP)) +
            dp(PageChromeMetrics.BOOK_TITLE_MARGIN_BOTTOM_DP)
        val pageNumberRowPx = singleLineHeightPx(sp(PageChromeMetrics.PAGE_NUMBER_TEXT_SIZE_SP)) +
            dp(PageChromeMetrics.PAGE_NUMBER_MARGIN_TOP_DP)

        val verticalChromePx = dp(
            PageChromeMetrics.CARD_MARGIN_VERTICAL_DP * 2 +
                PageChromeMetrics.SCROLL_PADDING_TOP_DP +
                PageChromeMetrics.SCROLL_PADDING_BOTTOM_DP
        ) + bookTitleRowPx + pageNumberRowPx

        val pageWidth = (rawWidth - horizontalChromePx).toInt().coerceAtLeast(1)
        val pageHeight = (rawHeight - verticalChromePx).toInt().coerceAtLeast(1)

        val pages = BookTextPaginator.paginate(this, ft, pageWidth, pageHeight, fontSize, lineSpacing, fontFile)

        // Build cumulative text lengths for each original (Shamela) page
        val origPageEnds = mutableListOf<Int>()
        var cumulativeLen = 0
        for ((i, origPage) in content.pages.withIndex()) {
            cumulativeLen += stripHtml(origPage.body).length
            if (i < content.pages.lastIndex) cumulativeLen += 2 // "\n\n"
            origPageEnds.add(cumulativeLen)
        }

        // Map each display page to its original page number using text position in fullText.
        // Use a running search position for O(n) performance instead of O(n²).
        var searchPos = 0
        return pages.map { dp ->
            val idx = if (dp.text.isNotEmpty()) {
                val from = maxOf(searchPos, dp.startOffset)
                ft.indexOf(dp.text, from).takeIf { it >= 0 }
                    ?: ft.indexOf(dp.text.take(20), from)
                    ?: -1
            } else -1
            if (idx >= 0) searchPos = idx + 1
            val origNum = if (idx >= 0) {
                val origIdx = origPageEnds.indexOfFirst { idx < it }
                    .let { if (it < 0) content.pages.lastIndex else it }
                content.pages[origIdx].pageNum
            } else null
            dp.copy(originalPageNum = origNum)
        }
    }

    private fun singleLineHeightPx(textSizePx: Float): Float {
        val paint = TextPaint().apply { textSize = textSizePx }
        val fm = paint.fontMetrics
        return ceil((fm.bottom - fm.top).toDouble()).toFloat()
    }

    private fun repaginate() {
        if (bookContent == null) return
        repaginateJob?.cancel()
        val anchorPage = lastIntendedPage.coerceIn(0, (displayPages.size - 1).coerceAtLeast(0))
        // مرساة مستقرّة: char offset داخل fullText (لا يتغير عند إعادة الترقيم).
        val anchorCharOffset = displayPages.getOrNull(anchorPage)?.startOffset
            ?.takeIf { it >= 0 }
            ?: findCharOffset(anchorPage)
        repaginateJob = lifecycleScope.launch {
            val pages = try {
                withContext(Dispatchers.Default) { paginateBook() }
            } catch (e: Exception) {
                // فشل إعادة الترقيم لا يطرد المستخدم من الكتاب: نُبقي الصفحات
                // الحالية سليمة ونخطر فحسب.
                Toast.makeText(this@ShamelaBookReaderActivity, "تعذّر تحديث التخطيط", Toast.LENGTH_SHORT).show()
                return@launch
            }
            displayPages = pages
            buildAdapterAndBind()
            val restoredPos = (
                if (anchorCharOffset >= 0) {
                    findPageByCharOffset(anchorCharOffset).takeIf { it >= 0 }
                } else null
                ) ?: anchorPage.coerceIn(0, (displayPages.size - 1).coerceAtLeast(0))
            lastIntendedPage = restoredPos
            viewPager.setCurrentItem(restoredPos, false)
            updatePageInfo(restoredPos)
        }
    }

    private fun updatePageInfo(position: Int) {
        if (displayPages.isEmpty()) return
        // أظهر رقم الصفحة الأصلية بالنسبة لإجمالي الصفحات الأصلية للكتاب
        // (وليس عدد الصفحات الافتراضية الناتجة عن الترقيم).
        val page = displayPages.getOrNull(position)
        val current = page?.originalPageNum ?: (position + 1).coerceIn(1, displayPages.size)
        val totalOrig = bookContent?.pages?.size?.coerceAtLeast(1) ?: displayPages.size
        tvPageInfo.text = "صفحة $current / $totalOrig"
    }

    private fun setupToc() {
        val content = bookContent ?: return
        val rvToc = findViewById<RecyclerView>(R.id.rvToc)
        rvToc.layoutManager = LinearLayoutManager(this)
        val tocAdapter = ShamelaTocAdapter(content.toc) { entry ->
            val targetPage = findPageForTocEntry(entry)
            if (targetPage >= 0) {
                navigateToPage(targetPage)
                overlayManager.closeCurrent()
            }
        }
        rvToc.adapter = tocAdapter
        lifecycleScope.launch(Dispatchers.Default) {
            val mapping = buildTocPageMapping(content)
            withContext(Dispatchers.Main) { tocAdapter.setPageNumbers(mapping) }
        }
    }

    private fun buildTocPageMapping(content: ShamelaBookContent): Map<Int, Int> {
        val mapping = mutableMapOf<Int, Int>()
        val pageIds = content.pages.map { it.pageId }
        val totalShamelaPages = content.pages.size.coerceAtLeast(1)
        val totalDisplayPages = displayPages.size.coerceAtLeast(1)
        val displayPerPage = totalDisplayPages.toFloat() / totalShamelaPages
        for (entry in content.toc) {
            val targetIdx = pageIds.indexOf(entry.pageId)
            if (targetIdx >= 0) {
                val approxPage = (targetIdx * displayPerPage).toInt().coerceIn(0, displayPages.size - 1)
                mapping[entry.titleId] = approxPage + 1
            }
        }
        return mapping
    }

    private fun findPageForTocEntry(entry: ShamelaTocEntry): Int {
        val content = bookContent ?: return -1
        val pageIds = content.pages.map { it.pageId }
        val targetIdx = pageIds.indexOf(entry.pageId)
        if (targetIdx < 0) return 0
        val totalShamelaPages = content.pages.size.coerceAtLeast(1)
        val totalDisplayPages = displayPages.size.coerceAtLeast(1)
        val displayPerPage = totalDisplayPages.toFloat() / totalShamelaPages
        val approxDisplayPage = (targetIdx * displayPerPage).toInt()
        return approxDisplayPage.coerceIn(0, displayPages.size - 1)
    }

    private fun stripHtml(html: String): String {
        var result = html
        result = result.replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
            try { String(Character.toChars(Integer.parseInt(match.groupValues[1], 16))) } catch (_: Exception) { "" }
        }
        result = result.replace(Regex("&#(\\d+);")) { match ->
            try { String(Character.toChars(Integer.parseInt(match.groupValues[1]))) } catch (_: Exception) { "" }
        }
        result = result
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        result = result
            .replace(Regex("<span[^>]*data-type=\"title\"[^>]*>"), "\n\u25C6 ")
            .replace(Regex("<span[^>]*>"), "").replace(Regex("</span>"), "\n")
            .replace(Regex("<hr[^>]*/?>"), "\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n")
            .replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("<p[^>]*>"), "\n").replace(Regex("</p>"), "\n")
            .replace(Regex("<div[^>]*>"), "\n").replace(Regex("</div>"), "\n")
            .replace(Regex("<b>"), "").replace(Regex("</b>"), "")
            .replace(Regex("<i>"), "").replace(Regex("</i>"), "")
            .replace(Regex("<u>"), "").replace(Regex("</u>"), "")
            .replace(Regex("<sup>"), "").replace(Regex("</sup>"), "")
            .replace(Regex("<sub>"), "").replace(Regex("</sub>"), "")
            .replace(Regex("<[^>]+>"), "")
        result = result.replace("\r", "\n").replace(Regex("\\n{3,}"), "\n\n").trim()
        return result
    }

    private fun scheduleScrollSave(position: Int) {
        scrollSaveRunnable?.let { scrollSaveHandler?.removeCallbacks(it) }
        scrollSaveRunnable = Runnable { saveReadingProgress() }
        scrollSaveHandler?.postDelayed(scrollSaveRunnable!!, 500)
    }

    private fun loadSettings() {
        fontSize = ShamelaBookStorage.getFontSize(this)
        lineSpacing = ShamelaBookStorage.getLineSpacing(this)
        paraSpacing = ShamelaBookStorage.getParaSpacing(this)
        marginSize = ShamelaBookStorage.getMarginSize(this)
        readingWidth = ShamelaBookStorage.getReadingWidth(this)
        fontFile = ShamelaBookStorage.getReaderFont(this)
        val alignInt = ShamelaBookStorage.getTextAlign(this)
        textAlign = when (alignInt) {
            1 -> Layout.Alignment.ALIGN_OPPOSITE
            2 -> Layout.Alignment.ALIGN_CENTER
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        currentTypeface = FontManager.loadTypeface(this, fontFile)
    }

    private fun saveSettings() {
        ShamelaBookStorage.saveFontSize(this, fontSize)
        ShamelaBookStorage.saveLineSpacing(this, lineSpacing)
        ShamelaBookStorage.saveParaSpacing(this, paraSpacing)
        ShamelaBookStorage.saveMarginSize(this, marginSize)
        ShamelaBookStorage.saveReadingWidth(this, readingWidth)
        ShamelaBookStorage.saveReaderFont(this, fontFile)
        val alignInt = when (textAlign) {
            Layout.Alignment.ALIGN_OPPOSITE -> 1
            Layout.Alignment.ALIGN_CENTER -> 2
            else -> 0
        }
        ShamelaBookStorage.saveTextAlign(this, alignInt)
    }

    private fun saveReadingProgress() {
        val page = displayPages.getOrNull(lastIntendedPage)
        // احفظ الموضع بمعيار مستقر (char offset داخل fullText) بدلاً من فهرس الصفحة
        // الافتراضية الذي يتغير عند تعديل حجم الخط أو إعادة الترقيم.
        val charOffset = page?.startOffset?.takeIf { it >= 0 }
            ?: findCharOffset(lastIntendedPage)
        if (charOffset >= 0) {
            ShamelaBookStorage.saveLastReadCharOffset(this, bookId, charOffset)
        }
        // ولأغراض العرض في بطاقات المكتبة نحفظ رقم الصفحة الأصلية (وليس الفهرس الافتراضي).
        val origNum = page?.originalPageNum ?: (lastIntendedPage + 1)
        ShamelaBookStorage.saveLastReadPage(this, bookId, origNum)
    }

    /**
     * يسترجع موضع القراءة المحفوظ معتمدًا على معيار مستقر:
     * 1) char offset داخل fullText (الأدق — يبقى صحيحًا مهما تغيّر الترقيم).
     * 2) رقم الصفحة الأصلية (originalPageNum) كاحتياط عند عدم وجود char offset.
     * 3) الفهرس الافتراضي القديم كآخر خيار للتوافق مع البيانات المحفوظة سابقًا.
     */
    private fun restoreReadingPosition(): Int {
        val savedOffset = ShamelaBookStorage.getLastReadCharOffset(this, bookId)
        if (savedOffset >= 0) {
            val byOffset = findPageByCharOffset(savedOffset)
            if (byOffset >= 0) return byOffset
        }

        val savedPageNum = ShamelaBookStorage.getLastReadPage(this, bookId)
        if (savedPageNum > 0) {
            val byOrigNum = displayPages.indexOfFirst { it.originalPageNum == savedPageNum }
            if (byOrigNum >= 0) return byOrigNum
            // البيانات القديمة كانت تحفظ الفهرس الافتراضي مباشرة — نجربها كاحتياط أخير.
            if (savedPageNum in displayPages.indices) return savedPageNum
        }
        return 0
    }

    // ------------------------------------------------------------------
    // Bookmarks
    // ------------------------------------------------------------------

    private fun findCharOffset(position: Int): Int {
        val pageText = displayPages.getOrNull(position)?.text?.trimStart() ?: return -1
        if (fullText.isEmpty() || pageText.isEmpty()) return -1
        val idx = fullText.indexOf(pageText)
        return if (idx >= 0) idx else fullText.indexOf(pageText.take(20))
    }

    private fun findPageByCharOffset(charOffset: Int): Int {
        if (charOffset < 0 || fullText.isEmpty() || displayPages.isEmpty()) return -1
        for (i in displayPages.indices) {
            val page = displayPages[i]
            if (page.text.isEmpty()) continue
            val start = page.startOffset
            if (start >= 0 && start <= charOffset && charOffset < start + page.text.length) {
                return i
            }
        }
        return -1
    }

    private fun toggleBookmark() {
        val currentPage = lastIntendedPage
        val charOffset = findCharOffset(currentPage)
        val existing = if (charOffset >= 0) {
            ShamelaBookmarkManager.getAll(this).find {
                it.bookId == bookId && it.charOffset == charOffset
            }
        } else {
            ShamelaBookmarkManager.getBookmark(this, bookId, currentPage)
        }
        if (existing != null) {
            ShamelaBookmarkManager.remove(this, bookId, existing.page)
            Toast.makeText(this, "تم حذف العلامة", Toast.LENGTH_SHORT).show()
        } else {
            val pageText = displayPages.getOrNull(currentPage)?.text?.take(200) ?: ""
            ShamelaBookmarkManager.add(
                this,
                ShamelaBookmark(
                    bookId = bookId,
                    page = currentPage,
                    charOffset = charOffset,
                    bookTitle = bookTitle,
                    text = pageText,
                    time = System.currentTimeMillis()
                )
            )
            Toast.makeText(this, "تم حفظ العلامة", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showBookmarksList() {
        val bookmarks = ShamelaBookmarkManager.getByBookId(this, bookId)
        if (bookmarks.isEmpty()) {
            Toast.makeText(this, "لا توجد علامات مرجعية في هذا الكتاب", Toast.LENGTH_SHORT).show()
            return
        }
        val items = bookmarks.sortedBy { it.page }.map { bm ->
            val text = bm.text.take(80).replace("\n", " ")
            "${bm.page + 1}: $text"
        }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("العلامات المرجعية")
            .setItems(items) { _, which ->
                val target = bookmarks.sortedBy { it.page }[which]
                val pos = if (target.charOffset >= 0) {
                    findPageByCharOffset(target.charOffset).takeIf { it >= 0 }
                } else null
                navigateToPage(pos ?: target.page)
                overlayManager.closeCurrent()
            }
            .setPositiveButton("إغلاق", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        saveReadingProgress()
    }

    override fun onDestroy() {
        super.onDestroy()
        searchJob?.cancel()
        repaginateJob?.cancel()
        scrollSaveHandler?.removeCallbacksAndMessages(null)
        repaginateHandler?.removeCallbacksAndMessages(null)
        searchDebounceHandler?.removeCallbacksAndMessages(null)
        saveReadingProgress()
    }

    companion object {
        const val PAGE_CONTEXT_HALF = 2
        private const val MAX_SEARCH_SCROLL_RETRIES = 12
        private const val SEARCH_RETRY_DELAY_MS = 60L
        private const val SEARCH_HIGHLIGHT_MS = 3000L
        private const val SEARCH_HIGHLIGHT_COLOR = 0x401A73E8
    }
}
