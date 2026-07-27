package com.urwah.dhikr

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ShamelaTocAdapter(
    private val entries: List<ShamelaTocEntry>,
    private val onPageMapping: ((ShamelaTocEntry) -> Unit)? = null,
    private val onEntryClick: (ShamelaTocEntry) -> Unit
) : RecyclerView.Adapter<ShamelaTocAdapter.ViewHolder>() {

    private val pageNumbers = mutableMapOf<Int, Int>()

    fun setPageNumber(titleId: Int, displayPage: Int) {
        pageNumbers[titleId] = displayPage
        notifyItemChanged(entries.indexOfFirst { it.titleId == titleId })
    }

    fun setPageNumbers(mapping: Map<Int, Int>) {
        pageNumbers.putAll(mapping)
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvItem: TextView = view.findViewById(R.id.tvTocItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shamela_toc, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        val indent = if (entry.parentId != null) "    " else ""

        val pageNumber = pageNumbers[entry.titleId]
        val pageStr = if (pageNumber != null) "  ·  ص $pageNumber" else ""

        holder.tvItem.text = "$indent${entry.titleText}$pageStr"
        holder.tvItem.setOnClickListener { onEntryClick(entry) }

        // Bold for top-level entries
        if (entry.parentId == null) {
            holder.tvItem.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
            holder.tvItem.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
    }

    override fun getItemCount() = entries.size
}
