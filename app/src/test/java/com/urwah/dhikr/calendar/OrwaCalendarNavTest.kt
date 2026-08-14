package com.urwah.dhikr.calendar

import org.junit.Assert.assertTrue
import org.junit.Test

class OrwaCalendarNavTest {

    // Replicates OrwaCalendarFragment.changeMonth(delta) using the real data layer.
    private fun navigate(starYear: Int, starMonth: Int, starHijriDay: Int, delta: Int, steps: Int) {
        var y = starYear
        var m = starMonth
        var selDay = starHijriDay
        var selGd = -1
        var selGm = -1
        var selGy = -1
        for (i in 0 until steps) {
            var ny = y
            var nm = m + delta
            if (nm < 1) { nm = 12; ny-- } else if (nm > 12) { nm = 1; ny++ }
            y = ny
            m = nm

            val hijriDay = selDay.coerceAtMost(OrwaCalendarData.hijriMonthLength(y, m))
            val (gy, gm, gd) = OrwaCalendarData.hijriToGregorian(y, m, hijriDay)
            assertTrue("hijriToGregorian produced invalid gregorian for ($y,$m,$hijriDay): $gd/$gm/$gy",
                gy in 1900..2200 && gm in 1..12 && gd in 1..31)

            val backDay = OrwaCalendarData.hijriDayFor(gd, gm, gy)
            assertTrue("hijriDayFor invalid for $gd/$gm/$gy -> $backDay", backDay in 1..30)

            selDay = hijriDay
            selGd = gd; selGm = gm; selGy = gy
        }
    }

    @Test
    fun navigationNeverProducesInvalidDates() {
        for (delta in listOf(-1, 1)) {
            navigate(1448, 2, 29, delta, 1200)
        }
    }

    @Test
    fun gridCellsRoundTrip() {
        for (y in 1400..1500) {
            for (m in 1..12) {
                val grid = OrwaCalendarData.buildMonthGrid(y, m)
                assertTrue("month $y/$m cells empty", grid.cells.isNotEmpty())
                grid.cells.forEach { c ->
                    val day = OrwaCalendarData.hijriDayFor(c.gregDay, c.gregMonth, c.gregYear)
                    assertTrue("cell $c hijriDay=$day mismatch", day == c.hijriDay)
                }
            }
        }
    }
}
