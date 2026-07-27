package com.urwah.dhikr

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LibraryBookAdapter(
    private val books: List<LibraryBook>,
    private val onBookClick: (LibraryBook) -> Unit
) : RecyclerView.Adapter<LibraryBookAdapter.BookViewHolder>() {

    class BookViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivBookIcon: ImageView = view.findViewById(R.id.ivBookIcon)
        val tvBookTitle: TextView = view.findViewById(R.id.tvBookTitle)
        val tvBookDescription: TextView = view.findViewById(R.id.tvBookDescription)
        val tvChaptersCount: TextView = view.findViewById(R.id.tvChaptersCount)
        val tvLastRead: TextView = view.findViewById(R.id.tvLastRead)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_library_book, parent, false)
        return BookViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val book = books[position]
        holder.tvBookTitle.text = book.title
        holder.tvBookDescription.text = book.description
        holder.tvChaptersCount.text = "${book.chaptersCount} فصل"
        holder.ivBookIcon.setImageResource(book.iconResId)

        holder.itemView.setOnClickListener {
            onBookClick(book)
        }
    }

    override fun getItemCount(): Int = books.size
}
