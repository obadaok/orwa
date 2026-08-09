package com.urwah.dhikr

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class QuoteBackgroundAdapter(
    private val items: List<QuoteBackground>,
    private val onSelect: (QuoteBackground) -> Unit
) : RecyclerView.Adapter<QuoteBackgroundAdapter.VH>() {

    private var selected = 0

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val swatch: View = view.findViewById(R.id.bgSwatch)
        val label: TextView = view.findViewById(R.id.bgLabel)
        val indicator: View = view.findViewById(R.id.bgIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_quote_bg, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.swatch.setBackgroundColor(item.bgColor)
        holder.label.text = item.displayName
        holder.label.setTextColor(item.textColor)
        holder.indicator.visibility = if (position == selected) View.VISIBLE else View.INVISIBLE
        holder.itemView.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            val old = selected
            selected = pos
            notifyItemChanged(old)
            notifyItemChanged(pos)
            onSelect(items[pos])
        }
    }

    override fun getItemCount() = items.size
}
