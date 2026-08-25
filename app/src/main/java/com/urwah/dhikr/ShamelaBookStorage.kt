package com.urwah.dhikr

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

object ShamelaBookStorage {

    private const val BASE_URL = "https://huggingface.co/datasets/AuthenticIlm/Shamela4_Full_DB/resolve/main"
    private const val PREFS_NAME = "urwah_shamela_downloads"
    private const val KEY_LAST_READ = "last_read_%d"
    private const val KEY_LAST_PAGE = "last_page_%d"
    private const val KEY_LAST_CHAR_OFFSET = "last_char_offset_%d"
    private const val KEY_FONT_SIZE = "font_size"
    private const val KEY_LINE_SPACING = "line_spacing"
    private const val KEY_PARA_SPACING = "para_spacing"
    private const val KEY_TEXT_ALIGN = "text_align"
    private const val KEY_MARGIN_SIZE = "margin_size"
    private const val KEY_READING_WIDTH = "reading_width"
    private const val KEY_FONT_FILE = "reader_font_file"
    private const val KEY_PAGE_COUNT = "page_count_%d"
    private const val KEY_MAX_PAGE_NUM = "max_page_num_%d"

    /** مفاتيح بأرقام لاتينية دائمًا حتى لا تنكسر عند تغيير لغة النظام (أرقام عربية شرقية). */
    private fun key(template: String, bookId: Int): String =
        String.format(Locale.US, template, bookId)

    private val pageCountCache = java.util.concurrent.ConcurrentHashMap<Int, Int>()
    private val maxPageNumCache = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    private fun getBooksDir(context: Context): File {
        val dir = File(context.filesDir, "shamela_books")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getBookDir(context: Context, bookId: Int): File {
        return File(getBooksDir(context), bookId.toString())
    }

    fun isBookDownloaded(context: Context, bookId: Int): Boolean {
        val dir = getBookDir(context, bookId)
        return dir.exists() && File(dir, "pages.jsonl").exists() && File(dir, "toc.jsonl").exists()
    }

    fun getBookDownloadSize(context: Context, bookId: Int): Long {
        val dir = getBookDir(context, bookId)
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun getDownloadState(context: Context, bookId: Int): DownloadState {
        if (isBookDownloaded(context, bookId)) {
            return DownloadState(bookId, 1f, DownloadStatus.DOWNLOADED)
        }
        return DownloadState(bookId, 0f, DownloadStatus.NOT_DOWNLOADED)
    }

    fun getBookPageUrl(bookShamelaId: Int, categoryDir: String, bookDir: String): String {
        return "$BASE_URL/$categoryDir/$bookDir/pages.jsonl"
    }

    fun getBookTocUrl(bookShamelaId: Int, categoryDir: String, bookDir: String): String {
        return "$BASE_URL/$categoryDir/$bookDir/toc.jsonl"
    }

    fun getBookMetadataUrl(categoryDir: String, bookDir: String): String {
        return "$BASE_URL/$categoryDir/$bookDir/book_metadata.json"
    }

    fun saveBookContent(context: Context, bookId: Int, pages: List<ShamelaPage>, toc: List<ShamelaTocEntry>) {
        val dir = getBookDir(context, bookId)
        if (!dir.exists()) dir.mkdirs()

        // كتابة ذرّية (tmp ثم rename): أي انقطاع/امتلاء قرص لا يترك كتابًا تالفًا
        // يبدو مكتملًا. تُكتب إحصائيات الصفحات فقط بعد نجاح الحفظ الفعلي.

        // Save pages as JSONL
        val tmpPages = File(dir, "pages.jsonl.tmp")
        tmpPages.bufferedWriter().use { writer ->
            for (page in pages) {
                val obj = JSONObject().apply {
                    put("page_id", page.pageId)
                    put("shamela_page_id", page.shamelaPageId)
                    put("part", page.part ?: JSONObject.NULL)
                    put("page_num", page.pageNum ?: JSONObject.NULL)
                    put("body", page.body)
                    put("footnotes", page.footnotes ?: JSONObject.NULL)
                }
                writer.write(obj.toString())
                writer.newLine()
            }
        }
        val pagesFile = File(dir, "pages.jsonl")
        if (pagesFile.exists()) pagesFile.delete()
        if (!tmpPages.renameTo(pagesFile)) {
            tmpPages.delete()
            throw Exception("تعذّر حفظ صفحات الكتاب على الجهاز")
        }

        // Save TOC as JSONL
        val tmpToc = File(dir, "toc.jsonl.tmp")
        tmpToc.bufferedWriter().use { writer ->
            for (entry in toc) {
                val obj = JSONObject().apply {
                    put("title_id", entry.titleId)
                    put("page_id", entry.pageId)
                    put("parent_id", entry.parentId ?: JSONObject.NULL)
                    put("title_text", entry.titleText)
                }
                writer.write(obj.toString())
                writer.newLine()
            }
        }
        val tocFile = File(dir, "toc.jsonl")
        if (tocFile.exists()) tocFile.delete()
        if (!tmpToc.renameTo(tocFile)) {
            tmpToc.delete()
            throw Exception("تعذّر حفظ فهرس الكتاب على الجهاز")
        }

        // الإحصائيات بعد نجاح كتابة الملفات (لا صفحات وهمية لكتاب فشل حفظه)
        if (pages.isNotEmpty()) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt(key(KEY_PAGE_COUNT, bookId), pages.size)
                .putInt(key(KEY_MAX_PAGE_NUM, bookId), pages.mapNotNull { it.pageNum }.maxOrNull() ?: pages.size)
                .apply()
            pageCountCache[bookId] = pages.size
            maxPageNumCache[bookId] = pages.mapNotNull { it.pageNum }.maxOrNull() ?: pages.size
        }
    }

    fun loadBookContent(context: Context, bookId: Int): ShamelaBookContent? {
        if (!isBookDownloaded(context, bookId)) return null

        val dir = getBookDir(context, bookId)

        // Load pages — سطور تالفة (حفظ قديم غير ذرّي) تُتخطى بدل تعطيل الكتاب كله
        val pages = mutableListOf<ShamelaPage>()
        File(dir, "pages.jsonl").bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                try {
                    val obj = JSONObject(line)
                    pages.add(
                        ShamelaPage(
                            pageId = obj.getInt("page_id"),
                            shamelaPageId = obj.getInt("shamela_page_id"),
                            part = if (obj.isNull("part")) null else obj.getString("part"),
                            pageNum = if (obj.isNull("page_num")) null else obj.getInt("page_num"),
                            body = obj.getString("body"),
                            footnotes = if (obj.isNull("footnotes")) null else obj.getString("footnotes")
                        )
                    )
                } catch (_: Exception) {
                }
            }
        }
        if (pages.isEmpty()) return null

