package com.urwah.dhikr

import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PageViewAdapter(
    private val pages: List<PageData>,
    private val uthmanicTypeface: Typeface?,
    private val isDark: Boolean
) : RecyclerView.Adapter<PageViewAdapter.PageViewHolder>() {

    private val ayahColor = if (isDark) Color.parseColor("#e8e0d6") else Color.parseColor("#5E4B40")

    data class PageData(
        val pageNumber: Int,
        val ayahs: List<PageAyah>
    )

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAyahs: TextView = itemView.findViewById(R.id.tvPageAyahs)
        val tvPageNumber: TextView = itemView.findViewById(R.id.tvPageNumber)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_quran_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val page = pages[position]

        val sb = StringBuilder()
        page.ayahs.forEachIndexed { index, ayah ->
            sb.append(ayah.text.trimEnd())
            if (index < page.ayahs.size - 1) {
                sb.append("  ")
            }
        }

        val textLen = sb.length
        val dynamicSize = when {
            textLen > 3500 -> 16f
            textLen > 2500 -> 18f
            textLen > 1500 -> 20f
            else -> 22f
        }

        holder.tvAyahs.apply {
            typeface = uthmanicTypeface
            textSize = dynamicSize
            setTextColor(ayahColor)
            textDirection = View.TEXT_DIRECTION_RTL
            if (Build.VERSION.SDK_INT >= 26) {
                justificationMode = 1
            }
            text = sb.toString()
        }

        holder.tvPageNumber.text = toHindiDigits(page.pageNumber)
    }

    override fun getItemCount() = pages.size

    private fun toHindiDigits(number: Int): String {
        val hindiDigits = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
        return number.toString().map { hindiDigits[it - '0'] }.joinToString("")
    }
}
