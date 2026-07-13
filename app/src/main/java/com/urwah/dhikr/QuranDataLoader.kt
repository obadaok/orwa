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

    private var surahMap: Map<Int, QuranSurah>? = null

    fun load(context: Context): Map<Int, QuranSurah> {
        surahMap?.let { return it }

        val jsonString = readJsonFromAssets(context)
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

        surahMap = result
        return result
    }

    fun getSurah(context: Context, surahNumber: Int): QuranSurah? {
        return load(context)[surahNumber]
    }

    private fun normalizeText(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFC)
            .replace('\u06DF', '\u0652')
            .replace('\u06E0', '\u0652')
    }

    private fun readJsonFromAssets(context: Context): String {
        val inputStream = context.assets.open("quran_uthmani.json")
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
