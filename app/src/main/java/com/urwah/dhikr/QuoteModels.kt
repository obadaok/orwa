package com.urwah.dhikr

import android.graphics.Color

data class TextSpan(
    val start: Int,
    val end: Int,
    val effect: QuoteEffect,
    val params: MutableMap<String, Any> = mutableMapOf()
)

enum class QuoteEffect {
    NONE,
    HIGHLIGHT,
    TEXT_COLOR,
    BOLD,
    UNDERLINE,
    BLUR,
    DIM,
    HIDE,
    SCALE_UP,
    SCALE_DOWN
}

enum class QuoteBackground(
    val displayName: String,
    val bgColor: Int,
    val textColor: Int,
    val metaColor: Int,
    val dividerColor: Int,
    val isNight: Boolean = false
) {
    LIGHT("فاتح", Color.parseColor("#F7F5F0"), Color.parseColor("#5E4B40"), Color.parseColor("#8B6F5E"), Color.parseColor("#C4AFA3")),
    WARM("دافئ", Color.parseColor("#FFFDFA"), Color.parseColor("#5E4B40"), Color.parseColor("#8B6F5E"), Color.parseColor("#E4D9CF")),
    PAPER("ورقي", Color.parseColor("#F5F0E8"), Color.parseColor("#3D2E24"), Color.parseColor("#6B5744"), Color.parseColor("#D4C9B8")),
    URWAH("عروة", Color.parseColor("#F0EBE5"), Color.parseColor("#5E4B40"), Color.parseColor("#8B6F5E"), Color.parseColor("#C4AFA3")),
    DARK("ليلي", Color.parseColor("#1A1714"), Color.parseColor("#E8DFD3"), Color.parseColor("#C4AFA3"), Color.parseColor("#3D2E24"), isNight = true),
    DARK_BROWN("بني", Color.parseColor("#2C2118"), Color.parseColor("#E8DFD3"), Color.parseColor("#C4AFA3"), Color.parseColor("#5E4B40"), isNight = true);

    companion object {
        fun getDefault() = LIGHT
    }
}

data class QuoteSpanStyle(
    var bgColor: Int = Color.TRANSPARENT,
    var textColor: Int? = null,
    var isBold: Boolean = false,
    var isUnderlined: Boolean = false,
    var isBlurred: Boolean = false,
    var isDimmed: Boolean = false,
    var isHidden: Boolean = false,
    var scaleFactor: Float = 1f
) : java.io.Serializable
