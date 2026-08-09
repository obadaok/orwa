package com.urwah.dhikr

import android.app.Dialog
import android.content.Context
import android.view.ViewGroup
import android.widget.TextView

class BookDeleteConfirmDialog(
    ctx: Context,
    private val book: ShamelaBook,
    private val onConfirmed: () -> Unit
) : Dialog(ctx, R.style.BookActionDialog) {

    private val appContext = ctx

    init {
        setContentView(R.layout.dialog_book_delete_confirm)
        setCanceledOnTouchOutside(true)
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val size = ShamelaBookStorage.getBookDownloadSize(appContext, book.id)
        val sizeText = ShamelaBookStorage.formatFileSize(size)

        findViewById<TextView>(R.id.tvDeleteBookName)!!.text = book.title
        findViewById<TextView>(R.id.tvDeleteBookSize)!!.text = "الحجم: $sizeText"
        findViewById<TextView>(R.id.tvDeleteNote)!!.text =
            "سيتم حذف النسخة المحملة فقط. بيانات الفهرس والتصنيفات ستبقى محفوظة."

        findViewById<TextView>(R.id.btnDeleteCancel)!!.setOnClickListener { dismiss() }
        findViewById<TextView>(R.id.btnDeleteConfirm)!!.setOnClickListener {
            dismiss()
            onConfirmed()
        }
    }
}
