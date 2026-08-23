package com.urwah.dhikr

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator

/**
 * أنيميشن ظهور خفيف لبطاقات السور: انزلاق قصير لأعلى مع شفافية عند ربط العنصر.
 * لا يغيّر حجم المحتوى الداخلي ولا يتدخل أثناء التمرير السريع (لا lag):
 * يعمل فقط على أول ربط للـViewHolder، والأنيميشن قصير (220ms).
 */
class SurahCardItemAnimator : SimpleItemAnimator() {

    override fun animateAppearance(
        viewHolder: RecyclerView.ViewHolder,
        preLayoutInfo: RecyclerView.ItemAnimator.ItemHolderInfo?,
        postLayoutInfo: RecyclerView.ItemAnimator.ItemHolderInfo
    ): Boolean {
        val v = viewHolder.itemView
        // فقط للعناصر التي تظهر أسفل الشاشة (ربط جديد أثناء التمرير للأسفل)
        if (preLayoutInfo == null && postLayoutInfo.top > 0) {
            runAppearAnimation(v)
            return true
        }
        return false
    }

    private fun runAppearAnimation(v: View) {
        v.alpha = 0f
        v.translationY = 28f
        v.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220L)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.4f))
            .withEndAction {
                v.alpha = 1f
                v.translationY = 0f
                v.scaleX = 1f
                v.scaleY = 1f
            }
            .start()
    }

    override fun animateDisappearance(
        viewHolder: RecyclerView.ViewHolder,
        preLayoutInfo: RecyclerView.ItemAnimator.ItemHolderInfo,
        postLayoutInfo: RecyclerView.ItemAnimator.ItemHolderInfo?
    ): Boolean = false

    override fun animateAdd(viewHolder: RecyclerView.ViewHolder): Boolean = false

    override fun animateRemove(viewHolder: RecyclerView.ViewHolder): Boolean = false

    override fun animatePersistence(
        viewHolder: RecyclerView.ViewHolder,
        preLayoutInfo: RecyclerView.ItemAnimator.ItemHolderInfo,
        postLayoutInfo: RecyclerView.ItemAnimator.ItemHolderInfo
    ): Boolean = false

    @Suppress("DEPRECATION")
    override fun animateMove(
        viewHolder: RecyclerView.ViewHolder, fromX: Int, fromY: Int, toX: Int, toY: Int
    ): Boolean = false

    @Suppress("DEPRECATION")
    override fun animateChange(
        oldViewHolder: RecyclerView.ViewHolder,
        newViewHolder: RecyclerView.ViewHolder,
        fromX: Int, fromY: Int, toX: Int, toY: Int
    ): Boolean = false

    override fun runPendingAnimations() {}

    override fun endAnimation(viewHolder: RecyclerView.ViewHolder) {
        viewHolder.itemView.animate().cancel()
    }

    override fun endAnimations() {}

    override fun isRunning(): Boolean = false
}
