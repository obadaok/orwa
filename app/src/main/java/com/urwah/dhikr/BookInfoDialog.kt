package com.urwah.dhikr

import android.app.Dialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookInfoDialog(
    ctx: Context,
    private val book: ShamelaBook
) : Dialog(ctx, R.style.BookActionDialog) {

    private val appContext = ctx

    init {
        setContentView(R.layout.dialog_book_info)
        setCanceledOnTouchOutside(true)
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val pageCount = ShamelaBookStorage.getPageCount(appContext, book.id)
        val downloadSize = ShamelaBookStorage.getBookDownloadSize(appContext, book.id)
        val sizeText = ShamelaBookStorage.formatFileSize(downloadSize)
        val lastRead = ShamelaBookStorage.getLastReadTime(appContext, book.id)
        val lastPage = ShamelaBookStorage.getLastReadPage(appContext, book.id)

        val progressText = if (pageCount > 0 && lastPage > 0) {
            val pct = ((lastPage.toFloat() / pageCount) * 100).toInt().coerceIn(0, 100)
            "$pct% — صفحة $lastPage من $pageCount"
        } else "—"

        val infoLines = mapOf(
            "الاسم" to book.title,
            "المؤلف" to book.displayAuthor,
            "القسم" to book.bookType,
            "عدد الصفحات" to (if (pageCount > 0) "$pageCount صفحة" else "—"),
            "حجم الملف" to sizeText,
            "تاريخ التحميل" to getDownloadTime(),
            "آخر قراءة" to (if (lastRead > 0L) formatDate(lastRead) else "—"),
            "التقدم" to progressText,
            "إصدار الكتاب" to (book.version.ifBlank { "—" })
        )

        findViewById<TextView>(R.id.tvBookInfoTitle)!!.text = book.title

        val container = findViewById<ViewGroup>(R.id.infoContainer)!!
        var idx = 0
        for ((key, value) in infoLines) {
            val row = layoutInflater.inflate(R.layout.item_book_info_row, container, false)
            row.findViewById<TextView>(R.id.tvInfoKey).text = key
            row.findViewById<TextView>(R.id.tvInfoValue).text = value
            container.addView(row)
            if (idx < infoLines.size - 1) {
                val divider = View(appContext)
                divider.layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1
                )
                divider.setBackgroundColor(appContext.getColor(R.color.sh_divider_line))
                container.addView(divider)
            }
            idx++
        }
    }

    private fun getDownloadTime(): String {
        val dir = ShamelaBookStorage.getBookDir(appContext, book.id)
        val metaFile = File(dir, "book_metadata.json")
        if (metaFile.exists()) {
            return formatDate(metaFile.lastModified())
        }
        return "—"
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("d MMM yyyy", Locale("ar"))
        return sdf.format(Date(timestamp))
    }
}
