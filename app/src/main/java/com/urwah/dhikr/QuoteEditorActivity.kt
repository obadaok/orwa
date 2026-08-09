package com.urwah.dhikr

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max

class QuoteEditorActivity : AppCompatActivity() {

    private lateinit var tvContent: TextView
    private lateinit var tvMeta: TextView
    private lateinit var tvDivider: TextView
    private lateinit var tvWatermark: TextView
    private lateinit var previewRoot: View
    private lateinit var tvSelectionInfo: TextView
    private lateinit var bgSelector: RecyclerView
    private lateinit var btnSave: TextView
    private lateinit var btnShare: TextView
    private lateinit var btnClose: TextView
    private lateinit var btnTools: TextView
    private lateinit var toolsOverlay: FrameLayout
    private lateinit var toolsPanel: LinearLayout
    private lateinit var toolsScroll: androidx.core.widget.NestedScrollView
    private lateinit var btnCloseTools: TextView
    private lateinit var fontChips: LinearLayout
    private lateinit var btnCopy: TextView
    private lateinit var btnClearEffects: TextView
    private lateinit var btnDeleteSelection: TextView
    private lateinit var btnHideUnselected: TextView
    private lateinit var selectionTools: LinearLayout
    private lateinit var btnAlignRight: TextView
    private lateinit var btnAlignCenter: TextView
    private lateinit var btnAlignLeft: TextView
    private lateinit var btnAlignJustify: TextView
    private lateinit var fontSizeSlider: SeekBar
    private lateinit var btnBoldAll: TextView
    private lateinit var lineSpacingSlider: SeekBar
    private lateinit var tvLineSpacingValue: TextView
    private lateinit var paraSpacingSlider: SeekBar
    private lateinit var tvParaSpacingValue: TextView
    private lateinit var letterSpacingSlider: SeekBar
    private lateinit var tvLetterSpacingValue: TextView
    private lateinit var textWidthSlider: SeekBar
    private lateinit var tvTextWidthValue: TextView
    private lateinit var scrollView: androidx.core.widget.NestedScrollView

    private val scope = MainScope()
    private var currentAlign = Layout.Alignment.ALIGN_NORMAL
    private var isJustify = false
    private var currentBg = QuoteBackground.LIGHT
    private var currentTypeface: Typeface? = null
    private var currentFontSize = 18f
    private var currentLineSpacing = 1.6f
    private var currentParaSpacing = 1.0f
    private var currentLetterSpacing = 0f
    private var textWidth = 0.92f
    private var isBoldAll = false
    private var fullText = ""
    private var bookTitle = ""
    private var author = ""
    private var edition = ""

    private var selectionBase = -1
    private var selectionExtent = -1
    private var hideUnselected = false

    private enum class AdjustMode { NONE, NEW, START, END }
    private var adjustMode = AdjustMode.NONE

    private val bgList = QuoteBackground.entries.toList()

