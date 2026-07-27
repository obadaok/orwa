package com.urwah.dhikr

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ReaderSearchAdapter(
    private var results: List<SearchResult>,
    private val onResultClick: (Int) -> Unit
) : RecyclerView.Adapter<ReaderSearchAdapter.ResultViewHolder>() {

    private var activePosition = -1

    data class SearchResult(
        val pageIndex: Int,
        val pageNumber: Int,
        val snippet: String,
        val matchStart: Int,
        val matchEnd: Int
    )

    fun setActivePosition(pos: Int) {
        val prev = activePosition
        activePosition = pos
        if (prev >= 0) notifyItemChanged(prev)
        if (pos >= 0) notifyItemChanged(pos)
    }

    fun updateResults(newResults: List<SearchResult>) {
        results = newResults
        activePosition = -1
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        val result = results[position]
        holder.bind(result, position == activePosition)
        holder.itemView.setOnClickListener {
            onResultClick(result.pageIndex)
        }
    }

    override fun getItemCount() = results.size

    inner class ResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPageNum: TextView = itemView.findViewById(R.id.tvResultPageNum)
        private val tvSnippet: TextView = itemView.findViewById(R.id.tvResultSnippet)

        fun bind(result: SearchResult, isActive: Boolean) {
            tvPageNum.text = "صفحة ${result.pageNumber}"

            if (isActive) {
                itemView.setBackgroundResource(R.drawable.bg_search_result_active)
            } else {
                itemView.setBackgroundResource(R.drawable.bg_search_result_normal)
            }

            val snippet = result.snippet
            val matchStart = result.matchStart
            val matchEnd = result.matchEnd

            if (matchStart >= 0 && matchEnd <= snippet.length && matchStart < matchEnd) {
                val spannable = SpannableString(snippet)
                spannable.setSpan(
                    BackgroundColorSpan(Color.parseColor("#408B6F5E")),
                    matchStart,
                    matchEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                tvSnippet.text = spannable
            } else {
                tvSnippet.text = snippet
            }
        }
    }
}
