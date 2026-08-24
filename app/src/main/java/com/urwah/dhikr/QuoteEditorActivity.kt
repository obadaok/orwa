package com.urwah.dhikr

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.text.Editable
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
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
import kotlinx.coroutines.cancel
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
    private lateinit var tvBrandLabel: TextView
    private lateinit var tvDivider: TextView
    private lateinit var ivQuoteLogo: ImageView
    private lateinit var previewRoot: LinearLayout
    private lateinit var previewFrame: FrameLayout
    private lateinit var previewWrap: FrameLayout
    private lateinit var previewBorder: View
    private lateinit var tvSelectionInfo: TextView
    private lateinit var selHandleStart: View
    private lateinit var selHandleEnd: View
    private lateinit var bgSelector: RecyclerView
    private lateinit var sizeSelector: RecyclerView
    private lateinit var btnSave: TextView
    private lateinit var btnShare: TextView
    private lateinit var btnClose: TextView
    private lateinit var btnUndo: View
    private lateinit var btnRedo: View
    private lateinit var btnPreview: TextView
    private lateinit var toolsOverlay: FrameLayout
    private lateinit var toolsPanel: LinearLayout
    private lateinit var toolsScroll: androidx.core.widget.NestedScrollView
    private lateinit var btnCloseTools: TextView
    private lateinit var fontChips: LinearLayout
    private lateinit var btnCopy: TextView
    private lateinit var btnClearEffects: TextView
    private var tvEffectsHint: TextView? = null
    private lateinit var btnDeleteSelection: TextView
    private lateinit var btnHideUnselected: TextView
    private lateinit var btnFormat: TextView
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
    private lateinit var textWidthSlider: SeekBar
    private lateinit var tvTextWidthValue: TextView
    private lateinit var scrollView: androidx.core.widget.NestedScrollView
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var ivPreviewBitmap: android.widget.ImageView
    private lateinit var etCitation: EditText
    private lateinit var etCommentary: EditText
    private lateinit var topBar: LinearLayout
    private var topBarsHidden = false
    private var lastScrollY = 0

    private val scope = MainScope()
    private var currentAlign = Layout.Alignment.ALIGN_NORMAL
    private var isJustify = false
    private var currentBg = QuoteBackground.LIGHT
    private var currentTypeface: Typeface? = null
    private var currentFontSize = 18f
    private var currentLineSpacing = 1.6f
    private var currentParaSpacing = 1.0f
    private var textWidth = 0.92f
    private var isBoldAll = false
    private var fullText = ""
    private var bookTitle = ""
    private var author = ""
    private var edition = ""
    private var citation = ""
    private var commentary = ""

    private var selectionBase = -1
    private var selectionExtent = -1
    private var hideUnselected = false
    private var previewMode = false

    // سياق المستند: عند فتح المحرر من نافذة صفحات مجاورة (عبر الصفحات)
    private var hasDocumentContext = false
    private var pageMarkers: List<PageMarker> = emptyList()

    private data class PageMarker(val start: Int, val end: Int, val pageNum: Int)
    private data class CleanPiece(val start: Int, val end: Int)

    private enum class AdjustMode { NONE, NEW, START, END }
    private var adjustMode = AdjustMode.NONE

    // الضغط المطول لبدء التحديد (بدلًا من أي لمسة) + تمرير تلقائي عند الحواف
    private val mainHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var pendingTouchOffset = 0
    private var downX = 0f
    private var downY = 0f
    private var wasDownOutsideSelection = false
    private var lastHapticOffset = -1

    private val bgList = QuoteBackground.entries.toList()

    private val quoteSizes = listOf(
        QuoteSize("تلقائي", 0, 0),
        QuoteSize("1:1", 1, 1),
        QuoteSize("4:5", 4, 5),
        QuoteSize("3:4", 3, 4),
        QuoteSize("9:16", 9, 16),
        QuoteSize("16:9", 16, 9),
        QuoteSize("3:2", 3, 2),
        QuoteSize("4:3", 4, 3),
        QuoteSize("2:3", 2, 3),
    )
    private var sizeIndex = 0

    private var bgAdapter: QuoteBackgroundAdapter? = null
    private var sizeAdapter: QuoteSizeAdapter? = null

    // Undo / Redo
    private data class EditorState(
        val fullText: String,
        val spans: Map<IntRange, QuoteSpanStyle>,
        val align: Layout.Alignment,
        val isJustify: Boolean,
        val bg: QuoteBackground,
        val fontFile: String,
        val fontSize: Float,
        val lineSpacing: Float,
        val paraSpacing: Float,
        val textWidth: Float,
        val isBoldAll: Boolean,
        val hideUnselected: Boolean,
        val selBase: Int,
        val selExtent: Int,
        val sizeIndex: Int,
        val citation: String,
        val commentary: String
    ) : java.io.Serializable

    private val undoStack = ArrayDeque<EditorState>()
    private val redoStack = ArrayDeque<EditorState>()
    private var pendingGesture: EditorState? = null
    private val maxHistory = 60

    private val selStart: Int get() = min(selectionBase, selectionExtent).coerceIn(0, fullText.length)
    private val selEnd: Int get() = max(selectionBase, selectionExtent).coerceIn(0, fullText.length)
    private val hasSelection: Boolean get() = selectionBase >= 0 && selectionExtent >= 0 && selStart < selEnd
    private val selLen: Int get() = selEnd - selStart

    private val effectChips = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quote_editor)

        tvContent = findViewById(R.id.tvPreviewContent)
        tvMeta = findViewById(R.id.tvPreviewMeta)
        tvBrandLabel = findViewById(R.id.tvBrandLabel)
        tvDivider = findViewById(R.id.tvPreviewDivider)
        ivQuoteLogo = findViewById(R.id.ivQuoteLogo)
        previewRoot = findViewById(R.id.previewRoot)
        previewFrame = findViewById(R.id.previewFrame)
        previewWrap = findViewById(R.id.previewWrap)
        previewBorder = findViewById(R.id.previewBorder)
        tvSelectionInfo = findViewById(R.id.tvSelectionInfo)
        selHandleStart = findViewById(R.id.selHandleStart)
        selHandleEnd = findViewById(R.id.selHandleEnd)
        bgSelector = findViewById(R.id.bgSelector)
        sizeSelector = findViewById(R.id.sizeSelector)
        btnSave = findViewById(R.id.btnSave)
        btnShare = findViewById(R.id.btnShare)
        btnClose = findViewById(R.id.btnClose)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnPreview = findViewById(R.id.btnPreview)
        toolsOverlay = findViewById(R.id.toolsOverlay)
        toolsPanel = findViewById(R.id.toolsPanel)
        toolsScroll = findViewById(R.id.toolsScroll)
        btnCloseTools = findViewById(R.id.btnCloseTools)
        fontChips = findViewById(R.id.fontChips)
        btnCopy = findViewById(R.id.btnCopy)
        btnClearEffects = findViewById(R.id.btnClearEffects)
        tvEffectsHint = findViewById(R.id.tvEffectsHint)
        btnDeleteSelection = findViewById(R.id.btnDeleteSelection)
        btnHideUnselected = findViewById(R.id.btnHideUnselected)
        btnFormat = findViewById(R.id.btnFormat)
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
        textWidthSlider = findViewById(R.id.textWidthSlider)
        tvTextWidthValue = findViewById(R.id.tvTextWidthValue)
        scrollView = findViewById(R.id.editorScrollView)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        ivPreviewBitmap = findViewById(R.id.ivPreviewBitmap)
        etCitation = findViewById(R.id.etCitation)
        etCommentary = findViewById(R.id.etCommentary)
        topBar = findViewById(R.id.topBar)

        effectChips += findViewById<View>(R.id.btnEffectBold)
        effectChips += findViewById<View>(R.id.btnEffectUnderline)
        effectChips += findViewById<View>(R.id.btnEffectScaleUp)
        effectChips += findViewById<View>(R.id.btnEffectScaleDown)
        effectChips += findViewById<View>(R.id.btnEffectDim)
        effectChips += findViewById<View>(R.id.btnEffectHide)
        // ملاحظة: زر "مسح كل التأثيرات" غير موجود هنا عمدًا — فهو يعمل على كامل
        // الاقتباس ولا يجب أن يُعطَّل لمجرد عدم وجود تحديد نشط حاليًا.

        setupTouch()
        hookSelectionHandlesRelayout()
        setupAutoHideTopBars()
        loadData()
        setupEffectsBar()
        setupBackgroundSelector()
        setupCitationInputs()
        setupSizeSelector()
        setupAlignControls()
        setupFontSizeSlider()
        setupAdvancedFormatting()
        setupFontChips()
        setupToolsPanel()
        setupSheetDrag()
        setupButtons()
        setupToolbarTools()
        applyImageSize()
        refreshPreview(preserveScroll = false)
        updateUndoRedoUI()
        updateEffectsEnabled()
        updateEffectButtonsActiveState()

        val saved = savedInstanceState?.getSerializable(KEY_EDITOR_STATE) as? EditorState
        if (saved != null) {
            restoreState(saved)
        } else {
            scrollView.post { scrollView.scrollTo(0, 0) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::loadingOverlay.isInitialized) {
            outState.putSerializable(KEY_EDITOR_STATE, captureState())
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val KEY_EDITOR_STATE = "editor_state_v1"
        private val PAGE_MARKER_REGEX = Regex("≪ صفحة (\\d+) ≫")
        // نطاق قبض حدّ التحديد بالبكسل المستقل عن الكثافة (يضمن سهولة السحب
        // بغض النظر عن حجم الخط أو طول النص المحدد).
        private const val GRAB_TOLERANCE_DP = 28f
        // طول اللمس الطويل القابل للتجاهل (حركة أصغر من ذلك لا تُلغي الضغط المطول)
        private const val LONG_PRESS_SLOP_DP = 12f
        // حافة التمرير التلقائي وسرعته أثناء سحب حد التحديد خارج الشاشة
        private const val AUTO_SCROLL_EDGE_DP = 48f
        private const val AUTO_SCROLL_SPEED_DP = 26f
        // حد ارتفاع المعاينة النقطية (بكسلات): النص الأطول يعرض بالمحرر الحي الحي.
    }

    private fun setupTouch() {
        tvContent.setOnTouchListener { _, event ->
            if (previewMode) return@setOnTouchListener false
            val layout = tvContent.layout ?: return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val touchOffset = offsetAt(event, layout)
                    pendingTouchOffset = touchOffset
                    downX = event.x
                    downY = event.y
                    wasDownOutsideSelection = !(hasSelection && touchOffset >= selStart && touchOffset <= selEnd)
                    lastHapticOffset = -1
                    if (!wasDownOutsideSelection) {
                        // داخل التحديد: إذا اقتربت اللمسة من أحد الحدود نبدأ سحبه فورًا
                        val grabTolerancePx = dp(GRAB_TOLERANCE_DP)
                        val startLine = layout.getLineForOffset(selStart)
                        val endLine = layout.getLineForOffset(selEnd)
                        val touchLine = layout.getLineForVertical(event.y.toInt())
                        val distToStart = if (touchLine == startLine)
                            abs(event.x - layout.getPrimaryHorizontal(selStart)) else Float.MAX_VALUE
                        val distToEnd = if (touchLine == endLine)
                            abs(event.x - layout.getPrimaryHorizontal(selEnd)) else Float.MAX_VALUE
                        if (distToStart <= grabTolerancePx && distToStart <= distToEnd) {
                            adjustMode = AdjustMode.START
                            selectionBase = selEnd
                            selectionExtent = touchOffset
                            scrollView.requestDisallowInterceptTouchEvent(true)
                        } else if (distToEnd <= grabTolerancePx) {
                            adjustMode = AdjustMode.END
                            selectionBase = selStart
                            selectionExtent = touchOffset
                            scrollView.requestDisallowInterceptTouchEvent(true)
                        } else {
                            adjustMode = AdjustMode.NONE
                            scheduleLongPress(touchOffset)
                        }
                    } else {
                        // خارج التحديد: لا تحديد جديد إلا بالضغط المطول،
                        // مع ترك التمرير الطبيعي يعمل (لا نستولي على اللمسة)
                        adjustMode = AdjustMode.NONE
                        scheduleLongPress(touchOffset)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val off = offsetAt(event, layout)
                    pendingTouchOffset = off
                    when (adjustMode) {
                        AdjustMode.START, AdjustMode.END -> {
                            autoScrollDuringDrag(event)
                            val newOffset = offsetAt(event, layout)
                            selectionExtent = newOffset
                            updateSelectionHighlight()
                            updateSelectionUI()
                            performHandleHaptic(newOffset)
                        }
                        else -> {
                            // حركة تتجاوز حد اللمس = نية تمرير، نلغي اللمس الطويل
                            val slopPx = dp(LONG_PRESS_SLOP_DP)
                            if (abs(event.x - downX) + abs(event.y - downY) > slopPx) {
                                cancelPendingLongPress()
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelPendingLongPress()
                    when {
                        adjustMode == AdjustMode.NONE && wasDownOutsideSelection && hasSelection -> {
                            // نقرة عادية خارج التحديد تُسقطه
                            clearSelection()
                            refreshPreview()
                        }
                        adjustMode == AdjustMode.NONE && hasSelection -> {
                            updateSelectionUI()
                        }
                        !hasSelection -> {
                            clearSelection()
                            refreshPreview()
                        }
                        else -> {
                            adjustMode = AdjustMode.NONE
                            updateSelectionUI()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun scheduleLongPress(offset: Int) {
        cancelPendingLongPress()
        val runnable = Runnable {
            startSelectionFromLongPress(offset)
        }
        longPressRunnable = runnable
        mainHandler.postDelayed(runnable, ViewConfiguration.getLongPressTimeout().toLong())
    }

    private fun cancelPendingLongPress() {
        longPressRunnable?.let { mainHandler.removeCallbacks(it) }
        longPressRunnable = null
    }

    /**
     * الضغط المطول يبدأ التحديد على الكلمة كاملة (وليس عند حرف اللمسة)،
     * مع اهتزاز تأكيد، ثم السحب يوسّع التحديد من بداية الكلمة.
     */
    private fun startSelectionFromLongPress(offset: Int) {
        val (wordStart, wordEnd) = snapWordRange(pendingTouchOffset.coerceIn(0, fullText.length))
        if (wordStart >= wordEnd) return
        scrollView.requestDisallowInterceptTouchEvent(true)
        selectionBase = wordStart
        selectionExtent = wordEnd
        adjustMode = AdjustMode.START
        lastHapticOffset = -1
        tvContent.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        updateSelectionHighlight()
        updateSelectionUI()
    }

    private fun snapWordRange(offset: Int): Pair<Int, Int> {
        val text = fullText
        if (text.isEmpty()) return 0 to 0
        var center = offset.coerceIn(0, text.length)
        if (center > 0 && center == text.length) center = text.length
        while (center > 0 && text[center - 1].isWhitespace()) center--
        if (center >= text.length && text.isNotEmpty()) center = text.length - 1
        var start = center
        var end = center
        while (start > 0 && !text[start - 1].isWhitespace()) start--
        while (end < text.length && !text[end].isWhitespace()) end++
        return start to end
    }

    /**
     * عند سحب حد التحديد باتجاه الحافة العليا/السفلى للشاشة، يمرر المحرر
     * تلقائيًا (بسرعة تتناسب مع عمق الاقتراب من الحافة) ليواصل التحديد
     * دون الحاجة لإبعاد الإصبع عن الحافة.
     */
    private fun autoScrollDuringDrag(event: MotionEvent) {
        val content = scrollView.getChildAt(0) ?: return
        val scrollRange = content.height - scrollView.height
        if (scrollRange <= 0) return
        val edge = dp(AUTO_SCROLL_EDGE_DP)
        val location = IntArray(2)
        scrollView.getLocationOnScreen(location)
        val top = location[1].toFloat()
        val bottom = top + scrollView.height
        val rawY = event.rawY
        val maxSpeed = dp(AUTO_SCROLL_SPEED_DP)
        var delta = 0f
        if (rawY < top + edge) {
            delta = -maxSpeed * (1f - (rawY - top) / edge).coerceIn(0f, 1f)
        } else if (rawY > bottom - edge) {
            delta = maxSpeed * (1f - (bottom - rawY) / edge).coerceIn(0f, 1f)
        }
        if (delta == 0f) return
        val newScrollY = (scrollView.scrollY + delta).toInt().coerceIn(0, scrollRange)
        scrollView.scrollTo(scrollView.scrollX, newScrollY)
    }

    private var lastHapticTime = 0L

    private fun performHandleHaptic(newOffset: Int) {
        // تبسيط: منع التكرار المطلق للـ offset نفسه + حد زمني أدنى 40ms
        // بين الإشعارات أثناء السحب، تجنّبًا لإرباك الإحساس بالمهتز.
        if (newOffset == lastHapticOffset) return
        val now = SystemClock.uptimeMillis()
        if (now - lastHapticTime < 40) return
        lastHapticOffset = newOffset
        lastHapticTime = now
        tvContent.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
    }

    /**
     * عند السكرول في النص: الشريط العلوي (والسفلي) يختفي، ويرجع مع السكرول العكسي.
     */
    private fun setupAutoHideTopBars() {
        scrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (selectionTools.visibility == View.VISIBLE) return@setOnScrollChangeListener
            lastScrollY = scrollY
            val hideThreshold = (topBar.height * 0.5f).toInt().coerceAtLeast(dp(24f).toInt())
            if (scrollY > hideThreshold && scrollY > oldScrollY && !topBarsHidden) {
                hideTopBars()
            } else if ((scrollY < oldScrollY || scrollY <= hideThreshold) && topBarsHidden) {
                showTopBars()
            }
        }
    }

    private fun hideTopBars() {
        topBarsHidden = true
        val dy = topBar.height.toFloat()
        topBar.animate().translationY(-dy).alpha(0f).setDuration(220).start()
        // bottomBar أُزيل — الحفظ/المشاركة في الشريط العلوي الآن
    }

    private fun showTopBars() {
        topBarsHidden = false
        topBar.animate().translationY(0f).alpha(1f).setDuration(220).start()
    }

    private fun offsetAt(event: MotionEvent, layout: android.text.Layout): Int {
        val line = layout.getLineForVertical(event.y.toInt()).coerceIn(0, layout.lineCount - 1)
        val off = layout.getOffsetForHorizontal(line, event.x)
        return off.coerceIn(0, fullText.length)
    }

    private fun clearSelection() {
        cancelPendingLongPress()
        selectionBase = -1
        selectionExtent = -1
        adjustMode = AdjustMode.NONE
        lastHapticOffset = -1
        selectionTools.visibility = View.GONE
        tvSelectionInfo.text = ""
        selHandleStart.visibility = View.GONE
        selHandleEnd.visibility = View.GONE
        updateEffectsEnabled()
    }

    private fun updateSelectionHighlight() {
        if (!hasSelection) return
        val ssb = SpannableStringBuilder(fullText)
        ssb.setSpan(
            BackgroundColorSpan(getColor(R.color.urwah_selection)),
            selStart, selEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        applyBaseStyles(ssb)
        applyHideUnselected(ssb)
        tvContent.text = ssb
        positionSelectionHandles()
    }

    /**
     * يضع مقبضين مرئيين صغيرين عند بداية ونهاية التحديد بإحداثيات دقيقة
     * مأخوذة من Layout النص نفسه، لتسهيل رؤية وسحب حدود التحديد باللمس
     * (لم تكن هناك أي إشارة مرئية لحدود التحديد سابقًا).
     */
    private fun positionSelectionHandles() {
        if (!hasSelection || previewMode) {
            selHandleStart.visibility = View.GONE
            selHandleEnd.visibility = View.GONE
            return
        }
        val layout = tvContent.layout ?: return
        val handleSize = selHandleStart.layoutParams?.width?.takeIf { it > 0 } ?: dp(22f).toInt()

        val startLine = layout.getLineForOffset(selStart)
        val startX = layout.getPrimaryHorizontal(selStart)
        val startY = layout.getLineBottom(startLine).toFloat()
        selHandleStart.translationX = tvContent.left + startX - handleSize / 2f
        selHandleStart.translationY = tvContent.top + startY - dp(4f)

        val endLine = layout.getLineForOffset(selEnd)
        val endX = layout.getPrimaryHorizontal(selEnd)
        val endY = layout.getLineBottom(endLine).toFloat()
        selHandleEnd.translationX = tvContent.left + endX - handleSize / 2f
        selHandleEnd.translationY = tvContent.top + endY - dp(4f)

        selHandleStart.visibility = View.VISIBLE
        selHandleEnd.visibility = View.VISIBLE
    }

    /**
     * السبب الجذري لاختفاء المقابض: كانت تُموضع مرة واحدة بعد تغيير النص
     * قبل اكتمال layout. الحل: إعادة التموضع عند كل تمرير layout للمضيف —
     * (تغيير خط، مقاس، فتح/إغلاق اللوحة، سكرول) فيبقى المقابض ظاهرة دائمًا.
     */
    private fun hookSelectionHandlesRelayout() {
        findViewById<View>(R.id.tvContentHost).addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (hasSelection && !previewMode) {
                selHandleStart.post { positionSelectionHandles() }
                selHandleEnd.post { positionSelectionHandles() }
            }
        }
    }

    private fun updateSelectionUI() {
        if (hasSelection) {
            showTopBars()
            if (selectionTools.visibility != View.VISIBLE) {
                selectionTools.visibility = View.VISIBLE
                selectionTools.alpha = 0f
                selectionTools.translationY = dp(10f)
                selectionTools.animate().alpha(1f).translationY(0f).setDuration(160).start()
            }
            tvSelectionInfo.text = buildSelectionInfoText()
            positionSelectionHandles()
        } else {
            selectionTools.visibility = View.GONE
            tvSelectionInfo.text = ""
            selHandleStart.visibility = View.GONE
            selHandleEnd.visibility = View.GONE
        }
        updateEffectsEnabled()
        updateEffectButtonsActiveState()
    }

    private fun buildSelectionInfoText(): String {
        val base = "$selLen حرف محدد"
        if (!hasDocumentContext || pageMarkers.isEmpty()) return base
        val firstPage = pageMarkers.lastOrNull { it.start < selStart }?.pageNum
        val lastPage = pageMarkers.lastOrNull { it.start < selEnd }?.pageNum
        return when {
            firstPage != null && lastPage != null && firstPage != lastPage ->
                "$base · من صفحة $firstPage إلى صفحة $lastPage"
            firstPage != null || lastPage != null ->
                "$base · صفحة ${firstPage ?: lastPage}"
            else -> base
        }
    }

    private fun updateEffectsEnabled() {
        // نعطّل الأزرار فعليًا (وليس فقط تعتيمها) حتى لا يضغط المستخدم على أداة
        // لا تعمل فيفاجأ برسالة "حدّد النص أولًا" بلا داعٍ في كل مرة.
        val enabled = hasSelection
        val alpha = if (enabled) 1f else 0.35f
        effectChips.forEach {
            it.alpha = alpha
            it.isEnabled = enabled
        }
        tvEffectsHint?.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    /**
     * يلوّن أزرار التأثيرات حسب ما هو مطبَّق فعلًا على التحديد الحالي بالضبط،
     * فيرى المستخدم على الفور أن هذا الجزء "عريض" أو "مُميَّز" مثلًا.
     */
    private fun updateEffectButtonsActiveState() {
        val style = if (hasSelection) currentSpans[selStart..selEnd] else null
        fun paint(view: View, active: Boolean) {
            view.setBackgroundResource(if (active) R.drawable.bg_segment_active_reader else R.drawable.bg_chip_neo)
            (view as? TextView)?.setTextColor(
                getColor(if (active) R.color.urwah_card_bg else R.color.urwah_thread_dark)
            )
        }
        paint(findViewById(R.id.btnEffectBold), style?.isBold == true)
        paint(findViewById(R.id.btnEffectUnderline), style?.isUnderlined == true)
        paint(findViewById(R.id.btnEffectHighlight), (style?.bgColor ?: android.graphics.Color.TRANSPARENT) != android.graphics.Color.TRANSPARENT)
        paint(findViewById(R.id.btnEffectDim), style?.isDimmed == true)
        paint(findViewById(R.id.btnEffectHide), style?.isHidden == true)
        paint(findViewById(R.id.btnEffectScaleUp), style?.scaleFactor == 1.2f)
        paint(findViewById(R.id.btnEffectScaleDown), style?.scaleFactor == 0.8f)
    }

    private fun copySelection() {
        if (!hasSelection) return
        val text = selectedCleanText().first
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("quote", text))
        Toast.makeText(this, "تم نسخ النص المحدد", Toast.LENGTH_SHORT).show()
    }

    private fun deleteSelectedText() {
        if (!hasSelection) return
        pushHistory()
        val start = selStart
        val end = selEnd
        val removed = end - start

        fullText = fullText.substring(0, start) + fullText.substring(end)
        // حذف السطر الذي أُفرغ بسب الحذف (أو whitespace-only) حتى لا تبقى مساحة فارغة.
        // يعمل لأول سطر وآخر سطر ووسطها: السطر الفارغ عند حدود النص يكون lineStart == lineEnd.
        // ملاحظة: عندما start == 0 فالسطر المعني يبدأ من 0 حتماً (لا نبحث عن \n وإلا التُقط السطر الجديد نفسه).
        val lineStart = if (start == 0) 0 else fullText.lastIndexOf('\n', start - 1) + 1
        val lineEnd = fullText.indexOf('\n', start).let { if (it == -1) fullText.length else it }
        if (fullText.substring(lineStart, lineEnd).isBlank()) {
            // نحذف السطر مع أحد الـ newline المجاورين فقط (لا الاثنين) حتى نحافظ على أسطر الجيران
            val delStart: Int
            val delEnd: Int
            when {
                lineEnd < fullText.length -> { delStart = lineStart; delEnd = lineEnd + 1 }        // يوجد \n بعده
                lineStart > 0 -> { delStart = lineStart - 1; delEnd = lineEnd }                     // آخر سطر: نحذف \n قبله
                else -> { delStart = lineStart; delEnd = lineEnd }                                  // السطر الوحيد
            }
            fullText = fullText.substring(0, delStart) + fullText.substring(delEnd)
        }
        pageMarkers = detectPageMarkers(fullText)

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

        val windowText = intent.getStringExtra("WINDOW_TEXT")
        if (!windowText.isNullOrBlank()) {
            fullText = windowText
            hasDocumentContext = true
            pageMarkers = detectPageMarkers(windowText)
        }

        if (fullText.isBlank()) {
            fullText = "نص تجريبي\n\nهذا النص يُستخدم لاختبار المحرر."
            bookTitle = "كتاب تجريبي"
            author = "مؤلف تجريبي"
        }

        if (pageNumber > 0) {
            pageNum = pageNumber
        }
    }

    private fun detectPageMarkers(text: String): List<PageMarker> {
        val markers = mutableListOf<PageMarker>()
        var idx = 0
        while (idx < text.length) {
            val m = PAGE_MARKER_REGEX.find(text, idx) ?: break
            markers.add(
                PageMarker(
                    m.range.first,
                    m.range.last + 1,
                    m.groupValues[1].toIntOrNull() ?: 0
                )
            )
            idx = m.range.last + 1
        }
        return markers
    }

    /**
     * يقسّم نطاقًا [from, to) إلى أجزاء «نص حقيقي» خالية من علامات الصفحات،
     * ليُصدَّر المحتوى النظيف دون شوائب «≪ صفحة N ≫».
     */
    private fun cleanPieces(from: Int, to: Int): List<CleanPiece> {
        if (!hasDocumentContext) return listOf(CleanPiece(from, to))
        val pieces = mutableListOf<CleanPiece>()
        var cursor = from
        for (m in pageMarkers) {
            if (m.end <= from) continue
            if (m.start >= to) break
            if (m.start > cursor) pieces.add(CleanPiece(cursor, minOf(m.start, to)))
            cursor = maxOf(cursor, m.end)
            if (cursor >= to) break
        }
        if (cursor < to) pieces.add(CleanPiece(cursor, to))
        return pieces
    }

    private fun toCleanOffset(pieces: List<CleanPiece>, global: Int): Int {
        var off = 0
        for (piece in pieces) {
            if (global <= piece.start) return off
            if (global >= piece.end) {
                off += piece.end - piece.start
                continue
            }
            return off + (global - piece.start)
        }
        return off
    }

    private fun selectedCleanText(): Pair<String, List<CleanPiece>> {
        val pieces = cleanPieces(selStart, selEnd)
        return pieces.joinToString("") { fullText.substring(it.start, it.end) } to pieces
    }

    private var pageNum: Int = 0

    private fun buildMetaText(): String = buildString {
    val parts = mutableListOf<String>()
    if (bookTitle.isNotBlank()) parts += bookTitle
    if (author.isNotBlank()) parts += author
    if (edition.isNotBlank()) parts += edition
    if (citation.isNotBlank()) parts += citation
    if (pageNum > 0) parts += "صفحة $pageNum"
    if (commentary.isNotBlank()) parts += commentary
    append(parts.joinToString(" · "))
}

    private fun refreshPreview(preserveScroll: Boolean = true) {
        // نثبّت سطر الإرساء (السطر أعلى النافذة) قبل إعادة البناء حتى لا يقفز
        // التمرير عند تغيير حجم الخط أو العرض أو التباعد: نحسب الفارق في إزاحات
        // هذا السطر قبل/بعد، ونعيد الضبط بنفس إزاحة الشاشة السابقة.
        val oldLayout = if (preserveScroll && ::scrollView.isInitialized) tvContent.layout else null
        val oldScrollY = if (preserveScroll && ::scrollView.isInitialized) scrollView.scrollY else 0
        val anchorLine = oldLayout?.let { it.getLineForVertical(oldScrollY).coerceIn(0, it.lineCount - 1) }
        val oldAnchorTop = if (anchorLine != null && oldLayout != null) oldLayout.getLineTop(anchorLine) else 0

        val bg = currentBg
        previewRoot.setBackgroundColor(bg.bgColor)

        tvMeta.text = buildMetaText()

        tvContent.apply {
            typeface = if (isBoldAll && !hasSelection)
                Typeface.create(currentTypeface, Typeface.BOLD)
            else
                currentTypeface
            textSize = currentFontSize
            setLineSpacing(dp(currentParaSpacing * currentFontSize * 0.5f), currentLineSpacing)
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
        if (hasSelection) {
            ssb.setSpan(
                BackgroundColorSpan(getColor(R.color.urwah_selection)),
                selStart, selEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        applyHideUnselected(ssb)
        tvContent.text = ssb

        val metaRatio = 0.85f
        tvMeta.textSize = currentFontSize * metaRatio
        tvMeta.typeface = Typeface.create(currentTypeface, Typeface.NORMAL)
        tvMeta.setTextColor(bg.metaColor)
        tvMeta.textAlignment = tvContent.textAlignment

        tvBrandLabel.setTextColor(bg.metaColor)
        tvBrandLabel.textAlignment = tvContent.textAlignment

        tvDivider.setBackgroundColor(bg.dividerColor)
        tvDivider.visibility = View.VISIBLE

        ivQuoteLogo.alpha = 0.85f

        tvMeta.post {
            val available = (tvMeta.width - dp(4f)).toInt().coerceAtLeast(dp(40f).toInt())
            shrinkToFit(tvMeta, available)
        }

        if (hasSelection && !previewMode) positionSelectionHandles()

        if (preserveScroll && ::scrollView.isInitialized) {
            val newLayout = tvContent.layout
            val newScrollY = if (anchorLine != null && newLayout != null)
                oldScrollY + (newLayout.getLineTop(anchorLine.coerceAtMost(newLayout.lineCount - 1)) - oldAnchorTop)
            else
                oldScrollY
            scrollView.post { scrollView.scrollTo(0, newScrollY.coerceAtLeast(0)) }
        }
    }

    private fun shrinkToFit(view: TextView, maxWidthPx: Int) {
        val text = view.text.toString()
        if (text.isEmpty() || maxWidthPx <= 0) return
        val paint = android.text.TextPaint(view.paint)
        var sizePx = view.textSize
        while (sizePx > dp(7f)) {
            paint.textSize = sizePx
            if (paint.measureText(text) <= maxWidthPx) break
            sizePx -= 1f
        }
        if (view.textSize != sizePx) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx)
        }
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

    private fun applyBaseStyles(ssb: SpannableStringBuilder) {
        if (hasDocumentContext && pageMarkers.isNotEmpty()) {
            val dimColor = (currentBg.metaColor and 0x00FFFFFF) or (0x59 shl 24)
            for (m in pageMarkers) {
                if (m.start >= ssb.length) continue
                val s = m.start.coerceIn(0, ssb.length)
                val e = m.end.coerceAtMost(ssb.length)
                if (s >= e) continue
                ssb.setSpan(ForegroundColorSpan(dimColor), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ssb.setSpan(RelativeSizeSpan(0.75f), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        for ((range, style) in currentSpans) {
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
        pushHistory()
        val range = selStart..selEnd
        val existing = currentSpans[range] ?: QuoteSpanStyle()
        val highlightColor = android.graphics.Color.parseColor("#FFFFEB3B")
        when (effect) {
            QuoteEffect.HIGHLIGHT -> existing.bgColor =
                if (existing.bgColor != android.graphics.Color.TRANSPARENT) android.graphics.Color.TRANSPARENT else highlightColor
            QuoteEffect.TEXT_COLOR -> existing.textColor = currentBg.textColor
            QuoteEffect.BOLD -> existing.isBold = !existing.isBold
            QuoteEffect.UNDERLINE -> existing.isUnderlined = !existing.isUnderlined
            QuoteEffect.DIM -> existing.isDimmed = !existing.isDimmed
            QuoteEffect.HIDE -> existing.isHidden = !existing.isHidden
            QuoteEffect.SCALE_UP -> existing.scaleFactor = if (existing.scaleFactor == 1.2f) 1f else 1.2f
            QuoteEffect.SCALE_DOWN -> existing.scaleFactor = if (existing.scaleFactor == 0.8f) 1f else 0.8f
            QuoteEffect.BLUR -> {}
            QuoteEffect.NONE -> currentSpans.remove(range)
        }
        if (effect != QuoteEffect.NONE) {
            if (isDefaultStyle(existing)) currentSpans.remove(range) else currentSpans[range] = existing
        }
        refreshPreview()
        updateUndoRedoUI()
        updateEffectButtonsActiveState()
    }

    private fun isDefaultStyle(style: QuoteSpanStyle): Boolean {
        return style.bgColor == android.graphics.Color.TRANSPARENT &&
            style.textColor == null &&
            !style.isBold && !style.isUnderlined && !style.isBlurred &&
            !style.isDimmed && !style.isHidden && style.scaleFactor == 1f
    }

    private fun clearAllEffects() {
        pushHistory()
        currentSpans.clear()
        clearSelection()
        refreshPreview()
        updateUndoRedoUI()
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
            pushHistory()
            hideUnselected = !hideUnselected
            updateHideUnselectedUI()
            if (hasSelection) {
                refreshPreview()
            }
            updateUndoRedoUI()
        }
        btnFormat.setOnClickListener { showToolsPanel(true) }
    }

    private fun setupCitationInputs() {
        val citationWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                citation = s?.toString()?.trim() ?: ""
                tvMeta.text = buildMetaText()
            }
        }
        val commentaryWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                commentary = s?.toString()?.trim() ?: ""
                tvMeta.text = buildMetaText()
            }
        }
        etCitation.addTextChangedListener(citationWatcher)
        etCommentary.addTextChangedListener(commentaryWatcher)
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
                pushHistory()
                currentFontFile = font.fileName
                currentTypeface = FontManager.loadTypeface(this, font.fileName)
                refreshPreview()
                setupFontChips()
                updateUndoRedoUI()
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
        // اللمس على الخلفية المعتمة = طيّ اللوحة للمقبض فقط
        findViewById<View>(R.id.toolsScrim).setOnClickListener { setSheetState(false) }
        btnCloseTools.setOnClickListener { setSheetState(false) }
    }

    /**
     * كل زر في الشريط السفلي يفتح Bottom Sheet مستقلاً به:
     * نُظهر قسمه فقط ونخفي بقية الأقسام — لا صفحة أدوات واحدة ولا تمرير للوصول للإعداد.
     */
    private fun setupToolbarTools() {
        val allSections = listOf(
            R.id.sectionAlign, R.id.sectionFont, R.id.sectionSpacing,
            R.id.sectionWidth, R.id.sectionSize, R.id.sectionEffects,
            R.id.sectionBackground, R.id.sectionMeta
        )
        fun showOnly(sectionId: Int) {
            for (id in allSections) {
                findViewById<View>(id).visibility = if (id == sectionId) View.VISIBLE else View.GONE
            }
            toolsScroll.scrollTo(0, 0)
        }
        // الخط + التباعد + العرض معاً في نفس اللوحة
        fun showFontFamily() {
            for (id in allSections) {
                findViewById<View>(id).visibility =
                    if (id == R.id.sectionFont || id == R.id.sectionSpacing || id == R.id.sectionWidth)
                        View.VISIBLE else View.GONE
            }
            toolsScroll.scrollTo(0, 0)
        }

        findViewById<View>(R.id.toolAlign).setOnClickListener {
            showOnly(R.id.sectionAlign)
            openToolsSheet()
        }
        findViewById<View>(R.id.toolFont).setOnClickListener {
            showFontFamily()
            openToolsSheet()
        }
        findViewById<View>(R.id.toolSize).setOnClickListener {
            showOnly(R.id.sectionSize)
            openToolsSheet()
        }
        findViewById<View>(R.id.toolEffects).setOnClickListener {
            if (!hasSelection) {
                Toast.makeText(this, "حدّد جزءًا من النص أولًا لتفعيل التأثيرات", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showOnly(R.id.sectionEffects)
            openToolsSheet()
        }
        findViewById<View>(R.id.toolBackground).setOnClickListener {
            showOnly(R.id.sectionBackground)
            openToolsSheet()
        }
        findViewById<View>(R.id.toolMeta).setOnClickListener {
            showOnly(R.id.sectionMeta)
            openToolsSheet()
        }
    }

    /** فتح اللوحة موسّعة على القسم المحدد مسبقاً. */
    private fun openToolsSheet() {
        if (previewMode) return
        setSheetState(true)
    }

    // ===== Bottom Sheet موحد: حالتان (موسّع / مطوي) + سحب على المقبض =====
    private var sheetExpanded = false
    private val sheetPeekHeight: Int get() = dp(72f).toInt() // المقبض + العنوان يظهران دائمًا

    private fun setupSheetDrag() {
        val handle = findViewById<View>(R.id.toolsHandle)
        var downY = 0f
        var startTranslation = 0f
        var dragging = false
        handle.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    startTranslation = toolsPanel.translationY
                    dragging = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragging && toolsPanel.height > 0) {
                        val maxT = (toolsPanel.height - sheetPeekHeight).toFloat()
                        toolsPanel.translationY = (startTranslation + (event.rawY - downY)).coerceIn(0f, maxT)
                        true
                    } else false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        dragging = false
                        val maxT = (toolsPanel.height - sheetPeekHeight).toFloat()
                        // أقرب حالة للإفلات: فوق المنتصف = موسّع، وإلا مطوي
                        setSheetState(toolsPanel.translationY < maxT / 2f)
                        true
                    } else false
                }
                else -> false
            }
        }
        // نقرة على المقبض/العنوان والمطوية = توسيع
        handle.setOnClickListener { if (!sheetExpanded) setSheetState(true) }
        findViewById<View>(R.id.toolsHeader).setOnClickListener { if (!sheetExpanded) setSheetState(true) }
    }

    /** الحالة الموحدة: expanded=true يظهر المحتوى كاملاً، false يبقى المقبض فقط. */
    private fun setSheetState(expanded: Boolean) {
        sheetExpanded = expanded
        if (expanded) {
            if (previewMode) return
            showTopBars()
            toolsOverlay.visibility = View.VISIBLE
            toolsScroll.post {
                val maxH = (resources.displayMetrics.heightPixels * 0.6f).toInt()
                toolsScroll.layoutParams.height = maxH
                toolsScroll.layoutParams = toolsScroll.layoutParams
            }
            toolsPanel.post {
                toolsPanel.animate().translationY(0f).setDuration(220).start()
            }
        } else {
            toolsPanel.post {
                val target = (toolsPanel.height - sheetPeekHeight).coerceAtLeast(0).toFloat()
                toolsPanel.animate().translationY(target).setDuration(200)
                    .withEndAction { /* اللوحة تبقى ظاهرة بالمقبض */ }
                    .start()
            }
        }
    }

    /** فتح الأداة: إن كانت مطوية نوسّع، وإن كانت مفتوحة نبقى (سكروول فقط). */
    private fun showToolsPanel(show: Boolean) {
        if (show) {
            if (previewMode) return
            if (!sheetExpanded || toolsOverlay.visibility != View.VISIBLE) {
                setSheetState(true)
            }
        } else {
            setSheetState(false)
        }
    }

    override fun onBackPressed() {
        if (previewMode) {
            togglePreviewMode()
        } else if (sheetExpanded) {
            setSheetState(false)
        } else if (toolsOverlay.visibility == View.VISIBLE) {
            // اللوحة مطوية (مقبض ظاهر) — الرجوع يخفيها كلياً
            toolsOverlay.visibility = View.GONE
        } else {
            super.onBackPressed()
        }
    }

    private fun setupBackgroundSelector() {
        bgSelector.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        val adapter = QuoteBackgroundAdapter(bgList) { bg ->
            pushHistory()
            currentBg = bg
            refreshPreview()
            updateUndoRedoUI()
        }
        bgAdapter = adapter
        bgSelector.adapter = adapter
    }

    private fun setupSizeSelector() {
        sizeSelector.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        val adapter = QuoteSizeAdapter(quoteSizes) { size ->
            pushHistory()
            sizeIndex = quoteSizes.indexOf(size).coerceAtLeast(0)
            applyImageSize()
            refreshPreview()
            updateUndoRedoUI()
        }
        sizeAdapter = adapter
        sizeSelector.adapter = adapter
    }

    private fun applyImageSize() {
        previewFrame.post {
            val w = previewFrame.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            val sz = quoteSizes[sizeIndex]
            val flp = previewFrame.layoutParams
            if (sz.w > 0 && sz.h > 0) {
                flp.height = (w.toLong() * sz.h / sz.w).toInt()
                val rlp = previewRoot.layoutParams
                rlp.height = ViewGroup.LayoutParams.MATCH_PARENT
                previewRoot.layoutParams = rlp
            } else {
                flp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                val rlp = previewRoot.layoutParams
                rlp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                previewRoot.layoutParams = rlp
            }
            previewFrame.layoutParams = flp
            previewBorder.visibility = if (sizeIndex > 0) View.VISIBLE else View.GONE
            previewBorder.alpha = if (previewMode) 1f else 0.55f
        }
    }

    private fun togglePreviewMode() {
        previewMode = !previewMode
        if (previewMode) {
            clearSelection()
            showToolsPanel(false)
            btnUndo.visibility = View.GONE
            btnRedo.visibility = View.GONE
            btnPreview.text = "تحرير"
            toolsOverlay.visibility = View.GONE
        } else {
            btnUndo.visibility = View.VISIBLE
            btnRedo.visibility = View.VISIBLE
            btnPreview.text = "معاينة"
            ivPreviewBitmap.visibility = View.GONE
        }
        applyImageSize()
        refreshPreview()
        if (previewMode) {
            // المعاينة الحقيقية: نُصدّر الناتج بنفس renderer الحفظ ونعرضه كما هو.
            previewFrame.post { renderPreviewBitmap() }
        }
    }

    /**
     * يبني صورة الناتج النهائي بنفس مسار التصدير (capturePreviewBitmap) ويعرضها،
     * فيرى المستخدم EXACTLY ما سيُحفظ. الإصلاح الجذري للمعاينة الفارغة:
     * 1) القياس عبر measure() صريح قبل الالتقاط (لا اعتماد على measuredHeight القديم).
     * 2) ضبط ارتفاع ImageView من نسبة أبعاد الصورة الفعلية (fitCenter وحده لا يمنح ارتفاعاً
     *    داخل match_parent فيظهر فارغاً إن كانت الصورة أطول من الشاشة).
     */
    private fun renderPreviewBitmap() {
        if (!previewMode) return
        val wrapW = previewWrap.width.takeIf { it > 0 } ?: return
        val bmp = capturePreviewBitmap(1f) ?: run {
            // فشل الالتقاط: نُبقي المحرر الحي ظاهراً بدل شاشة فارغة
            ivPreviewBitmap.visibility = View.GONE
            return
        }
        ivPreviewBitmap.setImageBitmap(bmp)
        val lp = ivPreviewBitmap.layoutParams
        lp.height = (wrapW.toLong() * bmp.height / bmp.width).toInt().coerceAtLeast(1)
        ivPreviewBitmap.layoutParams = lp
        ivPreviewBitmap.visibility = View.VISIBLE
    }

    private fun setupAlignControls() {
        btnAlignRight.setOnClickListener {
            if (currentAlign == Layout.Alignment.ALIGN_NORMAL && !isJustify) return@setOnClickListener
            pushHistory(); currentAlign = Layout.Alignment.ALIGN_NORMAL; isJustify = false; updateAlignUI(); refreshPreview(); updateUndoRedoUI()
        }
        btnAlignCenter.setOnClickListener {
            if (currentAlign == Layout.Alignment.ALIGN_CENTER && !isJustify) return@setOnClickListener
            pushHistory(); currentAlign = Layout.Alignment.ALIGN_CENTER; isJustify = false; updateAlignUI(); refreshPreview(); updateUndoRedoUI()
        }
        btnAlignLeft.setOnClickListener {
            if (currentAlign == Layout.Alignment.ALIGN_OPPOSITE && !isJustify) return@setOnClickListener
            pushHistory(); currentAlign = Layout.Alignment.ALIGN_OPPOSITE; isJustify = false; updateAlignUI(); refreshPreview(); updateUndoRedoUI()
        }
        btnAlignJustify.setOnClickListener {
            if (isJustify) return@setOnClickListener
            pushHistory(); currentAlign = Layout.Alignment.ALIGN_NORMAL; isJustify = true; updateAlignUI(); refreshPreview(); updateUndoRedoUI()
        }
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
            override fun onStartTrackingTouch(seekBar: SeekBar) { beginGesture() }
            override fun onStopTrackingTouch(seekBar: SeekBar) { endGesture() }
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
            override fun onStartTrackingTouch(seekBar: SeekBar?) { beginGesture() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { endGesture() }
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
            override fun onStartTrackingTouch(seekBar: SeekBar?) { beginGesture() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { endGesture() }
        })

        textWidthSlider.progress = (textWidth * 100).toInt().coerceIn(50, 100)
        textWidthSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                textWidth = (progress.coerceIn(50, 100)) / 100f
                tvTextWidthValue.text = "${(textWidth * 100).toInt()}%"
                refreshPreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { beginGesture() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { endGesture() }
        })

        btnBoldAll.setOnClickListener {
            pushHistory()
            if (hasSelection) {
                applyEffectToSelection(QuoteEffect.BOLD)
            } else {
                isBoldAll = !isBoldAll
                updateBoldAllUI()
                refreshPreview()
            }
            updateUndoRedoUI()
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
        btnPreview.setOnClickListener { togglePreviewMode() }
        btnUndo.setOnClickListener { undo() }
        btnRedo.setOnClickListener { redo() }
    }

    // ===== Undo / Redo =====

    private fun captureState(): EditorState = EditorState(
        fullText = fullText,
        spans = currentSpans.mapValues { (_, style) -> style.copy() },
        align = currentAlign,
        isJustify = isJustify,
        bg = currentBg,
        fontFile = currentFontFile,
        fontSize = currentFontSize,
        lineSpacing = currentLineSpacing,
        paraSpacing = currentParaSpacing,
        textWidth = textWidth,
        isBoldAll = isBoldAll,
        hideUnselected = hideUnselected,
        selBase = selectionBase,
        selExtent = selectionExtent,
        sizeIndex = sizeIndex,
        citation = citation,
        commentary = commentary
    )

    private fun pushHistory() = pushState(captureState())

    private fun pushState(state: EditorState) {
        undoStack.addLast(state)
        if (undoStack.size > maxHistory) undoStack.removeFirst()
        redoStack.clear()
        updateUndoRedoUI()
    }

    private fun beginGesture() {
        pendingGesture = captureState()
    }

    private fun endGesture() {
        val p = pendingGesture ?: return
        pendingGesture = null
        if (p != captureState()) pushState(p)
        updateUndoRedoUI()
    }

    private fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(captureState())
        if (redoStack.size > maxHistory) redoStack.removeFirst()
        restoreState(undoStack.removeLast())
    }

    private fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(captureState())
        if (undoStack.size > maxHistory) undoStack.removeFirst()
        restoreState(redoStack.removeLast())
    }

    private fun restoreState(state: EditorState) {
        fullText = state.fullText
        pageMarkers = detectPageMarkers(fullText)
        currentSpans.clear()
        currentSpans.putAll(state.spans.mapValues { (_, style) -> style.copy() })
        currentAlign = state.align
        isJustify = state.isJustify
        currentBg = state.bg
        currentFontFile = state.fontFile
        currentTypeface = FontManager.loadTypeface(this, state.fontFile)
        currentFontSize = state.fontSize
        currentLineSpacing = state.lineSpacing
        currentParaSpacing = state.paraSpacing
        textWidth = state.textWidth
        isBoldAll = state.isBoldAll
        hideUnselected = state.hideUnselected
        selectionBase = state.selBase
        selectionExtent = state.selExtent
        sizeIndex = state.sizeIndex
        citation = state.citation
        commentary = state.commentary
        adjustMode = AdjustMode.NONE

        syncControls()
        applyImageSize()
        refreshPreview()
        updateSelectionUI()
        updateUndoRedoUI()
    }

    private fun syncControls() {
        fontSizeSlider.progress = (currentFontSize - 8f).toInt().coerceIn(0, 40)
        lineSpacingSlider.progress = (((currentLineSpacing - 1f) / 2f) * 100f).toInt().coerceIn(0, 100)
        paraSpacingSlider.progress = (((currentParaSpacing - 0.5f) / 2.5f) * 100f).toInt().coerceIn(0, 100)
        textWidthSlider.progress = (textWidth * 100f).toInt().coerceIn(50, 100)
        tvLineSpacingValue.text = "${String.format("%.1f", currentLineSpacing)}x"
        tvParaSpacingValue.text = "${String.format("%.1f", currentParaSpacing)}x"
        tvTextWidthValue.text = "${(textWidth * 100f).toInt()}%"
        updateAlignUI()
        updateBoldAllUI()
        updateHideUnselectedUI()
        bgAdapter?.setSelected(currentBg)
        sizeAdapter?.setSelected(quoteSizes[sizeIndex])
        setupFontChips()
        if (::etCitation.isInitialized) etCitation.setText(citation)
        if (::etCommentary.isInitialized) etCommentary.setText(commentary)
    }

    private fun updateUndoRedoUI() {
        btnUndo.alpha = if (undoStack.isEmpty()) 0.4f else 1f
        btnRedo.alpha = if (redoStack.isEmpty()) 0.4f else 1f
    }

    /**
     * أرقام الصفحات («≪ صفحة 12 ≫») علامات داخلية للمحرر فقط — تُحذف فعليًا
     * من نص التصدير/المعاينة مع إزاحة الـ spans المحيطة، فلا تظهر في الصورة النهائية.
     */
    private fun stripPageMarkersForExport(): Pair<String, Map<IntRange, QuoteSpanStyle>> {
        var text = fullText
        var spans = currentSpans.map { it.key to it.value }
        for (m in PAGE_MARKER_REGEX.findAll(fullText).toList().reversed()) {
            val s = m.range.first
            val e = m.range.last + 1
            val len = e - s
            text = text.substring(0, s) + text.substring(e.coerceAtMost(text.length))
            spans = spans.mapNotNull { (r, st) ->
                when {
                    r.first >= e -> ((r.first - len)..(r.last - len)) to st
                    r.last < s -> r to st
                    else -> null
                }
            }
        }
        return text to spans.toMap()
    }

    private fun capturePreviewBitmap(scale: Float = 3f): android.graphics.Bitmap? {
        // If user selected text, export only the selection
        val savedText = fullText
        if (hasSelection) {
            val (selText, pieces) = selectedCleanText()
            val ssb = SpannableStringBuilder(selText)
            for ((range, style) in currentSpans) {
                val overlapStart = maxOf(range.first, selStart)
                val overlapEnd = minOf(range.last, selEnd)
                if (overlapStart < overlapEnd) {
                    val localStart = toCleanOffset(pieces, overlapStart)
                    val localEnd = toCleanOffset(pieces, overlapEnd)
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

        // تصدير بلا أرقام صفحات: استبدال مؤقت للنص/spans ثم استرجاع كامل بعد الالتقاط.
        val savedMarkers = pageMarkers
        val savedSpans = LinkedHashMap(currentSpans)
        var swapped = false
        if (!hasSelection && pageMarkers.isNotEmpty()) {
            val (exportText, exportSpanMap) = stripPageMarkersForExport()
            currentSpans.clear()
            currentSpans.putAll(exportSpanMap)
            pageMarkers = emptyList()
            val exportSsb = SpannableStringBuilder(exportText)
            applyBaseStyles(exportSsb)
            tvContent.text = exportSsb
            swapped = true
        }

        val frame = previewFrame
        val sz = quoteSizes[sizeIndex]
        val w = frame.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val h = if (sz.w > 0 && sz.h > 0) (w.toLong() * sz.h / sz.w).toInt() else 0
        frame.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            if (h > 0)
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
            else
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val mw = frame.measuredWidth
        val mh = frame.measuredHeight
        if (mw <= 0 || mh <= 0) {
            if (hasSelection) restoreLiveText(savedText)
            return null
        }
        frame.layout(0, 0, mw, mh)

        // الرسم مباشرة بدقة عالية عبر canvas.scale — نص ورسوميات vector-sharp،
        // بدل الرسم بدقة الشاشة ثم تكبير راستري يسبب blur.
        // حماية ذاكرة: النصوص الطويلة جداً ×3 قد تتجاوز heap — نخفض المقياس
        // تدريجياً (3→2→1) بدل OutOfMemory، مع الحفاظ على أعلى جودة ممكنة.
        var effScale = if (scale > 1f) scale else 1f
        var outW = (mw * effScale).toInt()
        var outH = (mh * effScale).toInt()
        val maxBytes = 128L * 1024 * 1024 // سقف ~128MB للبيتكماپ (ARGB_8888 = 4 بايت/بكسل)
        while (effScale > 1f && outW.toLong() * outH * 4 > maxBytes) {
            effScale = when {
                effScale > 2f -> 2f
                else -> 1f
            }
            outW = (mw * effScale).toInt()
            outH = (mh * effScale).toInt()
        }
        val bitmap = try {
            android.graphics.Bitmap.createBitmap(outW, outH, android.graphics.Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            if (hasSelection) restoreLiveText(savedText)
            return null
        }
        val canvas = android.graphics.Canvas(bitmap)
        if (effScale > 1f) canvas.scale(effScale, effScale)
        frame.draw(canvas)

        // Restore full text (بما في ذلك إعادة تطبيق «إخفاء غير المحدد» حتى لا يختفي
        // التعتيم بعد التصدير — إصلاح الحالة القديمة العالقة)
        if (swapped) {
            pageMarkers = savedMarkers
            currentSpans.clear()
            currentSpans.putAll(savedSpans)
        }
        if (hasSelection) {
            restoreLiveText(savedText)
        } else if (swapped) {
            restoreLiveText(fullText)
        }

        return bitmap
    }

    private fun restoreLiveText(text: String) {
        val ssb = SpannableStringBuilder(text)
        applyBaseStyles(ssb)
        if (hasSelection) {
            ssb.setSpan(
                BackgroundColorSpan(getColor(R.color.urwah_selection)),
                selStart, selEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        applyHideUnselected(ssb)
        tvContent.text = ssb
    }

    private fun saveImage() {
        btnSave.isEnabled = false
        btnShare.isEnabled = false
        if (::loadingOverlay.isInitialized) loadingOverlay.visibility = View.VISIBLE
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
            btnShare.isEnabled = true
            if (::loadingOverlay.isInitialized && !isFinishing && !isDestroyed) {
                loadingOverlay.visibility = View.GONE
            }
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
        if (::loadingOverlay.isInitialized) loadingOverlay.visibility = View.VISIBLE
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
                if (isFinishing || isDestroyed) return@launch
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
            if (::loadingOverlay.isInitialized && !isFinishing && !isDestroyed) {
                loadingOverlay.visibility = View.GONE
            }
        }
    }
}
