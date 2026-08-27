package com.urwah.dhikr.align

/**
 * موجِّه «سطر القراءة» لوضع المرتّل — مع تقسيم واعٍ للوقف.
 *
 * سلوك البطاقة:
 * - سطر واحد عادةً (٤–٦ كلمات حسب عرض الشاشة)،
 * - إذا كان متن الآية كاملاً لا يتجاوز سطرين (≤ 2×K) يُعرض كاملاً في بطاقة واحدة
 *   (يلتف تلقائياً إلى سطرين داخل نفس البطاقة).
 * - للآيات الأطول: تُقسَّم إلى أسطر متتابعة غير متداخلة، كل سطر ≈ K كلمات،
 *   لكن الحدود تُختار عند علامات الوقف (صلى، قلى، ج، م …) لتكون طبيعية.
 * - هذه القاعدة تُطبَّق فقط لحفص والروايات التي تستخدم نفس نظام الوقف؛
 *   الروايات الأخرى تستخدم تقسيماً ثابتاً كل K كلمات.
 *
 * التوقيت: يوزَّع تناسبياً بنفس منهجية [WordAlignmentTracker] مع هوامش بداية/نهاية.
 * الانتقال: مشتق لحظياً من موضع الصوت + تقدّم استباقي (≈ 350ms) حتى يظهر
 * السطر التالي قبل وصول الصوت إليه — يشعر المستخدم أن النص يسبق الصوت بسلاسة،
 * بلا أي تتبع بصري لكلمة.
 */
