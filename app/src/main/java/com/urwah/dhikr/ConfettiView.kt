package com.urwah.dhikr

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.util.AttributeSet
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ConfettiView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var startTime = 0L
    private var isRunning = false
    private lateinit var colors: List<Int>

    init {
        resolveColors()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        resolveColors()
    }

    private fun resolveColors() {
        colors = listOf(
            ContextCompat.getColor(context, R.color.confetti_1),
            ContextCompat.getColor(context, R.color.confetti_2),
            ContextCompat.getColor(context, R.color.confetti_3),
            ContextCompat.getColor(context, R.color.confetti_4),
            ContextCompat.getColor(context, R.color.confetti_5),
            ContextCompat.getColor(context, R.color.confetti_6)
        )
    }

    data class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var radius: Float,
        var color: Int,
        var alpha: Float = 1f,
        var life: Float = 1f,
        var rotation: Float = 0f,
        var rotSpeed: Float = 0f
    )

    fun burst(cx: Float, cy: Float) {
        particles.clear()
        val count = 30
        for (i in 0 until count) {
            val angle = Random.nextFloat() * Math.PI.toFloat() * 2
            val speed = Random.nextFloat() * 600f + 300f
            particles.add(
                Particle(
                    x = cx + Random.nextFloat() * 40f - 20f,
                    y = cy + Random.nextFloat() * 40f - 20f,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed - 200f,
                    radius = Random.nextFloat() * 12f + 6f,
                    color = colors[Random.nextInt(colors.size)],
                    rotSpeed = Random.nextFloat() * 6f - 3f
                )
            )
        }
        startTime = System.currentTimeMillis()
        isRunning = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isRunning) return

        val elapsed = (System.currentTimeMillis() - startTime) / 1000f
        val duration = 1.5f

        if (elapsed > duration) {
            isRunning = false
            particles.clear()
            invalidate()
            return
        }

        val dt = 0.016f
        for (p in particles) {
            p.vy += 400f * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.rotation += p.rotSpeed
            p.life = 1f - (elapsed / duration)
            p.alpha = p.life.coerceIn(0f, 1f)

            paint.color = p.color
            paint.alpha = (p.alpha * 255).toInt()
            canvas.save()
            canvas.rotate(p.rotation, p.x, p.y)
            canvas.drawCircle(p.x, p.y, p.radius * p.life, paint)
            canvas.restore()
        }

        invalidate()
    }
}
