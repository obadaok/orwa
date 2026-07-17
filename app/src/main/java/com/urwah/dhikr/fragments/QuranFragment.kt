package com.urwah.dhikr.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager

import com.urwah.dhikr.BookmarksActivity
import com.urwah.dhikr.R
import com.urwah.dhikr.SurahAdapter
import com.urwah.dhikr.SurahData
import com.urwah.dhikr.SurahDataProvider
import com.urwah.dhikr.SurahDetailActivity
import com.urwah.dhikr.databinding.FragmentQuranBinding

class QuranFragment : Fragment(), com.urwah.dhikr.SearchableFragment {

    private var _binding: FragmentQuranBinding? = null
    private val binding get() = _binding!!
    private var allSurahs = listOf<SurahData>()
    private var adapter: SurahAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQuranBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        allSurahs = SurahDataProvider.allSurahs

        binding.rvSurahs.layoutManager = LinearLayoutManager(requireContext())

        binding.ivBookmarksIcon.setOnClickListener {
            startActivity(Intent(requireContext(), BookmarksActivity::class.java))
        }

        // Continue reading banner
        val continueBanner = setupContinueReadingBanner()

        adapter = SurahAdapter(
            initialItems = allSurahs,
            onItemClick = { surah ->
                val intent = Intent(requireContext(), SurahDetailActivity::class.java)
                intent.putExtra("SURAH_NUMBER", surah.number)
                intent.putExtra("SURAH_NAME", surah.name)
                intent.putExtra("VERSE_COUNT", surah.verseCount)
                startActivity(intent)
            },
            headerView = continueBanner
        )
        binding.rvSurahs.adapter = adapter

        setupSearch()
    }

    override fun onResume() {
        super.onResume()
        updateContinueReadingBanner()
    }

    private fun setupContinueReadingBanner(): View? {
        val pos = com.urwah.dhikr.ReadingTracker.getPosition(requireContext()) ?: return null
        val banner = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_continue_reading, binding.rvSurahs, false) as LinearLayout
        banner.findViewById<TextView>(R.id.tvContinueSurahName).text = pos.surahName
        banner.findViewById<TextView>(R.id.tvContinueAyahNum).text = "الآية ${pos.ayahNumber}"
        banner.setOnClickListener {
            val surah = allSurahs.find { it.number == pos.surahNumber }
            if (surah != null) {
                val intent = Intent(requireContext(), SurahDetailActivity::class.java)
                intent.putExtra("SURAH_NUMBER", surah.number)
                intent.putExtra("SURAH_NAME", surah.name)
                intent.putExtra("VERSE_COUNT", surah.verseCount)
                intent.putExtra("LAST_AYAH", pos.ayahNumber)
                startActivity(intent)
            }
        }
        banner.tag = "continue_banner"
        binding.rvSurahs.tag = banner
        return banner
    }

    private fun updateContinueReadingBanner() {
        val pos = com.urwah.dhikr.ReadingTracker.getPosition(requireContext())
        val existing = binding.rvSurahs.findViewWithTag<View>("continue_banner")
        if (pos == null && existing != null) {
            (existing.parent as? ViewGroup)?.removeView(existing)
            adapter?.notifyDataSetChanged()
        } else if (pos != null && existing == null) {
            adapter?.notifyDataSetChanged()
        }
    }

    private fun setupSearch() {
        binding.ivSearchClose.setOnClickListener { hideSearch() }
        binding.layoutSearchOverlay.setOnClickListener { hideSearch() }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val filtered = SurahDataProvider.search(binding.etSearch.text.toString())
                if (filtered.isNotEmpty()) {
                    val surah = filtered.first()
                    val intent = Intent(requireContext(), SurahDetailActivity::class.java)
                    intent.putExtra("SURAH_NUMBER", surah.number)
                    intent.putExtra("SURAH_NAME", surah.name)
                    intent.putExtra("VERSE_COUNT", surah.verseCount)
                    startActivity(intent)
                    hideSearch()
                }
                true
            } else false
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterSurahs(s?.toString() ?: "")
            }
        })
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
        adapter?.updateList(allSurahs)
    }

    private fun filterSurahs(query: String) {
        adapter?.updateList(SurahDataProvider.search(query))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
