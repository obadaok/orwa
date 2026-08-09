package com.urwah.dhikr

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class BookActionDialog(
    context: Context,
    private val book: ShamelaBook,
    private val mode: String,
    private val onDataChanged: () -> Unit
) : Dialog(context, R.style.BookActionDialog) {

    private val actions = mutableListOf<ActionItem>()

    data class ActionItem(
        val label: String,
        val onClick: () -> Unit
    )

    init {
        setContentView(R.layout.dialog_book_action)
        setCanceledOnTouchOutside(true)
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        bindInfo()
        buildActions()
        renderActions()
    }

    private fun bindInfo() {
        val tvTitle = findViewById<TextView>(R.id.tvBookTitle)!!
        val tvAuthor = findViewById<TextView>(R.id.tvBookAuthor)!!
        val tvLastRead = findViewById<TextView>(R.id.tvLastRead)!!

        tvTitle.text = book.title
        tvAuthor.text = book.displayAuthor

        val lastRead = ShamelaBookStorage.getLastReadTime(context, book.id)
        val lastPage = ShamelaBookStorage.getLastReadPage(context, book.id)
        if (lastRead > 0L) {
            val elapsed = formatElapsedTime(lastRead)
            val pageText = if (lastPage > 0) " — صفحة $lastPage" else ""
            tvLastRead.text = "آخر قراءة: $elapsed$pageText"
            tvLastRead.visibility = View.VISIBLE
        }

        if (book.displayAuthor.isBlank()) {
            tvAuthor.visibility = View.GONE
        }
    }

    private fun buildActions() {
        actions.add(ActionItem("فتح الكتاب") { openBook() })
        actions.add(ActionItem("متابعة القراءة") { continueReading() })
        actions.add(ActionItem("معلومات الكتاب") { showBookInfo() })
        actions.add(ActionItem("إعادة تحميل الكتاب") { reloadBook() })
        actions.add(ActionItem("التحقق من وجود تحديث") { checkForUpdate() })
        actions.add(ActionItem("مشاركة الكتاب") { shareBook() })
        actions.add(ActionItem(if (isFavorite()) "إزالة من المفضلة" else "إضافة إلى المفضلة") { toggleFavorite() })
        actions.add(ActionItem("حذف الكتاب") { deleteBook() })
    }

    private fun renderActions() {
        val container = findViewById<LinearLayout>(R.id.actionContainer) ?: return
        container.removeAllViews()
        val inflater = LayoutInflater.from(context)

        for ((i, action) in actions.withIndex()) {
            val row = inflater.inflate(R.layout.item_dialog_action, container, false)
            val tvLabel = row.findViewById<TextView>(R.id.tvActionLabel)
            tvLabel.text = action.label
            row.setOnClickListener {
                action.onClick()
            }
            if (i < actions.size - 1) {
                val divider = View(context)
                divider.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1
                ).apply { marginStart = 48; marginEnd = 0 }
                divider.setBackgroundColor(context.getColor(R.color.sh_divider_line))
                (container as ViewGroup).addView(divider)
            }
            container.addView(row)
        }
    }

    private fun openBook() {
        dismiss()
        val intent = Intent(context, ShamelaBookReaderActivity::class.java)
        intent.putExtra("BOOK_ID", book.id)
        intent.putExtra("BOOK_TITLE", book.title)
        context.startActivity(intent)
    }

    private fun continueReading() {
        dismiss()
        val lastPage = ShamelaBookStorage.getLastReadPage(context, book.id)
        val intent = Intent(context, ShamelaBookReaderActivity::class.java).apply {
            putExtra("BOOK_ID", book.id)
            putExtra("BOOK_TITLE", book.title)
            putExtra("GO_TO_PAGE", lastPage)
        }
        context.startActivity(intent)
    }

    private fun showBookInfo() {
        dismiss()
        val infoDialog = BookInfoDialog(context, book)
        infoDialog.show()
    }

    private fun reloadBook() {
        dismiss()
        deleteBookFiles()
        val appCtx = context.applicationContext
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        Toast.makeText(appCtx, "جاري إعادة تحميل ${book.title}", Toast.LENGTH_SHORT).show()
        Executors.newSingleThreadExecutor().execute {
            val success = runBlocking {
                ShamelaBookDownloader.downloadBook(appCtx, book)
            }
            handler.post {
                if (success) {
                    Toast.makeText(appCtx, "تم إعادة تحميل ${book.title}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(appCtx, "فشل إعادة التحميل", Toast.LENGTH_SHORT).show()
                }
                onDataChanged()
            }
        }
    }

    private fun checkForUpdate() {
        fetchLatestVersion { latestVersion, error ->
            if (!isShowing && latestVersion == null) return@fetchLatestVersion
            if (latestVersion != null) {
                val currentVersion = book.version
                if (latestVersion != currentVersion && latestVersion.isNotBlank()) {
                    showVersionDialog(currentVersion, latestVersion)
                } else {
                    Toast.makeText(context, "الكتاب محدث بالفعل (الإصدار $currentVersion)", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, error ?: "تعذر فحص التحديثات", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareBook() {
        dismiss()
        val shareText = "📖 ${book.title}\n${book.displayAuthor}\n\nمن تطبيق عروة"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة الكتاب"))
    }

    private fun toggleFavorite() {
        dismiss()
        if (isFavorite()) {
            removeFavorite()
            Toast.makeText(context, "تمت إزالة الكتاب من المفضلة", Toast.LENGTH_SHORT).show()
        } else {
            addFavorite()
            Toast.makeText(context, "تمت إضافة الكتاب إلى المفضلة", Toast.LENGTH_SHORT).show()
        }
        onDataChanged()
    }

    private fun deleteBook() {
        dismiss()
        val confirm = BookDeleteConfirmDialog(context, book) {
            deleteBookFiles()
            Toast.makeText(context, "تم حذف الكتاب", Toast.LENGTH_SHORT).show()
            onDataChanged()
        }
        confirm.show()
    }

    private fun deleteBookFiles() {
        ShamelaBookStorage.deleteBook(context, book.id)
    }

    private fun isFavorite(): Boolean {
        val prefs = context.getSharedPreferences("urwah_favorites", Context.MODE_PRIVATE)
        return prefs.getBoolean("fav_${book.id}", false)
    }

    private fun addFavorite() {
        val prefs = context.getSharedPreferences("urwah_favorites", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("fav_${book.id}", true).apply()
    }

    private fun removeFavorite() {
        val prefs = context.getSharedPreferences("urwah_favorites", Context.MODE_PRIVATE)
        prefs.edit().remove("fav_${book.id}").apply()
    }

    private fun formatElapsedTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(diff)
        val days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            minutes < 1 -> "الآن"
            minutes < 60 -> "منذ $minutes دقيقة"
            hours < 24 -> "منذ $hours ساعة"
            days < 7 -> "منذ $days يوم"
            days < 30 -> "منذ ${days / 7} أسبوع"
            else -> {
                val sdf = java.text.SimpleDateFormat("d MMM", java.util.Locale("ar"))
                sdf.format(java.util.Date(timestamp))
            }
        }
    }

    private fun fetchLatestVersion(callback: (String?, String?) -> Unit) {
        val urlStr = book.metadataUrl
        if (urlStr.isBlank()) {
            callback(null, "مصدر التحديثات غير متاح")
            return
        }
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        Thread {
            try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val json = reader.readText()
                reader.close()
                val obj = JSONObject(json)
                val remoteVersion = buildString {
                    append(obj.optInt("version_major", 1))
                    append(".")
                    append(obj.optInt("version_minor", 0))
                }
                handler.post { callback(remoteVersion, null) }
            } catch (e: Exception) {
                handler.post { callback(null, e.message ?: "خطأ في الاتصال") }
            }
        }.start()
    }

    private fun showVersionDialog(currentVersion: String, newVersion: String) {
        if (!isShowing) return
        val msg = "الإصدار الحالي: $currentVersion\nالإصدار الجديد: $newVersion"
        val dialog = BookCustomConfirmDialog(
            context,
            "تحديث متوفر",
            msg,
            positiveText = "تحديث الآن",
            negativeText = "لاحقاً",
            onPositive = {
                reloadBook()
            }
        )
        dialog.show()
    }
}
