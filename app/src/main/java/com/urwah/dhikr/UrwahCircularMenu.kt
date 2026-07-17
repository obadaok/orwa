package com.urwah.dhikr

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.content.res.AppCompatResources
import kotlin.math.*

class UrwahCircularMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val items = mutableListOf<CircularMenuItem>()

    private var centerX = 0f
    private var centerY = 0f
    private var outerRadius = 0f
    private var innerRadius = 0f

    private val itemAnimations = mutableListOf<Float>()
    private var isShowing = false
    private var isHiding = false
    private var selectedIndex = -1

    private var isDarkTheme = false

    private val overlayColor = 0x88000000.toInt()
    private var primaryColor = 0
    private var surfaceColor = 0
    private var accentColor = 0

    private var currentHideAnimator: ValueAnimator? = null
    private val showAnimators = mutableListOf<ValueAnimator>()

    var onMenuDismissed: (() -> Unit)? = null

    data class CircularMenuItem(
        val iconResId: Int,
        val label: String,
        val onClick: () -> Unit
    )

    init {
        setBackgroundColor(0x00000000)
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isClickable = true
        isFocusable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        val maxRadius = min(w, h) * 0.45f
        outerRadius = maxRadius
        innerRadius = outerRadius * 0.32f
    }

    fun addMenuItem(iconResId: Int, label: String, onClick: () -> Unit) {
        items.add(CircularMenuItem(iconResId, label, onClick))
        itemAnimations.add(0f)
        invalidate()
    }

    private fun updateThemeColors() {
        val ctx = context
        primaryColor = ctx.getColor(R.color.urwah_primary)
        surfaceColor = ctx.getColor(R.color.urwah_card_bg)
        accentColor = ctx.getColor(R.color.urwah_card_shadow)
        isDarkTheme = ctx.getColor(R.color.urwah_background) == 0xFF121212.toInt() ||
                ctx.getColor(R.color.urwah_background) == 0xFF14120E.toInt()
    }

    fun show() {
        if (isShowing || isHiding) return
        updateThemeColors()
        cancelAllAnimations()
        isShowing = true
        isHiding = false
        visibility = VISIBLE
        bringToFront()

        items.indices.forEach { index ->
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 400
                startDelay = index * 100L
                interpolator = OvershootInterpolator(1.4f)
                addUpdateListener { animation ->
                    itemAnimations[index] = animation.animatedValue as Float
                    invalidate()
                }
            }
            showAnimators.add(animator)
            animator.start()
        }
    }

    fun hide(onComplete: (() -> Unit)? = null) {
        if (isHiding || (!isShowing && itemAnimations.all { it == 0f })) {
            onComplete?.invoke()
            onMenuDismissed?.invoke()
            return
        }
        cancelAllAnimations()
        isHiding = true
        isShowing = false

        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                for (i in itemAnimations.indices) {
                    itemAnimations[i] = progress
                }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isHiding = false
                    visibility = GONE
                    post {
                        onComplete?.invoke()
                        onMenuDismissed?.invoke()
                    }
                }
            })
        }
        currentHideAnimator = animator
        animator.start()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isShowing && !isHiding) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val touchX = event.x
                val touchY = event.y
                val dx = touchX - centerX
                val dy = touchY - centerY
                val distance = sqrt(dx * dx + dy * dy)

                if (distance <= innerRadius) {
                    selectedIndex = -2
                    invalidate()
                    return true
                }

                if (distance <= outerRadius && distance > innerRadius) {
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    if (angle < 0) angle += 360f
                    angle = (angle + 90f) % 360f

                    val anglePerItem = 360f / items.size
                    val index = (angle / anglePerItem).toInt()

                    if (index != selectedIndex && itemAnimations.getOrNull(index) == 1f) {
                        selectedIndex = index
                        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        invalidate()
                    }
                    return true
                } else {
                    selectedIndex = -1
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val touchX = event.x
                val touchY = event.y
                val dx = touchX - centerX
                val dy = touchY - centerY
                val distance = sqrt(dx * dx + dy * dy)

                if (distance <= innerRadius) {
                    selectedIndex = -2
                    invalidate()
                    return true
                }

                if (distance <= outerRadius && distance > innerRadius) {
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    if (angle < 0) angle += 360f
                    angle = (angle + 90f) % 360f

                    val anglePerItem = 360f / items.size
                    val index = (angle / anglePerItem).toInt()

                    if (index != selectedIndex && itemAnimations.getOrNull(index) == 1f) {
                        selectedIndex = index
                        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        invalidate()
                    }
                    return true
                } else {
                    selectedIndex = -1
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (selectedIndex == -2) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    hide()
                } else if (selectedIndex >= 0) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    val clickedIndex = selectedIndex
                    selectedIndex = -1
                    hide {
                        items.getOrNull(clickedIndex)?.onClick?.invoke()
                    }
                } else {
                    hide()
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                selectedIndex = -1
                hide()
                return true
            }
        }
        return true
    }

    private fun cancelAllAnimations() {
        showAnimators.forEach { it.cancel() }
        showAnimators.clear()
        currentHideAnimator?.cancel()
        currentHideAnimator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isShowing && !isHiding && itemAnimations.all { it == 0f }) return

        canvas.drawColor(overlayColor)

        val itemCount = items.size
        if (itemCount == 0) return

        val anglePerItem = 360f / itemCount

        items.forEachIndexed { index, item ->
            val progress = itemAnimations[index]
            if (progress > 0) {
                val startAngle = -90f + (anglePerItem * index)
                val sweepAngle = anglePerItem * progress

                val path = Path()
                path.moveTo(centerX, centerY)

                val rect = RectF(
                    centerX - outerRadius,
                    centerY - outerRadius,
                    centerX + outerRadius,
                    centerY + outerRadius
                )

                path.arcTo(rect, startAngle, sweepAngle)
                path.lineTo(centerX, centerY)
                path.close()

                paint.color = if (index == selectedIndex) primaryColor else surfaceColor
                paint.style = Paint.Style.FILL
                canvas.drawPath(path, paint)

                paint.color = primaryColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                canvas.drawPath(path, paint)

                if (progress >= 0.5f) {
                    val iconProgress = (progress - 0.5f) * 2f
                    val angleRad = Math.toRadians((startAngle + sweepAngle / 2).toDouble())
                    val iconDistance = (innerRadius + outerRadius) / 2

                    val iconX = (centerX + cos(angleRad) * iconDistance).toFloat()
                    val iconY = (centerY + sin(angleRad) * iconDistance).toFloat()
                    val iconSize = (outerRadius * 0.22f * iconProgress).toInt()

                    val drawable = AppCompatResources.getDrawable(context, item.iconResId)
                    drawable?.let { d ->
                        d.setBounds(
                            (iconX - iconSize / 2).toInt(),
                            (iconY - iconSize / 2).toInt(),
                            (iconX + iconSize / 2).toInt(),
                            (iconY + iconSize / 2).toInt()
                        )
                        d.alpha = (255 * iconProgress).toInt()
                        if (index == selectedIndex) {
                            d.setTint(surfaceColor)
                        } else {
                            d.setTint(primaryColor)
                        }
                        d.draw(canvas)
                    }
                }
            }
        }

        val maxProgress = itemAnimations.maxOrNull() ?: 0f
        if (maxProgress > 0) {
            paint.color = accentColor
            paint.style = Paint.Style.FILL
            paint.isAntiAlias = true
            canvas.drawCircle(centerX, centerY + 4, innerRadius * maxProgress, paint)

            paint.color = primaryColor
            canvas.drawCircle(centerX, centerY, innerRadius * maxProgress, paint)

            paint.color = surfaceColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawCircle(centerX, centerY, innerRadius * maxProgress * 0.95f, paint)

            if (maxProgress >= 0.7f) {
                val iconProgress = (maxProgress - 0.7f) * 3.33f
                val iconSize = (innerRadius * 0.45f * iconProgress).toInt()
                val closeDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_cancel)
                closeDrawable?.let { d ->
                    d.setTint(surfaceColor)
                    d.setBounds(
                        (centerX - iconSize / 2).toInt(),
                        (centerY - iconSize / 2).toInt(),
                        (centerX + iconSize / 2).toInt(),
                        (centerY + iconSize / 2).toInt()
                    )
                    d.alpha = (255 * iconProgress).toInt()
                    d.draw(canvas)
                }
            }
        }
    }
}
