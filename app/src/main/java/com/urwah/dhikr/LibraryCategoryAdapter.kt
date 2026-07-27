package com.urwah.dhikr

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class LibraryCategoryAdapter(
    private val categories: List<LibraryCategory>,
    private val onBookClick: (LibraryBook) -> Unit
) : RecyclerView.Adapter<LibraryCategoryAdapter.CategoryViewHolder>() {

    class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivCategoryIcon: ImageView = view.findViewById(R.id.ivCategoryIcon)
        val tvCategoryTitle: TextView = view.findViewById(R.id.tvCategoryTitle)
        val tvCategoryDescription: TextView = view.findViewById(R.id.tvCategoryDescription)
        val rvBooks: RecyclerView = view.findViewById(R.id.rvBooks)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_library_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.tvCategoryTitle.text = category.title
        holder.tvCategoryDescription.text = category.description
        holder.ivCategoryIcon.setImageResource(category.iconResId)

        val booksAdapter = LibraryBookAdapter(category.books) { book ->
            onBookClick(book)
        }
        holder.rvBooks.layoutManager = LinearLayoutManager(
            holder.itemView.context,
            LinearLayoutManager.VERTICAL,
            false
        )
        holder.rvBooks.adapter = booksAdapter
    }

    override fun getItemCount() = categories.size
}
