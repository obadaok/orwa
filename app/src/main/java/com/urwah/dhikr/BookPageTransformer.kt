package com.urwah.dhikr

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs
import kotlin.math.max

/**
 * RTL Book Page Flip Transformer for ViewPager2.
 *
 * Simulates a book page flip with 3D rotation around the spine (left edge for RTL).
 * - Pages flip from right-to-left (RTL reading direction).
 * - The flipping page rotates around its left edge (spine of the book).
 * - The page behind is revealed with a subtle shadow.
 * - Includes a slight scale effect for depth perception.
 */
class BookPageTransformer : ViewPager2.PageTransformer {

    private val cameraDistance = 12000f

    override fun transformPage(page: View, position: Float) {
        val viewPager = page.parent as? ViewPager2 ?: return
        val isRtl = viewPager.layoutDirection == View.LAYOUT_DIRECTION_RTL

        // Normalize position: -1 = off-screen left, 0 = center, 1 = off-screen right
        when {
            position < -1f -> {
                // Page is fully off-screen to the left
                page.alpha = 0f
            }
            position <= 0f -> {
                // Page is coming into view from the right (next page) or is centered
                page.alpha = 1f
                page.visibility = View.VISIBLE

                // Pivot on the left edge (spine) for RTL book
                page.pivotX = if (isRtl) page.width.toFloat() else 0f
                page.pivotY = page.height * 0.5f

                // Camera distance for 3D effect
                page.cameraDistance = cameraDistance

                // 3D rotation: the page flips around the spine
                val rotation = if (isRtl) {
                    -180f * abs(position)
                } else {
                    180f * abs(position)
                }
                page.rotationY = rotation

                // Subtle scale for depth
                val scale = max(0.95f, 1f - abs(position) * 0.05f)
                page.scaleX = scale
                page.scaleY = scale

                // Shadow effect: dim the page as it flips
                val absPos = abs(position)
                page.alpha = if (absPos > 0.7f) {
                    1f - (absPos - 0.7f) / 0.3f
                } else {
                    1f
                }
            }
            position <= 1f -> {
                // Page is going off-screen to the right (previous page)
                page.alpha = 1f
                page.visibility = View.VISIBLE

                page.pivotX = if (isRtl) page.width.toFloat() else 0f
                page.pivotY = page.height * 0.5f
                page.cameraDistance = cameraDistance

                val rotation = if (isRtl) {
                    180f * abs(position)
                } else {
                    -180f * abs(position)
                }
                page.rotationY = rotation

                val scale = max(0.95f, 1f - abs(position) * 0.05f)
                page.scaleX = scale
                page.scaleY = scale

                val absPos = abs(position)
                page.alpha = if (absPos > 0.7f) {
                    1f - (absPos - 0.7f) / 0.3f
                } else {
                    1f
                }
            }
            else -> {
                // Page is fully off-screen to the right
                page.alpha = 0f
            }
        }
    }
}
