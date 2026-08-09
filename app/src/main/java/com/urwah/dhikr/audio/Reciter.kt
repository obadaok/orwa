package com.urwah.dhikr.audio

/**
 * قارئ واحد ضمن قائمة القرّاء (Gapped per-ayah).
 *
 * cdnPath: اسم المجلد داخل mirrors.quranicaudio.com/everyayah/
 * الملف لكل آية يُبنى كالتالي: {cdnPath}/{sura:03d}{ayah:03d}.mp3
 */
data class Reciter(
    val id: Int,
    val nameArabic: String,
    val nameEnglish: String,
    val cdnPath: String,
    val riwaya: String,
    val bitrate: String
) {
    fun ayahUrl(surah: Int, ayah: Int): String {
        return "https://mirrors.quranicaudio.com/everyayah/$cdnPath/" +
            String.format("%03d%03d.mp3", surah, ayah)
    }

    fun bitrateDisplay(): String {
        val digits = bitrate.filter { it.isDigit() }
        return if (digits.isNotEmpty()) "$digits كيلوبت/ث" else bitrate
    }

    companion object {
        const val RIWAYA_HAFS = "حفص عن عاصم"
        const val RIWAYA_QALOON = "قالون عن نافع"
    }
}
