package com.urwah.dhikr

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat

class CircularCounterView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var current = 0
    private var target = 1
    private var animatedSweep = 0f
    private var isComplete = false
    private var animating = false

    private val ringBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = ContextCompat.getColor(context, R.color.counter_ring_bg)
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }
    private val ringProgress = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = ContextCompat.getColor(context, R.color.counter_ring_progress)
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.counter_text)
        textAlign = Paint.Align.CENTER
        textSize = 34f
        isFakeBoldText = true
        isAntiAlias = true
    }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.counter_check)
        textAlign = Paint.Align.CENTER
        textSize = 40f
        isFakeBoldText = true
        isAntiAlias = true
    }

    private val rect = RectF()

    fun setProgress(current: Int, target: Int) {
        this.current = current
        this.target = target.coerceAtLeast(1)
        isComplete = current >= this.target

        val newSweep = 360f * (current.toFloat() / this.target).coerceIn(0f, 1f)
        ValueAnimator.ofFloat(animatedSweep, newSweep).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animatedSweep = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        ringProgress.color = if (isComplete) ContextCompat.getColor(context, R.color.counter_check) else ContextCompat.getColor(context, R.color.counter_ring_progress)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val stroke = ringBg.strokeWidth
        val halfStroke = stroke / 2f

        rect.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke)

        canvas.drawArc(rect, 0f, 360f, false, ringBg)
        canvas.drawArc(rect, -90f, animatedSweep, false, ringProgress)

        if (isComplete && animatedSweep >= 359f) {
            val yPos = h / 2f - (checkPaint.descent() + checkPaint.ascent()) / 2f
            canvas.drawText("✓", w / 2f, yPos, checkPaint)
        } else {
            val label = "$current"
            val yPos = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(label, w / 2f, yPos, textPaint)
        }
    }
}
