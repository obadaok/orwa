package com.urwah.dhikr

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

/**
 * محوّل بسيط لصفحات المصحف: انزلاق أفقي مع تظليل طفيف، دون قلب 3D ثقيل.
 * يعمل بنفس اتجاه RTL سواء كانت الواجهة العربية أو غيرها.
 */
class MushafPageFadeTransformer : ViewPager2.PageTransformer {

    override fun transformPage(page: View, position: Float) {
        val absPos = abs(position)
        if (absPos > 1f) {
            page.alpha = 0f
            return
        }
        if (position == 0f) {
            // الصفحة الجارية في المنتصف: تُعاد دائماً إلى الحالة الطبيعية، وإلا
            // تظل على alpha/scale ثابتين من حالة مُعاد تدويرها (یختفي الرسم حتى
            // أول تمرير — المؤثر المعروف عند setCurrentItem بلا انزلاق).
            page.alpha = 1f
            page.scaleX = 1f
            page.scaleY = 1f
            return
        }
        page.alpha = 1f - absPos * 0.08f
        page.scaleX = 1f - absPos * 0.015f
        page.scaleY = 1f - absPos * 0.015f
    }
}