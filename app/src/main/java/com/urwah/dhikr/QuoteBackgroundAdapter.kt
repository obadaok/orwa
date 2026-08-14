package com.urwah.dhikr

import android.graphics.drawable.GradientDrawable
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
        val swatchFill: View = view.findViewById(R.id.bgSwatchFill)
        val label: TextView = view.findViewById(R.id.bgLabel)
        val indicator: View = view.findViewById(R.id.bgIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_quote_bg, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        // نلوّن نسخة خاصة من الدرابل بدل استبداله بالكامل، فيبقى بردر الكارت ظاهرًا
        // لكل الخلفيات (كانت setBackgroundColor سابقًا تمحو البردر تمامًا).
        val fill = (holder.swatchFill.background?.mutate() as? GradientDrawable)
        fill?.setColor(item.bgColor)

        // لون ثابت للتسمية بدل لون نص الخلفية نفسها، لأن التسمية تظهر فوق خلفية
        // المنتقي وليس فوق السواتش — كانت الخلفيات الداكنة تصبح شبه غير مقروءة.
        holder.label.text = item.displayName

        holder.indicator.visibility = if (position == selected) View.VISIBLE else View.INVISIBLE
        holder.itemView.contentDescription = item.displayName
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

    fun setSelected(bg: QuoteBackground) {
        val pos = items.indexOf(bg)
        if (pos == selected || pos < 0) return
        val old = selected
        selected = pos
        notifyItemChanged(old)
        notifyItemChanged(pos)
    }

    override fun getItemCount() = items.size
}
