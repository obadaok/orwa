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
import android.text.Editable
import android.text.Layout
import android.text.TextPaint
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.ceil

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
    private var displayPages: List<BookTextPaginator.Page> = emptyList()
    private var pageAdapter: BookPageAdapter? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shamela_book_reader)

        bookId = intent.getIntExtra("BOOK_ID", 0)
        bookTitle = intent.getStringExtra("BOOK_TITLE") ?: ""

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
        viewPager.isUserInputEnabled = false

        gestureLayout.setup { direction ->
            val current = viewPager.currentItem
            val target = current + direction
            if (target in 0 until (viewPager.adapter?.itemCount ?: 0)) {
                viewPager.setCurrentItem(target, true)
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePageInfo(position)
                updateBookmarkIcon()
                scheduleScrollSave(position)
            }
        })

        viewPager.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val w = viewPager.width
                val h = viewPager.height
                if (w <= 0 || h <= 0) return
                val changed = w != lastKnownViewPagerWidth || h != lastKnownViewPagerHeight
                val hadPreviousSize = lastKnownViewPagerWidth != 0 && lastKnownViewPagerHeight != 0
                lastKnownViewPagerWidth = w
                lastKnownViewPagerHeight = h
                if (changed && hadPreviousSize && bookContent != null) {
                    repaginate()
                }
            }
        })

        setupOverlayManager()
        setupBackHandling()
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

        overlayManager.onOverlayActiveChanged = { isActive ->
            gestureLayout.setSwipeEnabled(!isActive)
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

        circularMenu.addMenuItem(R.drawable.ic_list, "الفهرس") {
            overlayManager.open(ReaderOverlayManager.Overlay.TOC)
        }

        circularMenu.addMenuItem(R.drawable.ic_auto_awesome_black_24dp, "إعدادات") {
            overlayManager.open(ReaderOverlayManager.Overlay.SETTINGS_PANEL)
        }

        circularMenu.addMenuItem(R.drawable.ic_bookmark_outline, "حفظ") {
            toggleBookmark()
        }

        circularMenu.addMenuItem(R.drawable.ic_share, "مشاركة") {
            shareCurrentPage()
        }

        circularMenu.addMenuItem(R.drawable.ic_copy, "نسخ") {
            copyCurrentPage()
        }

        circularMenu.addMenuItem(R.drawable.ic_search, "صفحة") {
            overlayManager.open(ReaderOverlayManager.Overlay.JUMP_PANEL)
        }

        circularMenu.addMenuItem(R.drawable.ic_book_24dp, "معلومات") {
            showBookInfo()
        }
    }

    private fun shareCurrentPage() {
        val pageText = displayPages.getOrNull(viewPager.currentItem)?.text ?: return
        val shareText = "$bookTitle\n\n$pageText"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, "مشاركة اقتباس"))
    }

    private fun copyCurrentPage() {
        val pageText = displayPages.getOrNull(viewPager.currentItem)?.text ?: return
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

        view.findViewById<TextView>(R.id.tvPageRange).text = "من 1 إلى ${displayPages.size}"
        val etPage = view.findViewById<EditText>(R.id.etPageNumber)
        etPage.requestFocus()
        etPage.postDelayed({
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etPage, InputMethodManager.SHOW_IMPLICIT)
        }, 200)

        view.findViewById<TextView>(R.id.btnJumpGo).setOnClickListener {
            val pageNum = etPage.text.toString().toIntOrNull() ?: return@setOnClickListener
            val index = (pageNum - 1).coerceIn(0, displayPages.size - 1)
            viewPager.setCurrentItem(index, true)
            updatePageInfo(index)
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

        searchAdapter = ReaderSearchAdapter(emptyList()) { pageIndex ->
            viewPager.setCurrentItem(pageIndex, true)
            updatePageInfo(pageIndex)
        }
        rvResults.layoutManager = LinearLayoutManager(this)
        rvResults.adapter = searchAdapter

        searchResults.clear()
        currentSearchIndex = -1

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
                searchDebounceRunnable = Runnable { performReaderSearch(query, rvResults, navRow, tvCount, tvNoResults) }
                searchDebounceHandler?.postDelayed(searchDebounceRunnable!!, 250L)
            }
        })

        ivClear.setOnClickListener { etSearch.text?.clear() }

        view.findViewById<ImageView>(R.id.ivSearchPrev).setOnClickListener {
            if (searchResults.isEmpty()) return@setOnClickListener
            currentSearchIndex = (currentSearchIndex - 1 + searchResults.size) % searchResults.size
            searchAdapter.setActivePosition(currentSearchIndex)
            val result = searchResults[currentSearchIndex]
            viewPager.setCurrentItem(result.pageIndex, true)
            updatePageInfo(result.pageIndex)
            rvResults.scrollToPosition(currentSearchIndex)
            tvCount.text = "${currentSearchIndex + 1} / ${searchResults.size}"
        }

        view.findViewById<ImageView>(R.id.ivSearchNext).setOnClickListener {
            if (searchResults.isEmpty()) return@setOnClickListener
            currentSearchIndex = (currentSearchIndex + 1) % searchResults.size
            searchAdapter.setActivePosition(currentSearchIndex)
            val result = searchResults[currentSearchIndex]
            viewPager.setCurrentItem(result.pageIndex, true)
            updatePageInfo(result.pageIndex)
            rvResults.scrollToPosition(currentSearchIndex)
            tvCount.text = "${currentSearchIndex + 1} / ${searchResults.size}"
        }

        view.findViewById<TextView>(R.id.btnCloseSearch).setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
            overlayManager.closeCurrent()
        }

        dim.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
            overlayManager.closeCurrent()
        }
    }

    private fun performReaderSearch(
        query: String,
        rvResults: RecyclerView,
        navRow: View,
        tvCount: TextView,
        tvNoResults: TextView
    ) {
        if (query.isBlank() || displayPages.isEmpty()) {
            searchResults.clear()
            searchAdapter.updateResults(emptyList())
            navRow.visibility = View.GONE
            tvNoResults.visibility = View.VISIBLE
            tvNoResults.text = "ابدأ الكتابة للبحث..."
            return
        }

        val normalizedQuery = stripDiacritics(query.trim())
        if (normalizedQuery.isEmpty()) {
            searchResults.clear()
            searchAdapter.updateResults(emptyList())
            navRow.visibility = View.GONE
            tvNoResults.visibility = View.VISIBLE
            tvNoResults.text = "ابدأ الكتابة للبحث..."
            return
        }

        lifecycleScope.launch {
            val results = withContext(Dispatchers.Default) {
                val found = mutableListOf<ReaderSearchAdapter.SearchResult>()
                for ((index, page) in displayPages.withIndex()) {
                    val normalizedPage = stripDiacritics(page.text)
                    var searchFrom = 0
                    while (true) {
                        val matchIdx = normalizedPage.indexOf(normalizedQuery, searchFrom, ignoreCase = true)
                        if (matchIdx < 0) break

                        val snippetStart = (matchIdx - 40).coerceAtLeast(0)
                        val snippetEnd = (matchIdx + normalizedQuery.length + 40).coerceAtMost(page.text.length)
                        var snippet = page.text.substring(snippetStart, snippetEnd)
                        if (snippetStart > 0) snippet = "…$snippet"
                        if (snippetEnd < page.text.length) snippet = "$snippet…"

                        val localMatchStart = matchIdx - snippetStart + if (snippetStart > 0) 1 else 0
                        val localMatchEnd = localMatchStart + normalizedQuery.length

                        found.add(
                            ReaderSearchAdapter.SearchResult(
                                pageIndex = index,
                                pageNumber = index + 1,
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

            searchResults.clear()
            searchResults.addAll(results)
            currentSearchIndex = -1
            searchAdapter.updateResults(results)

            if (results.isEmpty()) {
                navRow.visibility = View.GONE
                tvNoResults.visibility = View.VISIBLE
                tvNoResults.text = "لا توجد نتائج"
            } else {
                navRow.visibility = View.VISIBLE
                tvNoResults.visibility = View.GONE
                currentSearchIndex = 0
                searchAdapter.setActivePosition(0)
                tvCount.text = "1 / ${results.size}"
                val firstResult = results[0]
                viewPager.setCurrentItem(firstResult.pageIndex, true)
                updatePageInfo(firstResult.pageIndex)
            }
        }
    }

    private fun stripDiacritics(text: String): String {
        return text.replace(Regex("[\u064B-\u065F\u0670\u06D6-\u06ED]"), "")
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

        lifecycleScope.launch {
            bookContent = withContext(Dispatchers.IO) {
                ShamelaBookStorage.loadBookContent(this@ShamelaBookReaderActivity, bookId)
            }

            if (bookContent == null) {
                Toast.makeText(this@ShamelaBookReaderActivity, "الكتاب غير محمّل", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            ShamelaBookStorage.saveLastReadTime(this@ShamelaBookReaderActivity, bookId)

            val pages = withContext(Dispatchers.Default) { paginateBook() }
            displayPages = pages

            buildAdapterAndBind()

            val lastPage = ShamelaBookStorage.getLastReadPage(this@ShamelaBookReaderActivity, bookId)
            val targetPage = if (lastPage in displayPages.indices) lastPage else 0
            if (targetPage > 0) {
                viewPager.setCurrentItem(targetPage, false)
            }
            updatePageInfo(targetPage)
            updateBookmarkIcon()

            loadingView.visibility = View.GONE
            viewPager.visibility = View.VISIBLE

            setupToc()
        }
    }

    private fun buildAdapterAndBind() {
        pageAdapter = BookPageAdapter(
            pages = displayPages,
            bookTitle = bookTitle,
            fontSize = fontSize,
            lineSpacing = lineSpacing,
            typeface = currentTypeface,
            onPageScrollState = { },
            onScrollViewReady = { scrollView ->
                runOnUiThread {
                    gestureLayout.updateScrollState(scrollView)
                }
            }
        )
        viewPager.adapter = pageAdapter
    }

    private fun paginateBook(): List<BookTextPaginator.Page> {
        val content = bookContent ?: return listOf(BookTextPaginator.Page(0, ""))
        val fullText = content.pages.joinToString("\n\n") { stripHtml(it.body) }

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

        return BookTextPaginator.paginate(this, fullText, pageWidth, pageHeight, fontSize, lineSpacing, fontFile)
    }

    private fun singleLineHeightPx(textSizePx: Float): Float {
        val paint = TextPaint().apply { textSize = textSizePx }
        val fm = paint.fontMetrics
        return ceil((fm.bottom - fm.top).toDouble()).toFloat()
    }

    private fun repaginate() {
        if (bookContent == null) return
        val currentPage = viewPager.currentItem
        lifecycleScope.launch {
            val pages = withContext(Dispatchers.Default) { paginateBook() }
            displayPages = pages
            buildAdapterAndBind()
            val restoredPos = if (currentPage < displayPages.size) currentPage else 0
            viewPager.setCurrentItem(restoredPos, false)
            updatePageInfo(restoredPos)
        }
    }

    private fun updatePageInfo(position: Int) {
        if (displayPages.isEmpty()) return
        val total = displayPages.size
        val current = (position + 1).coerceIn(1, total)
        tvPageInfo.text = "صفحة $current / $total"
    }

    private fun setupToc() {
        val content = bookContent ?: return
        val rvToc = findViewById<RecyclerView>(R.id.rvToc)
        rvToc.layoutManager = LinearLayoutManager(this)
        val tocAdapter = ShamelaTocAdapter(content.toc) { entry ->
            val targetPage = findPageForTocEntry(entry)
            if (targetPage >= 0) {
                viewPager.setCurrentItem(targetPage, true)
                updatePageInfo(targetPage)
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
        ShamelaBookStorage.saveLastReadPage(this, bookId, viewPager.currentItem)
    }

    // ------------------------------------------------------------------
    // Bookmarks
    // ------------------------------------------------------------------

    private fun toggleBookmark() {
        val currentPage = viewPager.currentItem
        val prefs = getSharedPreferences("urwah_shamela_bookmarks", Context.MODE_PRIVATE)
        val key = "bm_${bookId}_${currentPage}"
        val existing = prefs.getString(key, null)
        if (existing != null) {
            prefs.edit().remove(key).apply()
            Toast.makeText(this, "تم حذف العلامة", Toast.LENGTH_SHORT).show()
        } else {
            val pageText = displayPages.getOrNull(currentPage)?.text?.take(200) ?: ""
            val json = JSONObject().apply {
                put("book_title", bookTitle)
                put("page", currentPage)
                put("text", pageText)
                put("time", System.currentTimeMillis())
            }
            prefs.edit().putString(key, json.toString()).apply()
            Toast.makeText(this, "تم حفظ العلامة", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateBookmarkIcon() {
        val currentPage = viewPager.currentItem
        val prefs = getSharedPreferences("urwah_shamela_bookmarks", Context.MODE_PRIVATE)
        val key = "bm_${bookId}_${currentPage}"
        val ivMenu = findViewById<ImageView>(R.id.ivMenu)
        if (prefs.getString(key, null) != null) {
            ivMenu.setImageResource(R.drawable.ic_bookmark_filled)
        } else {
            ivMenu.setImageResource(R.drawable.ic_more_menu)
        }
    }

    override fun onPause() {
        super.onPause()
        saveReadingProgress()
    }

    override fun onDestroy() {
        super.onDestroy()
        scrollSaveHandler?.removeCallbacksAndMessages(null)
        repaginateHandler?.removeCallbacksAndMessages(null)
        searchDebounceHandler?.removeCallbacksAndMessages(null)
        saveReadingProgress()
    }
}
