package com.urwah.dhikr

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import kotlin.math.ceil

/**
 * يقسّم نص الكتاب الكامل إلى صفحات ثابتة الارتفاع عبر قياسه بـ StaticLayout.
 * ملاحظة مهمة: هذه الدالة تتلقى pageWidthPx/pageHeightPx كمساحة النص الصافية
 * فقط (بعد طرح كل الهوامش/الحشوات/العناصر الثابتة الأخرى من طرف المستدعي) —
 * انظر PageChromeMetrics في ShamelaBookReaderActivity.kt لتفاصيل هذا الحساب.
 */
object BookTextPaginator {

    data class Page(
        val index: Int,
        val text: String,
        val chapterTitle: String? = null,
        val originalPageNum: Int? = null,
        val startOffset: Int = 0
    )

    // نفس علامة عنوان الفصل/القسم التي يضعها ShamelaBookReaderActivity.stripHtml()
    // عند تحويل span[data-type="title"] إلى نص عادي داخل المتن الكامل.
    private const val CHAPTER_MARKER = "\u25C6 "

    @Volatile
    private var cachedTypeface: Typeface? = null
    private var cachedFontKey: String = ""

    fun getCachedTypeface(): Typeface? = cachedTypeface

    fun invalidateTypefaceCache() {
        cachedTypeface = null
        cachedFontKey = ""
    }

    private fun getBodyTypeface(context: Context, fontFile: String? = null): Typeface {
        val key = fontFile ?: "__default__"
        if (cachedTypeface != null && cachedFontKey == key) return cachedTypeface!!
        synchronized(this) {
            if (cachedTypeface != null && cachedFontKey == key) return cachedTypeface!!
            val tf = if (fontFile != null) {
                try { Typeface.createFromAsset(context.assets, "fonts/$fontFile") } catch (_: Exception) { Typeface.DEFAULT }
            } else {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.applicationContext.resources.getFont(R.font.noto_naskh_arabic)
                    } else Typeface.DEFAULT
                } catch (_: Exception) { Typeface.DEFAULT }
            }
            cachedTypeface = tf
            cachedFontKey = key
            return tf
        }
    }

    /**
     * Paginate the full book text into pages that fit the given dimensions.
     *
     * @param context Android context
     * @param fullText The complete book text (all ShamelaPage bodies joined)
     * @param pageWidthPx Width of the *text content* area in pixels (صافي، بدون أي هوامش)
     * @param pageHeightPx Height of the *text content* area in pixels (صافي، بدون أي هوامش)
     * @param fontSizeSp Font size in sp
     * @param lineSpacingMultiplier Line spacing multiplier
     * @return List of pages with text chunks
     */
    fun paginate(
        context: Context,
        fullText: String,
        pageWidthPx: Int,
        pageHeightPx: Int,
        fontSizeSp: Float = 18f,
        lineSpacingMultiplier: Float = 1.7f,
        fontFile: String? = null
    ): List<Page> {
        if (fullText.isBlank()) return listOf(Page(0, ""))

        val fontSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, fontSizeSp, context.resources.displayMetrics
        )

        val paint = TextPaint().apply {
            isAntiAlias = true
            typeface = getBodyTypeface(context, fontFile)
            textSize = fontSizePx
        }

        // Create a StaticLayout to measure text
        val layout = StaticLayout.Builder.obtain(
            fullText, 0, fullText.length, paint, pageWidthPx
        )
            .setLineSpacing(0f, lineSpacingMultiplier)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
            .build()

        val totalLines = layout.lineCount
        if (totalLines == 0) return listOf(Page(0, fullText))

        // Calculate how many lines fit in one page
        val linesPerPage = calculateLinesPerPage(layout, pageHeightPx)
        if (linesPerPage <= 0) return listOf(Page(0, fullText))

        val totalPages = ceil(totalLines.toDouble() / linesPerPage).toInt()
        val pages = mutableListOf<Page>()

        for (pageIndex in 0 until totalPages) {
            val startLine = pageIndex * linesPerPage
            val endLine = minOf(startLine + linesPerPage, totalLines)

            val lineStart = layout.getLineStart(startLine)
            val endOffset = if (endLine < totalLines) {
                layout.getLineStart(endLine)
            } else {
                fullText.length
            }

            val rawSegment = fullText.substring(lineStart, endOffset)
            val leadingWs = rawSegment.length - rawSegment.trimStart().length
            val trimmedStart = lineStart + leadingWs

            var pageText = rawSegment.trim()
            var chapterTitle: String? = null

            // إن كانت الصفحة تبدأ فعليًا عند علامة عنوان فصل/قسم، نستخرجها لعرضها
            // كعنوان منسّق (tvPageChapterTitle) بدل تركها نصًا عاديًا داخل المتن.
            if (pageText.startsWith(CHAPTER_MARKER)) {
                val newlineIdx = pageText.indexOf('\n')
                val titleLine = if (newlineIdx >= 0) pageText.substring(0, newlineIdx) else pageText
                chapterTitle = titleLine.removePrefix(CHAPTER_MARKER).trim().ifBlank { null }
                pageText = if (newlineIdx >= 0) pageText.substring(newlineIdx + 1).trim() else ""
            }

            val contentStart = if (chapterTitle != null) {
                fullText.indexOf(pageText, trimmedStart).takeIf { it >= 0 } ?: trimmedStart
            } else {
                trimmedStart
            }
            pages.add(Page(pageIndex, pageText, chapterTitle, startOffset = contentStart))
        }

        return pages
    }

    /**
     * Calculate how many text lines fit within the given pixel height.
     */
    private fun calculateLinesPerPage(layout: Layout, heightPx: Int): Int {
        var lines = 0
        var currentHeight = 0

        for (i in 0 until layout.lineCount) {
            val lineHeight = layout.getLineBottom(i) - layout.getLineTop(i)
            if (currentHeight + lineHeight > heightPx) break
            currentHeight += lineHeight
            lines++
        }

        return maxOf(lines, 1)
    }
}
