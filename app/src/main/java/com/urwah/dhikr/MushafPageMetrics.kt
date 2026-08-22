package com.urwah.dhikr

/**
 * مقاييس صفحة المصحف — أبعاد مرجعية ثابتة لكل الصفحات بلا استثناء.
 *
 * المرجع: أبعاد مصحف المدينة النبوية (KFGQPC) الفعلية 19.5×28.5 سم
 * (نسبة 0.684)، مع هوامش مشابهة للمطبوع. كل قيم التخطيط قابلة للضبط
 * من هنا فقط — لا قيم عشوائية داخل العارض.
 */
object MushafPageMetrics {

    /** أبعاد الصفحة المرجعية بأي وحدة متناسقة (20×29.2 ≈ 19.5:28.5). */
    const val PAGE_W: Float = 780f
    const val PAGE_H: Float = 1140f

    /** نسب الهوامش من أبعاد الصفحة (تشبه منطقة النص في المطبوع). */
    const val MARGIN_LEFT_RATIO: Float = 0.085f
    const val MARGIN_RIGHT_RATIO: Float = 0.085f
    // الهوامش الرأسية مضبوطة لتقريب منطقة النص من حافتي الصفحة العلوية/السفلية
    // (المطبوع: ~2-3%) — الفائض من كتلة النص مُستغَل في الشبكة بدل الفراغ.
    const val MARGIN_TOP_RATIO: Float = 0.03f
    const val MARGIN_BOTTOM_RATIO: Float = 0.05f

    /** الفجوة الأساسية بين الكلمات (كجزء من حجم الخط) — ضيقة كمصحف. */
    const val WORD_GAP_EM: Float = 0.35f

    /**
     * سقف ارتفاع السطر: نص السطر (بالتشكيل) قد يصل إلى LINE_CAP من
     * الانسجام الشبكي (خط KFGQPC ارتفاعه ≈1.76em) قبل أن يتقلص الخط.
     */
    const val LINE_CAP: Float = 1.0f

    /** نسبة سطر مصحف المدينيّة 1440×232 (mushaf-imad QuranPageView.kt) — ميل السطر الطبيعي من العرض. */
    const val LINE_ASPECT_1440x232: Float = 1440f / 232f
    /** حدود تحجيم الخط (px) — حماية عامة (الملء العرضي الكامل يوصل ~64.4). */
    const val FONT_MIN: Float = 14f
    const val FONT_MAX: Float = 68f

    /** ألوان خارج/داخل الصفحة (تُمرَّر الألوان الفعلية من العارض). */
    const val PAGE_FRAME_ALPHA: Int = 60
}