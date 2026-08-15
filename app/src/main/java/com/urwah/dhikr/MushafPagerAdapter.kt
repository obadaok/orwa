package com.urwah.dhikr

import android.graphics.Typeface
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/**
 * محوّل صفحات المصحف لـ ViewPager2.
 * التحميل كسول: ViewPager2 مع offscreenPageLimit=1 يبقي الصفحات N-1/N/N+1 حية
 * فقط ويعيد استخدام (recycle) الصفحات البعيدة، فيبقى استخدام الذاكرة منخفضاً
 * رغم مرور المستخدم على 604 صفحات.
 */
class MushafPagerAdapter(
    private val pageCount: Int,
    private val typeface: Typeface,
    private val inkColor: Int,
    private val accentColor: Int,
    private val surahNameProvider: (Int) -> String,
    private val pagesProvider: (Int) -> QuranPageLayouts.Page,
    private val onPageTap: (Int) -> Unit,
    private val onPageLongPress: (Int) -> Unit,
) : RecyclerView.Adapter<MushafPagerAdapter.PageHolder>() {

    class PageHolder(val pageView: MushafPageView) : RecyclerView.ViewHolder(pageView) {
        var boundPage: Int = -1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val view = MushafPageView(parent.context)
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        return PageHolder(view)
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        holder.boundPage = position + 1
        getItemViewHolder(holder, position)
        // صفحات ViewPager2 تُعاد تدويرها عبر RecyclerView، وقد تصل حاملة حالة
        // المحوّل السابقة (alpha=0 لصفحة خرجت خارج النطاق). إعادة الضبط هنا تضمن
        // أن أي صفحة تدخل الشاشة تكون مرئية فوراً مهما كان تسلسل إعادة الاستخدام.
        holder.pageView.apply {
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            translationX = 0f
            translationY = 0f
        }
        holder.pageView.setCallbacks(
            onSingleTap = { onPageTap.invoke(holder.boundPage) },
            onLongPress = { onPageLongPress.invoke(holder.boundPage) },
        )
    }

    private fun getItemViewHolder(holder: PageHolder, position: Int) {
        val page = pagesProvider(position + 1)
        holder.pageView.bind(
            page = page,
            typeface = typeface,
            inkColor = inkColor,
            accentColor = accentColor,
            surahNameProvider = surahNameProvider,
        )
    }

    override fun getItemCount(): Int = pageCount
}