package com.urwah.dhikr

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SurahAdapter(
    initialItems: List<SurahData>,
    private val onItemClick: (SurahData) -> Unit,
    private val headerView: View? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = initialItems.toMutableList()

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val number: TextView = view.findViewById(R.id.tv_surah_number)
        val name: TextView = view.findViewById(R.id.tv_surah_name)
        val verseCount: TextView = view.findViewById(R.id.tv_verse_count)
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemViewType(position: Int): Int {
        return if (position == 0 && headerView != null) TYPE_HEADER else TYPE_ITEM
    }

    override fun getItemCount(): Int = items.size + if (headerView != null) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(headerView!!)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_surah_card, parent, false)
            ViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == TYPE_HEADER) return
        val dataPos = position - if (headerView != null) 1 else 0
        val surah = items[dataPos]
        val vh = holder as ViewHolder
        vh.number.text = surah.number.toString()
        vh.name.text = surah.name
        vh.verseCount.text = "عدد الآيات: ${surah.verseCount}"
        vh.itemView.setOnClickListener { onItemClick(surah) }
    }

    fun updateList(newItems: List<SurahData>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
