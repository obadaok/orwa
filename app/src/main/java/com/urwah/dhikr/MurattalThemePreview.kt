package com.urwah.dhikr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/**
 * معاينة مصغّرة لواجهة المرتّل تُرسم من [MurattalPalette] مباشرةً —
 * يستخدمها نشاط اختيار المظهر لإظهار كل مظهر بشكل واقعي.
 */
class MurattalThemePreview @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var palette: MurattalPalette? = null
        set(value) {
            field = value
            invalidate()
        }

    private val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
    }
    private val miniBmp = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = palette ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val dp = resources.displayMetrics.density

        rectPaint.style = Paint.Style.FILL

        // الخلفية
        rectPaint.color = p.background
        canvas.drawRect(0f, 0f, w, h, rectPaint)

        // زخرفة علوية (في المظاهر المزخرفة)
        if (p.showOrnament) {
            rectPaint.color = p.accent
            val ox = w * 0.30f
            val oy = dp * 3f
            canvas.drawRect(ox, 0f, w - ox, oy, rectPaint)
        }

        // الشريط العلوي
        val barTop = dp * 6f
        val barH = dp * 13f
        rectPaint.color = p.surface
        canvas.drawRect(dp * 3f, barTop, w - dp * 3f, barTop + barH, rectPaint)
        // أيقونات الشريط
        val iconSize = dp * 3.6f
        rectPaint.color = p.iconTint
        canvas.drawCircle(dp * 8f, barTop + barH / 2f, iconSize, rectPaint)
        canvas.drawCircle(w - dp * 8f, barTop + barH / 2f, iconSize, rectPaint)
        canvas.drawCircle(w - dp * 16f, barTop + barH / 2f, iconSize, rectPaint)

        // بطاقة معلومات
        val cardTop = barTop + barH + dp * 3f
        val cardH = dp * 18f
        rectPaint.color = p.surface
        canvas.drawRoundRect(
            RectF(dp * 3f, cardTop, w - dp * 3f, cardTop + cardH),
            dp * 3f, dp * 3f, rectPaint
        )
        // حد البطاقة
        rectPaint.style = Paint.Style.STROKE
        rectPaint.strokeWidth = dp * 0.8f
        rectPaint.color = p.surfaceBorder
        canvas.drawRoundRect(
            RectF(dp * 3f, cardTop, w - dp * 3f, cardTop + cardH),
            dp * 3f, dp * 3f, rectPaint
        )
        rectPaint.style = Paint.Style.FILL
        // شارة العدّاد
        rectPaint.color = p.accent
        canvas.drawRoundRect(
            RectF(dp * 5f, cardTop + dp * 2f, dp * 20f, cardTop + dp * 5.6f),
            dp * 2f, dp * 2f, rectPaint
        )
        rectPaint.color = p.divider
        canvas.drawRoundRect(
            RectF(dp * 22f, cardTop + dp * 2.4f, w - dp * 5f, cardTop + dp * 3.6f),
            dp * 1.6f, dp * 1.6f, rectPaint
        )

        // إطار الآية
        val qTop = cardTop + cardH + dp * 4f
        val qH = dp * 22f
        rectPaint.color = p.quranFrame
        canvas.drawRoundRect(
            RectF(dp * 3f, qTop, w - dp * 3f, qTop + qH),
            dp * 3f, dp * 3f, rectPaint
        )
        rectPaint.style = Paint.Style.STROKE
        rectPaint.color = p.quranFrameBorder
        rectPaint.strokeWidth = dp * 0.8f
        canvas.drawRoundRect(
            RectF(dp * 3f, qTop, w - dp * 3f, qTop + qH),
            dp * 3f, dp * 3f, rectPaint
        )
        rectPaint.style = Paint.Style.FILL
        // سطر الآية
        textPaint.color = p.textPrimary
        textPaint.textSize = dp * 4.2f
        canvas.drawText("﷽  ﴿...﴾", dp * 6f, qTop + qH * 0.52f, textPaint)

        // شريط التقدم
        val prTop = qTop + qH + dp * 3f
        val prH = dp * 2.4f
        rectPaint.color = p.progressTrack
        canvas.drawRoundRect(RectF(dp * 3f, prTop, w - dp * 3f, prTop + prH), prH, prH, rectPaint)
        rectPaint.color = p.progressFill
        canvas.drawRoundRect(
            RectF(dp * 3f, prTop, dp * 3f + (w - dp * 6f) * 0.42f, prTop + prH), prH, prH, rectPaint
        )

        // أزرار المشغّل السفلية
        val btnTop = prTop + dp * 3f
        val smallBtn = dp * 4f
        rectPaint.color = p.surfaceBorder
        canvas.drawCircle(dp * 9f, btnTop + smallBtn, smallBtn, rectPaint)
        canvas.drawCircle(dp * 17f, btnTop + smallBtn, smallBtn, rectPaint)
        canvas.drawCircle(w - dp * 9f, btnTop + smallBtn, smallBtn, rectPaint)
        canvas.drawCircle(w - dp * 17f, btnTop + smallBtn, smallBtn, rectPaint)
        // زر التشغيل البارز
        rectPaint.color = p.accent
        canvas.drawCircle(w / 2f, btnTop + smallBtn, dp * 5.6f, rectPaint)
        textPaint.color = p.accentText
        textPaint.textSize = dp * 4.4f
        canvas.drawText("▶", w / 2f, btnTop + smallBtn + dp * 1.5f, textPaint)

        // أثر الظل السفلي
        rectPaint.color = Color.argb(24, 0, 0, 0)
        canvas.drawRect(0f, h - dp * 1.2f, w, h, rectPaint)
    }
}