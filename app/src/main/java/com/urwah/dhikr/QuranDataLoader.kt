package com.urwah.dhikr

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class QuranSurah(
    val number: Int,
    val name: String,
    val ayahs: List<AyahData>,
    val revelationPlace: String
)

data class RiwayatInfo(
    val id: String,
    val arabicName: String,
    val qiraaName: String,
    val fileName: String?,
    val fontResId: Int,
    val available: Boolean,
    val description: String,
    val basmala: String
)

object QuranDataLoader {

    private const val PREFS_NAME = "urwah_quran"
    private const val KEY_QIRAAT = "qiraat"
    private const val DEFAULT_QIRAAT = "hafs"

    private const val HAFS_BASMALA = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"
    private const val WARSH_BASMALA = "بِسْمِ اِ۬للَّهِ اِ۬لرَّحْمَٰنِ اِ۬لرَّحِيمِ"

    private val caches = ConcurrentHashMap<String, Map<Int, QuranSurah>>()
    private val parseLock = Any()

    val riwayat: List<RiwayatInfo> = listOf(
        RiwayatInfo(
            "hafs", "حفص عن عاصم", "قراءة عاصم",
            "quran_uthmani.json", R.font.uthmanic_hafs, true,
            "الرواية الأكثر انتشاراً في العالم الإسلامي", HAFS_BASMALA
        ),
        RiwayatInfo(
            "shouba", "شعبة عن عاصم", "قراءة عاصم",
            "quran_shouba.json", R.font.uthmanic_shouba, true,
            "رواية شعبة عن عاصم، وتتميز بوقف حمزة وهشام", HAFS_BASMALA
        ),
        RiwayatInfo(
            "warsh", "ورش عن نافع", "قراءة نافع",
            "quran_warsh.json", R.font.uthmanic_warsh, true,
            "رواية أهل المغرب العربي والأندلس", WARSH_BASMALA
        ),
        RiwayatInfo(
            "qaloon", "قالون عن نافع", "قراءة نافع",
            "quran_qaloon.json", R.font.uthmanic_qaloon, true,
            "رواية أهل ليبيا وتونس وإفريقية", WARSH_BASMALA
        ),
        RiwayatInfo(
            "doori", "الدوري عن أبي عمرو", "قراءة أبي عمرو",
            "quran_doori.json", R.font.uthmanic_doori, true,
            "رواية أهل السودان والصومال وشرق إفريقيا", WARSH_BASMALA
        ),
        RiwayatInfo(
            "soosi", "السوسي عن أبي عمرو", "قراءة أبي عمرو",
            "quran_soosi.json", R.font.uthmanic_soosi, true,
            "الرواية الثانية عن أبي عمرو البصري", WARSH_BASMALA
        ),
        RiwayatInfo(
            "bazzi", "البزي عن ابن كثير", "قراءة ابن كثير",
            "quran_bazzi.json", R.font.uthmanic_bazzi, true,
            "رواية أهل مكة المكرمة", HAFS_BASMALA
        ),
        RiwayatInfo(
            "qumbul", "قنبل عن ابن كثير", "قراءة ابن كثير",
            "quran_qumbul.json", R.font.uthmanic_qumbul, true,
            "الرواية الثانية عن ابن كثير المكي", HAFS_BASMALA
        ),
        RiwayatInfo(
            "hisham", "هشام عن ابن عامر", "قراءة ابن عامر",
            null, R.font.uthmanic_hafs, false, "قريباً", HAFS_BASMALA
        ),
        RiwayatInfo(
            "dhakwan", "ابن ذكوان عن ابن عامر", "قراءة ابن عامر",
            null, R.font.uthmanic_hafs, false, "قريباً", HAFS_BASMALA
        ),
        RiwayatInfo(
            "khalaf", "خلف عن حمزة", "قراءة حمزة",
            null, R.font.uthmanic_hafs, false, "قريباً", HAFS_BASMALA
        ),
        RiwayatInfo(
            "khallad", "خلاد عن حمزة", "قراءة حمزة",
            null, R.font.uthmanic_hafs, false, "قريباً", HAFS_BASMALA
        ),
        RiwayatInfo(
            "alharith", "أبو الحارث عن الكسائي", "قراءة الكسائي",
            null, R.font.uthmanic_hafs, false, "قريباً", HAFS_BASMALA
        ),
        RiwayatInfo(
            "duri_kisai", "الدوري عن الكسائي", "قراءة الكسائي",
            null, R.font.uthmanic_hafs, false, "قريباً", HAFS_BASMALA
        ),
        RiwayatInfo(
            "ibnwardan", "ابن وردان عن أبي جعفر", "قراءة أبي جعفر",
            null, R.font.uthmanic_hafs, false, "قريباً", HAFS_BASMALA
        ),
        RiwayatInfo(
            "ibnjammaz", "ابن جماز عن أبي جعفر", "قراءة أبي جعفر",
            null, R.font.uthmanic_hafs, false, "قريباً", HAFS_BASMALA
        ),
        RiwayatInfo(
            "ruways", "رويس عن يعقوب", "قراءة يعقوب",
            null, R.font.uthmanic_hafs, false, "قريباً", HAFS_BASMALA
        ),
        RiwayatInfo(
            "ruh", "روح عن يعقوب", "قراءة يعقوب",
            null, R.font.uthmanic_hafs, false, "قريباً", HAFS_BASMALA
        ),
        RiwayatInfo(
            "ishaak", "إسحاق عن خلف", "قراءة خلف العاشر",
            null, R.font.uthmanic_hafs, false, "قريباً", HAFS_BASMALA
        ),
        RiwayatInfo(
            "idris", "إدريس عن خلف", "قراءة خلف العاشر",
            null, R.font.uthmanic_hafs, false, "قريباً", HAFS_BASMALA
        )
    )

