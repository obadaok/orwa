package com.urwah.dhikr.audio

/**
 * قائمة القرّاء المتوفرة — كل المسارات تم التحقق منها فعلياً (HTTP 200)
 * عبر mirrors.quranicaudio.com/everyayah/ بتاريخ 2026-07-31.
 */
object ReciterCatalog {

    val reciters: List<Reciter> = listOf(
        Reciter(0, "مشاري راشد العفاسي", "Mishary Rashid Alafasy", "Alafasy_128kbps", Reciter.RIWAYA_HAFS, "128kbps"),
        Reciter(1, "عبد الله بصفر", "Abdullah Basfar", "Abdullah_Basfar_192kbps", Reciter.RIWAYA_HAFS, "192kbps"),
        Reciter(2, "أبو بكر الشاطري", "Abu Bakr Ash-Shaatree", "Abu_Bakr_Ash-Shaatree_128kbps", Reciter.RIWAYA_HAFS, "128kbps"),
        Reciter(3, "محمود خليل الحصري", "Mahmoud Khalil Al-Husary", "Husary_128kbps_Mujawwad", Reciter.RIWAYA_HAFS, "128kbps"),
        Reciter(4, "محمد صديق المنشاوي", "Mohamed Siddiq El-Minshawi", "Minshawy_Mujawwad_192kbps", Reciter.RIWAYA_HAFS, "192kbps"),
        Reciter(5, "ماهر المعيقلي", "Maher Al Muaiqly", "Maher_AlMuaiqly_64kbps", Reciter.RIWAYA_HAFS, "64kbps"),
        Reciter(6, "سعود الشريم", "Saood Ash-Shuraym", "Saood_ash-Shuraym_128kbps", Reciter.RIWAYA_HAFS, "128kbps"),
        Reciter(7, "محمد الطبلاوي", "Mohammad Al-Tablaway", "Mohammad_al_Tablaway_128kbps", Reciter.RIWAYA_HAFS, "128kbps"),
        Reciter(8, "محمد جبريل", "Muhammad Jibreel", "Muhammad_Jibreel_128kbps", Reciter.RIWAYA_HAFS, "128kbps"),
        Reciter(9, "سعد الغامدي", "Saad Al-Ghamdi", "Ghamadi_40kbps", Reciter.RIWAYA_HAFS, "40kbps"),
        Reciter(10, "أيمن سويد", "Ayman Suwaid", "Ayman_Sowaid_64kbps", Reciter.RIWAYA_HAFS, "64kbps"),
        Reciter(11, "هاني الرفاعي", "Hani Ar-Rifai", "Hani_Rifai_192kbps", Reciter.RIWAYA_HAFS, "192kbps"),
        Reciter(12, "علي الحذيفي", "Ali Al-Hudhaify", "qaloon_hudhayfi_96kbps", Reciter.RIWAYA_QALOON, "96kbps")
    )

    private val byId = reciters.associateBy { it.id }

    fun getById(id: Int): Reciter = byId[id] ?: reciters.first()

    fun getDefault(): Reciter = reciters.first()
}
