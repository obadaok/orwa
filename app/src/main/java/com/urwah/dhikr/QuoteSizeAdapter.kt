package com.urwah.dhikr

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class QuoteSize(
    val label: String,
    val w: Int,
    val h: Int
)

class QuoteSizeAdapter(
    private val items: List<QuoteSize>,
    private val onSelect: (QuoteSize) -> Unit
) : RecyclerView.Adapter<QuoteSizeAdapter.VH>() {

    private var selected = 0

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val shape: View = view.findViewById(R.id.sizeShape)
        val label: TextView = view.findViewById(R.id.sizeLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_quote_size, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val shortSide = dp(holder.itemView.context, 30f)
        val maxSide = dp(holder.itemView.context, 52f)
        val lp = holder.shape.layoutParams
        if (item.w <= 0 || item.h <= 0) {
            lp.width = shortSide
            lp.height = shortSide
        } else if (item.w >= item.h) {
            lp.height = shortSide
            lp.width = (shortSide * item.w / item.h).coerceAtMost(maxSide)
        } else {
            lp.width = shortSide
            lp.height = (shortSide * item.h / item.w).coerceAtMost(maxSide)
        }
        holder.shape.layoutParams = lp
        holder.shape.setBackgroundResource(
            if (position == selected) R.drawable.bg_size_shape_active else R.drawable.bg_size_shape
        )
        holder.label.text = item.label
        holder.itemView.contentDescription = item.label
        holder.label.setTextColor(
            if (position == selected)
                holder.itemView.context.getColor(R.color.urwah_thread_dark)
            else
                holder.itemView.context.getColor(R.color.urwah_thread_brown)
        )
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

    fun setSelected(size: QuoteSize) {
        val pos = items.indexOf(size)
        if (pos == selected || pos < 0) return
        val old = selected
        selected = pos
        notifyItemChanged(old)
        notifyItemChanged(pos)
    }

    override fun getItemCount() = items.size

    private fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