    private val selStart: Int get() = min(selectionBase, selectionExtent).coerceIn(0, fullText.length)
    private val selEnd: Int get() = max(selectionBase, selectionExtent).coerceIn(0, fullText.length)
    private val hasSelection: Boolean get() = selectionBase >= 0 && selectionExtent >= 0 && selStart < selEnd
    private val selLen: Int get() = selEnd - selStart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quote_editor)

        tvContent = findViewById(R.id.tvPreviewContent)
        tvMeta = findViewById(R.id.tvPreviewMeta)
        tvDivider = findViewById(R.id.tvPreviewDivider)
        tvWatermark = findViewById(R.id.tvPreviewWatermark)
        previewRoot = findViewById(R.id.previewRoot)
        tvSelectionInfo = findViewById(R.id.tvSelectionInfo)
        bgSelector = findViewById(R.id.bgSelector)
        btnSave = findViewById(R.id.btnSave)
        btnShare = findViewById(R.id.btnShare)
        btnClose = findViewById(R.id.btnClose)
        btnTools = findViewById(R.id.btnTools)
        toolsOverlay = findViewById(R.id.toolsOverlay)
        toolsPanel = findViewById(R.id.toolsPanel)
        toolsScroll = findViewById(R.id.toolsScroll)
        btnCloseTools = findViewById(R.id.btnCloseTools)
        fontChips = findViewById(R.id.fontChips)
        btnCopy = findViewById(R.id.btnCopy)
        btnClearEffects = findViewById(R.id.btnClearEffects)
        btnDeleteSelection = findViewById(R.id.btnDeleteSelection)
        btnHideUnselected = findViewById(R.id.btnHideUnselected)
        selectionTools = findViewById(R.id.selectionTools)
        btnAlignRight = findViewById(R.id.btnAlignRight)
        btnAlignCenter = findViewById(R.id.btnAlignCenter)
        btnAlignLeft = findViewById(R.id.btnAlignLeft)
        btnAlignJustify = findViewById(R.id.btnAlignJustify)
        fontSizeSlider = findViewById(R.id.fontSizeSlider)
        btnBoldAll = findViewById(R.id.btnBoldAll)
        lineSpacingSlider = findViewById(R.id.lineSpacingSlider)
        tvLineSpacingValue = findViewById(R.id.tvLineSpacingValue)
        paraSpacingSlider = findViewById(R.id.paraSpacingSlider)
        tvParaSpacingValue = findViewById(R.id.tvParaSpacingValue)
        letterSpacingSlider = findViewById(R.id.letterSpacingSlider)
        tvLetterSpacingValue = findViewById(R.id.tvLetterSpacingValue)
        textWidthSlider = findViewById(R.id.textWidthSlider)
        tvTextWidthValue = findViewById(R.id.tvTextWidthValue)
        scrollView = findViewById(R.id.editorScrollView)

        setupTouch()
        loadData()
        setupEffectsBar()
        setupBackgroundSelector()
        setupAlignControls()
        setupFontSizeSlider()
        setupAdvancedFormatting()
        setupFontChips()
        setupToolsPanel()
        setupButtons()
        refreshPreview()
    }

    private fun setupTouch() {
        tvContent.setOnTouchListener { _, event ->
            val layout = tvContent.layout ?: return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    scrollView.requestDisallowInterceptTouchEvent(true)
                    val touchOffset = offsetAt(event, layout)
                    if (hasSelection) {
                        if (touchOffset >= selStart && touchOffset <= selEnd) {
                            val distToStart = abs(touchOffset - selStart)
                            val distToEnd = abs(touchOffset - selEnd)
                            if (selLen < 4) {
                                adjustMode = AdjustMode.START
                                selectionBase = selEnd
                                selectionExtent = touchOffset
                            } else if (distToStart < distToEnd && distToStart < selLen / 3) {
                                adjustMode = AdjustMode.START
                                selectionBase = selEnd
                                selectionExtent = touchOffset
                            } else if (distToEnd < distToStart && distToEnd < selLen / 3) {
                                adjustMode = AdjustMode.END
                                selectionBase = selStart
                                selectionExtent = touchOffset
                            } else {
                                adjustMode = AdjustMode.NONE
                            }
                        } else {
                            clearSelection()
                            adjustMode = AdjustMode.NEW
                            selectionBase = touchOffset
                            selectionExtent = touchOffset
                        }
                    } else {
                        adjustMode = AdjustMode.NEW
                        selectionBase = touchOffset
                        selectionExtent = touchOffset
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (adjustMode == AdjustMode.NONE) return@setOnTouchListener true
                    val offset = offsetAt(event, layout)
                    selectionExtent = offset
                    updateSelectionHighlight()
                    updateSelectionUI()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (adjustMode == AdjustMode.NONE) {
                        if (hasSelection) updateSelectionUI()
                    } else if (!hasSelection) {
                        clearSelection()
                        refreshPreview()
                    } else {
                        adjustMode = AdjustMode.NONE
                        updateSelectionUI()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun offsetAt(event: MotionEvent, layout: android.text.Layout): Int {
        val line = layout.getLineForVertical(event.y.toInt()).coerceIn(0, layout.lineCount - 1)
        val off = layout.getOffsetForHorizontal(line, event.x)
        return off.coerceIn(0, fullText.length)
    }

    private fun clearSelection() {
        selectionBase = -1
        selectionExtent = -1
        adjustMode = AdjustMode.NONE
        selectionTools.visibility = View.GONE
        tvSelectionInfo.text = ""
    }

    private fun updateSelectionHighlight() {
        if (!hasSelection) return
        val ssb = SpannableStringBuilder(fullText)
        ssb.setSpan(
            BackgroundColorSpan(0x338B6F5E),
            selStart, selEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        applyBaseStyles(ssb)
        applyHideUnselected(ssb)
        tvContent.text = ssb
    }

    private fun updateSelectionUI() {
        if (hasSelection) {
            selectionTools.visibility = View.VISIBLE
            tvSelectionInfo.text = "$selLen حرف محدد"
        } else {
            selectionTools.visibility = View.GONE
            tvSelectionInfo.text = ""
        }
    }

    private fun copySelection() {
        if (!hasSelection) return
        val text = fullText.substring(selStart, selEnd)
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("quote", text))
        Toast.makeText(this, "تم نسخ النص المحدد", Toast.LENGTH_SHORT).show()
    }

    private fun deleteSelectedText() {
        if (!hasSelection) return
        val start = selStart
        val end = selEnd
        val removed = end - start

        fullText = fullText.substring(0, start) + fullText.substring(end)

        val newSpans = mutableMapOf<IntRange, QuoteSpanStyle>()
        for ((range, style) in currentSpans) {
            if (range.first >= end) {
                val shifted = (range.first - removed)..(range.last - removed)
                newSpans[shifted] = style
            } else if (range.last <= start) {
                newSpans[range] = style
            }
        }
        currentSpans.clear()
        currentSpans.putAll(newSpans)

        clearSelection()
        refreshPreview()
        Toast.makeText(this, "تم حذف النص المحدد", Toast.LENGTH_SHORT).show()
    }

    private fun loadData() {
        fullText = intent.getStringExtra("PAGE_TEXT") ?: ""
        bookTitle = intent.getStringExtra("BOOK_TITLE") ?: ""
        author = intent.getStringExtra("AUTHOR") ?: ""
        edition = intent.getStringExtra("EDITION") ?: ""
        val pageNumber = intent.getIntExtra("PAGE_NUMBER", 0)
        val fontFile = intent.getStringExtra("FONT_FILE") ?: "amiri_regular.ttf"
        currentFontSize = intent.getFloatExtra("FONT_SIZE", 18f)
        currentLineSpacing = intent.getFloatExtra("LINE_SPACING", 1.6f)
        currentParaSpacing = intent.getFloatExtra("PARA_SPACING", 1.0f)
        val alignOrdinal = intent.getIntExtra("TEXT_ALIGN", Layout.Alignment.ALIGN_NORMAL.ordinal)
        currentAlign = Layout.Alignment.entries.toTypedArray().getOrElse(alignOrdinal) { Layout.Alignment.ALIGN_NORMAL }
        currentTypeface = FontManager.loadTypeface(this, fontFile)
        currentFontFile = fontFile

        if (fullText.isBlank()) {
            fullText = "نص تجريبي\n\nهذا النص يُستخدم لاختبار المحرر."
            bookTitle = "كتاب تجريبي"
            author = "مؤلف تجريبي"
        }

        if (pageNumber > 0) {
            pageNum = pageNumber
        }
    }

    private var pageNum: Int = 0

    private fun refreshPreview() {
        val bg = currentBg
        previewRoot.setBackgroundColor(bg.bgColor)

        val metaText = buildString {
            append(bookTitle)
            if (author.isNotBlank()) append("\n$author")
            if (edition.isNotBlank()) append("\n$edition")
            if (pageNum > 0) append("\nصفحة $pageNum")
        }
        tvMeta.text = metaText

        tvContent.apply {
            typeface = if (isBoldAll && !hasSelection)
                Typeface.create(currentTypeface, Typeface.BOLD)
            else
                currentTypeface
            textSize = currentFontSize
            setLineSpacing(dp(currentParaSpacing * currentFontSize * 0.5f), currentLineSpacing)
            setLetterSpacing(currentLetterSpacing)
            textDirection = android.widget.TextView.TEXT_DIRECTION_RTL
            setTextColor(bg.textColor)

            val align = currentAlign
            textAlignment = when {
                isJustify -> android.widget.TextView.TEXT_ALIGNMENT_TEXT_START
                align == Layout.Alignment.ALIGN_CENTER -> android.widget.TextView.TEXT_ALIGNMENT_CENTER
                align == Layout.Alignment.ALIGN_OPPOSITE -> android.widget.TextView.TEXT_ALIGNMENT_TEXT_END
                else -> android.widget.TextView.TEXT_ALIGNMENT_TEXT_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                justificationMode = if (isJustify)
                    Layout.JUSTIFICATION_MODE_INTER_WORD
                else
                    Layout.JUSTIFICATION_MODE_NONE
            }
        }

        applyTextWidth()
        previewRoot.layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL

        val ssb = SpannableStringBuilder(fullText)
        applyBaseStyles(ssb)
        applyHideUnselected(ssb)
        tvContent.text = ssb

        val metaRatio = 0.85f
        tvMeta.textSize = currentFontSize * metaRatio
        tvWatermark.textSize = currentFontSize * metaRatio * 0.82f
        tvMeta.typeface = Typeface.create(currentTypeface, Typeface.NORMAL)
        tvMeta.setTextColor(bg.metaColor)
        tvMeta.textAlignment = tvContent.textAlignment

        tvDivider.setBackgroundColor(bg.dividerColor)
        tvDivider.visibility = View.VISIBLE

        tvWatermark.setTextColor(bg.metaColor)
        tvWatermark.typeface = Typeface.create(currentTypeface, Typeface.NORMAL)
        tvWatermark.textAlignment = tvContent.textAlignment
    }

    /**
     * عند تفعيل خيار "إخفاء غير المحدد": نجعل النص خارج نطاق التحديد شفافًا
     * فيبقى التحديد مع الهامش ظاهرًا فقط (نفس ما يُصدَّر فعليًا عند الحفظ).
     */
    private fun applyHideUnselected(ssb: SpannableStringBuilder) {
        if (!hideUnselected || !hasSelection) return
        if (selStart > 0) {
            ssb.setSpan(
                ForegroundColorSpan(0x00000000),
                0, selStart,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        if (selEnd < ssb.length) {
            ssb.setSpan(
                ForegroundColorSpan(0x00000000),
                selEnd, ssb.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    /**
     * يتحكم في عرض منطقة النص بتعديل الهوامش الأفقية لـ tvContent
     * (يؤثر على المعاينة والتصدير معًا لأن التصدير يرسم نفس الـ View).
     */
    private fun applyTextWidth() {
        val baseWidthPx = previewRoot.width.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        val targetWidth = (baseWidthPx * textWidth).toInt()
        val totalMargin = (baseWidthPx - targetWidth).coerceAtLeast(0)
        val lp = tvContent.layoutParams as ViewGroup.MarginLayoutParams
        lp.setMarginStart(totalMargin / 2)
        lp.setMarginEnd(totalMargin / 2)
        tvContent.layoutParams = lp
    }

    private fun applyBaseStyles(ssb: SpannableStringBuilder) {        for ((range, style) in currentSpans) {
            val start = range.first.coerceIn(0, ssb.length)
            val end = range.last.coerceIn(start, ssb.length)
            if (start >= end) continue

            if (style.bgColor != android.graphics.Color.TRANSPARENT) {
                ssb.setSpan(BackgroundColorSpan(style.bgColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            style.textColor?.let {
                ssb.setSpan(ForegroundColorSpan(it), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (style.isBold) {
                ssb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (style.isUnderlined) {
                ssb.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (style.isHidden) {
                ssb.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (style.isDimmed) {
                ssb.setSpan(ForegroundColorSpan(0x60808080), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (style.scaleFactor != 1f) {
                ssb.setSpan(RelativeSizeSpan(style.scaleFactor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private val currentSpans = mutableMapOf<IntRange, QuoteSpanStyle>()

    private fun applyEffectToSelection(effect: QuoteEffect) {
        if (!hasSelection) return
        val range = selStart..selEnd
        val existing = currentSpans[range] ?: QuoteSpanStyle()
        when (effect) {
            QuoteEffect.HIGHLIGHT -> existing.bgColor = android.graphics.Color.parseColor("#FFFFEB3B")
            QuoteEffect.TEXT_COLOR -> existing.textColor = currentBg.textColor
            QuoteEffect.BOLD -> existing.isBold = true
            QuoteEffect.UNDERLINE -> existing.isUnderlined = true
            QuoteEffect.DIM -> existing.isDimmed = true
            QuoteEffect.HIDE -> existing.isHidden = true
            QuoteEffect.SCALE_UP -> existing.scaleFactor = 1.2f
            QuoteEffect.SCALE_DOWN -> existing.scaleFactor = 0.8f
            QuoteEffect.BLUR -> {}
            QuoteEffect.NONE -> currentSpans.remove(range)
        }
        if (effect != QuoteEffect.NONE) currentSpans[range] = existing
        refreshPreview()
    }

    private fun clearAllEffects() {
        currentSpans.clear()
        clearSelection()
        refreshPreview()
    }

    private fun setupEffectsBar() {
        val effects = listOf(
            R.id.btnEffectHighlight to QuoteEffect.HIGHLIGHT,
            R.id.btnEffectBold to QuoteEffect.BOLD,
            R.id.btnEffectUnderline to QuoteEffect.UNDERLINE,
            R.id.btnEffectScaleUp to QuoteEffect.SCALE_UP,
            R.id.btnEffectScaleDown to QuoteEffect.SCALE_DOWN,
            R.id.btnEffectDim to QuoteEffect.DIM,
            R.id.btnEffectHide to QuoteEffect.HIDE,
        )
        effects.forEach { (id, effect) ->
            findViewById<View>(id).setOnClickListener { applyEffectToSelection(effect) }
        }
        btnClearEffects.setOnClickListener { clearAllEffects() }
        btnDeleteSelection.setOnClickListener { deleteSelectedText() }
        btnCopy.setOnClickListener { copySelection() }
        btnHideUnselected.setOnClickListener {
            hideUnselected = !hideUnselected
            updateHideUnselectedUI()
            if (hasSelection) {
                refreshPreview()
            }
        }
    }

    private fun updateHideUnselectedUI() {
        btnHideUnselected.setBackgroundResource(
            if (hideUnselected) R.drawable.bg_segment_active_reader
            else R.drawable.bg_chip_neo
        )
        btnHideUnselected.setTextColor(
            if (hideUnselected) getColor(R.color.urwah_card_bg)
            else getColor(R.color.urwah_thread_dark)
        )
    }

    private var currentFontFile = "amiri_regular.ttf"

    private fun setupFontChips() {
        val fonts = FontManager.getFonts()
        val margin = dp(4f).toInt()
        fontChips.removeAllViews()
        fonts.forEach { font ->
            val chip = TextView(this)
            chip.text = font.displayName
            chip.setTypeface(FontManager.loadTypeface(this, font.fileName))
            chip.textSize = 12f
            chip.gravity = android.view.Gravity.CENTER
            chip.setPadding(dp(12f).toInt(), dp(4f).toInt(), dp(12f).toInt(), dp(4f).toInt())
            val active = font.fileName == currentFontFile
            updateFontChipStyle(chip, active)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(34f).toInt()
            )
            lp.setMargins(margin, 0, margin, 0)
            chip.layoutParams = lp
            chip.setOnClickListener {
                currentFontFile = font.fileName
                currentTypeface = FontManager.loadTypeface(this, font.fileName)
                refreshPreview()
                setupFontChips()
            }
            fontChips.addView(chip)
        }
    }

    private fun updateFontChipStyle(chip: TextView, active: Boolean) {
        chip.setBackgroundResource(
            if (active) R.drawable.bg_segment_active_reader
            else R.drawable.bg_chip_neo
        )
        chip.setTextColor(
            if (active) getColor(R.color.urwah_card_bg)
            else getColor(R.color.urwah_thread_dark)
        )
    }

    private fun setupToolsPanel() {
        btnTools.setOnClickListener { showToolsPanel(true) }
        btnCloseTools.setOnClickListener { showToolsPanel(false) }
        findViewById<View>(R.id.toolsScrim).setOnClickListener { showToolsPanel(false) }
    }

    private fun showToolsPanel(show: Boolean) {
        if (show) {
            toolsOverlay.visibility = View.VISIBLE
            toolsScroll.post {
                val maxH = (resources.displayMetrics.heightPixels * 0.6f).toInt()
                toolsScroll.layoutParams.height = maxH
                toolsScroll.layoutParams = toolsScroll.layoutParams
            }
            toolsPanel.post {
                toolsPanel.translationY = toolsPanel.height.toFloat()
                toolsPanel.animate().translationY(0f).setDuration(220).start()
            }
        } else {
            toolsPanel.animate()
                .translationY(toolsPanel.height.toFloat())
                .setDuration(200)
                .withEndAction {
                    toolsOverlay.visibility = View.GONE
                    toolsPanel.translationY = 0f
                }
                .start()
        }
    }

    override fun onBackPressed() {
        if (toolsOverlay.visibility == View.VISIBLE) {
            showToolsPanel(false)
        } else {
            super.onBackPressed()
        }
    }

    private fun setupBackgroundSelector() {
        bgSelector.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        bgSelector.adapter = QuoteBackgroundAdapter(bgList) { bg ->
            currentBg = bg
            refreshPreview()
        }
    }

    private fun setupAlignControls() {
        btnAlignRight.setOnClickListener { currentAlign = Layout.Alignment.ALIGN_NORMAL; isJustify = false; updateAlignUI(); refreshPreview() }
        btnAlignCenter.setOnClickListener { currentAlign = Layout.Alignment.ALIGN_CENTER; isJustify = false; updateAlignUI(); refreshPreview() }
        btnAlignLeft.setOnClickListener { currentAlign = Layout.Alignment.ALIGN_OPPOSITE; isJustify = false; updateAlignUI(); refreshPreview() }
        btnAlignJustify.setOnClickListener { currentAlign = Layout.Alignment.ALIGN_NORMAL; isJustify = true; updateAlignUI(); refreshPreview() }
        updateAlignUI()
    }

    private fun updateAlignUI() {
        val isR = currentAlign == Layout.Alignment.ALIGN_NORMAL && !isJustify
        val isC = currentAlign == Layout.Alignment.ALIGN_CENTER
        val isL = currentAlign == Layout.Alignment.ALIGN_OPPOSITE
        val isJ = isJustify
        val active = R.drawable.bg_segment_active_reader
        val inactive = R.drawable.bg_segment_inactive_reader
        btnAlignRight.setBackgroundResource(if (isR) active else inactive)
        btnAlignCenter.setBackgroundResource(if (isC) active else inactive)
        btnAlignLeft.setBackgroundResource(if (isL) active else inactive)
        btnAlignJustify.setBackgroundResource(if (isJ) active else inactive)
        val ac = getColor(R.color.urwah_card_bg)
        val ic = getColor(R.color.urwah_thread_dark)
        btnAlignRight.setTextColor(if (isR) ac else ic)
        btnAlignCenter.setTextColor(if (isC) ac else ic)
        btnAlignLeft.setTextColor(if (isL) ac else ic)
        btnAlignJustify.setTextColor(if (isJ) ac else ic)
    }

    private fun setupFontSizeSlider() {
        fontSizeSlider.progress = (currentFontSize - 8f).toInt().coerceIn(0, 40)
        fontSizeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                currentFontSize = (8 + progress).toFloat()
                refreshPreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun setupAdvancedFormatting() {
        val lineSpacingMin = 1.0f
        val lineSpacingMax = 3.0f
        lineSpacingSlider.progress = ((currentLineSpacing - lineSpacingMin) / (lineSpacingMax - lineSpacingMin) * 100).toInt().coerceIn(0, 100)
        lineSpacingSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                currentLineSpacing = lineSpacingMin + (progress / 100f) * (lineSpacingMax - lineSpacingMin)
                tvLineSpacingValue.text = "${String.format("%.1f", currentLineSpacing)}x"
                refreshPreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val paraMin = 0.5f
        val paraMax = 3.0f
        paraSpacingSlider.progress = ((currentParaSpacing - paraMin) / (paraMax - paraMin) * 100).toInt().coerceIn(0, 100)
        paraSpacingSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                currentParaSpacing = paraMin + (progress / 100f) * (paraMax - paraMin)
                tvParaSpacingValue.text = "${String.format("%.1f", currentParaSpacing)}x"
                refreshPreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val letterMin = -0.2f
        val letterMax = 0.5f
        letterSpacingSlider.progress = ((currentLetterSpacing - letterMin) / (letterMax - letterMin) * 100).toInt().coerceIn(0, 100)
        letterSpacingSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                currentLetterSpacing = letterMin + (progress / 100f) * (letterMax - letterMin)
                tvLetterSpacingValue.text = if (currentLetterSpacing >= 0) "+${String.format("%.1f", currentLetterSpacing)}" else String.format("%.1f", currentLetterSpacing)
                refreshPreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        textWidthSlider.progress = (textWidth * 100).toInt().coerceIn(50, 100)
        textWidthSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                textWidth = (progress.coerceIn(50, 100)) / 100f
                tvTextWidthValue.text = "${(textWidth * 100).toInt()}%"
                refreshPreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnBoldAll.setOnClickListener {
            if (hasSelection) {
                applyEffectToSelection(QuoteEffect.BOLD)
            } else {
                isBoldAll = !isBoldAll
                updateBoldAllUI()
                refreshPreview()
            }
        }
        updateBoldAllUI()
    }

    private fun updateBoldAllUI() {
        btnBoldAll.setBackgroundResource(
            if (isBoldAll) R.drawable.bg_segment_active_reader
            else R.drawable.bg_chip_neo
        )
        btnBoldAll.setTextColor(
            if (isBoldAll) getColor(R.color.urwah_card_bg)
            else getColor(R.color.urwah_thread_dark)
        )
    }

    private fun setupButtons() {
        btnSave.setOnClickListener { saveImage() }
        btnShare.setOnClickListener { shareImage() }
        btnClose.setOnClickListener { finish() }
    }

    private fun capturePreviewBitmap(scale: Float = 3f): android.graphics.Bitmap? {
        // If user selected text, export only the selection
        val savedText = fullText
        if (hasSelection) {
            val selText = fullText.substring(selStart, selEnd)
            val ssb = SpannableStringBuilder(selText)
            for ((range, style) in currentSpans) {
                val overlapStart = maxOf(range.first, selStart)
                val overlapEnd = minOf(range.last, selEnd)
                if (overlapStart < overlapEnd) {
                    val localStart = overlapStart - selStart
                    val localEnd = overlapEnd - selStart
                    if (style.bgColor != android.graphics.Color.TRANSPARENT) {
                        ssb.setSpan(BackgroundColorSpan(style.bgColor), localStart, localEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    style.textColor?.let {
                        ssb.setSpan(ForegroundColorSpan(it), localStart, localEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    if (style.isBold) {
                        ssb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), localStart, localEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    if (style.isUnderlined) {
                        ssb.setSpan(UnderlineSpan(), localStart, localEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    if (style.isHidden) {
                        ssb.setSpan(StrikethroughSpan(), localStart, localEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    if (style.isDimmed) {
                        ssb.setSpan(ForegroundColorSpan(0x60808080), localStart, localEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    if (style.scaleFactor != 1f) {
                        ssb.setSpan(RelativeSizeSpan(style.scaleFactor), localStart, localEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
            tvContent.text = ssb
        }

        previewRoot.measure(
            View.MeasureSpec.makeMeasureSpec(previewRoot.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val mw = previewRoot.measuredWidth
        val mh = previewRoot.measuredHeight
        if (mw <= 0 || mh <= 0) {
            if (hasSelection) tvContent.text = SpannableStringBuilder(savedText).also { applyBaseStyles(it) }
            return null
        }
        previewRoot.layout(0, 0, mw, mh)

        val bitmap = android.graphics.Bitmap.createBitmap(mw, mh, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        previewRoot.draw(canvas)

        // Restore full text
        if (hasSelection) {
            tvContent.text = SpannableStringBuilder(savedText).also { applyBaseStyles(it) }
        }

        if (scale <= 1f) return bitmap
        val sw = (mw * scale).toInt()
        val sh = (mh * scale).toInt()
        val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        bitmap.recycle()
        return scaled
    }

    private fun saveImage() {
        btnSave.isEnabled = false
        scope.launch {
            val bmp = capturePreviewBitmap(3f)
            if (bmp != null) {
                val ok = withContext(Dispatchers.IO) { saveToGallery(bmp) }
                if (ok) {
                    Toast.makeText(this@QuoteEditorActivity, "تم الحفظ ضمن صور الجهاز في مجلّد «الاقتباسات»", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@QuoteEditorActivity, "خطأ في الحفظ", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this@QuoteEditorActivity, "خطأ في إنشاء الصورة", Toast.LENGTH_SHORT).show()
            }
            btnSave.isEnabled = true
        }
    }

    private fun saveToGallery(bitmap: android.graphics.Bitmap): Boolean {
        return try {
            val name = "Orwa_Quote_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Orwa/Quotes")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                }
                uri != null
            } else {
                val dir = File("${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)}/Orwa/Quotes")
                dir.mkdirs()
                FileOutputStream(File(dir, name)).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                true
            }
        } catch (_: Exception) { false }
    }

    private fun shareImage() {
        btnShare.isEnabled = false
        scope.launch {
            val bmp = capturePreviewBitmap(3f)
            if (bmp != null) {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(cacheDir, "quotes_share")
                    dir.mkdirs()
                    val f = File(dir, "share_${System.currentTimeMillis()}.png")
                    FileOutputStream(f).use { out ->
                        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    }
                    f
                }
                val uri = FileProvider.getUriForFile(this@QuoteEditorActivity, "${packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "مشاركة اقتباس"))
            } else {
                Toast.makeText(this@QuoteEditorActivity, "خطأ في إنشاء الصورة", Toast.LENGTH_SHORT).show()
            }
            btnShare.isEnabled = true
        }
    }
}
