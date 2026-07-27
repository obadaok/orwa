package com.urwah.dhikr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class ShamelaBookListActivity : AppCompatActivity() {

    private lateinit var rvBooks: RecyclerView
    private lateinit var adapter: ShamelaBookListAdapter
    private lateinit var searchBar: View
    private lateinit var etSearch: EditText
    private lateinit var ivClearSearch: ImageView
    private lateinit var emptySearchView: View
    private lateinit var searchDivider: View
    private lateinit var tvCategoryTitle: TextView
    private lateinit var tvBookCount: TextView

    private var categoryId = 0
    private var categoryName = ""
    private var authorName = ""
    private var allBooks: List<ShamelaBook> = emptyList()
    private var isSearchOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shamela_book_list)

        categoryId = intent.getIntExtra("CATEGORY_ID", 0)
        categoryName = intent.getStringExtra("CATEGORY_NAME") ?: ""
        authorName = intent.getStringExtra("AUTHOR_NAME") ?: ""

        tvCategoryTitle = findViewById(R.id.tvCategoryTitle)
        tvBookCount = findViewById(R.id.tvBookCount)
        searchBar = findViewById(R.id.searchBar)
        etSearch = findViewById(R.id.etSearch)
        ivClearSearch = findViewById(R.id.ivClearSearch)
        emptySearchView = findViewById(R.id.emptySearchView)
        searchDivider = findViewById(R.id.searchDivider)

        tvCategoryTitle.text = categoryName

        allBooks = if (authorName.isNotBlank()) {
            ShamelaCatalogReader.getBooksByAuthor(this, authorName)
        } else {
            ShamelaCatalogReader.getBooksByCategory(this, categoryId)
        }
        tvBookCount.text = "${allBooks.size} كتاب"

        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<ImageView>(R.id.ivSearch).setOnClickListener {
            openSearch()
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
                if (query.length >= 1) {
                    filterBooks(query)
                } else {
                    showAllBooks()
                }
            }
        })

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
                true
            } else false
        }

        rvBooks = findViewById(R.id.rvBooks)
        rvBooks.layoutManager = LinearLayoutManager(this)

        adapter = ShamelaBookListAdapter(
            books = allBooks,
            onBookClick = { book -> openBook(book) },
            onDownloadClick = { book -> downloadBook(book) },
            onCancelClick = { book -> cancelDownload(book) },
            onBookLongClick = { book -> showBookManagementDialog(book) }
        )
        rvBooks.adapter = adapter
    }

    private fun openSearch() {
        isSearchOpen = true
        searchBar.visibility = View.VISIBLE
        searchDivider.visibility = View.VISIBLE
        etSearch.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun filterBooks(query: String) {
        val results = allBooks.filter { book ->
            book.title.contains(query, ignoreCase = true) ||
            book.author.contains(query, ignoreCase = true)
        }
        adapter.updateBooks(results)
        tvBookCount.text = "${results.size} كتاب"
        if (results.isEmpty()) {
            emptySearchView.visibility = View.VISIBLE
            rvBooks.visibility = View.GONE
        } else {
            emptySearchView.visibility = View.GONE
            rvBooks.visibility = View.VISIBLE
        }
    }

    private fun showAllBooks() {
        adapter.updateBooks(allBooks)
        tvBookCount.text = "${allBooks.size} كتاب"
        emptySearchView.visibility = View.GONE
        rvBooks.visibility = View.VISIBLE
    }

    private fun openBook(book: ShamelaBook) {
        val state = adapter.getDownloadState(book.id)
        when {
            state?.status == DownloadStatus.DOWNLOADING -> {
                Toast.makeText(this, "لا يمكن فتح الكتاب قبل اكتمال التحميل", Toast.LENGTH_SHORT).show()
            }
            state?.status == DownloadStatus.FAILED -> {
                Toast.makeText(this, "فشل التحميل، أعد المحاولة", Toast.LENGTH_SHORT).show()
            }
            ShamelaBookStorage.isBookDownloaded(this, book.id) -> {
                val intent = Intent(this, ShamelaBookReaderActivity::class.java)
                intent.putExtra("BOOK_ID", book.id)
                intent.putExtra("BOOK_TITLE", book.title)
                startActivity(intent)
            }
            else -> {
                downloadBook(book)
            }
        }
    }

    private fun downloadBook(book: ShamelaBook) {
        adapter.updateDownloadState(book.id, DownloadState(book.id, 0f, DownloadStatus.DOWNLOADING))

        lifecycleScope.launch {
            ShamelaBookDownloader.downloadBook(
                context = this@ShamelaBookListActivity,
                book = book,
                listener = object : ShamelaBookDownloader.DownloadListener {
                    override fun onProgress(progress: Float) {
                        runOnUiThread {
                            adapter.updateDownloadState(
                                book.id,
                                DownloadState(book.id, progress, DownloadStatus.DOWNLOADING)
                            )
                        }
                    }

                    override fun onComplete(success: Boolean, error: String?) {
                        runOnUiThread {
                            if (success) {
                                adapter.updateDownloadState(
                                    book.id,
                                    DownloadState(book.id, 1f, DownloadStatus.DOWNLOADED)
                                )
                                Toast.makeText(this@ShamelaBookListActivity, "تم تحميل ${book.title}", Toast.LENGTH_SHORT).show()
                            } else {
                                adapter.updateDownloadState(
                                    book.id,
                                    DownloadState(book.id, 0f, DownloadStatus.FAILED, error)
                                )
                                Toast.makeText(this@ShamelaBookListActivity, "فشل التحميل: ${error ?: "خطأ غير معروف"}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        }
    }

    private fun cancelDownload(book: ShamelaBook) {
        ShamelaBookDownloader.cancelDownload()
        adapter.updateDownloadState(book.id, DownloadState(book.id, 0f, DownloadStatus.NOT_DOWNLOADED))
    }

    private fun showBookManagementDialog(book: ShamelaBook) {
        if (!ShamelaBookStorage.isBookDownloaded(this, book.id)) return

        val storageBytes = ShamelaBookStorage.getBookDownloadSize(this, book.id)
        val storageText = ShamelaBookStorage.formatFileSize(storageBytes)
        val pageCount = ShamelaBookStorage.getPageCount(this, book.id)

        val message = buildString {
            appendLine("الحجم: $storageText")
            if (pageCount > 0) appendLine("الصفحات: $pageCount")
        }

        val options = arrayOf("فتح الكتاب", "إعادة التحميل", "حذف الكتاب")

        AlertDialog.Builder(this)
            .setTitle(book.title)
            .setMessage(message.trimEnd())
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openBook(book)
                    1 -> {
                        ShamelaBookStorage.deleteBook(this, book.id)
                        adapter.updateDownloadState(book.id, DownloadState(book.id, 0f, DownloadStatus.NOT_DOWNLOADED))
                        adapter.notifyDataSetChanged()
                        Toast.makeText(this, "جاري إعادة التحميل...", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        AlertDialog.Builder(this)
                            .setTitle("تأكيد الحذف")
                            .setMessage("هل تريد حذف \"${book.title}\"؟")
                            .setPositiveButton("حذف") { _, _ ->
                                ShamelaBookStorage.deleteBook(this, book.id)
                                adapter.updateDownloadState(book.id, DownloadState(book.id, 0f, DownloadStatus.NOT_DOWNLOADED))
                                adapter.notifyDataSetChanged()
                                Toast.makeText(this, "تم حذف الكتاب", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("إلغاء", null)
                            .show()
                    }
                }
            }
            .show()
    }
}
