package com.urwah.dhikr

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ShamelaCategoryAdapter(
    private val categories: List<ShamelaCategory>,
    private val onCategoryClick: (ShamelaCategory) -> Unit
) : RecyclerView.Adapter<ShamelaCategoryAdapter.ViewHolder>() {

    private val categoryIcons = mapOf(
        1 to R.drawable.ic_book_24dp,
        2 to R.drawable.ic_book_24dp,
        3 to R.drawable.ic_book_quran_24dp,
        4 to R.drawable.ic_book_quran_24dp,
        5 to R.drawable.ic_book_quran_24dp,
        6 to R.drawable.ic_auto_awesome_black_24dp,
        7 to R.drawable.ic_auto_awesome_black_24dp,
        8 to R.drawable.ic_auto_awesome_black_24dp,
        9 to R.drawable.ic_auto_awesome_black_24dp,
        10 to R.drawable.ic_auto_awesome_black_24dp,
        11 to R.drawable.ic_book_24dp,
        12 to R.drawable.ic_book_24dp,
        13 to R.drawable.ic_book_24dp,
        14 to R.drawable.ic_book_24dp,
        15 to R.drawable.ic_book_24dp,
        16 to R.drawable.ic_book_24dp,
        17 to R.drawable.ic_book_24dp,
        18 to R.drawable.ic_book_24dp,
        19 to R.drawable.ic_book_24dp,
        20 to R.drawable.ic_book_24dp,
        21 to R.drawable.ic_book_24dp,
        22 to R.drawable.ic_book_24dp,
        23 to R.drawable.ic_moon_stars_24dp,
        24 to R.drawable.ic_mosque_black_24dp,
        25 to R.drawable.ic_explore_black_24dp,
        26 to R.drawable.ic_people_gathering_24dp,
        27 to R.drawable.ic_people_gathering_24dp,
        28 to R.drawable.ic_explore_black_24dp,
        29 to R.drawable.ic_book_24dp,
        30 to R.drawable.ic_book_24dp,
        31 to R.drawable.ic_book_24dp,
        32 to R.drawable.ic_auto_awesome_black_24dp,
        33 to R.drawable.ic_auto_awesome_black_24dp,
        34 to R.drawable.ic_auto_awesome_black_24dp,
        35 to R.drawable.ic_auto_awesome_black_24dp,
        36 to R.drawable.ic_book_24dp,
        37 to R.drawable.ic_book_24dp,
        38 to R.drawable.ic_healing_black_24dp,
        39 to R.drawable.ic_book_24dp,
        40 to R.drawable.ic_book_24dp
    )

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_category_icon)
        val name: TextView = view.findViewById(R.id.tv_category_name)
        val count: TextView = view.findViewById(R.id.tv_category_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shamela_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cat = categories[position]
        holder.name.text = cat.name
        holder.count.text = "${cat.bookCount} كتاب"
        holder.icon.setImageResource(categoryIcons[cat.id] ?: R.drawable.ic_book_24dp)
        holder.itemView.setOnClickListener { onCategoryClick(cat) }
    }

    override fun getItemCount() = categories.size
}
