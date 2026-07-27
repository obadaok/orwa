package com.urwah.dhikr

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView

class BookPageAdapter(
    private val pages: List<BookTextPaginator.Page>,
    private val bookTitle: String,
    private val fontSize: Float = 18f,
    private val lineSpacing: Float = 1.7f,
    private val typeface: Typeface? = null,
    private val onPageScrollState: ((isAtBottom: Boolean) -> Unit)? = null,
    private val onScrollViewReady: ((NestedScrollView?) -> Unit)? = null
) : RecyclerView.Adapter<BookPageAdapter.PageViewHolder>() {

    class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val scrollView: NestedScrollView = view.findViewById(R.id.scrollView)
        val tvBookTitle: TextView = view.findViewById(R.id.tvPageBookTitle)
        val tvChapterTitle: TextView = view.findViewById(R.id.tvPageChapterTitle)
        val dividerTop: View = view.findViewById(R.id.dividerTop)
        val tvContent: TextView = view.findViewById(R.id.tvPageContent)
        val tvPageNumber: TextView = view.findViewById(R.id.tvPageNumber)
        val verticalProgressFill: View = view.findViewById(R.id.verticalProgressFill)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_book_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val page = pages[position]

        holder.tvBookTitle.text = bookTitle

        if (!page.chapterTitle.isNullOrBlank()) {
            holder.tvChapterTitle.text = page.chapterTitle
            holder.tvChapterTitle.visibility = View.VISIBLE
            holder.dividerTop.visibility = View.VISIBLE
        } else {
            holder.tvChapterTitle.visibility = View.GONE
            holder.dividerTop.visibility = View.GONE
        }

        holder.tvContent.text = page.text
        holder.tvContent.textSize = fontSize
        holder.tvContent.setLineSpacing(0f, lineSpacing)
        if (typeface != null) {
            holder.tvContent.typeface = typeface
        }

        holder.tvPageNumber.text = "${position + 1} / ${pages.size}"

        // مؤشر التقدم الرأسي: نستخدم scaleY بدل تعديل layoutParams.height في كل
        // حدث تمرير — تفادٍ لـ requestLayout المتكرر (رخيص جدًا كتحويل بصري فقط).
        holder.verticalProgressFill.pivotY = 0f
        holder.verticalProgressFill.scaleY = 0f

        holder.scrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY == oldScrollY) return@OnScrollChangeListener
            val child = holder.scrollView.getChildAt(0) ?: return@OnScrollChangeListener
            val totalScrollable = child.height - holder.scrollView.height
            if (totalScrollable <= 0) {
                holder.verticalProgressFill.scaleY = 1f
                onPageScrollState?.invoke(true)
                onScrollViewReady?.invoke(holder.scrollView)
                return@OnScrollChangeListener
            }
            val progress = (scrollY.toFloat() / totalScrollable).coerceIn(0f, 1f)
            holder.verticalProgressFill.scaleY = progress
            val atBottom = scrollY >= totalScrollable - 4
            onPageScrollState?.invoke(atBottom)
            onScrollViewReady?.invoke(holder.scrollView)
        })

        holder.scrollView.scrollTo(0, 0)
        // قياس أولي واحد فقط بعد اكتمال التخطيط (وليس مع كل بكسل تمرير لاحقًا)
        holder.verticalProgressFill.post {
            val child = holder.scrollView.getChildAt(0) ?: return@post
            val totalScrollable = child.height - holder.scrollView.height
            if (totalScrollable <= 0) {
                holder.verticalProgressFill.scaleY = 1f
                onPageScrollState?.invoke(true)
            } else {
                holder.verticalProgressFill.scaleY = 0f
                onPageScrollState?.invoke(false)
            }
            onScrollViewReady?.invoke(holder.scrollView)
        }
    }

    override fun getItemCount(): Int = pages.size
}
