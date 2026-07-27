package com.urwah.dhikr

import android.content.Context
import org.json.JSONObject

object ShamelaCatalogReader {

    private var cachedCatalog: ShamelaCatalog? = null

    fun getCatalog(context: Context): ShamelaCatalog {
        cachedCatalog?.let { return it }

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

        val catalog = ShamelaCatalog(categories = categories, books = books)
        cachedCatalog = catalog
        return catalog
    }

    fun getBooksByCategory(context: Context, categoryId: Int): List<ShamelaBook> {
        return getCatalog(context).books.filter { it.categoryId == categoryId }
    }

    fun searchBooks(context: Context, query: String): List<ShamelaBook> {
        if (query.isBlank()) return emptyList()
        val q = query.trim()
        val catalog = getCatalog(context)

        // Find category IDs matching the query
        val matchingCatIds = catalog.categories
            .filter { it.name.contains(q, ignoreCase = true) }
            .map { it.id }
            .toSet()

        return catalog.books.filter { book ->
            book.title.contains(q, ignoreCase = true) ||
            book.author.contains(q, ignoreCase = true) ||
            matchingCatIds.contains(book.categoryId)
        }
    }

    fun searchCategories(context: Context, query: String): List<ShamelaCategory> {
        if (query.isBlank()) return emptyList()
        val q = query.trim()
        return getCatalog(context).categories.filter {
            it.name.contains(q, ignoreCase = true)
        }
    }

    fun getCategoryName(context: Context, categoryId: Int): String {
        return getCatalog(context).categories.find { it.id == categoryId }?.name ?: ""
    }

    fun getAllAuthors(context: Context): List<ShamelaAuthor> {
        val catalog = getCatalog(context)
        val authorMap = mutableMapOf<String, MutableList<ShamelaBook>>()
        for (book in catalog.books) {
            if (book.author.isNotBlank()) {
                authorMap.getOrPut(book.author) { mutableListOf() }.add(book)
            }
        }
        return authorMap.map { (name, books) ->
            ShamelaAuthor(
                name = name,
                bookCount = books.size,
                books = books
            )
        }.sortedBy { it.name }
    }

    fun getBooksByAuthor(context: Context, authorName: String): List<ShamelaBook> {
        return getCatalog(context).books.filter { it.author == authorName }
    }
}
