package com.urwah.dhikr

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat

/**
 * صفحة مصحف واحدة — تُبنى من بيانات الأسطر الرسمية (QuranPageLayouts).
 *
 * الخوارزمية مطابقة للمرجع المفتوح (mushaf-renderer / QCF V2):
 *  - الشبكة: صفوف مبرّرة بين الحواف (justify-between) تملأ ارتفاع الصفحة،
 *    والصفحات الخاصة (الفاتحة وبداية البقرة) تتوسط أسطرها.
 *  - عنوان السورة + سطر البسملة يُدرجان قبل السطر الذي تبدأ فيه السورة
 *    عندما يحجز المصحف لهما سطرين (تفاوت line_number).
 *  - الأسطر القصيرة/الجزئية (التي تسبق عنوان سورة أو نهاية الصفحة) تتوسط.
 *  - رقم الصفحة في الأسفل.
 *
 * لا تُعاد هنا flow عشوائية: توزيع الكلمات على الأسطر قادم من المصدر الرسمي
 * حرفياً، فلا نص يُعاد توزيعه حسب عرض الشاشة.
 */
class MushafPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private var page: QuranPageLayouts.Page? = null
    private var wordTypeface: Typeface? = null
    private var surahNameProvider: ((Int) -> String)? = null
    private var inkColor: Int = 0
    private var accentColor: Int = 0
    private var pageNumber = 1
    private var onSingleTap: (() -> Unit)? = null
    private var onLongPress: (() -> Unit)? = null
    private var optimalTextSize: Float = 20f
    private var needsFontCompute = true

    private val gestureListener = object : android.view.GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onSingleTap?.invoke()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            onLongPress?.invoke()
        }
    }
    private val gestureDetector =
        android.view.GestureDetector(context, gestureListener)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        ViewCompat.setLayoutDirection(this, ViewCompat.LAYOUT_DIRECTION_RTL)
        setPadding(dp(18), dp(10), dp(18), dp(10))
    }

    fun setCallbacks(onSingleTap: () -> Unit, onLongPress: () -> Unit) {
        this.onSingleTap = onSingleTap
        this.onLongPress = onLongPress
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pg = page
        android.util.Log.i("MushafPageView", "onSizeChanged page=${pg?.number} w=$w h=$h oldw=$oldw oldh=$oldh childCount=$childCount needsFontCompute=$needsFontCompute")
        if (pg != null && (w != oldw || h != oldh) && (needsFontCompute || childCount == 0)) {
            computeOptimalFontSize()
            rebuild()
        }
        invalidate()
    }

    fun bind(
        page: QuranPageLayouts.Page,
        typeface: Typeface,
        inkColor: Int,
        accentColor: Int,
        surahNameProvider: (Int) -> String,
    ) {
        android.util.Log.i("MushafPageView", "bind page=${page.number} lines=${page.lines.size} firstLineWords=${page.lines.firstOrNull()?.words?.size}")
        this.page = page
        this.wordTypeface = typeface
        this.inkColor = inkColor
        this.accentColor = accentColor
        this.surahNameProvider = surahNameProvider
        pageNumber = page.number
        needsFontCompute = true
        computeOptimalFontSize()
        rebuild()
    }

    /**
     * يحسب حجم الخط المناسب بحيث تملأ الأسطر عرض الصفحة بالكامل:
     * أصغر من الحجم الذي يملأ أطول سطر (بعرض متاح) وبما يسمح به ارتفاع
     * الصفحة لكل الأسطر. لا يُعاد توزيع النص — فقط scale.
     */
    private fun computeOptimalFontSize() {
        val pg = page ?: return
        val tf = wordTypeface ?: return
        if (width <= 0 || height <= 0) return
        needsFontCompute = false

        // عرض الصفحة الداخلي بعد الهوامش
        val availW = width - dp(48)
        val availH = (height - dp(40)).coerceAtLeast(1)

        // أطول سطر نصياً (مجموع عروض كلماته عند حجم مرجعي) مع حساب
        // شكل الخط الحقيقي (shaping) عبر القياس الفعلي
        var maxLineWidthAtRef = 1f
        val paint = android.graphics.Paint().apply { this.typeface = tf; textSize = 100f }
        for (line in pg.lines) {
            var acc = 0f
            for (w in line.words) {
                acc += paint.measureText(w.text)
                acc += 4f * 100f / 20f // مسافة بين الكلمات
            }
            if (acc > maxLineWidthAtRef) maxLineWidthAtRef = acc
        }
        val sizeByWidth = availW / maxLineWidthAtRef * 100f

        val numRows = rowsCount(pg)
        // ارتفاع السطر الحقيقي للخط العثماني أكبر من حجم الخط بسبب التشكيل
        val sizeByHeight = availH / (numRows.coerceAtLeast(1) * 2.1f)

        optimalTextSize = sizeByWidth.coerceAtMost(sizeByHeight).coerceIn(14f, 40f)
    }

    private fun rowsCount(pg: QuranPageLayouts.Page): Int {
        var rows = pg.lines.size
        val lineNumbersOnPage = pg.lines.map { it.number }.toSet()
        for (s in pg.surahs) {
            val firstWord = pg.lines.asSequence().flatMap { it.words.asSequence() }
                .firstOrNull { it.surah == s && it.ayah == 1 } ?: continue
            val bismillahLine = firstWord.line - 2
            val hasBismillah = bismillahLine >= 1 && !lineNumbersOnPage.contains(bismillahLine)
            rows += 1 // عنوان السورة
            if (hasBismillah && s != 1 && s != 9) rows += 1
        }
        return rows
    }

    private fun rebuild() {
        removeAllViews()
        val pg = page ?: return
        val tf = wordTypeface ?: return
        val names = surahNameProvider ?: return
        val isSpecial = pg.number == 1 || pg.number == 2
        android.util.Log.i("MushafPageView", "rebuild page=${pg.number} optimalTextSize=$optimalTextSize width=$width height=$height")

        val lineNumbersOnPage = pg.lines.map { it.number }.toSet()

        // أين تبدأ كل سورة في الصفحة (سطر أول آية لها)
        val surahAtLine = LinkedHashMap<Int, Int>()
        val surahFirstVerseLines = mutableListOf<Int>()
        for (s in pg.surahs) {
            val firstWord = pg.lines.asSequence().flatMap { it.words.asSequence() }
                .firstOrNull { it.surah == s && it.ayah == 1 }
            if (firstWord != null) {
                surahAtLine[firstWord.line] = s
                surahFirstVerseLines.add(firstWord.line)
            }
        }

        // الأسطر الجزئية: السطر قبل عنوان سورة، والأسطر القصيرة المتتالية في النهاية
        val partialLines = mutableSetOf<Int>()
        val sortedDataLines = pg.lines.map { it.number }.sorted()
        for (firstVerseLine in surahFirstVerseLines) {
            for (i in sortedDataLines.indices.reversed()) {
                if (sortedDataLines[i] < firstVerseLine) {
                    partialLines.add(sortedDataLines[i])
                    break
                }
            }
        }
        for (i in pg.lines.indices.reversed()) {
            if (pg.lines[i].words.size <= 6) partialLines.add(pg.lines[i].number) else break
        }

        // بناء الصفوف بالترتيب
        val rows = mutableListOf<View>()
        for (line in pg.lines) {
            val surah = surahAtLine[line.number]
            if (surah != null) {
                // عنوان السورة الزخرفي
                rows.add(buildSurahHeader(names(surah)))
                // سطر بسملة مخصص: gap بين العنوان وأول آية
                val firstWordLine = surahAtLine.entries.first { it.value == surah }.key
                val bismillahLine = firstWordLine - 2
                val hasBismillahLine = bismillahLine >= 1 && !lineNumbersOnPage.contains(bismillahLine)
                if (surah != 1 && surah != 9 && hasBismillahLine) {
                    rows.add(buildBismillahLine(tf))
                }
            }
            rows.add(buildVerseLine(line, partialLines.contains(line.number), isSpecial, tf))
        }

        // شبكة الصفوف تملأ ارتفاع الصفحة، والتوزيع بين الحواف (justify-between)
        val grid = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        if (isSpecial) {
            // صفحات خاصة: الأسطر تتوسط ولا تتمدد
            for (r in rows) {
                grid.addView(r, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                if (r !== rows.last()) {
                    grid.addView(spacer(1))
                }
            }
            grid.gravity = Gravity.CENTER
        } else {
            // التوزيع المتساوي بين الصفوف يملأ الارتفاع
            val weight = 1f / rows.size
            for (r in rows) {
                grid.addView(r, LayoutParams(LayoutParams.MATCH_PARENT, 0, weight))
            }
        }
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        // رقم الصفحة
        val footer = TextView(context).apply {
            text = hindiDigits(pageNumber)
            setTextColor(inkColor)
            typeface = Typeface.create("serif", Typeface.NORMAL)
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        addView(footer)
    }

    private fun spacer(size: Int): View {
        return View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, size)
        }
    }

    private fun buildSurahHeader(name: String): View {
        val header = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
        }
        val title = TextView(context).apply {
            text = name
            typeface = Typeface.create("serif", Typeface.BOLD)
            setTextColor(inkColor)
            textSize = optimalTextSize * 0.9f
            gravity = Gravity.CENTER
        }
        header.addView(title)

        val ornament = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lp.topMargin = 4
            layoutParams = lp
        }
        val line = View(context).apply {
            val lp = LayoutParams(dp(28), dp(1))
            layoutParams = lp
            setBackgroundColor(accentColor)
        }
        val dot = View(context).apply {
            val lp = LayoutParams(dp(6), dp(6))
            lp.setMargins(dp(6), 0, dp(6), 0)
            layoutParams = lp
            background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_ayah_number)
        }
        val line2 = View(context).apply {
            val lp = LayoutParams(dp(28), dp(1))
            layoutParams = lp
            setBackgroundColor(accentColor)
        }
        ornament.addView(line)
        ornament.addView(dot)
        ornament.addView(line2)
        header.addView(ornament)
        return header
    }

    private fun buildBismillahLine(tf: Typeface): View {
        val tv = TextView(context).apply {
            text = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"
            typeface = tf
            setTextColor(inkColor)
            textSize = optimalTextSize
            gravity = Gravity.CENTER
        }
        return tv
    }

    private fun buildVerseLine(
        line: QuranPageLayouts.Line,
        isPartial: Boolean,
        isSpecial: Boolean,
        tf: Typeface,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            ViewCompat.setLayoutDirection(this, ViewCompat.LAYOUT_DIRECTION_RTL)
        }
        val rowParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        row.layoutParams = rowParams

        val stretch = !isSpecial && !isPartial
        if (stretch) {
            // justify-between: كلمات على الحواف والمسافات تتوزع بينها
            var first = true
            for (w in line.words) {
                if (!first) {
                    val spacerView = View(context).apply {
                        layoutParams = LayoutParams(0, 1, 1f)
                    }
                    row.addView(spacerView)
                }
                first = false
                row.addView(buildWordView(w, tf))
            }
        } else {
            // سطر جزئي/خاص: الكلمات متلاصقة تتوسط السطر
            for (w in line.words) {
                row.addView(buildWordView(w, tf))
            }
            row.gravity = Gravity.CENTER
        }
        return row
    }

    private fun buildWordView(w: QuranPageLayouts.Word, tf: Typeface): View {
        val tv = TextView(context).apply {
            text = w.text
            typeface = tf
            setTextColor(inkColor)
            textSize = optimalTextSize
            includeFontPadding = true
            gravity = Gravity.CENTER
        }
        tv.setPadding(dp(2), 0, dp(2), 0)
        if (w.type == "end") {
            // علامة نهاية الآية: الرقم داخل إطار دائري
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(4), 0, dp(4), 0)
            tv.layoutParams = lp
            tv.setBackgroundResource(R.drawable.bg_ayah_number)
            tv.setTextColor(android.graphics.Color.WHITE)
            tv.setPadding(dp(7), 0, dp(7), 0)
            tv.textSize = optimalTextSize * 0.72f
        } else {
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(1), 0, dp(1), 0)
            tv.layoutParams = lp
        }
        return tv
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun hindiDigits(number: Int): String {
        val arabicIndic = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
        return number.toString().map { arabicIndic[it - '0'] }.joinToString("")
    }
}
