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
    private val originalTotalPages: Int,
    private val fontSize: Float = 18f,
    private val lineSpacing: Float = 1.7f,
    private val typeface: Typeface? = null,
    private val onPageScrollState: ((isAtBottom: Boolean) -> Unit)? = null,
    private val onScrollViewReady: ((position: Int, NestedScrollView?) -> Unit)? = null
) : RecyclerView.Adapter<BookPageAdapter.PageViewHolder>() {

    class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val scrollView: NestedScrollView = view.findViewById(R.id.scrollView)
        val tvBookTitle: TextView = view.findViewById(R.id.tvPageBookTitle)
        val tvChapterTitle: TextView = view.findViewById(R.id.tvPageChapterTitle)
        val dividerTop: View = view.findViewById(R.id.dividerTop)
        val tvContent: TextView = view.findViewById(R.id.tvPageContent)
        val tvPageNumber: TextView = view.findViewById(R.id.tvPageNumber)
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

        val total = originalTotalPages.coerceAtLeast(1)
        if (page.originalPageNum != null) {
            holder.tvPageNumber.text = "${page.originalPageNum} / $total"
        } else {
            holder.tvPageNumber.text = "${position + 1} / $total"
        }

        // مؤشر التقدم الرأسي: نستخدم scaleY بدل تعديل layoutParams.height في كل
        // حدث تمرير — تفادٍ لـ requestLayout المتكرر (رخيص جدًا كتحويل بصري فقط).
        holder.scrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY == oldScrollY) return@OnScrollChangeListener
            val child = holder.scrollView.getChildAt(0) ?: return@OnScrollChangeListener
            val totalScrollable = child.height - holder.scrollView.height
            val atBottom = if (totalScrollable <= 0) true else scrollY >= totalScrollable - 4
            onPageScrollState?.invoke(atBottom)
            onScrollViewReady?.invoke(position, holder.scrollView)
        })

        holder.scrollView.scrollTo(0, 0)
        holder.scrollView.post {
            onScrollViewReady?.invoke(position, holder.scrollView)
        }
    }

    override fun getItemCount(): Int = pages.size
}
