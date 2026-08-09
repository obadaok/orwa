package com.urwah.dhikr

import android.app.Dialog
import android.content.Context
import android.view.ViewGroup
import android.widget.TextView

class BookCustomConfirmDialog(
    context: Context,
    title: String,
    message: String,
    private val positiveText: String = "موافق",
    private val negativeText: String? = null,
    private val onPositive: (() -> Unit)? = null,
    private val onNegative: (() -> Unit)? = null
) : Dialog(context, R.style.BookActionDialog) {

    init {
        setContentView(R.layout.dialog_book_custom_confirm)
        setCanceledOnTouchOutside(true)
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        findViewById<TextView>(R.id.tvDialogTitle)!!.text = title
        findViewById<TextView>(R.id.tvDialogMessage)!!.text = message

        val btnPositive = findViewById<TextView>(R.id.btnDialogPositive)!!
        btnPositive.text = positiveText
        btnPositive.setOnClickListener {
            dismiss()
            onPositive?.invoke()
        }

        val btnNegative = findViewById<TextView>(R.id.btnDialogNegative)!!
        if (negativeText != null) {
            btnNegative.text = negativeText
            btnNegative.visibility = android.view.View.VISIBLE
            btnNegative.setOnClickListener {
                dismiss()
                onNegative?.invoke()
            }
        } else {
            btnNegative.visibility = android.view.View.GONE
        }
    }
}
