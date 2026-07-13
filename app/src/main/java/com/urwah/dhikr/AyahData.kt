package com.urwah.dhikr

/**
 * بيانات آية واحدة.
 */
data class AyahData(
    val surahNumber: Int,
    val number: Int,
    val text: String
)

/**
 * بيانات وصفية عن السورة، تُستخدم في صندوق الترويسة العلوي.
 *
 * ligatureCode: الشيفرة النصية المستخدمة مع خطوط أسماء السور المخطوطة
 * (مثال: "surah100" لسورة العاديات) — راجع ملف التعليمات لمعرفة كيفية استخدامها.
 */
data class SurahInfo(
    val number: Int,
    val nameArabic: String,
    val nameEnglish: String,
    val revelationPlace: String, // "مكية" أو "مدنية"
    val ayahCount: Int,
    val ligatureCode: String
)
