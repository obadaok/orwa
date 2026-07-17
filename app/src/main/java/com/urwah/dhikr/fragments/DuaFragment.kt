package com.urwah.dhikr.fragments

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.urwah.dhikr.DuaDataProvider
import com.urwah.dhikr.DuaGroupedItem
import com.urwah.dhikr.DuaItem
import com.urwah.dhikr.R
import com.urwah.dhikr.databinding.FragmentDuaBinding

class DuaFragment : Fragment(), com.urwah.dhikr.SearchableFragment {

    private var _binding: FragmentDuaBinding? = null
    private val binding get() = _binding!!
    private var allGroupedDuas = listOf<DuaGroupedItem>()
    private var adapter: DuaCategoryAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDuaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        allGroupedDuas = DuaDataProvider.getGroupedDuas()

        binding.rvDuas.layoutManager = LinearLayoutManager(requireContext())
        adapter = DuaCategoryAdapter(allGroupedDuas) { dua ->
            showDuaDetail(dua)
        }
        binding.rvDuas.adapter = adapter

        binding.ivBack.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.ivSearchIcon.setOnClickListener { showSearch() }

        setupSearch()
    }

    private fun setupSearch() {
        binding.ivSearchClose.setOnClickListener { hideSearch() }
        binding.layoutSearchOverlay.setOnClickListener { hideSearch() }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                hideSearch()
                true
            } else false
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterDuas(s?.toString() ?: "")
            }
        })
    }

    private fun filterDuas(query: String) {
        val results = DuaDataProvider.search(query)
        adapter?.updateList(if (query.isBlank()) allGroupedDuas else results)
    }

    override fun showSearch() {
        binding.layoutSearchOverlay.visibility = View.VISIBLE
        binding.etSearch.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideSearch() {
        binding.layoutSearchOverlay.visibility = View.GONE
        binding.etSearch.setText("")
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        adapter?.updateList(allGroupedDuas)
    }

    private fun showDuaDetail(dua: DuaItem) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_dua_detail, null)
        dialogView.findViewById<TextView>(R.id.tvDuaDetailTitle).text = dua.title
        dialogView.findViewById<TextView>(R.id.tvDuaDetailCategory).text = dua.category
        dialogView.findViewById<TextView>(R.id.tvDuaDetailArabic).text = dua.arabicText
        dialogView.findViewById<TextView>(R.id.tvDuaDetailTranslation).text = dua.translation
        dialogView.findViewById<TextView>(R.id.tvDuaDetailSource).text = "المصدر: ${dua.source}"

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<View>(R.id.btnDuaDetailClose).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class DuaCategoryAdapter(
    private var items: List<DuaGroupedItem>,
    private val onDuaClick: (DuaItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is DuaGroupedItem.Header -> TYPE_HEADER
            is DuaGroupedItem.Item -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_dua_header, parent, false)
                HeaderViewHolder(v)
            }
            else -> {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_dua_card, parent, false)
                ItemViewHolder(v)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is DuaGroupedItem.Header -> {
                (holder as HeaderViewHolder).tvTitle.text = item.category
            }
            is DuaGroupedItem.Item -> {
                val vh = holder as ItemViewHolder
                vh.tvTitle.text = item.dua.title
                vh.tvSource.text = item.dua.source
                vh.tvPreview.text = item.dua.arabicText.take(50) + "..."
                vh.card.setOnClickListener { onDuaClick(item.dua) }
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateList(newItems: List<DuaGroupedItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class HeaderViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvTitle: TextView = v.findViewById(R.id.tvDuaHeaderTitle)
    }

    class ItemViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val card: MaterialCardView = v.findViewById(R.id.cardDuaItem)
        val tvTitle: TextView = v.findViewById(R.id.tvDuaItemTitle)
        val tvSource: TextView = v.findViewById(R.id.tvDuaItemSource)
        val tvPreview: TextView = v.findViewById(R.id.tvDuaItemPreview)
    }
}
