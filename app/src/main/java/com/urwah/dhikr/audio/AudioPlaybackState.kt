package com.urwah.dhikr.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * الحالة الحالية للتشغيل — تُحدَّث من [AudioPlayerService] وتُراقَب من
 * صفحة المصحف (لتظليل الآية الحالية وإظهار المشغل).
 */
object AudioPlaybackState {

    data class PlaybackUiState(
        val isActive: Boolean = false,
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val surahNumber: Int = 0,
        val currentAyah: Int = 0,
        val totalAyahs: Int = 0,
        val reciterId: Int = 0,
        val reciterName: String = "",
        val speed: Float = 1f,
        val repeatMode: Int = androidx.media3.common.Player.REPEAT_MODE_OFF,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L
    )

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    fun update(transform: (PlaybackUiState) -> PlaybackUiState) {
        _state.value = transform(_state.value)
    }

    fun reset() {
        _state.value = PlaybackUiState()
    }
}
