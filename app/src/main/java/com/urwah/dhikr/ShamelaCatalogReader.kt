package com.urwah.dhikr

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * قارئ الفهرس الجامع للكتب (8589 كتابًا في ملف assets/shamela_catalog.json).
 *
 * التحسينات:
 * - تحليل مرة واحدة فقط ثم كاش ذاكري ثابت للمعامل مع كاش قرص فائق السرعة:
 *   بعد أول تحليل كامل يُكتب الفهرس بشكل نصّي مضغوط (سطر لكل كتاب بفاصل
 *   tab)، ويُقرأ منه في المرات التالية بدل مشي كائنات JSON الثقيل — بلا
 *   تحليل كامل على الـ main thread عند كل برودة.
 * - فهرس بحث مُطبَّع (بلا تشكيل) يُبنى مرة واحدة لتصفية سريعة.
 * - قائمة المؤلفين مشكّلة مسبقًا (لا إعادة تجميع لكل مكالمة).
 * - تُدفَّأ الحرارة في الخلفية عبر [warmCache] من UrwahApplication.
 */
object ShamelaCatalogReader {

    private var cachedCatalog: ShamelaCatalog? = null
    private var cachedAuthors: List<ShamelaAuthor>? = null
    private var searchIndex: IndexedCatalog? = null

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "shamela-catalog").apply { priority = Thread.NORM_PRIORITY } }

    private class IndexedCatalog(
        val normalizedTitles: List<String>,
        val normalizedAuthors: List<String>
    )

    private fun indexFile(context: Context): File =
        File(context.filesDir, "shamela_catalog.idx")

    /** يُدفئ كاش الفهرس في الخلفية دون إبطاء الـ UI (آمن للنداء المتكرر). */
    fun warmCache(context: Context) {
        io.execute {
            try {
                getCatalog(context)
            } catch (_: Throwable) {
                // لا يوقف الدفء أي مسار آخر.
            }
        }
    }

    /**
     * يجلب الفهرس. يُفضَّل القناع الذاكري؛ إن بُرّد، يقرأ من كاش القرص
     * السريع؛ وإلا يُحلَّل ملف assets مرة واحدة ويُكتب الكاش.
     */
    fun getCatalog(context: Context): ShamelaCatalog {
        cachedCatalog?.let { return it }
        readIndexCache(context)?.let {
            cachedCatalog = it
            return it
        }
        val catalog = parseJsonCatalog(context)
        cachedCatalog = catalog
        writeIndexCache(context, catalog)
        return catalog
    }

    private fun parseJsonCatalog(context: Context): ShamelaCatalog {
        val json = context.assets.open("shamela_catalog.json").bufferedReader().use { it.readText() }
        val obj = JSONObject(json)

        val catsArray = obj.getJSONArray("cats")
        val categories = mutableListOf<ShamelaCategory>()
        for (i in 0 until catsArray.length()) {
            val c = catsArray.getJSONObject(i)
            categories.add(
                ShamelaCategory(
                    id = c.getInt("id"),
                    name = c.getString("name"),
                    bookCount = c.getInt("bookCount"),
                    folder = c.optString("folder", "")
                )
            )
        }

        val booksArray = obj.getJSONArray("books")
        val books = mutableListOf<ShamelaBook>()
        for (i in 0 until booksArray.length()) {
            val b = booksArray.getJSONObject(i)
            books.add(
                ShamelaBook(
                    id = b.getInt("id"),
                    shamelaId = b.getInt("sid"),
                    title = b.getString("t"),
                    author = b.optString("a", ""),
                    deathHijri = if (b.has("d") && !b.isNull("d")) b.getInt("d") else null,
                    categoryId = b.getInt("c"),
                    version = b.optString("v", "1.0"),
                    hasMultiPart = b.optBoolean("mp", false),
                    bookType = b.optString("bt", "كتاب"),
                    hfPath = b.optString("p", "")
                )
            )
        }
        return ShamelaCatalog(categories = categories, books = books)
    }

    private fun readIndexCache(context: Context): ShamelaCatalog? {
        val file = indexFile(context)
        if (!file.exists()) return null
        return try {
            val cats = mutableListOf<ShamelaCategory>()
            val books = mutableListOf<ShamelaBook>()
            var readingCats = true
            file.bufferedReader().forEachLine { line ->
                if (line == "##BOOKS") {
                    readingCats = false
                    return@forEachLine
                }
                if (line.isEmpty()) return@forEachLine
                if (readingCats) {
                    val p = line.split('\t')
                    if (p.size >= 4) {
                        cats.add(ShamelaCategory(p[0].toInt(), p[1], p[2].toInt(), p[3]))
                    }
                } else {
                    val p = line.split('\t')
                    if (p.size >= 10) {
                        books.add(
                            ShamelaBook(
                                id = p[0].toInt(),
                                shamelaId = p[1].toInt(),
                                title = p[2],
                                author = p[3],
                                deathHijri = p[4].toIntOrNull(),
                                categoryId = p[5].toInt(),
                                version = p[6],
                                hasMultiPart = p[7] == "1",
                                bookType = p[8],
                                hfPath = p[9]
                            )
                        )
                    }
                }
            }
            ShamelaCatalog(categories = cats, books = books)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeIndexCache(context: Context, catalog: ShamelaCatalog) {
        try {
            val file = indexFile(context)
            file.bufferedWriter().use { writer ->
                for (c in catalog.categories) {
                    writer.write("${c.id}\t${c.name}\t${c.bookCount}\t${c.folder}")
                    writer.newLine()
                }
                writer.write("##BOOKS")
                writer.newLine()
                for (b in catalog.books) {
                    writer.write("${b.id}\t${b.shamelaId}\t${b.title}\t${b.author}\t" +
                        "${b.deathHijri ?: ""}\t${b.categoryId}\t${b.version}\t" +
                        "${if (b.hasMultiPart) 1 else 0}\t${b.bookType}\t${b.hfPath}")
                    writer.newLine()
                }
            }
        } catch (_: Exception) {
            // فشل كتابة الكاش لا يمنع عمل التطبيق.
        }
    }

    private fun indexedCatalog(context: Context): IndexedCatalog {
        searchIndex?.let { return it }
        val catalog = getCatalog(context)
        val idx = IndexedCatalog(
            normalizedTitles = catalog.books.map { normalizeForSearch(it.title) },
            normalizedAuthors = catalog.books.map { normalizeForSearch(it.author) }
        )
        searchIndex = idx
        return idx
    }

    fun getBooksByCategory(context: Context, categoryId: Int): List<ShamelaBook> {
        return getCatalog(context).books.filter { it.categoryId == categoryId }
    }

    fun searchBooks(context: Context, query: String): List<ShamelaBook> {
        if (query.isBlank()) return emptyList()
        val q = normalizeForSearch(query.trim())
        val catalog = getCatalog(context)
        val idx = indexedCatalog(context)

        val matchingCatIds = catalog.categories
            .filter { normalizeForSearch(it.name).contains(q) }
            .map { it.id }
            .toSet()

        val result = mutableListOf<ShamelaBook>()
        for ((i, book) in catalog.books.withIndex()) {
            if (idx.normalizedTitles[i].contains(q) ||
                idx.normalizedAuthors[i].contains(q) ||
                matchingCatIds.contains(book.categoryId)
            ) {
                result.add(book)
            }
        }
        return result
    }

    fun searchCategories(context: Context, query: String): List<ShamelaCategory> {
        if (query.isBlank()) return emptyList()
        val q = normalizeForSearch(query.trim())
        return getCatalog(context).categories.filter {
            normalizeForSearch(it.name).contains(q)
        }
    }

    fun getCategoryName(context: Context, categoryId: Int): String {
        return getCatalog(context).categories.find { it.id == categoryId }?.name ?: ""
    }

    fun getAllAuthors(context: Context): List<ShamelaAuthor> {
        cachedAuthors?.let { return it }
        val catalog = getCatalog(context)
        val authorMap = mutableMapOf<String, MutableList<ShamelaBook>>()
        for (book in catalog.books) {
            if (book.author.isNotBlank()) {
                authorMap.getOrPut(book.author) { mutableListOf() }.add(book)
            }
        }
        cachedAuthors = authorMap.map { (name, books) ->
            ShamelaAuthor(
                name = name,
                bookCount = books.size,
                books = books
            )
        }.sortedBy { it.name }
        return cachedAuthors!!
    }

    fun getBooksByAuthor(context: Context, authorName: String): List<ShamelaBook> {
        return getCatalog(context).books.filter { it.author == authorName }
    }

    /** يبطل كاشات الفهرسة (تنظيف ذاكرة عند الحاجة). */
    fun clearCache() {
        cachedCatalog = null
        cachedAuthors = null
        searchIndex = null
    }

    /**
     * تطبيع نص عربي للبحث فقط (بلا تشكيل/همزات/تطويل/ألف مقصورة/تاء مربوطة)
     * دون تغيير النصوص الأصلية المعروضة.
     */
    private fun normalizeForSearch(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            val code = c.code
            sb.append(when {
                code in 0x064B..0x065F || code == 0x0670 || code == 0x0640 -> ""
                code in 0x06D6..0x06ED || code in 0x08F0..0x08FF -> ""
                c == '\u0622' || c == '\u0623' || c == '\u0625' || c == '\u0671' || c == '\u0621' -> '\u0627'
                c == '\u0624' -> '\u0648'
                c == '\u0626' -> '\u064A'
                c == '\u0649' -> '\u064A'
                c == '\u0629' -> '\u0647'
                else -> c
            })
        }
        return sb.toString()
    }
}