    private val riwayatById = riwayat.associateBy { it.id }

    private val qiraaOrder = listOf(
        "قراءة نافع",
        "قراءة ابن كثير",
        "قراءة أبي عمرو",
        "قراءة ابن عامر",
        "قراءة عاصم",
        "قراءة حمزة",
        "قراءة الكسائي",
        "قراءة أبي جعفر",
        "قراءة يعقوب",
        "قراءة خلف العاشر"
    )

    val availableRiwayat: List<RiwayatInfo>
        get() = riwayat.filter { it.available }

    val qiraaGroups: List<Pair<String, List<RiwayatInfo>>>
        get() {
            val grouped = riwayat.groupBy { it.qiraaName }
            return qiraaOrder.mapNotNull { name -> grouped[name]?.let { name to it } }
        }

    fun getRiwayatInfo(id: String?): RiwayatInfo {
        return riwayatById[id] ?: riwayatById.getValue(DEFAULT_QIRAAT)
    }

    fun isAvailable(id: String): Boolean = getRiwayatInfo(id).available

    fun riwayatIdForArabicName(arabicName: String?): String? {
        if (arabicName.isNullOrBlank()) return null
        return riwayatById.entries.firstOrNull { it.value.arabicName == arabicName }?.key
    }

    fun getQiraat(context: Context): String {
        val current = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_QIRAAT, DEFAULT_QIRAAT) ?: DEFAULT_QIRAAT
        return if (isAvailable(current)) current else DEFAULT_QIRAAT
    }

    fun setQiraat(context: Context, qiraat: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_QIRAAT, qiraat).apply()
    }

    fun load(context: Context): Map<Int, QuranSurah> {
        return loadWithQiraat(context, getQiraat(context))
    }

    fun loadHafs(context: Context): Map<Int, QuranSurah> {
        return loadWithQiraat(context, "hafs")
    }

    fun loadWarsh(context: Context): Map<Int, QuranSurah> {
        return loadWithQiraat(context, "warsh")
    }

    fun loadWithQiraat(context: Context, qiraat: String?): Map<Int, QuranSurah> {
        val info = getRiwayatInfo(qiraat)
        if (!info.available || info.fileName == null) {
            return loadWithQiraat(context, DEFAULT_QIRAAT)
        }
        caches[info.id]?.let { return it }

        synchronized(parseLock) {
            caches[info.id]?.let { return it }
            val jsonString = readJsonFromAssets(context, info.fileName)
            val root = JSONObject(jsonString)

            val result = mutableMapOf<Int, QuranSurah>()
            for (key in root.keys()) {
                val surahNum = key.toInt()
                val surahObj = root.getJSONObject(key)
                val name = surahObj.getString("n")
                val loc = surahObj.getString("l")
                val ayahsArray = surahObj.getJSONArray("a")

                val ayahs = mutableListOf<AyahData>()
                for (i in 0 until ayahsArray.length()) {
                    val ayahObj = ayahsArray.getJSONObject(i)
                    val ayahNum = ayahObj.getInt("n")
                    val ayahText = normalizeText(ayahObj.getString("t"))
                    if (ayahNum > 0) {
                        ayahs.add(AyahData(surahNum, ayahNum, ayahText))
                    }
                }

                result[surahNum] = QuranSurah(surahNum, name, ayahs, loc)
            }

            caches[info.id] = result
            return result
        }
    }

    /**
     * تحميل مسبق للرواية الحالية في خيط خلفي عند إقلاع التطبيق حتى لا يُحلَّل
     * ملف القرآن الكامل على الـ main thread داخل onCreate للأنشطة.
     */
    fun preloadAsync(context: Context) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                load(context.applicationContext)
            } catch (_: Exception) {
            }
        }
    }

    fun getSurah(context: Context, surahNumber: Int): QuranSurah? {
        return load(context)[surahNumber]
    }

    fun getAyahCount(context: Context, surahNumber: Int): Int {
        return load(context)[surahNumber]?.ayahs?.size ?: 0
    }

    fun invalidateCache() {
        caches.clear()
    }

    fun getUthmanicFontRes(context: Context): Int {
        return getRiwayatInfo(getQiraat(context)).fontResId
    }

    fun fontResFor(qiraat: String?): Int {
        return getRiwayatInfo(qiraat).fontResId
    }

    private fun normalizeText(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFC)
            .replace('\u06DF', '\u0652')
            .replace('\u06E0', '\u0652')
    }

    private fun readJsonFromAssets(context: Context, fileName: String): String {
        context.assets.open(fileName).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                return sb.toString()
            }
        }
    }
}
