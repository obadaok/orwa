package com.urwah.dhikr

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat

/**
 * اللوحة الكاملة لمظهر المرتّل — قيم ألوان محلولة (ARGB) تُمرَّر موحّدةً لكل عنصر.
 * أي مظهر جديد يُضاف = إضافة ألوانه + تعريفه فقط، وتُبنى قيمه هنا تلقائيًا.
 */
data class MurattalPalette(
    val background: Int,
    val surface: Int,
    val surfaceBorder: Int,
    val shadow: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val accent: Int,
    val accentText: Int,
    val divider: Int,
    val highlight: Int,
    val quranFrame: Int,
    val quranFrameBorder: Int,
    val progressTrack: Int,
    val progressFill: Int,
    val progressThumb: Int,
    val dim: Int,
    val iconTint: Int,
    val showOrnament: Boolean
)

data class MurattalTheme(
    val id: String,
    val name: String,
    @ColorRes val backgroundRes: Int,
    @ColorRes val cardRes: Int,
    @ColorRes val cardShadowRes: Int,
    @ColorRes val textPrimaryRes: Int,
    @ColorRes val textSecondaryRes: Int,
    @ColorRes val accentRes: Int,
    @ColorRes val accentTextRes: Int,
    @ColorRes val dividerRes: Int,
    @ColorRes val highlightRes: Int,
    @ColorRes val quranFrameRes: Int,
    @ColorRes val dimRes: Int,
    val showOrnament: Boolean
)

object MurattalThemeManager {

    const val PREFS_NAME = "urwah_settings"
    const val KEY_THEME = "murattal_theme"
    const val THEME_CLASSIC = "classic"
    const val THEME_DARK = "dark_luxury"
    const val THEME_PAPER = "paper"
    const val THEME_CALM = "calm"
    const val THEME_ORNATE = "ornate"

    const val DEFAULT_CLASSIC = THEME_CLASSIC

    val themes = listOf(
        MurattalTheme(
            id = THEME_CLASSIC,
            name = "تقليدي",
            backgroundRes = R.color.murattal_classic_bg,
            cardRes = R.color.murattal_classic_card,
            cardShadowRes = R.color.murattal_classic_shadow,
            textPrimaryRes = R.color.murattal_classic_text_primary,
            textSecondaryRes = R.color.murattal_classic_text_secondary,
            accentRes = R.color.murattal_classic_accent,
            accentTextRes = R.color.murattal_classic_accent_text,
            dividerRes = R.color.murattal_classic_divider,
            highlightRes = R.color.murattal_classic_highlight,
            quranFrameRes = R.color.murattal_classic_quran_frame,
            dimRes = R.color.murattal_classic_dim,
            showOrnament = false
        ),
        MurattalTheme(
            id = THEME_DARK,
            name = "داكن فاخر",
            showOrnament = true,
            backgroundRes = R.color.murattal_dark_bg,
            cardRes = R.color.murattal_dark_card,
            cardShadowRes = R.color.murattal_dark_shadow,
            textPrimaryRes = R.color.murattal_dark_text_primary,
            textSecondaryRes = R.color.murattal_dark_text_secondary,
            accentRes = R.color.murattal_dark_accent,
            accentTextRes = R.color.murattal_dark_accent_text,
            dividerRes = R.color.murattal_dark_divider,
            highlightRes = R.color.murattal_dark_highlight,
            quranFrameRes = R.color.murattal_dark_quran_frame,
            dimRes = R.color.murattal_dark_dim
        ),
        MurattalTheme(
            id = THEME_PAPER,
            name = "ورقي / مخطوط",
            showOrnament = true,
            backgroundRes = R.color.murattal_paper_bg,
            cardRes = R.color.murattal_paper_card,
            cardShadowRes = R.color.murattal_paper_shadow,
            textPrimaryRes = R.color.murattal_paper_text_primary,
            textSecondaryRes = R.color.murattal_paper_text_secondary,
            accentRes = R.color.murattal_paper_accent,
            accentTextRes = R.color.murattal_paper_accent_text,
            dividerRes = R.color.murattal_paper_divider,
            highlightRes = R.color.murattal_paper_highlight,
            quranFrameRes = R.color.murattal_paper_quran_frame,
            dimRes = R.color.murattal_paper_dim
        ),
        MurattalTheme(
            id = THEME_CALM,
            name = "هادئ وبسيط",
            showOrnament = false,
            backgroundRes = R.color.murattal_calm_bg,
            cardRes = R.color.murattal_calm_card,
            cardShadowRes = R.color.murattal_calm_shadow,
            textPrimaryRes = R.color.murattal_calm_text_primary,
            textSecondaryRes = R.color.murattal_calm_text_secondary,
            accentRes = R.color.murattal_calm_accent,
            accentTextRes = R.color.murattal_calm_accent_text,
            dividerRes = R.color.murattal_calm_divider,
            highlightRes = R.color.murattal_calm_highlight,
            quranFrameRes = R.color.murattal_calm_quran_frame,
            dimRes = R.color.murattal_calm_dim
        ),
        MurattalTheme(
            id = THEME_ORNATE,
            name = "زخرفي قرآني",
            showOrnament = true,
            backgroundRes = R.color.murattal_ornate_bg,
            cardRes = R.color.murattal_ornate_card,
            cardShadowRes = R.color.murattal_ornate_shadow,
            textPrimaryRes = R.color.murattal_ornate_text_primary,
            textSecondaryRes = R.color.murattal_ornate_text_secondary,
            accentRes = R.color.murattal_ornate_accent,
            accentTextRes = R.color.murattal_ornate_accent_text,
            dividerRes = R.color.murattal_ornate_divider,
            highlightRes = R.color.murattal_ornate_highlight,
            quranFrameRes = R.color.murattal_ornate_quran_frame,
            dimRes = R.color.murattal_ornate_dim
        )
    )

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun current(context: Context): MurattalTheme {
        val id = prefs(context).getString(KEY_THEME, DEFAULT_CLASSIC) ?: DEFAULT_CLASSIC
        return themes.firstOrNull { it.id == id } ?: themes.first()
    }

