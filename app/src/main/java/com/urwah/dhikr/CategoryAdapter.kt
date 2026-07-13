package com.urwah.dhikr

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CategoryAdapter(
    initialItems: List<CategoryGroupedItem>,
    private val onItemClick: (DhikrCategory) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = initialItems.toMutableList()

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tv_section_title)
    }

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_category_icon)
        val name: TextView = view.findViewById(R.id.tv_category_name)
        val count: TextView = view.findViewById(R.id.tv_category_count)
        val checkmark: View = view.findViewById(R.id.ivCategoryComplete)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is CategoryGroupedItem.Header -> TYPE_HEADER
            is CategoryGroupedItem.Item -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_section_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_category_card, parent, false)
                ItemViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is CategoryGroupedItem.Header -> {
                (holder as HeaderViewHolder).title.text = item.title
            }
            is CategoryGroupedItem.Item -> {
                val cat = item.category
                val itemHolder = holder as ItemViewHolder
                itemHolder.name.text = cat.name

                val dhikrCount = DhikrDataProvider.getDhikrs(cat.name).size
                itemHolder.count.text = "$dhikrCount أذكار"

                itemHolder.icon.post {
                    itemHolder.icon.setImageResource(cat.iconResId)
                }

                val isComplete = DhikrListAdapter.isCategoryComplete(holder.itemView.context, cat.name)
                itemHolder.checkmark.visibility = if (isComplete) View.VISIBLE else View.GONE

                itemHolder.itemView.setOnClickListener { onItemClick(cat) }
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateList(newItems: List<CategoryGroupedItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
