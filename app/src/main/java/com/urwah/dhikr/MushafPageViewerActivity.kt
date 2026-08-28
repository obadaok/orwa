package com.urwah.dhikr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MushafPageViewerActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tvPageNumber: TextView
    private lateinit var progressBar: ProgressBar
    private var riwayatId: String = "hisham"
    private var imageBaseUrl: String = ""
    private var currentPage: Int = 1
    private val TOTAL_PAGES = 604

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mushaf_page_viewer)

        riwayatId = intent.getStringExtra("riwayat_id") ?: "hisham"
        imageBaseUrl = intent.getStringExtra("image_base_url") ?: ""
        currentPage = intent.getIntExtra("page", 1)

        viewPager = findViewById(R.id.viewPager)
        tvPageNumber = findViewById(R.id.tvPageNumber)
        progressBar = findViewById(R.id.progressBar)

        val riwayatInfo = QuranDataLoader.getRiwayatInfo(riwayatId)
        findViewById<TextView>(R.id.tvTitle).text = riwayatInfo.arabicName

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        setupViewPager()
        updatePageInfo(currentPage)
    }

    private fun setupViewPager() {
        val adapter = MushafPageAdapter(imageBaseUrl, TOTAL_PAGES, cacheDir())
        viewPager.adapter = adapter
        viewPager.setCurrentItem(currentPage - 1, false)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position + 1
                updatePageInfo(currentPage)
            }
        })
    }

    private fun cacheDir(): File {
        val dir = File(cacheDir, "mushaf_pages/$riwayatId")
        dir.mkdirs()
        return dir
    }

    private fun updatePageInfo(page: Int) {
        tvPageNumber.text = "صفحة $page"
    }

    class MushafPageAdapter(
        private val baseUrl: String,
        private val totalPages: Int,
        private val cacheDir: File
    ) : RecyclerView.Adapter<MushafPageAdapter.PageViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_mushaf_page, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            holder.bind(position + 1, baseUrl, cacheDir)
        }

        override fun getItemCount(): Int = totalPages

        class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val imageView: ImageView = itemView.findViewById(R.id.ivPage)
            private val progressBar: ProgressBar = itemView.findViewById(R.id.pageProgress)
            private val scope = CoroutineScope(Dispatchers.Main)

            fun bind(pageNum: Int, baseUrl: String, cacheDir: File) {
                progressBar.visibility = View.VISIBLE
                imageView.setImageDrawable(null)

                val cachedFile = File(cacheDir, "page_$pageNum.png")
                if (cachedFile.exists()) {
                    loadFromFile(cachedFile, imageView, progressBar)
                    return
                }

                scope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        downloadPage(pageNum, baseUrl, cacheDir)
                    }
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    }
                    progressBar.visibility = View.GONE
                }
            }

            private fun loadFromFile(file: File, iv: ImageView, pb: ProgressBar) {
                scope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    }
                    iv.setImageBitmap(bitmap)
                    pb.visibility = View.GONE
                }
            }

            private fun downloadPage(pageNum: Int, baseUrl: String, cacheDir: File): Bitmap? {
                return try {
                    val url = URL("$baseUrl/page_$pageNum.png")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    conn.connect()

                    val inputStream = conn.inputStream
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()

                    if (bitmap != null) {
                        val cacheFile = File(cacheDir, "page_$pageNum.png")
                        FileOutputStream(cacheFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    }
                    conn.disconnect()
                    bitmap
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}