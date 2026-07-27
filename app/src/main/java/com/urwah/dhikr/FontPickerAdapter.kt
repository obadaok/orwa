package com.urwah.dhikr

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FontPickerAdapter(
    private val fonts: List<FontManager.FontDef>,
    private val selectedFont: String,
    private val onFontSelected: (String) -> Unit
) : RecyclerView.Adapter<FontPickerAdapter.FontViewHolder>() {

    private var currentSelected = selectedFont

    fun setSelected(fileName: String) {
        val prev = currentSelected
        currentSelected = fileName
        if (prev != fileName) {
            val prevIdx = fonts.indexOfFirst { it.fileName == prev }
            val newIdx = fonts.indexOfFirst { it.fileName == fileName }
            if (prevIdx >= 0) notifyItemChanged(prevIdx)
            if (newIdx >= 0) notifyItemChanged(newIdx)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FontViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_font_picker, parent, false)
        return FontViewHolder(view)
    }

    override fun onBindViewHolder(holder: FontViewHolder, position: Int) {
        val font = fonts[position]
        val isSelected = font.fileName == currentSelected

        holder.tvName.text = font.displayName
        holder.tvPreview.text = font.preview

        val typeface = FontManager.loadTypeface(holder.itemView.context, font.fileName)
        holder.tvPreview.typeface = typeface

        holder.ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE

        if (isSelected) {
            holder.itemView.setBackgroundResource(R.drawable.bg_search_result_active)
        } else {
            holder.itemView.setBackgroundResource(R.drawable.bg_search_result_normal)
        }

        holder.itemView.setOnClickListener {
            onFontSelected(font.fileName)
        }
    }

    override fun getItemCount() = fonts.size

    class FontViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvFontName)
        val tvPreview: TextView = view.findViewById(R.id.tvFontPreview)
        val ivCheck: ImageView = view.findViewById(R.id.ivFontCheck)
    }
}
