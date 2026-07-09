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
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.urwah.dhikr.CategoryAdapter
import com.urwah.dhikr.CategoryGroupedItem
import com.urwah.dhikr.DhikrCategory
import com.urwah.dhikr.DhikrDataProvider
import com.urwah.dhikr.DhikrDetailsActivity
import com.urwah.dhikr.R
import com.urwah.dhikr.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var allGroupedItems = listOf<CategoryGroupedItem>()
    private var adapter: CategoryAdapter? = null
    private var isSearchVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val allCategories = DhikrDataProvider.getAllCategories().map { name ->
            DhikrCategory(name, DhikrDataProvider.getCategoryIcon(name))
        }

        allGroupedItems = buildGroupedList(allCategories)

        val glm = GridLayoutManager(requireContext(), 2)
        glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (allGroupedItems[position]) {
                    is CategoryGroupedItem.Header -> 2
                    is CategoryGroupedItem.Item -> 1
                }
            }
        }
        binding.rvCategories.layoutManager = glm

        adapter = CategoryAdapter(allGroupedItems) { selected ->
            val intent = Intent(requireContext(), DhikrDetailsActivity::class.java)
            intent.putExtra("CATEGORY_NAME", selected.name)
            startActivity(intent)
        }
        binding.rvCategories.adapter = adapter

        setupHeroButtons(allCategories)
        setupChips()
        setupSearch()
    }

    private fun setupHeroButtons(allCategories: List<DhikrCategory>) {
        val morningCat = allCategories.find { it.name == "أذكار الصباح" }
        val eveningCat = allCategories.find { it.name == "أذكار المساء" }

        binding.heroMorning.setOnClickListener {
            morningCat?.let { cat ->
                val intent = Intent(requireContext(), DhikrDetailsActivity::class.java)
                intent.putExtra("CATEGORY_NAME", cat.name)
                startActivity(intent)
            }
        }
        binding.heroEvening.setOnClickListener {
            eveningCat?.let { cat ->
                val intent = Intent(requireContext(), DhikrDetailsActivity::class.java)
                intent.putExtra("CATEGORY_NAME", cat.name)
                startActivity(intent)
            }
        }
    }

    private fun setupSearch() {
        binding.ivSearchIcon.setOnClickListener { showSearch() }

        binding.ivSearchClose.setOnClickListener { hideSearch() }

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
                filterCategories(s?.toString() ?: "")
            }
        })
    }

    private fun showSearch() {
        isSearchVisible = true
        binding.ivSearchIcon.visibility = View.GONE
        binding.etSearch.visibility = View.VISIBLE
        binding.ivSearchClose.visibility = View.VISIBLE
        binding.etSearch.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideSearch() {
        isSearchVisible = false
        binding.ivSearchIcon.visibility = View.VISIBLE
        binding.etSearch.visibility = View.GONE
        binding.ivSearchClose.visibility = View.GONE
        binding.etSearch.setText("")
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        adapter?.updateList(allGroupedItems)
    }

    private fun setupChips() {
        val chipGroup = binding.chipGroup
        val groupKeys = linkedSetOf<String>()
        groupKeys.add("الكل")

        for (item in allGroupedItems) {
            if (item is CategoryGroupedItem.Header) {
                groupKeys.add(item.title)
            }
        }

        for (key in groupKeys) {
            val chip = layoutInflater.inflate(
                R.layout.item_chip_filter,
                chipGroup,
                false
            ) as Chip
            chip.text = key
            chip.isChecked = key == "الكل"

            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    scrollToSection(key)
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun scrollToSection(title: String) {
        if (title == "الكل") {
            binding.rvCategories.scrollToPosition(0)
            return
        }
        val pos = allGroupedItems.indexOfFirst {
            it is CategoryGroupedItem.Header && it.title == title
        }
        if (pos >= 0) {
            binding.rvCategories.scrollToPosition(pos)
        }
    }

    private fun filterCategories(query: String) {
        if (query.isBlank()) {
            adapter?.updateList(allGroupedItems)
            return
        }

        val catMatches = allGroupedItems.filter { item ->
            when (item) {
                is CategoryGroupedItem.Header -> false
                is CategoryGroupedItem.Item -> item.category.name.contains(query)
            }
        }
        val itemResults = DhikrDataProvider.searchInItems(query)

        val result = mutableListOf<CategoryGroupedItem>()
        if (catMatches.isNotEmpty()) {
            result.add(CategoryGroupedItem.Header("تصنيفات"))
            result.addAll(catMatches)
        }
        if (itemResults.isNotEmpty()) {
            result.add(CategoryGroupedItem.Header("نتائج البحث في الأذكار"))
            for ((catName, _) in itemResults.take(20)) {
                result.add(CategoryGroupedItem.Item(
                    DhikrCategory(catName, DhikrDataProvider.getCategoryIcon(catName))
                ))
            }
        }
        if (result.isEmpty()) {
            result.add(CategoryGroupedItem.Header("لا توجد نتائج"))
        }
        adapter?.updateList(result)
    }

    private fun buildGroupedList(categories: List<DhikrCategory>): List<CategoryGroupedItem> {
        val groups = linkedMapOf<String, MutableList<DhikrCategory>>()
        val misc = mutableListOf<DhikrCategory>()

        for (cat in categories) {
            if (cat.name == "أذكار الصباح" || cat.name == "أذكار المساء") continue

            val name = cat.name
            val group = when {
                name.contains("النوم") || name.contains("الاستيقاظ") || name.contains("الرؤيا") -> "النوم والاستيقاظ"
                name.contains("المسجد") || name.contains("الأذان") || name.contains("الصلاة") ||
                    name.contains("السجود") || name.contains("الركوع") || name.contains("التشهد") ||
                    name.contains("الوتر") || name.contains("الاستفتاح") || name.contains("الاستخارة") ||
                    name.contains("السلام من") -> "المسجد والصلاة"
                name.contains("الوضوء") || name.contains("الخلاء") -> "الطهارة"
                name.contains("المنزل") || name.contains("القرية") || name.contains("دخول") -> "المنزل"
                name.contains("الطعام") || name.contains("الأكل") || name.contains("الشراب") ||
                    name.contains("الشرب") -> "الطعام والشراب"
                name.contains("السفر") || name.contains("الركوب") || name.contains("المسافر") ||
                    name.contains("الرجوع من") -> "السفر"
                name.contains("المطر") || name.contains("الاستسقاء") || name.contains("الرعد") ||
                    name.contains("الريح") || name.contains("الرياح") || name.contains("الهلال") ||
                    name.contains("رؤية") -> "الطبيعة"
                name.contains("الحج") || name.contains("العمرة") || name.contains("عرفة") ||
                    name.contains("الصفا") || name.contains("المشعر") || name.contains("الجمار") ||
                    name.contains("الركن") || name.contains("التلبية") -> "الحج والعمرة"
                name.contains("المرض") || name.contains("المريض") || name.contains("عيادة") ||
                    name.contains("الميت") || name.contains("الموت") || name.contains("القبر") ||
                    name.contains("الجنازة") || name.contains("الدفن") || name.contains("زيارة القبور") ||
                    name.contains("المحتضر") || name.contains("التعزية") || name.contains("المصيبة") ||
                    name.contains("إغماض") -> "المرض والموت"
                name.contains("الثوب") || name.contains("لبس") -> "اللباس"
                name.contains("السوق") || name.contains("المجالس") || name.contains("المجلس") -> "الأسواق والمجالس"
                name.contains("الكرب") || name.contains("الهم") || name.contains("الحزن") ||
                    name.contains("الضيق") || name.contains("الدين") || name.contains("الوسوسة") ||
                    name.contains("الذنب") || name.contains("الخوف") || name.contains("الغضب") -> "الكرب والهم"
                name.contains("الخير") || name.contains("الفضل") || name.contains("التسبيح") ||
                    name.contains("الذكر") -> "عامة"
                else -> null
            }

            if (group != null) {
                groups.getOrPut(group) { mutableListOf() }.add(cat)
            } else {
                misc.add(cat)
            }
        }

        if (misc.isNotEmpty()) {
            groups["متنوعة"] = misc
        }

        val result = mutableListOf<CategoryGroupedItem>()
        for ((title, cats) in groups) {
            result.add(CategoryGroupedItem.Header(title))
            for (cat in cats) {
                result.add(CategoryGroupedItem.Item(cat))
            }
        }
        return result
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
