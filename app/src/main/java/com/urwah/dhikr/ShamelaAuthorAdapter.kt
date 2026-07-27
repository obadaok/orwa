package com.urwah.dhikr

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ShamelaAuthorAdapter(
    private var authors: List<ShamelaAuthor>,
    private val onAuthorClick: (ShamelaAuthor) -> Unit
) : RecyclerView.Adapter<ShamelaAuthorAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_category_icon)
        val name: TextView = view.findViewById(R.id.tv_category_name)
        val count: TextView = view.findViewById(R.id.tv_category_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shamela_author, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val author = authors[position]
        holder.name.text = author.name
        holder.count.text = "${author.bookCount} كتاب"
        holder.icon.setImageResource(R.drawable.ic_people_gathering_24dp)
        holder.itemView.setOnClickListener { onAuthorClick(author) }
    }

    override fun getItemCount() = authors.size

    fun updateAuthors(newAuthors: List<ShamelaAuthor>) {
        authors = newAuthors
        notifyDataSetChanged()
    }
}
