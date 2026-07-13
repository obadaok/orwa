package com.urwah.dhikr.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.urwah.dhikr.DhikrDataProvider
import com.urwah.dhikr.DhikrDetailsActivity
import com.urwah.dhikr.FavoritesManager
import com.urwah.dhikr.R
import com.urwah.dhikr.databinding.FragmentFavoritesBinding

class FavoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!
    private var items = listOf<Pair<String, String>>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.ivSettingsFav.setOnClickListener {
            findNavController().navigate(R.id.nav_settings)
        }
        refreshFavorites()
    }

    override fun onResume() {
        super.onResume()
        refreshFavorites()
    }

    private fun refreshFavorites() {
        val favIds = FavoritesManager.getAllFavorites()
        if (favIds.isEmpty()) {
            binding.rvFavorites.visibility = View.GONE
            binding.tvFavEmpty.visibility = View.VISIBLE
            return
        }
        binding.tvFavEmpty.visibility = View.GONE
        binding.rvFavorites.visibility = View.VISIBLE

        items = favIds.mapNotNull { id ->
            val item = DhikrDataProvider.getDhikrById(id) ?: return@mapNotNull null
            val catName = DhikrDataProvider.findCategoryForItem(id) ?: return@mapNotNull null
            Pair(catName, item.arabic.take(80) + "...")
        }.toList()

        binding.rvFavorites.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFavorites.adapter = FavoritesAdapter(items) { catName ->
            val intent = Intent(requireContext(), DhikrDetailsActivity::class.java)
            intent.putExtra("CATEGORY_NAME", catName)
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class FavoritesAdapter(
    private val items: List<Pair<String, String>>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<FavoritesAdapter.FavViewHolder>() {

    class FavViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardFavItem)
        val tvCategory: TextView = view.findViewById(R.id.tvFavCategory)
        val tvPreview: TextView = view.findViewById(R.id.tvFavPreview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite, parent, false)
        return FavViewHolder(view)
    }

    override fun onBindViewHolder(holder: FavViewHolder, position: Int) {
        val (catName, preview) = items[position]
        holder.tvCategory.text = catName
        holder.tvPreview.text = preview
        holder.card.setOnClickListener { onItemClick(catName) }
    }

    override fun getItemCount() = items.size
}
