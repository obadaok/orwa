package com.urwah.dhikr

/**
 * طبقة الربط الموحّدة بين صفحات المصدر الأصلي (Shamela4/pages.jsonl) وصفحات العرض.
 *
 * الحقيقة من المصدر:
 * - كل سطر في pages.jsonl يحمل [ShamelaPage.pageNum]: رقم الصفحة المطبوع الأصلي.
 * - pageNum ليس index+1: فيه فراغات (صفحات مفقودة من المطبوعة) وقيم null،
 *   فكتاب بعدد N سطر قد تصل أرقامه الأصلية إلى M > N.
 *
 * لذلك كل قراءة/عرض/انتقال للأرقام يجب أن يمر من هنا، ولا يجوز افتراض
 * أن page number = array index في أي موضع آخر بالتطبيق.
 */
object ShamelaPageMapper {

    /**
     * خريطة الجانب المصدري (ثابتة لكل تحميل كتاب، لا تتأثر بإعادة الترقيم):
     * - startOffsets[i] = موضع بداية نص الصفحة المصدرية i داخل fullText.
     */
    class SourceMap(
        val pages: List<ShamelaPage>,
        private val startOffsets: IntArray,
        val minOriginalNum: Int,
        val maxOriginalNum: Int
    ) {
        /** أول index مصدري لكل pageNum أصلي (قد يتكرر الرقم نظريًا — نأخذ الأول). */
        private val pageNumToIdx = HashMap<Int, Int>(pages.size)
        private val pageIdToIdx = HashMap<Int, Int>(pages.size)

        init {
            for ((i, p) in pages.withIndex()) {
                p.pageNum?.let { if (!pageNumToIdx.containsKey(it)) pageNumToIdx[it] = i }
                if (!pageIdToIdx.containsKey(p.pageId)) pageIdToIdx[p.pageId] = i
            }
        }

        /** عدد أسطر المصدر (وليس أكبر رقم صفحة أصلي). */
        val sourcePageCount: Int get() = pages.size

        /**
         * index مصدري لموضع نصي داخل fullText: آخر صفحة مصدرية تبدأ عند أو قبل offset.
         * بحث ثنائي O(log n).
         */
        fun sourceIndexForCharOffset(offset: Int): Int {
            if (startOffsets.isEmpty()) return -1
            var lo = 0
            var hi = startOffsets.size - 1
            var ans = 0
            while (lo <= hi) {
                val mid = (lo + hi) / 2
                if (startOffsets[mid] <= offset) { ans = mid; lo = mid + 1 } else hi = mid - 1
            }
            return ans
        }

        fun sourceIndexForPageId(pageId: Int): Int = pageIdToIdx[pageId] ?: -1

        /**
         * index مصدري لرقم صفحة أصلي أدخله المستخدم.
         * - مطابقة تامة إن وُجد الرقم.
         * - وإلا أول صفحة أصلية برقم أكبر (تجاوز الفراغات في المطبوع).
         * - وإلا (-1) إذا تجاوز أكبر رقم في الكتاب — المتصل يقرر (clamp أو رفض).
         */
        fun sourceIndexForPageNum(num: Int): Int {
            pageNumToIdx[num]?.let { return it }
            // أقرب رقم موجود ≥ num عبر مسح مفاتيح مرتبة (الأرقام قليلة النقص عمليًا)
            var best = -1
            var bestNum = Int.MAX_VALUE
            for ((n, idx) in pageNumToIdx) {
                if (n >= num && n < bestNum) { bestNum = n; best = idx }
            }
            return best
        }

        /** الرقم الأصلي لصفحة مصدرية (قد يكون null في المصدر نفسه). */
        fun originalNumAt(sourceIdx: Int): Int? =
            pages.getOrNull(sourceIdx)?.pageNum

        /** أقرب رقم أصلي غير null عند أو قبل sourceIdx — يمنع fallback إلى index+1 */
        fun originalNumAtOrPrev(sourceIdx: Int): Int? {
            for (i in sourceIdx downTo 0) {
                pages.getOrNull(i)?.pageNum?.let { return it }
            }
            return null
        }

        /** بداية الصفحة المصدرية i داخل fullText. */
        fun startOffsetAt(sourceIdx: Int): Int =
            startOffsets.getOrElse(sourceIdx) { -1 }
    }

    /**
     * يبني fullText وstartOffsets في مرور واحد محسوم:
     * fullText = joinToString("\n\n") لأجسام الصفحات بعد stripHtml،
     * وstartOffsets[i] = موضع بداية جسم الصفحة i داخل النص الناتج.
     */
    fun buildFullText(
        pages: List<ShamelaPage>,
        stripHtml: (String) -> String
    ): Pair<String, IntArray> {
        val sb = StringBuilder()
        val offsets = IntArray(pages.size)
        for ((i, p) in pages.withIndex()) {
            if (i > 0) sb.append("\n\n")
            offsets[i] = sb.length
            sb.append(stripHtml(p.body))
        }
        return sb.toString() to offsets
    }

    /** يبني SourceMap من صفحات المصدر وstartOffsets الناتجة عن buildFullText. */
    fun build(pages: List<ShamelaPage>, startOffsets: IntArray): SourceMap {
        var min = Int.MAX_VALUE
        var max = Int.MIN_VALUE
        for (p in pages) {
            p.pageNum?.let {
                if (it < min) min = it
                if (it > max) max = it
            }
        }
        if (min == Int.MAX_VALUE) { min = 1; max = pages.size.coerceAtLeast(1) }
        max = max.coerceAtLeast(min)
        return SourceMap(pages, startOffsets, min, max)
    }
}
