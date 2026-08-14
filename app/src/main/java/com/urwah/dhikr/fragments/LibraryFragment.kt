package com.urwah.dhikr.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.urwah.dhikr.ShamelaAuthorAdapter
import com.urwah.dhikr.ShamelaBookListAdapter
import com.urwah.dhikr.ShamelaBookListActivity
import com.urwah.dhikr.ShamelaBookReaderActivity
import com.urwah.dhikr.ShamelaBookStorage
import com.urwah.dhikr.ShamelaCatalogReader
import com.urwah.dhikr.ShamelaOnlineReader
import com.urwah.dhikr.DownloadState
import com.urwah.dhikr.DownloadStatus
import com.urwah.dhikr.ShamelaBookDownloader
import com.urwah.dhikr.R
import com.urwah.dhikr.databinding.FragmentLibraryBinding
import kotlinx.coroutines.launch

class LibraryFragment : Fragment(), com.urwah.dhikr.CircularMenuProvider {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var rvCategories: RecyclerView
    private lateinit var rvSearchResults: RecyclerView
    private lateinit var searchBar: View
    private lateinit var etSearch: EditText
    private lateinit var ivClearSearch: ImageView
    private lateinit var emptySearchView: View
    private lateinit var categoryAdapter: com.urwah.dhikr.ShamelaCategoryAdapter
    private lateinit var searchAdapter: ShamelaBookListAdapter
    private lateinit var authorAdapter: ShamelaAuthorAdapter
    private lateinit var downloadedAdapter: ShamelaBookListAdapter
    private lateinit var recentAdapter: ShamelaBookListAdapter
    private lateinit var bookmarksAdapter: ShamelaBookListAdapter

    private var currentMode = MODE_CATEGORIES
    private var isSearchOpen = false

    // Debounce + توليد رقم لكل عملية بحث: تُطبَّق نتيجة أحدث استعلام فقط ولا
    // تصل نتيجة قديمة بعد جديدة (منع عرض نتائج خاطئة أثناء الكتابة السريعة).
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchVersion = 0
    private val searchDebounce = Runnable { runSearch() }

