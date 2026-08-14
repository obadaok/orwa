package com.urwah.dhikr.align

/**
 * متتبع محاذاة الكلمات مع الصوت (محاذاة نسبية ذكية).
 *
 * الفكرة الأساسية: **نص الآية هو المصدر الثابت للحقيقة**. لا يعدّل التحليل
 * النص المعروض، بل يعمل في الخلفية بالكامل: يحلل صوت التلاوة، يحدد موضع
 * القارئ داخل الآية، يقارن الصوت مع نص الآية، ويتعامل مع التوقف والتلعثم
 * وإعادة الكلمات والرجوع. **المستخدم لا يرى تتبع كلمة** (لا تلوين ولا وميض
 * ولا تحريك لون بين الكلمات) — كل ما يعرضه الواجهة هو «نافذة كلمات» واسعة.
 *
 * لا يتوفر طابع زمني حقيقي لكل كلمة من ملفات التلاوة (Gapped per-ayah)، لذلك
 * نوزّع مدة الآية الصوتية على كلماتها تناسبيًا مع طول كل كلمة (عدد الحروف بعد
 * إزالة التشكيل).
 *
 * ## نافذة الكلمات المتمركزة (خلف نافذة ثابتة قابلة للانزلاق)
 *
 * عند تحميل آية جديدة تُعرض الآيات القصيرة (≤ [fullAyahWordLimit]) كاملة؛
 * غيرها تُعرض كـ «نافذة متابعة» من [maxWindowWords] كلمات تقريبًا متمركزة حول
 * موضع القارئ. النافذة لا تنزلق مع كل نتيجة لحظية؛ تتحرك وفق منطق ثبات:
 *
 *    CURRENT_WINDOW → WAIT_FOR_CONFIDENT_ADVANCE → ADVANCE_WINDOW
 *
 *  - الكلمة الحالية تبقى داخل «منطقة التثبيت» وسط النافذة (بدايتها..[advanceAt])
 *    دون أن تغيّر النافذة شيئًا، حتى وإن خمّن التحليل كلمة إضافية عابرة.
 *  - لا تنتقل النافذة إلى موضع جديد إلا عندما يتأكد النظام أن القارئ تقدّم
 *    فعلًا (المؤشر يخرق نهاية منطقة التثبيت) — انزلاق متداخل ثابت.
 *  - التلعثم/الإعادة/التوقف لا تعيد ترتيب الكلمات ولا تضيف كلمات مؤقتًا.
 *  - الرجوع للخلف يكتشفه التتبع ويعيد النافذة الممركزة للموضع الجديد.
 *  - الانتقال لآية أخرى يبني آية جديدة منطقتها من الصفر.
 *
 * ## استقرار المؤشر
 *
 *  - التقدّم والرجوع لا يُطبَّقان إلا بعد [stabilitySamples] عيّنات متتالية
 *    في الاتجاه نفسه — فلا يتحرك المؤشر مع ذبذبة صوتية واحدة.
 *  - هامش [forwardToleranceMs] يمتص تجاوز الحدود العابر.
 *  - القفز الكبير (سحب شريط التقدم) ينتقل مباشرة للكلمة الهدف.
 *
 * الصفّ نقي (خالٍ من Android) وبالتالي قابل للاختبار الوحدوي، ويمكن لاحقًا
 * استبدال توزيعه النسبي بمصدر طابع زمني حقيقي دون تغيير واجهة الاستخدام.
 */
