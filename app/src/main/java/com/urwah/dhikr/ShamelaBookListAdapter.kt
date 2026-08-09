package com.urwah.dhikr

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ShamelaBookListAdapter(
    private var books: List<ShamelaBook>,
    private val onBookClick: (ShamelaBook) -> Unit,
    private val onDownloadClick: (ShamelaBook) -> Unit,
    private val onCancelClick: (ShamelaBook) -> Unit,
    private val onReadOnlineClick: (ShamelaBook) -> Unit,
    private val onBookLongClick: ((ShamelaBook) -> Unit)? = null
) : RecyclerView.Adapter<ShamelaBookListAdapter.ViewHolder>() {

    private val downloadStates = mutableMapOf<Int, DownloadState>()

    fun updateDownloadState(bookId: Int, state: DownloadState) {
        downloadStates[bookId] = state
        val pos = books.indexOfFirst { it.id == bookId }
        if (pos >= 0) notifyItemChanged(pos)
    }

    fun getDownloadState(bookId: Int): DownloadState? = downloadStates[bookId]

    fun updateBooks(newBooks: List<ShamelaBook>) {
        books = newBooks
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: View = view.findViewById(R.id.iv_category_icon)
        val name: TextView = view.findViewById(R.id.tv_category_name)
        val author: TextView = view.findViewById(R.id.tv_category_count)
        val type: TextView = view.findViewById(R.id.tvBookType)
        val btnAction: MaterialButton = view.findViewById(R.id.btnAction)
        val btnReadOnline: MaterialButton = view.findViewById(R.id.btnReadOnline)
        val downloadProgressRow: LinearLayout = view.findViewById(R.id.downloadProgressRow)
        val tvDownloadState: TextView = view.findViewById(R.id.tvDownloadState)
        val tvDownloadPercent: TextView = view.findViewById(R.id.tvDownloadPercent)
        val progressDownload: LinearProgressIndicator = view.findViewById(R.id.progressDownload)
        val statsRow: LinearLayout = view.findViewById(R.id.statsRow)
        val tvPageCount: TextView = view.findViewById(R.id.tvPageCount)
        val tvStatsSeparator: View = view.findViewById(R.id.tvStatsSeparator)
        val ivTimeIcon: View = view.findViewById(R.id.ivTimeIcon)
        val tvLastRead: TextView = view.findViewById(R.id.tvLastRead)
        val readingProgressRow: LinearLayout = view.findViewById(R.id.readingProgressRow)
        val tvReadingProgress: TextView = view.findViewById(R.id.tvReadingProgress)
        val tvProgressPage: TextView = view.findViewById(R.id.tvProgressPage)
        val progressReading: LinearProgressIndicator = view.findViewById(R.id.progressReading)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shamela_book, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val book = books[position]
        val context = holder.itemView.context
        val state = downloadStates[book.id]

        holder.name.text = book.title
        holder.author.text = book.displayAuthor

        if (book.bookType.isNotBlank()) {
            holder.type.visibility = View.VISIBLE
            holder.type.text = book.bookType
        } else {
            holder.type.visibility = View.GONE
        }

        bindStats(holder, context, book)
        bindReadingProgress(holder, context, book)

        when {
            state?.status == DownloadStatus.DOWNLOADING -> {
                holder.btnAction.text = "إلغاء"
                holder.btnAction.setIconResource(R.drawable.ic_close_circle)
                holder.btnAction.isEnabled = true
                holder.btnReadOnline.visibility = View.GONE
                holder.downloadProgressRow.visibility = View.VISIBLE
                holder.tvDownloadState.text = "جاري التحميل..."
                holder.tvDownloadPercent.text = "${(state.progress * 100).toInt()}%"
                holder.progressDownload.progress = (state.progress * 100).toInt()
                holder.readingProgressRow.visibility = View.GONE
                holder.btnAction.setOnClickListener { onCancelClick(book) }
            }
            state?.status == DownloadStatus.DOWNLOADED || ShamelaBookStorage.isBookDownloaded(context, book.id) -> {
                holder.btnAction.text = "فتح"
                holder.btnAction.setIconResource(R.drawable.ic_open_book)
                holder.btnAction.isEnabled = true
                holder.btnReadOnline.visibility = View.GONE
                holder.downloadProgressRow.visibility = View.GONE
                holder.btnAction.setOnClickListener { onBookClick(book) }
            }
            state?.status == DownloadStatus.FAILED -> {
                holder.btnAction.text = "إعادة المحاولة"
                holder.btnAction.setIconResource(R.drawable.ic_download)
                holder.btnAction.isEnabled = true
                holder.btnReadOnline.visibility = View.VISIBLE
                holder.btnReadOnline.setOnClickListener { onReadOnlineClick(book) }
                holder.downloadProgressRow.visibility = View.GONE
                holder.readingProgressRow.visibility = View.GONE
                holder.btnAction.setOnClickListener { onDownloadClick(book) }
            }
            else -> {
                holder.btnAction.text = "تحميل"
                holder.btnAction.setIconResource(R.drawable.ic_download)
                holder.btnAction.isEnabled = true
                holder.btnReadOnline.visibility = View.VISIBLE
                holder.btnReadOnline.setOnClickListener { onReadOnlineClick(book) }
                holder.downloadProgressRow.visibility = View.GONE
                holder.readingProgressRow.visibility = View.GONE
                holder.btnAction.setOnClickListener { onDownloadClick(book) }
            }
        }

        holder.itemView.setOnClickListener { onBookClick(book) }

        if (onBookLongClick != null) {
            holder.itemView.setOnLongClickListener {
                onBookLongClick.invoke(book)
                true
            }
        } else {
            holder.itemView.setOnLongClickListener(null)
            holder.itemView.isLongClickable = false
        }
    }

    private fun bindStats(holder: ViewHolder, context: android.content.Context, book: ShamelaBook) {
        val isDownloaded = ShamelaBookStorage.isBookDownloaded(context, book.id)
        if (!isDownloaded) {
            holder.statsRow.visibility = View.GONE
            return
        }

        holder.statsRow.visibility = View.VISIBLE
        val pageCount = ShamelaBookStorage.getPageCount(context, book.id)
        if (pageCount > 0) {
            holder.tvPageCount.text = "$pageCount صفحة"
            holder.tvPageCount.visibility = View.VISIBLE
        } else {
            holder.tvPageCount.visibility = View.GONE
        }

        val lastReadTime = ShamelaBookStorage.getLastReadTime(context, book.id)
        if (lastReadTime > 0L) {
            val elapsed = formatElapsedTime(context, lastReadTime)
            holder.tvLastRead.text = "آخر قراءة: $elapsed"
            holder.tvLastRead.visibility = View.VISIBLE
            holder.ivTimeIcon.visibility = View.VISIBLE
            holder.tvStatsSeparator.visibility = View.VISIBLE
        } else {
            holder.tvLastRead.visibility = View.GONE
            holder.ivTimeIcon.visibility = View.GONE
            holder.tvStatsSeparator.visibility = View.GONE
        }
    }

    private fun bindReadingProgress(holder: ViewHolder, context: android.content.Context, book: ShamelaBook) {
        val isDownloaded = ShamelaBookStorage.isBookDownloaded(context, book.id)
        if (!isDownloaded) {
            holder.readingProgressRow.visibility = View.GONE
            return
        }

        val pageCount = ShamelaBookStorage.getPageCount(context, book.id)
        val lastPage = ShamelaBookStorage.getLastReadPage(context, book.id)

        if (pageCount > 0 && lastPage > 0) {
            holder.readingProgressRow.visibility = View.VISIBLE
            val progress = ((lastPage.toFloat() / pageCount) * 100).toInt().coerceIn(0, 100)
            holder.tvReadingProgress.text = "قرأت $progress%"
            holder.tvProgressPage.text = "صفحة $lastPage من $pageCount"
            holder.progressReading.progress = progress
        } else {
            holder.readingProgressRow.visibility = View.GONE
        }
    }

    private fun formatElapsedTime(context: android.content.Context, timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)

        return when {
            minutes < 1 -> "الآن"
            minutes < 60 -> "منذ $minutes دقيقة"
            hours < 24 -> "منذ $hours ساعة"
            days < 7 -> "منذ $days يوم"
            days < 30 -> "منذ ${days / 7} أسبوع"
            else -> {
                val sdf = SimpleDateFormat("d MMM", Locale("ar"))
                sdf.format(Date(timestamp))
            }
        }
    }

    override fun getItemCount() = books.size
}