        // Load TOC
        val toc = mutableListOf<ShamelaTocEntry>()
        File(dir, "toc.jsonl").bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                try {
                    val obj = JSONObject(line)
                    toc.add(
                        ShamelaTocEntry(
                            titleId = obj.getInt("title_id"),
                            pageId = obj.getInt("page_id"),
                            parentId = if (obj.isNull("parent_id")) null else obj.getInt("parent_id"),
                            titleText = obj.getString("title_text")
                        )
                    )
                } catch (_: Exception) {
                }
            }
        }

        // Load metadata
        val metaFile = File(dir, "book_metadata.json")
        val metadata = if (metaFile.exists()) {
            try {
                val obj = JSONObject(metaFile.readText())
                ShamelaBook(
                    id = obj.optInt("book_id", bookId),
                    shamelaId = obj.optInt("shamela_id", 0),
                    title = obj.optString("title_ar", ""),
                    author = obj.optString("main_author_name_ar", ""),
                    deathHijri = if (obj.has("main_author_death_hijri") && !obj.isNull("main_author_death_hijri")) obj.getInt("main_author_death_hijri") else null,
                    categoryId = obj.optInt("category_id", 0),
                    version = "${obj.optInt("version_major", 1)}.${obj.optInt("version_minor", 0)}",
                    hasMultiPart = obj.optBoolean("has_multi_part", false),
                    bookType = obj.optString("book_type_label", "كتاب")
                )
            } catch (_: Exception) {
                ShamelaBook(id = bookId, shamelaId = 0, title = "", author = "", deathHijri = null, categoryId = 0, version = "1.0", hasMultiPart = false, bookType = "كتاب")
            }
        } else {
            ShamelaBook(id = bookId, shamelaId = 0, title = "", author = "", deathHijri = null, categoryId = 0, version = "1.0", hasMultiPart = false, bookType = "كتاب")
        }

        return ShamelaBookContent(metadata = metadata, toc = toc, pages = pages)
    }

    /** يقرأ إصدار الكتاب المحفوظ محليًا من book_metadata.json إن وُجد. */
    fun getStoredVersion(context: Context, bookId: Int): String? {
        val metaFile = File(getBookDir(context, bookId), "book_metadata.json")
        if (!metaFile.exists()) return null
        return try {
            val obj = JSONObject(metaFile.readText())
            "${obj.optInt("version_major", 1)}.${obj.optInt("version_minor", 0)}"
        } catch (_: Exception) {
            null
        }
    }

    fun deleteBook(context: Context, bookId: Int): Boolean {
        val dir = getBookDir(context, bookId)
        if (dir.exists()) {
            dir.deleteRecursively()
            invalidatePageCount(bookId)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().remove(key(KEY_PAGE_COUNT, bookId)).apply()
            return true
        }
        return false
    }

    fun getDownloadedBooks(context: Context): List<Int> {
        val dir = getBooksDir(context)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()?.filter { it.isDirectory && File(it, "pages.jsonl").exists() }
            ?.mapNotNull { it.name.toIntOrNull() } ?: emptyList()
    }

    fun getTotalStorageUsed(context: Context): Long {
        val dir = getBooksDir(context)
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f", bytes / (1024.0 * 1024.0)) + " MB"
            else -> String.format(Locale.US, "%.2f", bytes / (1024.0 * 1024.0 * 1024.0)) + " GB"
        }
    }

    /**
     * عدد صفحات الكتاب. سريع:
     * - كاش ذاكرتي فوري خلال الجلسة.
     * - قيمة محفوظة تُكتب عند اكتمال التحميل (أو تُحسب أول مرة وتُحفظ).
     * - إن لزم الحساب يُمرَّر السطر تلو السطر دون بناء قائمة في الذاكرة.
     */
    fun getPageCount(context: Context, bookId: Int): Int {
        pageCountCache[bookId]?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getInt(key(KEY_PAGE_COUNT, bookId), -1)
        if (stored > -1) {
            pageCountCache[bookId] = stored
            return stored
        }
        val pagesFile = File(getBookDir(context, bookId), "pages.jsonl")
        if (!pagesFile.exists()) return 0
        var count = 0
        pagesFile.forEachLine { if (it.isNotBlank()) count++ }
        prefs.edit().putInt(key(KEY_PAGE_COUNT, bookId), count).apply()
        pageCountCache[bookId] = count
        return count
    }

    /** أكبر رقم صفحة أصلي في الكتاب (نظام الترقيم المعروض)؛ احتياطًا عدد الأسطر. */
    fun getMaxPageNum(context: Context, bookId: Int): Int {
        maxPageNumCache[bookId]?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getInt(key(KEY_MAX_PAGE_NUM, bookId), -1)
        if (stored > 0) { maxPageNumCache[bookId] = stored; return stored }
        // كتب قديمة حُفظت قبل هذا الحقل: خذ الحد الأقصى من الملف مباشرة (مرور واحد).
        val pagesFile = File(getBookDir(context, bookId), "pages.jsonl")
        if (!pagesFile.exists()) return 0
        var maxNum = 0
        pagesFile.forEachLine { line ->
            if (line.isNotBlank()) {
                val n = runCatching { JSONObject(line).optInt("page_num", -1) }.getOrDefault(-1)
                if (n > maxNum) maxNum = n
            }
        }
        if (maxNum > 0) {
            prefs.edit().putInt(key(KEY_MAX_PAGE_NUM, bookId), maxNum).apply()
            maxPageNumCache[bookId] = maxNum
        }
        return maxNum
    }

    /** بُسطات قراءة كتاب محفوظ تُعرض في قوائم المكتبة (تمنع تكرار الحساب). */
    data class BookListStats(
        val pageCount: Int,
        val lastPage: Int,
        val lastReadTime: Long,
        /** أكبر رقم صفحة أصلي (للعرض)؛ 0 لو غير متاح. */
        val maxPageNum: Int = 0
    )

    fun getBookListStats(context: Context, bookId: Int): BookListStats {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return BookListStats(
            pageCount = getPageCount(context, bookId),
            lastPage = prefs.getInt(key(KEY_LAST_PAGE, bookId), 0),
            lastReadTime = prefs.getLong(key(KEY_LAST_READ, bookId), 0L),
            maxPageNum = getMaxPageNum(context, bookId)
        )
    }

    /** يُصفّر الكاش الحسابي (بعد حذف كتاب أو إعادة تحميله). */
    fun invalidatePageCount(bookId: Int) {
        pageCountCache.remove(bookId)
        maxPageNumCache.remove(bookId)
    }

    fun getBookSizeBytes(context: Context, bookId: Int): Long {
        return getBookDownloadSize(context, bookId)
    }

    fun getLastReadPage(context: Context, bookId: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(key(KEY_LAST_PAGE, bookId), 0)
    }

    fun saveLastReadPage(context: Context, bookId: Int, page: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(key(KEY_LAST_PAGE, bookId), page).apply()
    }

    fun getLastReadCharOffset(context: Context, bookId: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(key(KEY_LAST_CHAR_OFFSET, bookId), -1)
    }

    fun saveLastReadCharOffset(context: Context, bookId: Int, charOffset: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(key(KEY_LAST_CHAR_OFFSET, bookId), charOffset).apply()
    }

    fun getLastReadTime(context: Context, bookId: Int): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(key(KEY_LAST_READ, bookId), 0L)
    }

    fun saveLastReadTime(context: Context, bookId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(key(KEY_LAST_READ, bookId), System.currentTimeMillis()).apply()
    }

    fun getFontSize(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_FONT_SIZE, 18f)
    }

    fun saveFontSize(context: Context, size: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_FONT_SIZE, size).apply()
    }

    fun getLineSpacing(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_LINE_SPACING, 1.6f)
    }

    fun saveLineSpacing(context: Context, spacing: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_LINE_SPACING, spacing).apply()
    }

    fun getParaSpacing(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_PARA_SPACING, 1.0f)
    }

    fun saveParaSpacing(context: Context, spacing: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_PARA_SPACING, spacing).apply()
    }

    fun getTextAlign(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_TEXT_ALIGN, 0)
    }

    fun saveTextAlign(context: Context, align: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_TEXT_ALIGN, align).apply()
    }

    fun getMarginSize(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_MARGIN_SIZE, 20f)
    }

    fun saveMarginSize(context: Context, size: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_MARGIN_SIZE, size).apply()
    }

    fun getReadingWidth(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_READING_WIDTH, 0.92f)
    }

    fun saveReadingWidth(context: Context, width: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_READING_WIDTH, width).apply()
    }

    fun getReaderFont(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_FONT_FILE, "amiri_regular.ttf") ?: "amiri_regular.ttf"
    }

    fun saveReaderFont(context: Context, fontFile: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FONT_FILE, fontFile).apply()
    }

    /** قراءة metadata بكتاب محدد مع حماية من ملف تالف/ناقص. */
    private fun readMetadataSafe(context: Context, bookId: Int): ShamelaBook? {
        val metaFile = File(getBookDir(context, bookId), "book_metadata.json")
        if (!metaFile.exists()) return null
        return try {
            val obj = JSONObject(metaFile.readText())
            ShamelaBook(
                id = obj.optInt("book_id", bookId),
                shamelaId = obj.optInt("shamela_id", 0),
                title = obj.optString("title_ar", ""),
                author = obj.optString("main_author_name_ar", ""),
                deathHijri = if (obj.has("main_author_death_hijri") && !obj.isNull("main_author_death_hijri")) obj.getInt("main_author_death_hijri") else null,
                categoryId = obj.optInt("category_id", 0),
                version = "${obj.optInt("version_major", 1)}.${obj.optInt("version_minor", 0)}",
                hasMultiPart = obj.optBoolean("has_multi_part", false),
                bookType = obj.optString("book_type_label", "كتاب")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getRecentlyReadBooks(context: Context): List<ShamelaBook> {
        val downloadedIds = getDownloadedBooks(context)
        if (downloadedIds.isEmpty()) return emptyList()

        return downloadedIds.mapNotNull { bookId ->
            val lastRead = getLastReadTime(context, bookId)
            if (lastRead <= 0L) return@mapNotNull null
            val meta = readMetadataSafe(context, bookId) ?: return@mapNotNull null
            Triple(meta, lastRead, getLastReadPage(context, bookId))
        }.sortedByDescending { it.second }.map { it.first }
    }

    fun getDownloadedBooksWithMeta(context: Context): List<ShamelaBook> {
        val downloadedIds = getDownloadedBooks(context)
        return downloadedIds.mapNotNull { bookId ->
            val meta = readMetadataSafe(context, bookId) ?: return@mapNotNull null
            meta.copy(lastReadAt = getLastReadTime(context, bookId))
        }.sortedByDescending { it.lastReadAt }
    }

    fun getBookmarkedBooksWithMeta(context: Context): List<ShamelaBook> {
        val bookIds = ShamelaBookmarkManager.getBookmarkedBookIds(context)
        if (bookIds.isEmpty()) return emptyList()
        return bookIds.mapNotNull { bookId ->
            val meta = readMetadataSafe(context, bookId) ?: return@mapNotNull null
            meta.copy(lastReadAt = getLastReadTime(context, bookId))
        }
    }
}
