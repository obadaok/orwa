package com.urwah.dhikr

/**
 * تطبيع موحّد للبحث العربي/الرقمي تشاركه شاشات البحث كافة:
 * - إزالة التشكيل والحركات
 * - توحيد الهمزات (آ/أ/إ/ٱ→ا، ؤ→و، ئ→ي، ى→ي، ة→ه)
 * - توحيد الأرقام العربية-الهندية/الفارسية إلى ASCII
 *   ليُطابق البحث عن «12» أو «١٢» نص «12» داخل الكتب.
 */
object SearchNormalizer {

    fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            val code = c.code
            sb.append(
                when {
                    code in 0x064B..0x065F || code == 0x0670 || code in 0x06D6..0x06ED -> ""
                    c == '\u0622' || c == '\u0623' || c == '\u0625' || c == '\u0671' -> '\u0627'
                    c == '\u0624' -> '\u0648'
                    c == '\u0626' || c == '\u0649' -> '\u064A'
                    c == '\u0629' -> '\u0647'
                    code in 0x0660..0x0669 -> ('0'.code + code - 0x0660).toChar()
                    code in 0x06F0..0x06F9 -> ('0'.code + code - 0x06F0).toChar()
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    /** هل الاستعلام رقميّ بحت؟ (رقم حديث / رقم صفحة) — يُعفى من حد الأحرف. */
    fun isNumericQuery(query: String): Boolean =
        query.isNotEmpty() && query.all { it.isDigit() }
}