    fun select(context: Context, id: String) {
        prefs(context).edit().putString(KEY_THEME, id).apply()
    }

    fun color(context: Context, @ColorRes res: Int): Int =
        ContextCompat.getColor(context, res)

    /** يبني لوحة الألوان الكاملة المحدَّدة (الهاي لايت / الثيم/اللايت) من تعريف المظهر. */
    fun palette(context: Context, theme: MurattalTheme): MurattalPalette {
        val bg = color(context, theme.backgroundRes)
        val surface = color(context, theme.cardRes)
        val border = color(context, theme.textPrimaryRes)
        val accent = color(context, theme.accentRes)
        val secondary = color(context, theme.textSecondaryRes)
        val divider = color(context, theme.dividerRes)
        return MurattalPalette(
            background = bg,
            surface = surface,
            surfaceBorder = border,
            shadow = color(context, theme.cardShadowRes),
            textPrimary = border,
            textSecondary = secondary,
            accent = accent,
            accentText = color(context, theme.accentTextRes),
            divider = divider,
            highlight = color(context, theme.highlightRes),
            quranFrame = color(context, theme.quranFrameRes),
            quranFrameBorder = border,
            progressTrack = withAlpha(accent, 0x26),
            progressFill = accent,
            progressThumb = accent,
            dim = color(context, theme.dimRes),
            iconTint = secondary,
            showOrnament = theme.showOrnament
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    fun dpToPx(context: Context, dp: Float): Float =
        dp * context.resources.displayMetrics.density

    /** بطاقة neo-brutalism بظل صلب Offset + حدّ سميك — من ألوان اللوحة. */
    fun neoCardDrawable(
        context: Context,
        palette: MurattalPalette,
        radiusDp: Float = 16f,
        strokeDp: Float = 2.5f,
        shadowOffsetDp: Float = 5f,
        fill: Int? = null
    ): LayerDrawable {
        val radius = dpToPx(context, radiusDp)
        val stroke = dpToPx(context, strokeDp).toInt()
        val offset = dpToPx(context, shadowOffsetDp).toInt()
        val colorFill = fill ?: palette.surface

        val shadow = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(palette.shadow)
        }
        val card = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(colorFill)
            setStroke(stroke, palette.surfaceBorder)
        }

        val items = arrayOfNulls<android.graphics.drawable.Drawable>(2)
        items[0] = shadow.apply { setBounds(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE) }
        items[1] = card.apply { setBounds(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE) }
        return LayerDrawable(items).apply {
            setLayerInset(0, offset, offset, -offset, -offset)
            setLayerInset(1, 0, 0, 0, 0)
        }
    }

    /** شريط تقدمseekbar من ألوان اللوحة. */
    fun seekbarProgressDrawable(context: Context, palette: MurattalPalette): LayerDrawable {
        val track = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 3f)
            setColor(palette.progressTrack)
        }
        val clip = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 3f)
            setColor(palette.progressFill)
        }
        val clipGravity = if (
            context.resources.configuration.layoutDirection ==
            android.view.View.LAYOUT_DIRECTION_RTL
        ) {
            android.view.Gravity.RIGHT
        } else {
            android.view.Gravity.LEFT
        }
        val clipDrawable = android.graphics.drawable.ClipDrawable(
            clip, clipGravity, android.graphics.drawable.ClipDrawable.HORIZONTAL
        )
        val layers = arrayOfNulls<android.graphics.drawable.Drawable>(2).apply {
            set(0, track)
            set(1, clipDrawable)
        }
        return LayerDrawable(layers)
    }

    /** مقبض التقدم مقاس ثابت من ألوان اللوحة. */
    fun seekbarThumbDrawable(context: Context, palette: MurattalPalette): GradientDrawable {
        val size = dpToPx(context, 16f).toInt()
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setSize(size, size)
            setColor(palette.progressThumb)
            setStroke(dpToPx(context, 3f).toInt(), palette.surface)
        }
    }

    /** قرص زر دائري (كبعد المعاينة) من اللوحة. */
    fun circleDrawable(context: Context, palette: MurattalPalette, fill: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            cornerRadius = dpToPx(context, 50f)
            setColor(fill)
        }
}