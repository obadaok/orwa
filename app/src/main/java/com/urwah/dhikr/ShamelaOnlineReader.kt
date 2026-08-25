package com.urwah.dhikr

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * جلب كتب المكتبة الشاملة من الإنترنت مباشرةً للقراءة دون تحميل مسبق.
 *
 * - البيانات مصدرها ملفات HuggingFace نفسها التي يعتمد عليها التحميل المحلي
 *   (book_metadata.json + toc.jsonl + pages.jsonl) فيُفرد هنا جلبٌ متدرّج:
 *   الفهرستُ والبيانات موجزة وتصل أولًا، ثم تصل الصفحات سطرًا-سطرًا.
 * - هذه الكائنات لا تُكتب على القرص إطلاقًا؛ فهي للقراءة اللحظية فقط،
 *   ولا تتداخل مع كتب المكتبة المحلية.
 * - التحديثات تُقارَن عبر حداثة الإصدار في book_metadata.json (مهمة #5).
 */
object ShamelaOnlineReader {

    private const val BASE_URL = "https://huggingface.co/datasets/AuthenticIlm/Shamela4_Full_DB/resolve/main"

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** يقارن إصدارين "major.minor" — يعيد true إذا كان [remote] أحدث من [local]. */
    fun isUpdateAvailable(local: String?, remote: String?): Boolean {
        if (remote.isNullOrBlank()) return false
        if (local.isNullOrBlank()) return true
        val parse = { v: String -> v.trim().split('.').mapNotNull { it.toIntOrNull() } }
        val l = parse(local)
        val r = parse(remote)
        val size = maxOf(l.size, r.size)
        for (i in 0 until size) {
            val a = l.getOrElse(i) { 0 }
            val b = r.getOrElse(i) { 0 }
            if (a != b) return b > a
        }
        return false
    }

    fun buildFileUrl(hfPath: String, fileName: String): String =
        "$BASE_URL/${hfPath.trim('/')}/$fileName"

    /** يُرجع [ShamelaBook] المُنقّح من بيانات الكتاب البعيدة. */
    fun fetchMetadata(hfPath: String): ShamelaBook {
        val jsonText = httpGet(buildFileUrl(hfPath, "book_metadata.json"))
        val o = JSONObject(jsonText)
        return ShamelaBook(
            id = o.optInt("book_id", 0),
            shamelaId = o.optInt("shamela_id", 0),
            title = o.optString("title_ar", ""),
            author = o.optString("main_author_name_ar", ""),
            deathHijri = if (o.has("main_author_death_hijri") && !o.isNull("main_author_death_hijri")) {
                o.getInt("main_author_death_hijri")
            } else null,
            categoryId = o.optInt("category_id", 0),
            version = "${o.optInt("version_major", 1)}.${o.optInt("version_minor", 0)}",
            hasMultiPart = o.optBoolean("has_multi_part", false),
            bookType = o.optString("book_type_label", "كتاب"),
            hfPath = hfPath
        )
    }

    /** يحمّل جدول المحتويات (ملف صغير) بالكامل. */
    fun fetchToc(hfPath: String): List<ShamelaTocEntry> {
        val lines = httpReadLines(buildFileUrl(hfPath, "toc.jsonl"))
        return lines.mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            try {
                val o = JSONObject(line)
                ShamelaTocEntry(
                    titleId = o.getInt("title_id"),
                    pageId = o.getInt("page_id"),
                    parentId = if (o.isNull("parent_id")) null else o.getInt("parent_id"),
                    titleText = o.getString("title_text")
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * يبثّ صفحات pages.jsonl سطرًا-سطرًا عبر [onPage] (تُنفَّذ على مؤشر IO).
     * يُسهم هذا في ظهور أولى الصفحات قبل اكتمال الجلب كاملًا.
     * [isCancelled] تُفحص دوريًا لإيقاف البث فور مغادرة الشاشة بدل استكمال
     * تنزيل الكتاب كاملًا في الخلفية.
     */
    fun streamPages(hfPath: String, isCancelled: () -> Boolean = { false }, onPage: (ShamelaPage) -> Unit) {
        val url = URL(buildFileUrl(hfPath, "pages.jsonl"))
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("User-Agent", "OrwaApp/1.0")
        try {
            conn.connect()
            if (conn.responseCode != 200) {
                throw Exception("HTTP ${conn.responseCode}: ${conn.responseMessage}")
            }
            val reader = BufferedReader(InputStreamReader(conn.inputStream), 8192 * 4)
            while (true) {
                if (isCancelled()) break
                val line = reader.readLine() ?: break
                if (isCancelled()) break
                if (line.isBlank()) continue
                try {
                    val o = JSONObject(line)
                    onPage(
                        ShamelaPage(
                            pageId = o.getInt("page_id"),
                            shamelaPageId = o.getInt("shamela_page_id"),
                            part = if (o.isNull("part")) null else o.getString("part"),
                            pageNum = if (o.isNull("page_num")) null else o.getInt("page_num"),
                            body = o.getString("body"),
                            footnotes = if (o.isNull("footnotes")) null else o.getString("footnotes")
                        )
                    )
                } catch (_: Exception) {
                    // يتجاوز السطر التالف دون إيقاف البث
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun httpGet(urlStr: String): String =
        httpReadLines(urlStr).joinToString("\n")

    private fun httpReadLines(urlStr: String): List<String> {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("User-Agent", "OrwaApp/1.0")
        try {
            conn.connect()
            if (conn.responseCode != 200) {
                throw Exception("HTTP ${conn.responseCode}: ${conn.responseMessage}")
            }
            val result = mutableListOf<String>()
            conn.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { result.add(it) }
            }
            return result
        } finally {
            conn.disconnect()
        }
    }
}