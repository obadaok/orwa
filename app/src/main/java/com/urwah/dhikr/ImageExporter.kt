package com.urwah.dhikr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Build
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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

object ImageExporter {

    data class ExportConfig(
        val fullText: String,
        val bookTitle: String,
        val author: String,
        val edition: String = "",
        val typeface: Typeface,
        val fontSizeSp: Float,
        val lineSpacing: Float,
        val textAlign: Layout.Alignment,
        val background: QuoteBackground,
        val isJustify: Boolean = false,
        val spanStyles: List<Pair<IntRange, QuoteSpanStyle>> = emptyList()
    )

    private const val EXPORT_WIDTH_DP = 720f

    fun exportPreview(config: ExportConfig, context: Context): Bitmap? {
        val metrics = context.resources.displayMetrics
        val targetWidthPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, EXPORT_WIDTH_DP, metrics
        ).toInt()

        val root = buildPreviewLayout(context, config, targetWidthPx)

        val widthSpec = View.MeasureSpec.makeMeasureSpec(targetWidthPx, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        root.measure(widthSpec, heightSpec)

        val mw = root.measuredWidth
        val mh = root.measuredHeight
        if (mw <= 0 || mh <= 0) return null

        root.layout(0, 0, mw, mh)

        val bitmap = Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        root.draw(canvas)

        return bitmap
    }

    fun exportHighRes(config: ExportConfig, context: Context, scale: Float = 3f): Bitmap? {
        val base = exportPreview(config, context) ?: return null
        if (scale <= 1f) return base
        val w = (base.width * scale).toInt()
        val h = (base.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(base, w, h, true)
        base.recycle()
        return scaled
    }

    private fun buildPreviewLayout(
        context: Context,
        config: ExportConfig,
        targetWidthPx: Int
    ): LinearLayout {
        val metrics = context.resources.displayMetrics
        val dp = { v: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, metrics).toInt() }

        val sideMarginPx = dp(32f)
        val contentWidthPx = targetWidthPx - sideMarginPx * 2
        val dividerMarginHPx = dp(40f)
        val dividerWidthPx = targetWidthPx - dividerMarginHPx * 2

        val bg = config.background

        val root = LinearLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(targetWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(sideMarginPx, dp(40f), sideMarginPx, dp(40f))
            setBackgroundColor(bg.bgColor)
        }

        val tvContent = createContentTextView(context, config, contentWidthPx)
        root.addView(tvContent)

        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dividerWidthPx,
                dp(1.5f)
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(60f)
            }
            setBackgroundColor(bg.dividerColor)
        }
        root.addView(divider)

        val metaText = buildString {
            val parts = mutableListOf<String>()
            if (config.bookTitle.isNotBlank()) parts += config.bookTitle
            if (config.author.isNotBlank()) parts += config.author
            if (config.edition.isNotBlank()) parts += config.edition
            parts += "عبر تطبيق عروة"
            append(parts.joinToString(" · "))
        }
        val footer = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12f)
            }
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
        }
        footer.addView(ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(22f), dp(22f))
            setImageResource(R.drawable.ic_splash_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0.85f
        })
        val tvMeta = createMetaTextView(context, metaText, contentWidthPx, bg.metaColor, config.typeface, dp, config.fontSizeSp)
        tvMeta.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(6f)
        }
        footer.addView(tvMeta)
        root.addView(footer)

        return root
    }

    private fun createContentTextView(
        context: Context,
        config: ExportConfig,
        contentWidthPx: Int
    ): TextView {
        val ssb = SpannableStringBuilder(config.fullText)
        applySpans(ssb, config.spanStyles, config.background)

        return TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(contentWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            typeface = config.typeface
            textSize = config.fontSizeSp
            setLineSpacing(0f, config.lineSpacing)
            textDirection = TextView.TEXT_DIRECTION_ANY_RTL
            setTextColor(config.background.textColor)
            text = ssb

            when {
                config.isJustify -> textAlignment = TextView.TEXT_ALIGNMENT_TEXT_START
                config.textAlign == Layout.Alignment.ALIGN_CENTER -> textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                config.textAlign == Layout.Alignment.ALIGN_OPPOSITE -> textAlignment = TextView.TEXT_ALIGNMENT_TEXT_END
                else -> textAlignment = TextView.TEXT_ALIGNMENT_TEXT_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                justificationMode = if (config.isJustify)
                    Layout.JUSTIFICATION_MODE_INTER_WORD
                else
                    Layout.JUSTIFICATION_MODE_NONE
            }
        }
    }

    private fun createMetaTextView(
        context: Context,
        text: String,
        contentWidthPx: Int,
        color: Int,
        typeface: Typeface,
        dp: (Float) -> Int,
        fontSizeSp: Float = 14f
    ): TextView {
        val ratio = 0.78f
        return TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(contentWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            this.text = text
            textDirection = TextView.TEXT_DIRECTION_RTL
            textAlignment = TextView.TEXT_ALIGNMENT_TEXT_START
            setTextColor(color)
            textSize = fontSizeSp * ratio
            this.typeface = Typeface.create(typeface, Typeface.NORMAL)
        }
    }

    private fun applySpans(
        ssb: SpannableStringBuilder,
        spanStyles: List<Pair<IntRange, QuoteSpanStyle>>,
        bg: QuoteBackground
    ) {
        for ((range, style) in spanStyles) {
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
                ssb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
}
