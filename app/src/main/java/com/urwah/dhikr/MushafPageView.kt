package com.urwah.dhikr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import java.util.concurrent.atomic.AtomicBoolean
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import java.util.Locale

/**
 * صفحة مصحف واحدة — تُرسم على الكانفس مباشرة (بلا أطفال).
 *
 * نموذج الصفحة (P0):
 *  - صفحة بنسبة ثابتة (19.5:28.5 — MushafPageMetrics) تُقاس تحجيماً موحّداً
 *    داخل الخلية Letterbox: `scale = min(cellW/PW, cellH/PH)` — لا Stretch.
 *  - منطقة نص داخل الصفحة بهوامش مصحفية — لا تساوي عرض الشاشة.
 *  - حجم خط واحد: أصغرُ من (أطول سطر طبيعي يملأ عرض النص) و(سطر الشبكة).
 *  - سطور الآيات تُبرَّر بتوزيع المتبقي على الفجوات فقط؛ الجزئيات وعناوين
 *    السور والبسملة والصفحتان 1-2 تتوسط بلا تمديد (مرجع quran.com d6a12e6).
 *  - شبكة صفوف ثابتة `pitch = textH / rows` والـ glyphs تتمحور في الخانات.
 *  - أرقام نهاية الآيات: أرقام هندية (U+0660–669) بحجم الخط الكامل في دائرة.
 *
 * البيانات (line breaks 15 سطراً) لا تُعاد توزيعها — تبقى من المصدر حرفياً.
 */
class MushafPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var page: QuranPageLayouts.Page? = null
    private var wordTypeface: Typeface? = null
    private var glyphTypeface: Typeface? = null
    private var surahNameProvider: ((Int) -> String)? = null
    private var inkColor: Int = 0
    private var accentColor: Int = 0
    private var pageNumber = 1
    private var onSingleTap: (() -> Unit)? = null
    private var onLongPress: (() -> Unit)? = null
    private var optimalTextSize: Float = 20f
    private var needsFontCompute = true

    // مساحة النطاق المتاح (px) فوق وتحت الصفحة — شريط الواجهة العلوي وسطر المشغّل
    private var insetTop: Int = 0
    private var insetBottom: Int = 0
    private var insetLeft: Int = 0
    private var insetRight: Int = 0

    /** هامش الحماية الأفقي الأدنى الإجمالي (px، يُوزّع يمنة ويسرة) — يمنع ملامسة النص للشاشة. */
    private val H_MARGIN_MIN: Float get() = dpv(24)

    private var layout: PageLayout? = null

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
        ViewCompat.setLayoutDirection(this, ViewCompat.LAYOUT_DIRECTION_RTL)
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
        if (page != null && (w != oldw || h != oldh) && (needsFontCompute || layout == null)) {
            buildLayout()
        }
        invalidate()
    }

    /** اختبار تشخيصي لمرة واحدة: يرسم أول كلمة glyph بالخط QCF على Bitmap ويحفظ PNG. */
    private val glyphProbeDone = AtomicBoolean(false)

    private fun reduceGlyphTraces() {} // no-op — أزيلت

    private fun probeGlyphFace(gtf: Typeface?) {
        if (glyphProbeDone.getAndSet(true)) return
        try {
            if (gtf == null) { writeProbe("no-face"); return }
            val bmp = android.graphics.Bitmap.createBitmap(1200, 400, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = gtf
                textSize = 140f
                color = android.graphics.Color.BLACK
            }
            c.drawColor(android.graphics.Color.WHITE)
            c.drawText("\uFC41\uFC42\uFC43", 40f, 260f, p)
            val out = java.io.FileOutputStream(java.io.File(context.filesDir, "qcf_probe.png"))
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            out.close()
        } catch (e: Exception) {
            writeProbe("err: ${e.message}")
        }
    }

    private fun writeProbe(msg: String) {
        try {
            java.io.FileOutputStream(java.io.File(context.filesDir, "qcf_probe.txt")).write(msg.toByteArray())
        } catch (e: Exception) { }
    }

    fun bind(
        page: QuranPageLayouts.Page,
        typeface: Typeface,
        glyphTypeface: Typeface? = null,
        inkColor: Int,
        accentColor: Int,
        surahNameProvider: (Int) -> String,
    ) {
        android.util.Log.i("MushafPageView", "bind page=${page.number} lines=${page.lines.size} firstLineWords=${page.lines.firstOrNull()?.words?.size}")
        probeGlyphFace(glyphTypeface)
        this.page = page
        this.wordTypeface = typeface
        this.glyphTypeface = glyphTypeface
        this.inkColor = inkColor
        this.accentColor = accentColor
        this.surahNameProvider = surahNameProvider
        pageNumber = page.number
        needsFontCompute = true
        buildLayout()
    }

    /** يضبط النطاق المتاح (شريط الواجهة أعلى/الحواف الآمنة يميناً ويساراً، المشغّل أسفل) ويعيد بناء الهندسة. */
    fun setInsets(top: Int, bottom: Int, left: Int = 0, right: Int = 0) {
        if (insetTop == top && insetBottom == bottom && insetLeft == left && insetRight == right) return
        insetTop = top
        insetBottom = bottom
        insetLeft = left
        insetRight = right
        if (width > 0 && height > 0) buildLayout()
    }

    override fun onDraw(canvas: Canvas) {
        if (layout == null && width > 0 && height > 0) buildLayout()
        layout?.draw(canvas)
    }

    // ===== هندسة الصفحة =====

    /** أبعاد الصفحة ومنطقة النص بعد التحجيم الموحّد والهوامش. */
    private class PageGeometry(
        val pageL: Float,
        val pageT: Float,
        val pageW: Float,
        val pageH: Float,
        val textL: Float,
        val textT: Float,
        val textR: Float,
        val textB: Float,
    ) {
        val textW: Float get() = textR - textL
        val textH: Float get() = textB - textT
        val pageCenterX: Float get() = pageL + pageW / 2f
        val pageBottom: Float get() = pageT + pageH
    }

    private fun computeGeometry(): PageGeometry {
        val cellW = width.toFloat()
        val cellH = height.toFloat()
        // القيود الفعلية: النطاق الرأسي بعد الشريط العلوي (أو Safe Area شريط
        // الحالة عند إخفائه) وفوق سطر المشغّل السفلي؛ والنطاق الأفقي بعد
        // الحواف الآمنة اليمنى/اليسرى + هامش حماية جانبي ثابت بسيط.
        val availW = (cellW - insetLeft - insetRight - H_MARGIN_MIN).coerceAtLeast(1f)
        val availH = (cellH - insetTop - insetBottom).coerceAtLeast(1f)
        // الورقة تملأ النطاق المتاح أفقيّاً (سطور الصفحة تعمل بعرض الصفحة):
        val pageW = availW
        val pageL = insetLeft + H_MARGIN_MIN / 2f
        val mL = MushafPageMetrics.MARGIN_LEFT_RATIO * pageW
        val mR = MushafPageMetrics.MARGIN_RIGHT_RATIO * pageW
        // === ملء mushaf-imad (QuranPageView.kt، منقول حرفياً) ===
        // كل سطر مدينيّ صورة 1440×232: الميل الطبيعي من العرض = العرض/6.2069،
        // وفي الشاشات الطويلة يأخذ السطر حصته المتساوية من الارتفاع المتاح
        // (بعد هامشَي الورقة 3%+5%) فيملأ الصفحة حتى الحافة — كما في المشروع.
        val rows = rowsCount(page ?: return PageGeometry(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
        val pitch = minOf(
            pageW / MushafPageMetrics.LINE_ASPECT_1440x232,
            availH * (1f - MushafPageMetrics.MARGIN_TOP_RATIO - MushafPageMetrics.MARGIN_BOTTOM_RATIO) / rows.coerceAtLeast(1),
        )
        val textH = pitch * rows
        val pageH = (textH / (1f - MushafPageMetrics.MARGIN_TOP_RATIO - MushafPageMetrics.MARGIN_BOTTOM_RATIO))
            .coerceAtMost(availH)
        val pageT = insetTop + (availH - pageH) / 2f
        val mT = MushafPageMetrics.MARGIN_TOP_RATIO * pageH
        val mB = MushafPageMetrics.MARGIN_BOTTOM_RATIO * pageH
        return PageGeometry(
            pageL, pageT, pageW, pageH,
            pageL + mL, pageT + mT, pageL + pageW - mR, pageT + pageH - mB,
        )
    }

    /**
     * حجم الخط: أصغرُ قيمة تملأ بها أطولُ سطرٍ طبيعيٍّ عرضَ النص،
     * أو يجعل ارتفاعَ السطر الحقيقي يملأ خانة الشبكة (pitch).
     */
    private fun computeOptimalFontSize(pg: QuranPageLayouts.Page, tf: Typeface, gtf: Typeface?, g: PageGeometry) {
        // أطول سطر عند حجم مرجعي (100): مجموع عروض كلماته + الفجوات
        // (الكمات المصحفية تُقاس بالكراكترز QPC، والباقي بالنص العادي)
        var maxK = 1f
        val probe = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = tf; textSize = 100f }
        val probeGlyph = gtf?.let { Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = it; textSize = 100f } }
        for (line in pg.lines) {
            var k = 0f
            var n = 0
            for (w in line.words) {
                val isEnd = w.type == "end"
                if (probeGlyph != null && w.g != null) {
                    if (isEnd) continue
                    k += probeGlyph.measureText(w.g) / 100f
                } else {
                    k += probe.measureText(w.text) / 100f
                }
                n++
            }
            k += MushafPageMetrics.WORD_GAP_EM * (n - 1).coerceAtLeast(0)
            if (k > maxK) maxK = k
        }
        val sByWidth = g.textW / maxK

        // ارتفاع سطر حقيقي للخط (بالأم) + سقف الشبكة
        val fm = probe.fontMetrics
        val em = (fm.descent - fm.ascent) / 100f
        val rows = rowsCount(pg)
        val pitch = g.textH / rows.coerceAtLeast(1)
        val sByHeight = pitch * MushafPageMetrics.LINE_CAP / em

        optimalTextSize =
            minOf(sByWidth, sByHeight).coerceIn(MushafPageMetrics.FONT_MIN, MushafPageMetrics.FONT_MAX)
        needsFontCompute = false
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

    // ===== البناء =====

    private fun buildLayout() {
        val pg = page ?: return
        val tf = wordTypeface ?: return
        val gtf = glyphTypeface
        val names = surahNameProvider ?: return
        if (width <= 0 || height <= 0) return
        val g = computeGeometry()
        computeOptimalFontSize(pg, tf, gtf, g)
        val s = optimalTextSize
        val isSpecial = pg.number == 1 || pg.number == 2
        android.util.Log.i("MushafPageView", "rebuild page=${pg.number} s=$s cell=${width}x$height " +
            "page=${g.pageW.toInt()}x${g.pageH.toInt()} text=${g.textW.toInt()}x${g.textH.toInt()}")

        val ink = inkColor
        val accent = accentColor
        val lineNumbersOnPage = pg.lines.map { it.number }.toSet()

        // أين تبدأ كل سورة في الصفحة (سطر أول آية لها)
        val surahAtLine = LinkedHashMap<Int, Int>()
        val surahFirstVerseLines = mutableListOf<Int>()
        for (sr in pg.surahs) {
            val firstWord = pg.lines.asSequence().flatMap { it.words.asSequence() }
                .firstOrNull { it.surah == sr && it.ayah == 1 }
            if (firstWord != null) {
                surahAtLine[firstWord.line] = sr
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

        // بناء الصفوف بالترتيب (مطابق للنسخة السابقة — لا إعادة توزيع)
        val rows = mutableListOf<Row>()
        val wordPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = s
            color = ink
            textLocale = Locale("ar")
        }
        val glyphPaint = if (gtf != null) TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = gtf
            textSize = s
            color = ink
            textLocale = Locale("ar")
        } else null
        val wordFm = wordPaint.fontMetrics
        val textH = (wordFm.descent - wordFm.ascent)
        for (line in pg.lines) {
            val surah = surahAtLine[line.number]
            if (surah != null) {
                rows.add(buildSurahHeader(names(surah), s, ink))
                val firstWordLine = surahAtLine.entries.first { it.value == surah }.key
                val bismillahLine = firstWordLine - 2
                val hasBismillahLine = bismillahLine >= 1 && !lineNumbersOnPage.contains(bismillahLine)
                if (surah != 1 && surah != 9 && hasBismillahLine) {
                    rows.add(buildBismillahLine(s, ink, tf))
                }
            }
            rows.add(buildVerseRow(line, partialLines.contains(line.number), isSpecial, wordPaint, glyphPaint, textH))
        }

        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("serif", Typeface.NORMAL)
            textSize = 12f * resources.displayMetrics.scaledDensity
            color = ink
            textAlign = Paint.Align.CENTER
        }

        layout = PageLayout(
            rows = rows,
            s = s,
            textH = textH,
            wordFm = wordFm,
            footerPaint = footerPaint,
            footerText = hindiDigits(pageNumber),
            ink = ink,
            accent = accent,
            badgeDark = 0xFF5E4B40.toInt(),
            paperColor = context.getColor(R.color.urwah_surface),
            geometry = g,
            viewW = width.toFloat(),
            vertical = verticalLayout(rows, footerPaint, isSpecial, g),
        )
        invalidate()
    }

    /** التوزيع الرأسي: شبكة pitch للعادية، كتلة متوسطة للخاصة. */
    private fun verticalLayout(
        rows: List<Row>,
        footerPaint: TextPaint,
        isSpecial: Boolean,
        g: PageGeometry,
    ): List<Pair<Float, Float>> {
        val out = mutableListOf<Pair<Float, Float>>()
        if (isSpecial) {
            val blockH = rows.sumOf { it.naturalHeight.toDouble() }.toFloat() +
                (rows.size - 1).coerceAtLeast(0) * dp(1)
            var top = g.textT + (g.textH - blockH) / 2f
            for (r in rows) {
                out.add(top to r.naturalHeight)
                top += r.naturalHeight + dp(1)
            }
        } else {
            val pitch = g.textH / rows.size.coerceAtLeast(1)
            for (i in rows.indices) {
                out.add(g.textT + i * pitch to pitch)
            }
        }
        return out
    }

    // ===== كائنات الرسم =====

    private sealed class Row {
        abstract val naturalHeight: Float

        class Header(val title: StaticLayout, val titleH: Float, val ornamentH: Float) :
            Row() { override val naturalHeight get() = titleH + 4f + ornamentH }

        class Bismillah(val layout: StaticLayout, val height: Float) :
            Row() { override val naturalHeight get() = height }

        class Verse(val words: List<WordDraw>, val stretch: Boolean, val height: Float) :
            Row() { override val naturalHeight get() = height }
    }

    private class WordDraw(
        val text: String,
        val layout: StaticLayout,
        val width: Float,   // العرض المُقاس + هوامشه
        val measured: Float, // العرض المُقاس فقط (للدائرة)
        val isEnd: Boolean,
    )

    private inner class PageLayout(
        val rows: List<Row>,
        val s: Float,
        val textH: Float,
        val wordFm: android.graphics.Paint.FontMetrics,
        val footerPaint: TextPaint,
        val footerText: String,
        val ink: Int,
        val accent: Int,
        val badgeDark: Int,
        val paperColor: Int,
        val geometry: PageGeometry,
        val viewW: Float,
        val vertical: List<Pair<Float, Float>>,
    ) {
        private val fm = footerPaint.fontMetrics
        private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ornamentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dpv(1)
        }

        fun draw(canvas: Canvas) {
            val g = geometry
            // الورقة: الصفحة بلون أفتح من الخلفية + إطار رفيع
            paperPaint.color = paperColor
            canvas.drawRect(g.pageL, g.pageT, g.pageL + g.pageW, g.pageBottom, paperPaint)
            framePaint.color = (accent and 0x00FFFFFF) or (MushafPageMetrics.PAGE_FRAME_ALPHA shl 24)
            canvas.drawRect(g.pageL, g.pageT, g.pageL + g.pageW, g.pageBottom, framePaint)

            // سطور الصفحة
            for (i in rows.indices) {
                val (top, h) = vertical[i]
                drawRow(canvas, rows[i], top, h, g)
            }

            // رقم الصفحة — يتوسط الهامش السفلي
            val bandMid = (g.textB + g.pageBottom) / 2f
            val baseline = bandMid - (fm.ascent + fm.descent) / 2f
            canvas.drawText(footerText, g.pageCenterX, baseline, footerPaint)
        }

        private fun drawRow(canvas: Canvas, row: Row, top: Float, h: Float, g: PageGeometry) {
            when (row) {
                is Row.Header -> {
                    val x = g.textL + (g.textW - row.title.width) / 2f
                    canvas.save()
                    canvas.translate(x, top)
                    row.title.draw(canvas)
                    canvas.restore()
                    drawOrnament(canvas, top + row.titleH + 4f, g)
                }
                is Row.Bismillah -> {
                    val x = g.textL + (g.textW - row.layout.width) / 2f
                    val y = top + (h - row.height) / 2f
                    canvas.save()
                    canvas.translate(x, y)
                    row.layout.draw(canvas)
                    canvas.restore()
                }
                is Row.Verse -> {
                    if (row.words.isEmpty()) return
                    val fmv = wordFm
                    val baseGap = s * MushafPageMetrics.WORD_GAP_EM
                    val baseline = top + h / 2f - (fmv.ascent + fmv.descent) / 2f
                    val cx = (g.textL + g.textR) / 2f
                    val widthWithMargins = row.words.sumOf { it.width.toDouble() }.toFloat()
                    val gapsBase = (row.words.size - 1) * baseGap
                    // المتبقي يُوزع على الفجوات فقط لسطور الآيات (لا تمديد اصطناعي)
                    val extra = if (row.stretch && row.words.size > 1)
                        (g.textW - widthWithMargins - gapsBase).coerceAtLeast(0f) / (row.words.size - 1)
                    else 0f
                    var cursor = if (row.stretch) g.textR
                    else {
                        val clusterW = widthWithMargins + gapsBase
                        cx + clusterW / 2f
                    }
                    for (w in row.words) {
                        val left = cursor - w.width
                        if (w.isEnd) {
                            drawEndBadge(canvas, w, left, left + w.width, baseline)
                        } else {
                            canvas.save()
                            canvas.translate(left + (w.width - w.measured), baseline + fmv.ascent)
                            w.layout.draw(canvas)
                            canvas.restore()
                        }
                        cursor -= w.width + baseGap + extra
                    }
                }
            }
        }

        private fun drawEndBadge(canvas: Canvas, w: WordDraw, boxLeft: Float, boxRight: Float, baseline: Float) {
            // الرقم جزء نظيف من النص بلا خلفية بنية/دائرة (حسب المهمة الأخيرة):
            // يُحتفظ بإطار الرقم كما هو بالضبط (المركز، العرض، الهامش) حتى لا
            // يتغير موضع نهاية الآية أو توزيع الكلمات — نرسم الرقم فقط بلون الحبر.
            val padX = dpv(5)
            val padY = dpv(2)
            val badgeH = w.layout.height + 2 * padY
            val badgeW = w.measured + 2 * padX
            if (badgeW <= 0 || badgeH <= 0) return
            val cxRaw = (boxLeft + boxRight) / 2f
            // احتواء خفيف يحفظ اكتماله داخل الشاشة عند الملء العرضي
            val cx = cxRaw.coerceIn(badgeW / 2f, viewW - badgeW / 2f)
            val top = baseline + wordFm.ascent + (textH - badgeH) / 2f
            badgePaint.color = ink
            badgePaint.style = Paint.Style.FILL
            badgePaint.typeface = wordTypeface
            badgePaint.textSize = s
            badgePaint.textLocale = Locale("ar")
            val textY = top + badgeH / 2f - (badgePaint.ascent() + badgePaint.descent()) / 2f
            canvas.drawText(w.text, cx, textY, badgePaint)
        }

        private fun drawOrnament(canvas: Canvas, oy: Float, g: PageGeometry) {
            val cx = g.pageCenterX
            val half = 37f * densityV
            val dotR = 3f * densityV
            val lineLen = 28f * densityV
            val thick = 1f * densityV
            ornamentPaint.color = accent
            ornamentPaint.style = Paint.Style.FILL
            val topLine = oy + dotR - thick / 2f
            canvas.drawRect(cx - half, topLine, cx - half + lineLen, topLine + thick, ornamentPaint)
            canvas.drawRect(cx + half - lineLen, topLine, cx + half, topLine + thick, ornamentPaint)
            ornamentPaint.color = badgeDark
            canvas.drawCircle(cx, oy + dotR, dotR, ornamentPaint)
        }
    }

    private fun buildSurahHeader(name: String, s: Float, ink: Int): Row.Header {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("serif", Typeface.BOLD)
            textSize = s * 0.9f
            color = ink
            textLocale = Locale("ar")
        }
        val width = (paint.measureText(name) + 2).toInt().coerceAtLeast(1)
        val title = StaticLayout(name, paint, width, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
        val fm = paint.fontMetrics
        return Row.Header(title, (fm.descent - fm.ascent), dpv(6))
    }

    private fun buildBismillahLine(s: Float, ink: Int, tf: Typeface): Row.Bismillah {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = s
            color = ink
            textLocale = Locale("ar")
        }
        val text = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"
        val width = (paint.measureText(text) + 2).toInt().coerceAtLeast(1)
        val layout = StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
        val fm = paint.fontMetrics
        return Row.Bismillah(layout, (fm.descent - fm.ascent))
    }

    private fun buildVerseRow(
        line: QuranPageLayouts.Line,
        isPartial: Boolean,
        isSpecial: Boolean,
        wordPaint: TextPaint,
        glyphPaint: TextPaint?,
        textH: Float,
    ): Row.Verse {
        val stretch = !isSpecial && !isPartial
        val words = line.words.mapNotNull { w ->
            val isEnd = w.type == "end"
            // وضعُنا الحالي: نعرض النص القرآني العاملي (w.text) بخط الخط
            // العثماني، ولا نخدام البيانات القديمة QCF (w.g) — فالخط الضروريّ
            // لـ w.g غير متوافق مع رموز Unicode الخاصة بـ QCF في cyan لدينا.
            val drawText = w.text
            val paint = wordPaint
            val measured = paint.measureText(drawText)
            // عرض النص الحقيقي فقط — وليس بطول +. هذا يمنع كلى تمدد
            // الكلمات وتلف تشكيل الأحرف.
            val layout = StaticLayout(drawText, paint, measured.toInt().coerceAtLeast(1), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
            WordDraw(drawText, layout, measured, measured, isEnd)
        }
        return Row.Verse(words, stretch, textH)
    }

    private val densityV: Float get() = resources.displayMetrics.density
    private fun dpv(v: Int): Float = v * densityV

    private fun dp(v: Int): Int = (v * densityV).toInt()

    private fun hindiDigits(number: Int): String {
        val arabicIndic = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
        return number.toString().map { arabicIndic[it - '0'] }.joinToString("")
    }
}