package com.urwah.dhikr.align

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordAlignmentTrackerTest {

    /** متتبع بلا هامش/مقدّمة/تأخير — الحدود النسبية تصبح دقيقة وسهلة التحقق. */
    private fun exactTracker(
        forwardToleranceMs: Long = 0L,
        backwardHysteresisMs: Long = 0L,
        bigJumpMs: Long = 5000L,
        stabilitySamples: Int = 1
    ): WordAlignmentTracker = WordAlignmentTracker(
        forwardToleranceMs = forwardToleranceMs,
        backwardHysteresisMs = backwardHysteresisMs,
        bigJumpMs = bigJumpMs,
        startPadFraction = 0.0,
        endPadFraction = 0.0,
        stabilitySamples = stabilitySamples
    )

    /** نص آية طويلة (١٦ كلمة) لاختبار النوافذ الثابتة. */
    private fun longAyahText(): String =
        "وَإِيَّاكَ نَعۡبُدُ وَإِيَّاكَ نَسۡتَعِينُ ٱهۡدِنَا ٱلصِّرَٰطَ ٱلۡمُسۡتَقِيمَ " +
            "صِرَٰطَ ٱلَّذِينَ أَنۡعَمۡتَ عَلَيۡهِمۡ غَيۡرِ ٱلۡمَغۡضُوبِ عَلَيۡهِمۡ وَلَا ٱلضَّآلِّينَ"

    @Test
    fun tokenize_splitsWordsAndDropsOrnaments() {
        val words = WordAlignmentTracker.tokenize("﴿بِسۡمِ ٱللَّهِ ﴾ ۝")
        assertEquals(listOf("بِسۡمِ", "ٱللَّهِ"), words)
    }

    @Test
    fun weightOf_countsLettersAfterRemovingDiacritics() {
        assertEquals(3, WordAlignmentTracker.weightOf("بِسۡمِ"))
        assertEquals(4, WordAlignmentTracker.weightOf("ٱللَّهِ"))
        assertEquals(6, WordAlignmentTracker.weightOf("ٱلرَّحۡمَٰنِ"))
        // كلمة أطول تُزن أكثر — أساس التوزيع التناسبي.
        assertTrue(WordAlignmentTracker.weightOf("ٱلرَّحۡمَٰنِ") > WordAlignmentTracker.weightOf("بِسۡمِ"))
    }

    @Test
    fun load_distributesBoundariesWithinDuration() {
        val tracker = WordAlignmentTracker()
        tracker.load("بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ", 10000L)
        assertEquals(4, tracker.wordCount)
        val words = (0 until 4).map { tracker.wordAt(it)!! }
        assertTrue(words.all { it.startMs >= 0L && it.endMs <= 10000L })
        for (i in 1 until words.size) {
            assertTrue(words[i].startMs >= words[i - 1].endMs)
        }
        // هامش بداية/نهاية للقارئ.
        assertTrue(words.first().startMs > 0L)
        assertTrue(words.last().endMs < 10000L)
    }

    @Test
    fun firstUpdate_pointsToContainingWord() {
        val tracker = exactTracker()
        tracker.load("أ ب ج د", 4000L)
        assertEquals(0, tracker.update(500L))
        assertEquals(3, tracker.update(3999L))
    }

    @Test
    fun advance_movesOnlyWhenCrossingBoundary() {
        val tracker = exactTracker()
        tracker.load("أ ب ج د", 4000L)
        assertEquals(0, tracker.update(500L))
        assertEquals(1, tracker.update(1500L)) // تجاوز حدّ الكلمة الأولى
        assertEquals(2, tracker.update(2501L)) // تجاوز حدّ الكلمة الثانية
    }

    @Test
    fun advance_holdsAtBoundaryEdgeUntilStrictlyPast() {
        val tracker = exactTracker()
        tracker.load("أ ب ج د", 4000L)
        assertEquals(0, tracker.update(500L))
        assertEquals(0, tracker.update(1000L))   // لا يتجاوز الحد بشكل صارم
        assertEquals(1, tracker.update(1001L))
    }

    @Test
    fun advance_insideWord_staysPut() {
        val tracker = exactTracker()
        tracker.load("أ ب ج د", 4000L)
        assertEquals(0, tracker.update(500L))
        assertEquals(0, tracker.update(700L))
        assertEquals(0, tracker.update(950L))
    }

    @Test
    fun forwardTolerance_absorbsSmallOvershoot() {
        // هامش 300ms: موضع يتجاوز الحد بقليل لا يُحرّك المؤشر.
        val tracker = exactTracker(forwardToleranceMs = 300L)
        tracker.load("أ ب ج د", 4000L)
        assertEquals(0, tracker.update(500L))
        assertEquals(0, tracker.update(1290L)) // 1290 > 1000 لكن دون 1000+300
        assertEquals(1, tracker.update(1301L)) // يتجاوز الهامش
    }

    @Test
    fun backtrack_returnsToContainingWord() {
        val tracker = exactTracker()
        tracker.load("أ ب ج د", 4000L)
        tracker.update(3500L)
        assertEquals(3, tracker.currentWordIndex)
        tracker.update(100L)
        assertEquals(0, tracker.currentWordIndex)
    }

    @Test
    fun repeatLoop_resetToStart() {
        // تكرار الآية: يعود الموضع للصفر فيرتجع المؤشر لأول كلمة.
        val tracker = exactTracker()
        tracker.load("أ ب ج د", 4000L)
        tracker.update(3500L)
        assertEquals(3, tracker.currentWordIndex)
        assertEquals(0, tracker.update(0L))
    }

    @Test
    fun bigForwardJump_skipsIntermediateWords() {
        val tracker = exactTracker(bigJumpMs = 0L)
        tracker.load("أ ب ج د", 4000L)
        tracker.update(100L)
        assertEquals(0, tracker.currentWordIndex)
        tracker.update(2500L)
        assertEquals(2, tracker.currentWordIndex)
    }

    @Test
    fun window_shortAyahIsShownFully() {
        val tracker = WordAlignmentTracker()
        tracker.load("بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ", 10000L)
        val w = tracker.window(2)
        assertTrue(w.fullAyah)
        assertEquals(0, w.startIndex)
        assertEquals(4, w.endIndex)
        assertTrue(w.contains(2))
    }

    @Test
    fun window_longAyah_centeredOnFirstPosition() {
        val tracker = exactTracker()
        tracker.load(longAyahText(), 20000L)
        assertEquals(16, tracker.wordCount)
        assertEquals(0, tracker.update(0L))
        val w = tracker.window()
        assertFalse(w.fullAyah)
        assertEquals(0, w.startIndex)
        assertTrue(w.endIndex <= 6)
        assertTrue(w.contains(0))
    }

    @Test
    fun window_holdsInsideHoldZone_doesNotJump() {
        // الكلمة الحالية داخل منطقة التثبيت (بداية النافذة..+4) لا تحرّك النافذة
        // حتى عند وصول نتائج جديدة — الثبات أولوية على التخمين.
        val tracker = exactTracker()
        tracker.load(longAyahText(), 20000L)
        tracker.update(0L)
        assertEquals(0, tracker.window().startIndex)

        // تقدّم حتى الكلمة 3: داخل منطقة التثبيت → النافذة تبقى [0,6).
        for (i in 1..3) {
            tracker.update(tracker.wordAt(i - 1)!!.endMs + 1L)
        }
        assertEquals(3, tracker.currentWordIndex)
        val w = tracker.window()
        assertEquals("بقيت النافذة ثابتة", 0, w.startIndex)
        assertEquals(6, w.endIndex)
        assertTrue(w.contains(3))
    }

    @Test
    fun window_slidesOnlyWhenCurrentCrossesHoldZone() {
        val tracker = exactTracker()
        tracker.load(longAyahText(), 20000L)
        tracker.update(0L)
        assertEquals(0, tracker.window().startIndex)

        // تقدّم تدريجيًا كلمةً كلمة حتى الكلمة 3 دون تغيير النافذة…
        for (i in 1..3) {
            tracker.update(tracker.wordAt(i - 1)!!.endMs + 1L)
        }
        assertEquals(0, tracker.window().startIndex)

        // …ثم الكلمة 4 تخترق نهاية منطقة التثبيت → انزلاق واثق مرة واحدة.
        tracker.update(tracker.wordAt(3)!!.endMs + 1L)
        assertEquals(4, tracker.currentWordIndex)
        val w = tracker.window()
        assertEquals(2, w.startIndex)
        assertEquals(8, w.endIndex)
        assertTrue(w.contains(4))
    }

    @Test
    fun window_advanceWindows_areOverlappingAndMonotonic() {
        val tracker = exactTracker()
        tracker.load(longAyahText(), 20000L)
        tracker.update(0L)
        var prevStart = tracker.window().startIndex
        var idx = 0
        for (i in 1..15) {
            tracker.update(tracker.wordAt(i - 1)!!.endMs + 1L)
            idx = tracker.currentWordIndex
            val w = tracker.window()
            assertTrue("النافذة تضم الكلمة الحالية: idx=$idx", w.contains(idx))
            assertTrue("بداية النافذة لا تتراجع", w.startIndex >= prevStart)
            assertTrue("حجم النافذة معقول", (w.endIndex - w.startIndex) <= 6)
            prevStart = w.startIndex
        }
        assertEquals(15, idx)
        val last = tracker.window()
        assertTrue("الذيل يُثبَّت على آخر الكلمات", last.endIndex == 16)
    }

    @Test
    fun window_tailIsClampedToLastWords() {
        val tracker = exactTracker()
        tracker.load(longAyahText(), 20000L)
        val w = tracker.window(15)
        assertEquals(10, w.startIndex)
        assertEquals(16, w.endIndex)
        assertTrue(w.contains(15))
    }

    @Test
    fun window_backtrack_recentersOnNewPosition() {
        val tracker = exactTracker()
        tracker.load(longAyahText(), 20000L)
        tracker.update(0L)
        // تقدّم حتى منتصف الآية
        tracker.update(tracker.wordAt(8)!!.endMs + 1L)
        assertTrue(tracker.window().startIndex >= 4)

        // القارئ يرجع للخلف — تعاد نافذة مناسبة للموضع الجديد.
        tracker.update(tracker.wordAt(1)!!.startMs)
        val backIdx = tracker.currentWordIndex
        val backWindow = tracker.window()
        assertTrue("المؤشر رجع", backIdx < 8)
        assertTrue("النافذة تُعيد تمركزها حول الموضع الجديد", backWindow.contains(backIdx))
    }

    @Test
    fun repeatLoop_windowReturnsToStart() {
        val tracker = exactTracker()
        tracker.load(longAyahText(), 20000L)
        tracker.update(14000L)
        assertTrue(tracker.currentWordIndex >= 10)

        // تكرار الآية: يعود الموضع للصفر → نافذة البداية من جديد.
        tracker.update(0L)
        val w = tracker.window()
        assertEquals(0, w.startIndex)
        assertEquals(6, w.endIndex)
    }

    @Test
    fun stabilitySamples_requiresConsecutiveEvidence() {
        val tracker = exactTracker(stabilitySamples = 2)
        tracker.load("أ ب ج د", 4000L)
        tracker.update(500L)
        assertEquals(0, tracker.currentWordIndex)
        // عيّنة واحدة تتجاوز الحد لا تحرّك المؤشر.
        tracker.update(1500L)
        assertEquals(0, tracker.currentWordIndex)
        // عيّنة متتالية ثانية تجعل التحرك مؤكدًا.
        tracker.update(1600L)
        assertEquals(1, tracker.currentWordIndex)
    }

    @Test
    fun stabilitySamples_oscillationAtBoundary_holdsStill() {
        val tracker = exactTracker(stabilitySamples = 2)
        tracker.load("أ ب ج د", 4000L)
        tracker.update(500L)
        // ذبذبة حول الحد: تجاوز ثم عودة ثم تجاوز — لا يصل الدليل إلى عيّنتين متتاليتين.
        tracker.update(1500L)
        tracker.update(500L)
        tracker.update(1500L)
        tracker.update(1600L)
        assertEquals(1, tracker.currentWordIndex)
    }

    @Test
    fun backtrack_movesPointerWithoutRebuildingWindow() {
        val tracker = exactTracker()
        tracker.load(longAyahText(), 20000L)
        tracker.update(14000L)
        val forwardIdx = tracker.currentWordIndex
        val forwardWindow = tracker.window(forwardIdx)
        assertTrue(forwardWindow.contains(forwardIdx))

        // القارئ يرجع — المؤشر يعود إلى كلمة سابقة داخل نفس النافذة الثابتة.
        tracker.update(2000L)
        val backIdx = tracker.currentWordIndex
        val backWindow = tracker.window(backIdx)
        assertTrue("المؤشر رجع: $forwardIdx -> $backIdx", backIdx < forwardIdx)
        assertTrue("النافذة لم تُبنَ من جديد", backWindow.contains(backIdx))
    }

    @Test
    fun repeatLoop_windowReturnsToFirstGroup() {
        val tracker = exactTracker()
        tracker.load(longAyahText(), 20000L)
        tracker.update(14000L)
        assertTrue(tracker.currentWordIndex >= 10)

        // تكرار الآية: يعود الموضع للصفر فيرجع المؤشر ويستقر المؤشر في أول مجموعة.
        tracker.update(0L)
        val w = tracker.window(tracker.currentWordIndex)
        assertEquals(0, w.startIndex)
        assertEquals(6, w.endIndex)
    }
}
