package com.urwah.dhikr

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class BookmarksActivity : AppCompatActivity() {

    private lateinit var rvBookmarks: RecyclerView
    private lateinit var tvEmpty: TextView
    private var adapter: BookmarkListAdapter? = null
    private var bookmarksList = listOf<SmartBookmark>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmarks)

        rvBookmarks = findViewById(R.id.rvBookmarks)
        tvEmpty = findViewById(R.id.tvEmpty)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        rvBookmarks.layoutManager = LinearLayoutManager(this)

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.adapterPosition
                if (pos < 0 || pos >= bookmarksList.size) {
                    adapter?.notifyItemChanged(pos)
                    return
                }
                val bm = bookmarksList[pos]
                showDeleteConfirmation(bm, pos)
            }

            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                val content = vh.itemView.findViewById<View>(R.id.bookmarkContent)
                if (content != null) {
                    content.translationX = dX.coerceIn(-content.width.toFloat(), content.width.toFloat())
                }
                val bg = vh.itemView.findViewById<View>(R.id.deleteBackground)
                if (bg != null && content != null) {
                    val absDx = if (dX < 0) -dX else dX
                    val fraction = minOf(absDx / content.width.toFloat(), 1f)
                    bg.alpha = fraction * 0.6f
                }
            }
        })
        touchHelper.attachToRecyclerView(rvBookmarks)

        loadBookmarks()
    }

    private fun showDeleteConfirmation(bm: SmartBookmark, pos: Int) {
        adapter?.notifyItemChanged(pos)

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_delete_bookmark, null)
        dialogView.findViewById<TextView>(R.id.tvDeleteMessage).text =
            "حذف العلامة \"${bm.name}\"؟"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.btnDeleteConfirm).setOnClickListener {
            BookmarkManager.delete(this, bm.name)
            dialog.dismiss()
            loadBookmarks()
        }
        dialogView.findViewById<View>(R.id.btnDeleteCancel).setOnClickListener {
            dialog.dismiss()
            loadBookmarks()
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        loadBookmarks()
    }

    private fun loadBookmarks() {
        bookmarksList = BookmarkManager.getAll(this)
        if (bookmarksList.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvBookmarks.visibility = View.GONE
            return
        }
        tvEmpty.visibility = View.GONE
        rvBookmarks.visibility = View.VISIBLE
        adapter = BookmarkListAdapter(bookmarksList) { bm ->
            val intent = Intent(this, SurahDetailActivity::class.java).apply {
                putExtra("SURAH_NUMBER", bm.surahNumber)
                putExtra("SURAH_NAME", bm.surahName)
                putExtra("VERSE_COUNT", SurahDataProvider.allSurahs.find { it.number == bm.surahNumber }?.verseCount ?: 0)
                putExtra("LAST_AYAH", bm.ayahNumber)
            }
            startActivity(intent)
        }
        rvBookmarks.adapter = adapter
    }

    private class BookmarkListAdapter(
        private val items: List<SmartBookmark>,
        private val onClick: (SmartBookmark) -> Unit
    ) : RecyclerView.Adapter<BookmarkListAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val colorDot: View = view.findViewById(R.id.colorDot)
            val tvName: TextView = view.findViewById(R.id.tvBookmarkName)
            val tvLocation: TextView = view.findViewById(R.id.tvBookmarkLocation)
        }

        override fun onCreateViewHolder(p: ViewGroup, vt: Int): VH {
            val v = LayoutInflater.from(p.context).inflate(R.layout.item_bookmark, p, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val bm = items[pos]
            h.colorDot.setBackgroundColor(bm.color)
            h.tvName.text = bm.name
            val ayahNum = bm.ayahNumber
            val hindiDigits = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
            val ayahStr = ayahNum.toString().map { hindiDigits[it - '0'] }.joinToString("")
            h.tvLocation.text = "${bm.surahName} • الآية $ayahStr"
            h.itemView.setOnClickListener { onClick(bm) }
        }
    }
}