class ReadingLinePager(
    private val maxWordsPerLine: Int = MAX_WORDS_PER_LINE,
    private val minWordsPerLine: Int = MIN_WORDS_PER_LINE,
    private val startPadFraction: Double = 0.06,
    private val endPadFraction: Double = 0.10
) {

    /** سطر قراءة واحد. */
    data class Line(
        val index: Int,
        val startIndex: Int,
        val words: List<String>
    )

    // ── الحالة الداخلية ──
    private var displayWords: List<String> = emptyList()
    private var isWaqfAfter: List<Boolean> = emptyList()
    private var cumulativeEnds = LongArray(0)
    private var durationMs: Long = 1L
    private var wordsPerLine: Int = maxWordsPerLine

    /** آخر مدة حقيقية حُمّلت بها (٠ = تحميل استباقي بتقدير فقط). */
    var loadedDurationMs: Long = 0L

    /** عرض النص الذي جرى ملاءمة [wordsPerLine] عنده. */
    var fittedForWidth: Int = 0

    /** هل تُطبَّق قواعد الوقف (حفص)؟ false = تقسيم ثابت. */
    var isWaqfAware: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                rebuildLines()
            }
        }

    /** الأسطر المحسوبة حالياً (مُعاد بناؤها بعد كل تغيير). */
    private var lines: List<Line> = emptyList()

    val wordCount: Int get() = displayWords.size
    val currentWordsPerLine: Int get() = wordsPerLine

    /** للقياس الخارجي — أول k كلمات من النص الأصلي */
    fun displayWordsForTest(): List<String> = displayWords

    private fun isWaqfChar(c: Char): Boolean = c in WAQF_SET

    /**
     * يحمّل نصاً جديداً ويوزع أزمنة الكلمات.
     */
    fun load(text: String, durationMs: Long) {
        this.durationMs = durationMs.coerceAtLeast(1L)

        // 1) رمّز بنفس طريقة WordAlignmentTracker ثم افصل الوقف عن الكلمات
        val rawTokens = WordAlignmentTracker.tokenize(text)
        val words = mutableListOf<String>()
        val waqfFlags = mutableListOf<Boolean>()

        for (token in rawTokens) {
            val isPureWaqf = token.isNotEmpty() && token.all { isWaqfChar(it) }
            if (isPureWaqf) {
                if (words.isNotEmpty()) {
                    waqfFlags[waqfFlags.lastIndex] = true
                }
                continue
            }
            // قد تحتوي الكلمة على وقف ملصق في آخرها (مثل "كَاتِبٌۢ")
            var hasAttached = false
            val cleanChars = StringBuilder()
            for (ch in token) {
                if (isWaqfChar(ch)) {
                    hasAttached = true
                } else {
                    cleanChars.append(ch)
                }
            }
            val clean = cleanChars.toString()
            if (clean.isEmpty()) {
                // الرمز كان وقفاً خالصاً — اعتبره فاصلاً
                if (words.isNotEmpty()) waqfFlags[waqfFlags.lastIndex] = true
                continue
            }
            words.add(clean)
            waqfFlags.add(hasAttached)
        }

        displayWords = words
        isWaqfAfter = waqfFlags

        // 2) وزّع الأزمنة تناسبياً
        val weights = displayWords.map { WordAlignmentTracker.weightOf(it) }
        val totalWeight = weights.sum().coerceAtLeast(1)
        val startPad = (this.durationMs * startPadFraction).toLong()
        val endPad = (this.durationMs * endPadFraction).toLong()
        val available = (this.durationMs - startPad - endPad).coerceAtLeast(0L)
        cumulativeEnds = LongArray(displayWords.size)
        var cursor = startPad
        for (i in displayWords.indices) {
            cursor += weights[i].toLong() * available / totalWeight
            cumulativeEnds[i] = cursor
        }
        rebuildLines()
    }

    /** ضبط عدد كلمات السطر (من القياس الفعلي لعرض البطاقة). */
    fun setWordsPerLine(k: Int) {
        val nk = k.coerceIn(1, maxWordsPerLine)
        if (nk != wordsPerLine) {
            wordsPerLine = nk
            rebuildLines()
        }
    }

    val lineCount: Int get() = lines.size

    /** فهرس السطر الذي يقع فيه موضع الصوت — بدون تقدّم استباقي. */
    fun lineIndexAt(positionMs: Long): Int {
        if (displayWords.isEmpty() || lines.isEmpty()) return 0
        val wordIdx = wordIndexAt(positionMs.coerceIn(0L, durationMs))
        // ابحث عن السطر الحاوي
        for (i in lines.indices) {
            val line = lines[i]
            val end = line.startIndex + line.words.size
            if (wordIdx in line.startIndex until end) return i
            if (wordIdx < line.startIndex) return i.coerceAtLeast(0)
        }
        return lines.lastIndex
    }

    fun lineAt(index: Int): Line {
        if (lines.isEmpty()) return Line(0, 0, emptyList())
        return lines[index.coerceIn(0, lines.lastIndex)]
    }

    fun isLastLine(index: Int): Boolean = lines.isNotEmpty() && index >= lines.lastIndex

    private fun wordIndexAt(pos: Long): Int {
        if (cumulativeEnds.isEmpty()) return 0
        var lo = 0
        var hi = cumulativeEnds.lastIndex
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (cumulativeEnds[mid] <= pos) lo = mid + 1 else hi = mid
        }
        return lo.coerceIn(0, cumulativeEnds.lastIndex)
    }

    private fun rebuildLines() {
        if (displayWords.isEmpty()) {
            lines = emptyList()
            return
        }
        // قاعدة السطرين: إذا كانت الآية كلها ≤ 2×K تُعرض كاملة في بطاقة واحدة (تلتف إلى سطرين)
        if (displayWords.size <= 2 * wordsPerLine) {
            lines = listOf(Line(0, 0, displayWords.toList()))
            return
        }

        if (!isWaqfAware) {
            // روايات لا تستخدم نظام حفص: تقسيم ثابت كل K
            val result = mutableListOf<Line>()
            var start = 0
            var idx = 0
            while (start < displayWords.size) {
                val end = (start + wordsPerLine).coerceAtMost(displayWords.size)
                result.add(Line(idx++, start, displayWords.subList(start, end).toList()))
                start = end
            }
            lines = result
            return
        }

        // واعٍ للوقف: ابحث عن وقف طبيعي قرب الحد المثالي
        val result = mutableListOf<Line>()
        var start = 0
        var idx = 0
        val minPerLine = minWordsPerLine.coerceAtMost(wordsPerLine)
        while (start < displayWords.size) {
            val remaining = displayWords.size - start
            if (remaining <= wordsPerLine) {
                result.add(Line(idx++, start, displayWords.subList(start, displayWords.size).toList()))
                break
            }
            if (remaining <= 2 * wordsPerLine) {
                // بقية قليلة: حاول جعلها سطرين متوازنين بدل سطر ممتلئ + ذيل قصير جداً
                // إذا كان الباقي ≤ wordsPerLine+2 قسّمها نصفين عند وقف
                // ببساطة اعتبرها سطراً واحداً أخيراً إذا كان وقف قريب
            }
            val idealEndExclusive = start + wordsPerLine // فهرس ما بعد آخر كلمة مثالية
            val windowStart = (start + minPerLine)
            val windowEndExclusive = idealEndExclusive.coerceAtMost(displayWords.size)

            // ابحث عن وقف داخل النافذة [windowStart-1 , windowEndExclusive-1] — الأقرب لنهاية النافذة أولى
            var breakAfter: Int? = null
            for (i in (windowEndExclusive - 1) downTo (windowStart - 1).coerceAtLeast(start)) {
                if (isWaqfAfter.getOrNull(i) == true) {
                    breakAfter = i
                    break
                }
            }

            val endExclusive = when {
                breakAfter != null -> breakAfter + 1
                else -> idealEndExclusive // لا وقف مناسب — اقطع عند الحد المثالي (تجنب عشوائية أكبر)
            }.coerceAtMost(displayWords.size)

            // حماية: لا تترك ذيلاً أقصر من minPerLine وحده
            val leftover = displayWords.size - endExclusive
            val finalEnd = if (leftover in 1 until minPerLine) {
                // ادمج الذيل مع السطر الحالي إذا كان قصيراً جداً
                displayWords.size
            } else {
                endExclusive
            }

            result.add(Line(idx++, start, displayWords.subList(start, finalEnd).toList()))
            start = finalEnd
        }
        lines = result
    }

    companion object {
        const val MAX_WORDS_PER_LINE: Int = 6
        const val MIN_WORDS_PER_LINE: Int = 3
        /** تقدّم استباقي: يظهر السطر التالي قبل وصول الصوت بنحو 350ms */
        const val LEAD_MS: Long = 350L
        const val ESTIMATED_MS_PER_LETTER: Long = 260L

        val WAQF_SET = setOf(
            '\u06D6', // ۖ صلى
            '\u06D7', // ۗ قلى
            '\u06DA', // ۚ ج
            '\u06D8', // ۘ م (مبدئية)
            '\u06E2', // ۢ م (منفصلة)
            '\u06ED', // ۭ م (سفلية)
            '\u06DB', // ۛ ثلاث نقاط
            '\u06DC'  // ۜ سين صغيرة (صه)
        )

        fun estimateDuration(text: String): Long {
            var letters = 0
            for (w in WordAlignmentTracker.tokenize(text)) letters += WordAlignmentTracker.weightOf(w)
            val est = letters * ESTIMATED_MS_PER_LETTER
            return est.coerceIn(2000L, 600_000L)
        }
    }
}