class WordAlignmentTracker(
    private val forwardToleranceMs: Long = 350L,
    private val backwardHysteresisMs: Long = 400L,
    private val bigJumpMs: Long = 1200L,
    private val startPadFraction: Double = 0.08,
    private val endPadFraction: Double = 0.12,
    private val maxWindowWords: Int = MAX_WINDOW_WORDS,
    private val fullAyahWordLimit: Int = FULL_AYAH_WORD_LIMIT,
    private val stabilitySamples: Int = 2,
    private val centerOffset: Int = CENTER_OFFSET,
    private val advanceAt: Int = ADVANCE_AT
) {

    /** كلمة واحدة بحدودها الزمنية المقدَّرة داخل الآية. */
    data class Word(
        val text: String,
        val weight: Int,
        val startMs: Long,
        val endMs: Long
    )

    /** نافذة القراءة المتمركزة: الكلمات من [startIndex] حتى [endIndex)، حول [currentIndex]. */
    data class Window(
        val startIndex: Int,
        val endIndex: Int,
        val currentIndex: Int,
        val fullAyah: Boolean
    ) {
        val isEmpty: Boolean get() = endIndex <= startIndex
        fun contains(index: Int): Boolean = index in startIndex until endIndex
    }

    private var words: List<Word> = emptyList()
    private var durationMs: Long = 0L
    private var currentIndex: Int = -1
    private var lastPositionMs: Long = -1L
    private var loaded = false

    /** حدود نافذة المتابعة الحالية (تُدار بآلة حالات ثابتة مهما تقدم الصوت). */
    private var windowStart = -1
    private var windowEnd = -1

    private var advanceSamples = 0
    private var backtrackSamples = 0

    val wordCount: Int get() = words.size
    val currentWordIndex: Int get() = currentIndex
    fun isLoaded(): Boolean = loaded

    /** يحمّل آية جديدة: يقسّم نصّها كلمات، يوزّع حدودها، ويصفّر حالة النافذة. */
    fun load(text: String, durationMs: Long) {
        this.durationMs = durationMs.coerceAtLeast(1L)
        val tokens = tokenize(text)
        val weights = tokens.map { weightOf(it) }
        val totalWeight = weights.sum().coerceAtLeast(1)

        val startPad = (this.durationMs * startPadFraction).toLong()
        val endPad = (this.durationMs * endPadFraction).toLong()
        val available = (this.durationMs - startPad - endPad).coerceAtLeast(0L)

        var cursor = startPad
        words = tokens.mapIndexed { index, token ->
            val w = weights[index]
            val start = cursor
            val end = start + w.toLong() * available / totalWeight
            cursor = end
            Word(token, w, start, end)
        }
        resetWindow()
        currentIndex = -1
        lastPositionMs = -1L
        advanceSamples = 0
        backtrackSamples = 0
        loaded = true
    }

    /** يصفّر حالة النافذة (يُستدعى عند تحميل آية جديدة). */
    private fun resetWindow() {
        windowStart = -1
        windowEnd = -1
    }

    /**
     * يحدّث الحالة من الموضع الحالي ويعيد فهرس الكلمة الجاري تلاوتها.
     * لا يُطبَّق التحرك إلا بعد عيّنات متتالية في الاتجاه نفسه (ثبات).
     */
    fun update(positionMs: Long): Int {
        if (!loaded || words.isEmpty()) return -1
        val pos = positionMs.coerceIn(0L, durationMs)

        if (currentIndex < 0 || lastPositionMs < 0) {
            currentIndex = containingIndex(pos)
            lastPositionMs = pos
            refreshWindowFor(currentIndex)
            return currentIndex
        }

        val current = words[currentIndex]

        when {
            // تقدّم: تجاوز نهاية الكلمة — يحتاج دليلاً متتابعًا.
            pos > current.endMs + forwardToleranceMs -> {
                backtrackSamples = 0
                advanceSamples++
                if (advanceSamples >= stabilitySamples) {
                    advanceSamples = 0
                    if (pos - current.endMs >= bigJumpMs) {
                        currentIndex = containingIndex(pos)
                    } else {
                        var i = currentIndex
                        while (i < words.lastIndex &&
                            pos > words[i].endMs + forwardToleranceMs

                        ) {
                            i++
                        }
                        currentIndex = i
                    }
                }
            }
            // رجوع ملحوظ: القارئ يعيد أو يسحب للخلف — يحتاج دليلاً متتابعًا.
            pos < current.startMs - backwardHysteresisMs -> {
                advanceSamples = 0
                backtrackSamples++
                if (backtrackSamples >= stabilitySamples) {
                    backtrackSamples = 0
                    currentIndex = containingIndex(pos)
                }
            }
            // وإلا: داخل الكلمة الحالية أو في هامش التسامح → ثبات (لا قفز).
            else -> {
                advanceSamples = 0
                backtrackSamples = 0
            }
        }

        refreshWindowFor(currentIndex)
        lastPositionMs = pos
        return currentIndex
    }

    /**
     * آلة حالات النافذة المتمركزة:
     *
     *    CURRENT_WINDOW → WAIT_FOR_CONFIDENT_ADVANCE → ADVANCE_WINDOW
     *
     * النافذة تحافظ على حدودها طالما بقيت الكلمة الحالية داخل منطقة التثبيت
     * ([windowStart .. windowStart + advanceAt)). لا يتغير شيء بمجرد تخمين كلمة
     * إضافية. حين يخرق المؤشر نهاية منطقة التثبيت (تقدّم واثق) تنزلق النافذة
     * مرة واحدة حول الموضع الجديد. الرجوع خارج بداية النافذة يعيد تمركزها.
     */
    private fun refreshWindowFor(index: Int) {
        if (words.isEmpty()) {
            windowStart = 0
            windowEnd = 0
            return
        }
        if (words.size <= fullAyahWordLimit) {
            windowStart = 0
            windowEnd = words.size
            return
        }
        val maxStart = (words.size - maxWindowWords).coerceAtLeast(0)
        if (windowStart < 0 || index < windowStart) {
            // أول موضع أو رجوع خارج بداية النافذة → نافذة جديدة متمركزة.
            windowStart = (index - centerOffset).coerceIn(0, maxStart)
        } else if (index >= windowStart + advanceAt) {
            // تقدّم واثق: انزلاق متداخل ثابت (لا قفز على كل كلمة).
            windowStart = (index - centerOffset).coerceIn(0, maxStart)
        }
        windowEnd = (windowStart + maxWindowWords).coerceAtMost(words.size)
        if (windowEnd == words.size) {
            // عند الاقتراب من نهاية الآية تُثبَّت النافذة على الذيل كاملًا.
            windowStart = maxStart
            windowEnd = words.size
        }
    }

    /** النافذة الممركزة الحالية التي تضم [currentIndex] — لا تنزلق مع حركة المؤشر داخلها. */
    fun window(currentIndex: Int = this.currentIndex): Window {
        if (words.isEmpty()) return Window(0, 0, 0, fullAyah = true)
        if (windowStart < 0) refreshWindowFor(currentIndex.coerceIn(0, words.lastIndex))
        val c = currentIndex.coerceIn(0, words.lastIndex)
        if (words.size <= fullAyahWordLimit) {
            return Window(0, words.size, c, fullAyah = true)
        }
        return Window(windowStart, windowEnd, c, fullAyah = false)
    }

    fun wordAt(index: Int): Word? = words.getOrNull(index)

    fun wordsForWindow(window: Window): List<Word> =
        if (window.isEmpty) emptyList() else words.subList(window.startIndex, window.endIndex)

    private fun containingIndex(pos: Long): Int {
        var lo = 0
        var hi = words.lastIndex
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (words[mid].endMs < pos) lo = mid + 1 else hi = mid
        }
        return lo.coerceIn(0, words.lastIndex)
    }

    companion object {

        /** حجم نافذة القراءة الثابتة للآيات الطويلة (كلمات لكل مجموعة). */
        const val MAX_WINDOW_WORDS: Int = 6

        /** الآيات التي لا يتجاوز عدد كلماتها هذا الحد تُعرض كاملة دون نافذة. */
        const val FULL_AYAH_WORD_LIMIT: Int = 8

        /** عدد الكلمات التي تُعرض قبل كلمة القراءة في النافذة المتمركزة. */
        const val CENTER_OFFSET: Int = 2

        /** موقع الكلمة الحالية داخل النافذة الذي يُفعّل الانزلاق الواثق (نهاية منطقة التثبيت). */
        const val ADVANCE_AT: Int = 4

        private val DIACRITICS = Regex("[\u064B-\u065F\u0670\u06D6-\u06ED\u08F0-\u08FF\u0640]")

        /**
         * يقسّم نص الآية إلى كلمات حقيقية ويتخلص من أقواس الزينة
         * وعلامات نهاية الآية (۝ ۞ ﴿ ﴾).
         */
        fun tokenize(text: String): List<String> =
            text.replace(Regex("[﴿﴾۝۞]"), " ")
                .trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }

        /**
         * وزن الكلمة = عدد حروفها بعد إزالة التشكيل.
         * الكلمة الأطول تتلوها أطول مدة — أساس التوزيع التناسبي.
         */
        fun weightOf(word: String): Int {
            val stripped = word.replace(DIACRITICS, "")
            val letters = stripped.count { c ->
                val code = c.code
                (code in 0x0621..0x064A) || (code in 0x0671..0x06D3) || code == 0x06D5
            }
            return letters.coerceAtLeast(1)
        }
    }
}
