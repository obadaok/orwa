package com.urwah.dhikr

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.NestedScrollView

class BookReaderActivity : AppCompatActivity() {

    private lateinit var scrollView: NestedScrollView
    private lateinit var tvBookTitle: android.widget.TextView
    private lateinit var tvChapterTitle: android.widget.TextView
    private lateinit var tvChapterContent: android.widget.TextView
    private lateinit var tvProgress: android.widget.TextView
    private lateinit var readingProgress: SeekBar
    private lateinit var settingsPanel: View
    private lateinit var fontSizeSlider: SeekBar
    private lateinit var lineSpacingSlider: SeekBar
    private lateinit var tvFontSizeValue: android.widget.TextView
    private lateinit var tvLineSpacingValue: android.widget.TextView
    private lateinit var searchBar: View
    private lateinit var etSearch: android.widget.EditText
    private lateinit var ivSearch: android.widget.ImageView
    private lateinit var ivClearSearch: android.widget.ImageView

    private var bookId = ""
    private var bookTitle = ""
    private var bookContent: BookContent? = null
    private var currentChapterIndex = 0
    private var fontSize = 18f
    private var lineSpacing = 1.6f

    private var scrollSaveHandler: Handler? = null
    private var scrollSaveRunnable: Runnable? = null
    private var lastQuery = ""
    private var pendingScrollY = 0
    private var lastDisplayedChapterIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_reader)

        bookId = intent.getStringExtra("BOOK_ID") ?: ""
        bookTitle = intent.getStringExtra("BOOK_TITLE") ?: ""

        scrollView = findViewById(R.id.scrollView)
        tvBookTitle = findViewById(R.id.tvBookTitle)
        tvChapterTitle = findViewById(R.id.tvChapterTitle)
        tvChapterContent = findViewById(R.id.tvChapterContent)
        tvProgress = findViewById(R.id.tvProgress)
        readingProgress = findViewById(R.id.readingProgress)
        settingsPanel = findViewById(R.id.settingsPanel)
        fontSizeSlider = findViewById(R.id.fontSizeSlider)
        lineSpacingSlider = findViewById(R.id.lineSpacingSlider)
        tvFontSizeValue = findViewById(R.id.tvFontSizeValue)
        tvLineSpacingValue = findViewById(R.id.tvLineSpacingValue)
        searchBar = findViewById(R.id.searchBar)
        etSearch = findViewById(R.id.etSearch)
        ivSearch = findViewById(R.id.ivSearch)
        ivClearSearch = findViewById(R.id.ivClearSearch)

        tvBookTitle.text = bookTitle

        scrollSaveHandler = Handler(Looper.getMainLooper())

        loadSettings()
        loadBookContent()
        updateChapterDisplay()

        setupToolbar()
        setupScrollProgress()
        setupFontControls()
        setupSearch()

        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val content = scrollView.getChildAt(0) ?: return@setOnScrollChangeListener
            val maxScroll = (content.height - scrollView.height).coerceAtLeast(1)
            readingProgress.progress = (scrollY * 1000 / maxScroll).coerceIn(0, 1000)
            scheduleScrollSave()
        }
    }

    private fun setupToolbar() {
        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener {
            saveReadingProgress()
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<android.widget.ImageButton>(R.id.btnSettings).setOnClickListener {
            settingsPanel.visibility = if (settingsPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        ivSearch.setOnClickListener {
            searchBar.visibility = View.VISIBLE
            etSearch.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
        }

        ivClearSearch.setOnClickListener {
            etSearch.text?.clear()
        }

        findViewById<android.widget.ImageButton>(R.id.btnSearchBack).setOnClickListener {
            hideSearch()
        }

        findViewById<android.widget.TextView>(R.id.btnPrevChapter).setOnClickListener {
            if (currentChapterIndex > 0) {
                saveReadingProgress()
                currentChapterIndex--
                updateChapterDisplay()
            }
        }

        findViewById<android.widget.TextView>(R.id.btnNextChapter).setOnClickListener {
            val chapters = bookContent?.chapters ?: emptyList()
            if (currentChapterIndex < chapters.size - 1) {
                saveReadingProgress()
                currentChapterIndex++
                updateChapterDisplay()
            }
        }
    }

    private fun setupScrollProgress() {
        readingProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val content = scrollView.getChildAt(0) ?: return
                val maxScroll = (content.height - scrollView.height).coerceAtLeast(0)
                val targetY = (progress * maxScroll / 1000).coerceIn(0, maxScroll)
                scrollView.scrollTo(0, targetY)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupFontControls() {
        fontSizeSlider.progress = ((fontSize - 12f) / (32f - 12f) * 100).toInt()
        tvFontSizeValue.text = "${fontSize.toInt()}sp"

        fontSizeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                fontSize = 12f + (progress / 100f) * (32f - 12f)
                tvFontSizeValue.text = "${fontSize.toInt()}sp"
                tvChapterContent.textSize = fontSize
                saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        lineSpacingSlider.progress = ((lineSpacing - 1.0f) / (3.0f - 1.0f) * 100).toInt()
        tvLineSpacingValue.text = String.format(java.util.Locale.US, "%.1fx", lineSpacing)

        lineSpacingSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                lineSpacing = 1.0f + (progress / 100f) * (3.0f - 1.0f)
                tvLineSpacingValue.text = String.format(java.util.Locale.US, "%.1fx", lineSpacing)
                tvChapterContent.setLineSpacing(0f, lineSpacing)
                saveSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                ivClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                if (query != lastQuery) {
                    lastQuery = query
                    highlightSearch(query)
                }
            }
        })

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideSearch()
                true
            } else false
        }
    }

    private fun highlightSearch(query: String) {
        val content = bookContent?.chapters?.getOrNull(currentChapterIndex) ?: return
        val contentBuilder = StringBuilder()
        contentBuilder.append(content.content)
        for (subheading in content.subheadings) {
            contentBuilder.append("\n\n")
            contentBuilder.append("◆ ${subheading.title}")
            contentBuilder.append("\n")
            contentBuilder.append(subheading.content)
        }
        val fullText = contentBuilder.toString()

        if (query.isBlank()) {
            tvChapterContent.text = fullText
            return
        }

        val ssb = SpannableStringBuilder(fullText)
        val lowerText = fullText.lowercase()
        val lowerQuery = query.lowercase()
        var start = 0
        while (start < lowerText.length) {
            val idx = lowerText.indexOf(lowerQuery, start)
            if (idx < 0) break
            ssb.setSpan(
                BackgroundColorSpan(Color.parseColor("#338B6F5E")),
                idx,
                idx + query.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            start = idx + 1
        }
        tvChapterContent.text = ssb
    }

    private fun hideSearch() {
        searchBar.visibility = View.GONE
        etSearch.text?.clear()
        lastQuery = ""
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
        updateChapterDisplay()
    }

    private fun loadBookContent() {
        bookContent = BookDataProvider.getBookContent(this, bookId)
        val progress = BookDataProvider.getReadingProgress(this, bookId)
        currentChapterIndex = progress.first
        pendingScrollY = progress.second
    }

    private fun updateChapterDisplay() {
        val chapters = bookContent?.chapters ?: return
        if (chapters.isEmpty()) return
        // حماية من فهرس محفوظ يتجاوز عدد الفصول الحالية (تغيّر المحتوى/البيانات)
        if (currentChapterIndex < 0 || currentChapterIndex >= chapters.size) currentChapterIndex = 0

        val chapter = chapters[currentChapterIndex]
        tvChapterTitle.text = chapter.title

        val contentBuilder = StringBuilder()
        contentBuilder.append(chapter.content)

        for (subheading in chapter.subheadings) {
            contentBuilder.append("\n\n")
            contentBuilder.append("◆ ${subheading.title}")
            contentBuilder.append("\n")
            contentBuilder.append(subheading.content)
        }

        tvChapterContent.text = contentBuilder.toString()
        tvChapterContent.textSize = fontSize
        tvChapterContent.setLineSpacing(0f, lineSpacing)

        val totalChapters = chapters.size
        tvProgress.text = "الفصل ${currentChapterIndex + 1} / $totalChapters"

        findViewById<android.widget.TextView>(R.id.btnPrevChapter).visibility =
            if (currentChapterIndex > 0) View.VISIBLE else View.INVISIBLE
        findViewById<android.widget.TextView>(R.id.btnNextChapter).visibility =
            if (currentChapterIndex < chapters.size - 1) View.VISIBLE else View.INVISIBLE

        // استعادة موضع التمرير المحفوظ عند أول عرض فقط؛ التنقل بين الفصول يبدأ من الأعلى.
        // إغلاق البحث (نفس الفصل بلا pendingScrollY) يجب ألا يمسح موضع التمرير الحالي.
        val targetY = pendingScrollY
        pendingScrollY = 0
        val chapterChanged = currentChapterIndex != lastDisplayedChapterIndex
        lastDisplayedChapterIndex = currentChapterIndex
        if (targetY > 0) {
            scrollView.post {
                val content = scrollView.getChildAt(0)
                val maxScroll = ((content?.height ?: 0) - scrollView.height).coerceAtLeast(0)
                scrollView.scrollTo(0, targetY.coerceIn(0, maxScroll))
            }
        } else if (chapterChanged) {
            scrollView.scrollTo(0, 0)
        }
    }

    private fun scheduleScrollSave() {
        scrollSaveRunnable?.let { scrollSaveHandler?.removeCallbacks(it) }
        scrollSaveRunnable = Runnable { saveReadingProgress() }
        scrollSaveHandler?.postDelayed(scrollSaveRunnable!!, 500)
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("urwah_library", Context.MODE_PRIVATE)
        fontSize = prefs.getFloat("font_size", 18f)
        lineSpacing = prefs.getFloat("line_spacing", 1.6f)
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("urwah_library", Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("font_size", fontSize)
            .putFloat("line_spacing", lineSpacing)
            .apply()
    }

    private fun saveReadingProgress() {
        val scrollY = scrollView.scrollY
        BookDataProvider.saveReadingProgress(this, bookId, currentChapterIndex, scrollY)
    }

    override fun onPause() {
        super.onPause()
        saveReadingProgress()
    }

    override fun onDestroy() {
        super.onDestroy()
        scrollSaveHandler?.removeCallbacksAndMessages(null)
        saveReadingProgress()
    }
}
