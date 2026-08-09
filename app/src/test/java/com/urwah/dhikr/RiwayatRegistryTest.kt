package com.urwah.dhikr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiwayatRegistryTest {

    @Test
    fun registry_containsAllExpectedRiwayat() {
        assertEquals(20, QuranDataLoader.riwayat.size)
        val ids = QuranDataLoader.riwayat.map { it.id }
        assertTrue("hafs" in ids)
        assertTrue("shouba" in ids)
        assertTrue("warsh" in ids)
        assertTrue("qaloon" in ids)
        assertTrue("doori" in ids)
        assertTrue("soosi" in ids)
        assertTrue("bazzi" in ids)
        assertTrue("qumbul" in ids)
    }

    @Test
    fun availableRiwayat_containsOnlyTheEightBundled() {
        val available = QuranDataLoader.availableRiwayat
        assertEquals(8, available.size)
        assertTrue(available.all { it.available })
        assertTrue(available.all { it.fileName != null })
        assertEquals(
            setOf("hafs", "shouba", "warsh", "qaloon", "doori", "soosi", "bazzi", "qumbul"),
            available.map { it.id }.toSet()
        )
    }

    @Test
    fun unavailableRiwayat_haveNoAssetFile() {
        QuranDataLoader.riwayat.filterNot { it.available }.forEach {
            assertFalse(QuranDataLoader.isAvailable(it.id))
            assertNull(it.fileName)
        }
    }

    @Test
    fun qiraaGroups_areGroupedByQiraaAndOrdered() {
        val groups = QuranDataLoader.qiraaGroups
        val allIds = groups.flatMap { it.second }.map { it.id }
        assertEquals(QuranDataLoader.riwayat.size, allIds.size)

        val byQiraa = groups.map { it.first }
        assertTrue("قراءة نافع" in byQiraa)
        assertTrue("قراءة عاصم" in byQiraa)

        val naafi = groups.first { it.first == "قراءة نافع" }.second
        assertEquals(setOf("warsh", "qaloon"), naafi.map { it.id }.toSet())

        val aasim = groups.first { it.first == "قراءة عاصم" }.second
        assertEquals(setOf("hafs", "shouba"), aasim.map { it.id }.toSet())
    }

    @Test
    fun getRiwayatInfo_fallsBackToHafsForUnknown() {
        val info = QuranDataLoader.getRiwayatInfo("does_not_exist")
        assertEquals("hafs", info.id)
        assertEquals("حفص عن عاصم", info.arabicName)
    }

    @Test
    fun riwayatIdForArabicName_mapsKnownNames() {
        assertEquals("hafs", QuranDataLoader.riwayatIdForArabicName("حفص عن عاصم"))
        assertEquals("warsh", QuranDataLoader.riwayatIdForArabicName("ورش عن نافع"))
        assertEquals("qaloon", QuranDataLoader.riwayatIdForArabicName("قالون عن نافع"))
        assertNull(QuranDataLoader.riwayatIdForArabicName("رواية غير معروفة"))
        assertNull(QuranDataLoader.riwayatIdForArabicName(null))
        assertNull(QuranDataLoader.riwayatIdForArabicName(""))
    }

    @Test
    fun basmala_isPerRiwayat() {
        val hafs = QuranDataLoader.getRiwayatInfo("hafs")
        val warsh = QuranDataLoader.getRiwayatInfo("warsh")
        assertEquals("بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ", hafs.basmala)
        assertEquals("بِسْمِ اِ۬للَّهِ اِ۬لرَّحْمَٰنِ اِ۬لرَّحِيمِ", warsh.basmala)
    }
}
