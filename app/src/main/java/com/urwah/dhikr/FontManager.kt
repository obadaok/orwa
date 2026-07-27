package com.urwah.dhikr

import android.content.Context
import android.graphics.Typeface
import android.os.Build

object FontManager {

    data class FontDef(
        val fileName: String,
        val displayName: String,
        val preview: String = "بسم الله الرحمن الرحيم"
    )

    private val FONTS = listOf(
        FontDef("amiri_regular.ttf", "Amiri"),
        FontDef("amiri_bold.ttf", "Amiri Bold"),
        FontDef("amiri_quran_regular.ttf", "Amiri Quran"),
        FontDef("noto_naskh_arabic_regular.ttf", "Noto Naskh Arabic"),
        FontDef("noto_kufi_arabic_regular.ttf", "Noto Kufi Arabic"),
        FontDef("cairo_regular.ttf", "Cairo"),
        FontDef("ibm_plex_sans_arabic_regular.ttf", "IBM Plex Sans Arabic"),
        FontDef("tajawal_regular.ttf", "Tajawal"),
        FontDef("almarai_regular.ttf", "Almarai"),
        FontDef("almarai_bold.ttf", "Almarai Bold"),
        FontDef("changa_regular.ttf", "Changa"),
        FontDef("el_messiri_regular.ttf", "El Messiri"),
        FontDef("reem_kufi_regular.ttf", "Reem Kufi"),
        FontDef("lateef_regular.ttf", "Lateef"),
        FontDef("scheherazade_new_regular.ttf", "Scheherazade New"),
        FontDef("harmattan_regular.ttf", "Harmattan"),
        FontDef("mada_regular.ttf", "Mada"),
        FontDef("markazi_text_regular.ttf", "Markazi Text"),
        FontDef("aref_ruqaa_regular.ttf", "Aref Ruqaa"),
        FontDef("aref_ruqaa_bold.ttf", "Aref Ruqaa Bold"),
        FontDef("gulzar_regular.ttf", "Gulzar"),
    )

    private val cache = mutableMapOf<String, Typeface>()
    private const val PREFS = "urwah_shamela_downloads"
    private const val KEY_FONT = "reader_font_file"

    fun getFonts(): List<FontDef> = FONTS

    fun loadTypeface(context: Context, fileName: String): Typeface {
        cache[fileName]?.let { return it }
        synchronized(cache) {
            cache[fileName]?.let { return it }
            val tf = try {
                Typeface.createFromAsset(context.assets, "fonts/$fileName")
            } catch (_: Exception) {
                Typeface.DEFAULT
            }
            cache[fileName] = tf
            return tf
        }
    }

    fun getSelectedFont(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_FONT, "amiri_regular.ttf") ?: "amiri_regular.ttf"
    }

    fun setSelectedFont(context: Context, fileName: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FONT, fileName).apply()
    }

    fun getSelectedTypeface(context: Context): Typeface {
        return loadTypeface(context, getSelectedFont(context))
    }

    fun getDisplayName(fileName: String): String {
        return FONTS.find { it.fileName == fileName }?.displayName ?: fileName
    }

    fun getPreview(fileName: String): String {
        return FONTS.find { it.fileName == fileName }?.preview ?: "بسم الله الرحمن الرحيم"
    }

    fun clearCache() {
        cache.clear()
    }
}