    companion object {
        const val MODE_CATEGORIES = 0
        const val MODE_AUTHORS = 1
        const val MODE_DOWNLOADED = 2
        const val MODE_RECENT = 3
        const val MODE_FAVORITES = 4
        const val MODE_BOOKMARKS = 5
        private const val PREFS_NAME = "urwah_library"
        private const val KEY_LAST_MODE = "last_mode"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvCategories = view.findViewById(R.id.rvCategories)
        rvSearchResults = view.findViewById(R.id.rvSearchResults)
        searchBar = view.findViewById(R.id.searchBar)
        etSearch = view.findViewById(R.id.etSearch)
        ivClearSearch = view.findViewById(R.id.ivClearSearch)
        emptySearchView = view.findViewById(R.id.emptySearchView)

        val catalog = ShamelaCatalogReader.getCatalog(requireContext())

        rvCategories.layoutManager = LinearLayoutManager(requireContext())
        categoryAdapter = com.urwah.dhikr.ShamelaCategoryAdapter(catalog.categories) { category ->
            val intent = Intent(requireContext(), ShamelaBookListActivity::class.java)
            intent.putExtra("CATEGORY_ID", category.id)
            intent.putExtra("CATEGORY_NAME", category.name)
            startActivity(intent)
        }
        rvCategories.adapter = categoryAdapter

        authorAdapter = ShamelaAuthorAdapter(emptyList()) { author ->
            val intent = Intent(requireContext(), ShamelaBookListActivity::class.java)
            intent.putExtra("AUTHOR_NAME", author.name)
            intent.putExtra("CATEGORY_NAME", author.name)
            startActivity(intent)
        }

        downloadedAdapter = ShamelaBookListAdapter(
            books = emptyList(),
            onBookClick = { book -> openBook(book) },
            onDownloadClick = { book -> downloadBookFromSearch(book) },
            onCancelClick = { book -> cancelDownloadFromSearch(book) },
            onReadOnlineClick = { book -> openBookOnline(book) },
            onBookLongClick = { book -> showBookManagementDialog(book) }
        )

        recentAdapter = ShamelaBookListAdapter(
            books = emptyList(),
            onBookClick = { book -> openBook(book) },
            onDownloadClick = { book -> downloadBookFromSearch(book) },
            onCancelClick = { book -> cancelDownloadFromSearch(book) },
            onReadOnlineClick = { book -> openBookOnline(book) },
            onBookLongClick = { book -> showBookManagementDialog(book) }
        )

        bookmarksAdapter = ShamelaBookListAdapter(
            books = emptyList(),
            onBookClick = { book -> openBook(book) },
            onDownloadClick = { book -> downloadBookFromSearch(book) },
            onCancelClick = { book -> cancelDownloadFromSearch(book) },
            onReadOnlineClick = { book -> openBookOnline(book) },
            onBookLongClick = { book -> showBookManagementDialog(book) }
        )

        rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        searchAdapter = ShamelaBookListAdapter(
            books = emptyList(),
            onBookClick = { book -> openBook(book) },
            onDownloadClick = { book -> downloadBookFromSearch(book) },
            onCancelClick = { book -> cancelDownloadFromSearch(book) },
            onReadOnlineClick = { book -> openBookOnline(book) },
            onBookLongClick = { book -> showBookManagementDialog(book) }
        )
        rvSearchResults.adapter = searchAdapter

        view.findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        view.findViewById<ImageView>(R.id.ivSearch).setOnClickListener {
            openSearch()
        }

        view.findViewById<ImageView>(R.id.ivSearchBack).setOnClickListener {
            closeSearch()
        }

        ivClearSearch.setOnClickListener {
            etSearch.text?.clear()
            etSearch.requestFocus()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                ivClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                searchHandler.removeCallbacks(searchDebounce)
                if (query.length >= 1) {
                    searchHandler.postDelayed(searchDebounce, 180L)
                } else {
                    searchVersion++
                    clearSearchResults()
                }
            }
        })

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                true
            } else false
        }

        val savedMode = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LAST_MODE, MODE_CATEGORIES)
        switchMode(savedMode)
    }

    private fun openSearch() {
        isSearchOpen = true
        searchBar.visibility = View.VISIBLE
        etSearch.requestFocus()
        showKeyboard()
        val query = etSearch.text?.toString() ?: ""
        if (query.length >= 1) {
            runSearch()
        } else {
            rvCategories.visibility = View.GONE
            rvSearchResults.visibility = View.VISIBLE
            emptySearchView.visibility = View.GONE
            searchAdapter.updateBooks(emptyList())
        }
    }

    private fun closeSearch() {
        isSearchOpen = false
        searchHandler.removeCallbacks(searchDebounce)
        searchVersion++
        searchBar.visibility = View.GONE
        rvSearchResults.visibility = View.GONE
        emptySearchView.visibility = View.GONE
        etSearch.text?.clear()
        hideKeyboard()
        showCurrentMode()
    }

    private fun runSearch() {
        val query = etSearch.text?.toString() ?: ""
        if (query.length < 1) {
            searchVersion++
            clearSearchResults()
            return
        }
        val version = ++searchVersion
        when (currentMode) {
            MODE_AUTHORS -> {
                val authors = ShamelaCatalogReader.getAllAuthors(requireContext())
                    .filter { it.name.contains(query, ignoreCase = true) }
                if (version != searchVersion) return // نتيجة قديمة بعد أحدث استعلام
                if (authors.isNotEmpty()) {
                    authorAdapter.updateAuthors(authors)
                    rvCategories.adapter = authorAdapter
                    rvCategories.visibility = View.VISIBLE
                    rvSearchResults.visibility = View.GONE
                    emptySearchView.visibility = View.GONE
                } else {
                    rvCategories.visibility = View.GONE
                    rvSearchResults.visibility = View.GONE
                    emptySearchView.visibility = View.VISIBLE
                }
            }
            else -> {
                val results = ShamelaCatalogReader.searchBooks(requireContext(), query)
                if (version != searchVersion) return
                searchAdapter.updateBooks(results)
                if (results.isEmpty()) {
                    emptySearchView.visibility = View.VISIBLE
                    rvSearchResults.visibility = View.GONE
                    rvCategories.visibility = View.GONE
                } else {
                    emptySearchView.visibility = View.GONE
                    rvSearchResults.visibility = View.VISIBLE
                    rvCategories.visibility = View.GONE
                }
            }
        }
    }

    private fun clearSearchResults() {
        searchAdapter.updateBooks(emptyList())
        rvSearchResults.visibility = View.VISIBLE
        rvCategories.visibility = View.GONE
        emptySearchView.visibility = View.GONE
    }

    private fun openBook(book: com.urwah.dhikr.ShamelaBook) {
        if (ShamelaBookStorage.isBookDownloaded(requireContext(), book.id)) {
            val intent = Intent(requireContext(), ShamelaBookReaderActivity::class.java)
            intent.putExtra("BOOK_ID", book.id)
            intent.putExtra("BOOK_TITLE", book.title)
            startActivity(intent)
        } else {
            Toast.makeText(requireContext(), "الكتاب غير محمل", Toast.LENGTH_SHORT).show()
        }
    }

    /** يفتح الكتاب مباشرةً من الإنترنت دون تحميل مسبق. */
    private fun openBookOnline(book: com.urwah.dhikr.ShamelaBook) {
        if (ShamelaBookStorage.isBookDownloaded(requireContext(), book.id)) {
            openBook(book)
            return
        }
        if (!ShamelaOnlineReader.isNetworkAvailable(requireContext())) {
            Toast.makeText(requireContext(), "لا يوجد اتصال بالإنترنت لقراءة الكتاب مباشرة، حمّله أولًا", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(requireContext(), ShamelaBookReaderActivity::class.java)
        intent.putExtra("BOOK_ID", book.id)
        intent.putExtra("BOOK_TITLE", book.title)
        intent.putExtra("ONLINE_MODE", true)
        intent.putExtra("BOOK_HF_PATH", book.hfPath)
        startActivity(intent)
    }

    private fun downloadBookFromSearch(book: com.urwah.dhikr.ShamelaBook) {
        if (ShamelaBookStorage.isBookDownloaded(requireContext(), book.id)) {
            openBook(book)
            return
        }
        searchAdapter.updateDownloadState(book.id, DownloadState(book.id, 0f, DownloadStatus.DOWNLOADING))

        lifecycleScope.launch {
            ShamelaBookDownloader.downloadBook(
                context = requireContext(),
                book = book,
                listener = object : ShamelaBookDownloader.DownloadListener {
                    override fun onProgress(progress: Float) {
                        activity?.runOnUiThread {
                            searchAdapter.updateDownloadState(
                                book.id,
                                DownloadState(book.id, progress, DownloadStatus.DOWNLOADING)
                            )
                        }
                    }

                    override fun onComplete(success: Boolean, error: String?) {
                        activity?.runOnUiThread {
                            if (success) {
                                searchAdapter.updateDownloadState(
                                    book.id,
                                    DownloadState(book.id, 1f, DownloadStatus.DOWNLOADED)
                                )
                                Toast.makeText(requireContext(), "تم تحميل ${book.title}", Toast.LENGTH_SHORT).show()
                            } else {
                                searchAdapter.updateDownloadState(
                                    book.id,
                                    DownloadState(book.id, 0f, DownloadStatus.FAILED, error)
                                )
                                Toast.makeText(requireContext(), "فشل التحميل: ${error ?: "خطأ غير معروف"}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        }
    }

    private fun cancelDownloadFromSearch(book: com.urwah.dhikr.ShamelaBook) {
        ShamelaBookDownloader.cancelDownload()
        searchAdapter.updateDownloadState(book.id, DownloadState(book.id, 0f, DownloadStatus.NOT_DOWNLOADED))
    }

    private fun showBookManagementDialog(book: com.urwah.dhikr.ShamelaBook) {
        if (!ShamelaBookStorage.isBookDownloaded(requireContext(), book.id)) return
        val dialog = com.urwah.dhikr.BookActionDialog(requireContext(), book, "downloaded") {
            switchMode(currentMode)
        }
        dialog.show()
    }

    fun switchMode(mode: Int) {
        currentMode = mode
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_LAST_MODE, mode).apply()

        if (isSearchOpen) {
            val query = etSearch.text?.toString() ?: ""
            if (query.length >= 1) {
                runSearch()
            }
            return
        }

        when (mode) {
            MODE_CATEGORIES -> {
                val catalog = ShamelaCatalogReader.getCatalog(requireContext())
                categoryAdapter = com.urwah.dhikr.ShamelaCategoryAdapter(catalog.categories) { category ->
                    val intent = Intent(requireContext(), ShamelaBookListActivity::class.java)
                    intent.putExtra("CATEGORY_ID", category.id)
                    intent.putExtra("CATEGORY_NAME", category.name)
                    startActivity(intent)
                }
                rvCategories.adapter = categoryAdapter
                showCurrentMode()
            }
            MODE_AUTHORS -> {
                val authors = ShamelaCatalogReader.getAllAuthors(requireContext())
                authorAdapter.updateAuthors(authors)
                rvCategories.adapter = authorAdapter
                showCurrentMode()
            }
            MODE_DOWNLOADED -> {
                val books = ShamelaBookStorage.getDownloadedBooksWithMeta(requireContext())
                downloadedAdapter.updateBooks(books)
                rvCategories.adapter = downloadedAdapter
                showCurrentMode()
            }
            MODE_RECENT -> {
                val books = ShamelaBookStorage.getRecentlyReadBooks(requireContext())
                recentAdapter.updateBooks(books)
                rvCategories.adapter = recentAdapter
                showCurrentMode()
            }
            MODE_FAVORITES -> {
                showCurrentMode()
            }
            MODE_BOOKMARKS -> {
                val books = ShamelaBookStorage.getBookmarkedBooksWithMeta(requireContext())
                bookmarksAdapter.updateBooks(books)
                rvCategories.adapter = bookmarksAdapter
                showCurrentMode()
            }
        }
    }

    private fun showCurrentMode() {
        rvCategories.visibility = View.VISIBLE
        rvSearchResults.visibility = View.GONE
        emptySearchView.visibility = View.GONE
    }

    private fun showKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
    }

    override fun setupCircularMenu(menu: com.urwah.dhikr.UrwahCircularMenu) {
        menu.addMenuItem(R.drawable.ic_search, "بحث في الكتب") {
            openSearch()
        }
        menu.addMenuItem(R.drawable.ic_book_categories, "حسب الأقسام") {
            switchMode(MODE_CATEGORIES)
        }
        menu.addMenuItem(R.drawable.ic_authors, "حسب المؤلفين") {
            switchMode(MODE_AUTHORS)
        }
        menu.addMenuItem(R.drawable.ic_bookmarks, "العلامات") {
            switchMode(MODE_BOOKMARKS)
        }
        menu.addMenuItem(R.drawable.ic_statistics, "آخر قراءة") {
            switchMode(MODE_RECENT)
        }
        menu.addMenuItem(R.drawable.ic_downloaded_books, "المحملة") {
            switchMode(MODE_DOWNLOADED)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
