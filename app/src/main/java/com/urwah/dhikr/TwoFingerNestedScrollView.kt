package com.urwah.dhikr

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.widget.NestedScrollView

/**
 * تمرير عمودي بإصبعين فقط: الإصبع الواحد محجوز للتحديد/الضغط المطوّل على النص،
 * بينما التمرير البرمجي (بحث/قفز/ملف تلقائي) يعمل طبيعيًا لأننا لا نلمسه.
 */
class TwoFingerNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NestedScrollView(context, attrs, defStyleAttr) {

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount < 2) return false
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount < 2) return false
        return super.onTouchEvent(ev)
    }
}
