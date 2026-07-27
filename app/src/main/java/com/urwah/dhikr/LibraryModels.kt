package com.urwah.dhikr

data class LibraryBook(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val chaptersCount: Int,
    val pagesCount: Int,
    val contentPath: String,
    val iconResId: Int = R.drawable.ic_book_24dp
)

data class LibraryCategory(
    val id: String,
    val title: String,
    val description: String,
    val iconResId: Int = R.drawable.ic_category_black_24dp,
    val books: List<LibraryBook>
)

data class BookChapter(
    val id: String,
    val title: String,
    val content: String,
    val subheadings: List<BookSubheading> = emptyList()
)

data class BookSubheading(
    val title: String,
    val content: String
)

data class BookContent(
    val id: String,
    val title: String,
    val chapters: List<BookChapter>
)

data class ShamelaBook(
    val id: Int,
    val shamelaId: Int,
    val title: String,
    val author: String,
    val deathHijri: Int?,
    val categoryId: Int,
    val version: String,
    val hasMultiPart: Boolean,
    val bookType: String,
    val hfPath: String = ""
) {
    val coverUrl: String
        get() = "https://huggingface.co/datasets/AuthenticIlm/Shamela4_Full_DB/resolve/main/$hfPath/book_metadata.json"

    val displayAuthor: String
        get() = if (deathHijri != null) "$author (ت $deathHijri هـ)" else author

    val pagesUrl: String
        get() = "https://huggingface.co/datasets/AuthenticIlm/Shamela4_Full_DB/resolve/main/$hfPath/pages.jsonl"

    val tocUrl: String
        get() = "https://huggingface.co/datasets/AuthenticIlm/Shamela4_Full_DB/resolve/main/$hfPath/toc.jsonl"

    val metadataUrl: String
        get() = "https://huggingface.co/datasets/AuthenticIlm/Shamela4_Full_DB/resolve/main/$hfPath/book_metadata.json"
}

data class ShamelaCategory(
    val id: Int,
    val name: String,
    val bookCount: Int,
    val folder: String = ""
)

data class ShamelaCatalog(
    val categories: List<ShamelaCategory>,
    val books: List<ShamelaBook>
)

data class ShamelaAuthor(
    val name: String,
    val bookCount: Int,
    val books: List<ShamelaBook> = emptyList()
)

data class ShamelaTocEntry(
    val titleId: Int,
    val pageId: Int,
    val parentId: Int?,
    val titleText: String
)

data class ShamelaPage(
    val pageId: Int,
    val shamelaPageId: Int,
    val part: String?,
    val pageNum: Int?,
    val body: String,
    val footnotes: String?
)

data class ShamelaBookContent(
    val metadata: ShamelaBook,
    val toc: List<ShamelaTocEntry>,
    val pages: List<ShamelaPage>
)

data class DownloadState(
    val bookId: Int,
    val progress: Float = 0f,
    val status: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    val error: String? = null
)

enum class DownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}
