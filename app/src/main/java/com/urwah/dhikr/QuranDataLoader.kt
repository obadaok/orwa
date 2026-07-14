package com.urwah.dhikr

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.Normalizer

data class QuranSurah(
    val number: Int,
    val name: String,
    val ayahs: List<AyahData>,
    val revelationPlace: String
)

object QuranDataLoader {

    private const val PREFS_NAME = "urwah_quran"
    private const val KEY_QIRAAT = "qiraat"

    private var hafsCache: Map<Int, QuranSurah>? = null
    private var warshCache: Map<Int, QuranSurah>? = null

    fun getQiraat(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_QIRAAT, "hafs") ?: "hafs"
    }

    fun setQiraat(context: Context, qiraat: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_QIRAAT, qiraat).apply()
    }

    fun load(context: Context): Map<Int, QuranSurah> {
        val qiraat = getQiraat(context)
        return loadWithQiraat(context, qiraat)
    }

    fun loadHafs(context: Context): Map<Int, QuranSurah> {
        return loadWithQiraat(context, "hafs")
    }

    fun loadWarsh(context: Context): Map<Int, QuranSurah> {
        return loadWithQiraat(context, "warsh")
    }

    private fun loadWithQiraat(context: Context, qiraat: String): Map<Int, QuranSurah> {
        if (qiraat == "warsh") {
            warshCache?.let { return it }
        } else {
            hafsCache?.let { return it }
        }

        val fileName = if (qiraat == "warsh") "quran_warsh.json" else "quran_uthmani.json"
        val jsonString = readJsonFromAssets(context, fileName)
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

        if (qiraat == "warsh") {
            warshCache = result
        } else {
            hafsCache = result
        }
        return result
    }

    fun getSurah(context: Context, surahNumber: Int): QuranSurah? {
        return load(context)[surahNumber]
    }

    fun getAyahCount(context: Context, surahNumber: Int): Int {
        return load(context)[surahNumber]?.ayahs?.size ?: 0
    }

    fun invalidateCache() {
        hafsCache = null
        warshCache = null
    }

    fun getUthmanicFontRes(context: Context): Int {
        return if (getQiraat(context) == "warsh") R.font.uthmanic_warsh else R.font.uthmanic_hafs
    }

    private fun normalizeText(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFC)
            .replace('\u06DF', '\u0652')
            .replace('\u06E0', '\u0652')
    }

    private fun readJsonFromAssets(context: Context, fileName: String): String {
        val inputStream = context.assets.open(fileName)
        val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line)
        }
        reader.close()
        return sb.toString()
    }
}
