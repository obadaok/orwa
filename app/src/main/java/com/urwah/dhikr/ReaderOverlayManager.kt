package com.urwah.dhikr

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.view.View

class ReaderOverlayManager {

    enum class Overlay { NONE, TOC, SETTINGS_PANEL, JUMP_PANEL, SEARCH_PANEL, CIRCULAR_MENU, FONT_PICKER }

    private var _current: Overlay = Overlay.NONE
    val currentOverlay: Overlay get() = _current

    private var isTransitioning = false

    var onTocShow: (() -> Unit)? = null
    var onTocHide: (() -> Unit)? = null
    var onTocHideAnimated: ((onDone: () -> Unit) -> Unit)? = null

    var onSettingsPanelShow: (() -> Unit)? = null
    var onSettingsPanelHide: (() -> Unit)? = null
    var onSettingsPanelHideAnimated: ((onDone: () -> Unit) -> Unit)? = null

    var onJumpPanelShow: (() -> Unit)? = null
    var onJumpPanelHide: (() -> Unit)? = null
    var onJumpPanelHideAnimated: ((onDone: () -> Unit) -> Unit)? = null

    var onSearchPanelShow: (() -> Unit)? = null
    var onSearchPanelHide: (() -> Unit)? = null
    var onSearchPanelHideAnimated: ((onDone: () -> Unit) -> Unit)? = null

    var onCircularMenuShow: (() -> Unit)? = null
    var onCircularMenuHide: (() -> Unit)? = null

    var onFontPickerShow: (() -> Unit)? = null
    var onFontPickerHide: (() -> Unit)? = null
    var onFontPickerHideAnimated: ((onDone: () -> Unit) -> Unit)? = null

    var onOverlayActiveChanged: ((isActive: Boolean) -> Unit)? = null

    val isActive: Boolean get() = _current != Overlay.NONE

    fun open(overlay: Overlay, closeAnimated: Boolean = true, onFullyOpened: (() -> Unit)? = null) {
        if (overlay == _current) return
        isTransitioning = false

        val previous = _current
        if (previous == Overlay.NONE) {
            _current = overlay
            onOverlayActiveChanged?.invoke(true)
            notifyShow(overlay)
            onFullyOpened?.invoke()
            return
        }

        isTransitioning = true
        notifyHide(previous, closeAnimated) {
            _current = overlay
            notifyShow(overlay)
            onFullyOpened?.invoke()
            isTransitioning = false
        }
    }

    fun closeCurrent(closeAnimated: Boolean = true, onFullyClosed: (() -> Unit)? = null) {
        if (_current == Overlay.NONE) {
            isTransitioning = false
            onFullyClosed?.invoke()
            return
        }

        isTransitioning = true
        val previous = _current
        _current = Overlay.NONE
        onOverlayActiveChanged?.invoke(false)
        notifyHide(previous, closeAnimated) {
            onFullyClosed?.invoke()
            isTransitioning = false
        }
    }

    fun closeAll() {
        isTransitioning = false
        val prev = _current
        _current = Overlay.NONE
        onOverlayActiveChanged?.invoke(false)
        when (prev) {
            Overlay.TOC -> onTocHide?.invoke()
            Overlay.SETTINGS_PANEL -> onSettingsPanelHide?.invoke()
            Overlay.JUMP_PANEL -> onJumpPanelHide?.invoke()
            Overlay.SEARCH_PANEL -> onSearchPanelHide?.invoke()
            Overlay.CIRCULAR_MENU -> onCircularMenuHide?.invoke()
            Overlay.FONT_PICKER -> onFontPickerHide?.invoke()
            else -> {}
        }
    }

    fun forceReset() {
        isTransitioning = false
        _current = Overlay.NONE
        onOverlayActiveChanged?.invoke(false)
    }

    private fun notifyShow(overlay: Overlay) {
        when (overlay) {
            Overlay.TOC -> onTocShow?.invoke()
            Overlay.SETTINGS_PANEL -> onSettingsPanelShow?.invoke()
            Overlay.JUMP_PANEL -> onJumpPanelShow?.invoke()
            Overlay.SEARCH_PANEL -> onSearchPanelShow?.invoke()
            Overlay.CIRCULAR_MENU -> onCircularMenuShow?.invoke()
            Overlay.FONT_PICKER -> onFontPickerShow?.invoke()
            else -> {}
        }
    }

    private fun notifyHide(overlay: Overlay, animated: Boolean, onDone: () -> Unit) {
        when (overlay) {
            Overlay.TOC -> {
                if (animated) onTocHideAnimated?.invoke(onDone)
                else { onTocHide?.invoke(); onDone() }
            }
            Overlay.SETTINGS_PANEL -> {
                if (animated) onSettingsPanelHideAnimated?.invoke(onDone)
                else { onSettingsPanelHide?.invoke(); onDone() }
            }
            Overlay.JUMP_PANEL -> {
                if (animated) onJumpPanelHideAnimated?.invoke(onDone)
                else { onJumpPanelHide?.invoke(); onDone() }
            }
            Overlay.SEARCH_PANEL -> {
                if (animated) onSearchPanelHideAnimated?.invoke(onDone)
                else { onSearchPanelHide?.invoke(); onDone() }
            }
            Overlay.CIRCULAR_MENU -> {
                onCircularMenuHide?.invoke()
                onDone()
            }
            Overlay.FONT_PICKER -> {
                if (animated) onFontPickerHideAnimated?.invoke(onDone)
                else { onFontPickerHide?.invoke(); onDone() }
            }
            else -> onDone()
        }
    }
}
