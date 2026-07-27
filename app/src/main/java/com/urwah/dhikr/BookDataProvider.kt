package com.urwah.dhikr

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader

object BookDataProvider {

    private var cachedContent = mutableMapOf<String, BookContent>()

    fun getCategories(): List<LibraryCategory> {
        return listOf(
            LibraryCategory(
                id = "literature",
                title = "الأدب العربي",
                description = "روائع الأدب العربي",
                iconResId = R.drawable.ic_auto_awesome_black_24dp,
                books = listOf(
                    LibraryBook(
                        id = "diwan_almutanabbi",
                        title = "ديوان المتنبي",
                        description = "أبو الطيب المتنبي — أعظم شعراء العربية",
                        category = "literature",
                        chaptersCount = 12,
                        pagesCount = 200,
                        contentPath = "books/diwan_almutanabbi.json"
                    )
                )
            )
        )
    }

    fun getAllBooks(): List<LibraryBook> {
        return getCategories().flatMap { it.books }
    }

    fun searchBooks(query: String): List<LibraryBook> {
        if (query.isBlank()) return emptyList()
        return getAllBooks().filter {
            it.title.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true)
        }
    }

    fun getShamelaCategories(context: Context): List<ShamelaCategory> {
        return ShamelaCatalogReader.getCatalog(context).categories
    }

    fun getShamelaBooksByCategory(context: Context, categoryId: Int): List<ShamelaBook> {
        return ShamelaCatalogReader.getBooksByCategory(context, categoryId)
    }

    fun searchShamelaBooks(context: Context, query: String): List<ShamelaBook> {
        return ShamelaCatalogReader.searchBooks(context, query)
    }

    fun getBookContent(context: Context, bookId: String): BookContent? {
        cachedContent[bookId]?.let { return it }

        val book = getAllBooks().find { it.id == bookId } ?: return null
        return try {
            val inputStream = context.assets.open(book.contentPath)
            val json = inputStream.bufferedReader().use { it.readText() }
            val content = parseBookContent(json)
            cachedContent[bookId] = content
            content
        } catch (e: Exception) {
            createFallbackContent(book)
        }
    }

    fun getShamelaBookContent(context: Context, bookId: Int): ShamelaBookContent? {
        return ShamelaBookStorage.loadBookContent(context, bookId)
    }

    private fun parseBookContent(json: String): BookContent {
        val obj = JSONObject(json)
        val id = obj.getString("id")
        val title = obj.getString("title")
        val chaptersArray = obj.getJSONArray("chapters")
        val chapters = mutableListOf<BookChapter>()

        for (i in 0 until chaptersArray.length()) {
            val chapterObj = chaptersArray.getJSONObject(i)
            val chapterTitle = chapterObj.getString("title")
            val chapterContent = chapterObj.getString("content")
            val subheadings = mutableListOf<BookSubheading>()

            if (chapterObj.has("subheadings")) {
                val subs = chapterObj.getJSONArray("subheadings")
                for (j in 0 until subs.length()) {
                    val subObj = subs.getJSONObject(j)
                    subheadings.add(
                        BookSubheading(
                            title = subObj.getString("title"),
                            content = subObj.getString("content")
                        )
                    )
                }
            }

            chapters.add(
                BookChapter(
                    id = "$id/chapter_$i",
                    title = chapterTitle,
                    content = chapterContent,
                    subheadings = subheadings
                )
            )
        }

        return BookContent(id = id, title = title, chapters = chapters)
    }

    private fun createFallbackContent(book: LibraryBook): BookContent {
        val chapters = listOf(
            BookChapter(
                id = "${book.id}/chapter_0",
                title = book.title,
                content = "هذا الكتاب قيد الإعداد. سيتم إضافة المحتوى قريباً إن شاء الله.\n\n${book.description}"
            )
        )
        return BookContent(id = book.id, title = book.title, chapters = chapters)
    }

    fun getReadingProgress(context: Context, bookId: String): Pair<Int, Int> {
        val prefs = context.getSharedPreferences("urwah_library", Context.MODE_PRIVATE)
        val chapter = prefs.getInt("${bookId}_chapter", 0)
        val scrollY = prefs.getInt("${bookId}_scroll", 0)
        return chapter to scrollY
    }

    fun saveReadingProgress(context: Context, bookId: String, chapterIndex: Int, scrollY: Int) {
        val prefs = context.getSharedPreferences("urwah_library", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("${bookId}_chapter", chapterIndex)
            .putInt("${bookId}_scroll", scrollY)
            .apply()
    }
}
