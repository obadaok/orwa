package com.urwah.dhikr.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.urwah.dhikr.R
import com.urwah.dhikr.SurahDataProvider

/**
 * مشغل التلاوة — MediaSessionService يعمل بالخلفية مع إشعار Media
 * وتحكم من شاشة القفل وسماعات البلوتوث.
 */
class AudioPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var progressHandler: android.os.Handler? = null
    private val progressRunnable = object : Runnable {
        override fun run() {
            val p = player ?: return
            if (p.isPlaying) {
                val dur = p.duration.takeIf { it > 0 } ?: 0L
                AudioPlaybackState.update {
                    it.copy(positionMs = p.currentPosition, durationMs = dur)
                }
                progressHandler?.postDelayed(this, 500L)
            }
        }
    }

    companion object {
        const val ACTION_PLAY = "com.urwah.dhikr.audio.PLAY"
        const val ACTION_PAUSE = "com.urwah.dhikr.audio.PAUSE"
        const val ACTION_RESUME = "com.urwah.dhikr.audio.RESUME"
        const val ACTION_STOP = "com.urwah.dhikr.audio.STOP"
        const val ACTION_NEXT = "com.urwah.dhikr.audio.NEXT"
        const val ACTION_PREVIOUS = "com.urwah.dhikr.audio.PREVIOUS"
        const val ACTION_SPEED = "com.urwah.dhikr.audio.SPEED"
        const val ACTION_REPEAT = "com.urwah.dhikr.audio.REPEAT"
        const val ACTION_SEEK = "com.urwah.dhikr.audio.SEEK"
        const val ACTION_PLAY_RANGE = "com.urwah.dhikr.audio.PLAY_RANGE"

        const val EXTRA_SURAH = "extra_surah"
        const val EXTRA_START_AYAH = "extra_start_ayah"
        const val EXTRA_TOTAL_AYAHS = "extra_total_ayahs"
        const val EXTRA_END_AYAH = "extra_end_ayah"
        const val EXTRA_RECITER_ID = "extra_reciter_id"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_REPEAT_MODE = "extra_repeat_mode"
        const val EXTRA_POSITION = "extra_position"

        const val CHANNEL_ID = "urwah_audio_playback"
        const val CHANNEL_NAME = "مشغل التلاوة"

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "التحكم بتلاوة القرآن الكريم"
                    setShowBadge(false)
                }
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(channel)
            }
        }

        fun play(
            context: Context,
            surah: Int,
            startAyah: Int,
            totalAyahs: Int,
            reciterId: Int
        ) {
            val intent = Intent(context, AudioPlayerService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_SURAH, surah)
                putExtra(EXTRA_START_AYAH, startAyah)
                putExtra(EXTRA_TOTAL_AYAHS, totalAyahs)
                putExtra(EXTRA_RECITER_ID, reciterId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) {
            context.startService(Intent(context, AudioPlayerService::class.java).apply {
                action = ACTION_PAUSE
            })
        }

        fun resume(context: Context) {
            context.startService(Intent(context, AudioPlayerService::class.java).apply {
                action = ACTION_RESUME
            })
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AudioPlayerService::class.java).apply {
                action = ACTION_STOP
            })
        }

        fun next(context: Context) {
            context.startService(Intent(context, AudioPlayerService::class.java).apply {
                action = ACTION_NEXT
            })
        }

        fun previous(context: Context) {
            context.startService(Intent(context, AudioPlayerService::class.java).apply {
                action = ACTION_PREVIOUS
            })
        }

        fun setSpeed(context: Context, speed: Float) {
            context.startService(Intent(context, AudioPlayerService::class.java).apply {
                action = ACTION_SPEED
                putExtra(EXTRA_SPEED, speed)
            })
        }

        fun setRepeatMode(context: Context, mode: Int) {
            context.startService(Intent(context, AudioPlayerService::class.java).apply {
                action = ACTION_REPEAT
                putExtra(EXTRA_REPEAT_MODE, mode)
            })
        }

        fun seek(context: Context, positionMs: Long) {
            context.startService(Intent(context, AudioPlayerService::class.java).apply {
                action = ACTION_SEEK
                putExtra(EXTRA_POSITION, positionMs)
            })
        }

        fun playRange(
            context: Context,
            surah: Int,
            startAyah: Int,
            endAyah: Int,
            reciterId: Int
        ) {
            val intent = Intent(context, AudioPlayerService::class.java).apply {
                action = ACTION_PLAY_RANGE
                putExtra(EXTRA_SURAH, surah)
                putExtra(EXTRA_START_AYAH, startAyah)
                putExtra(EXTRA_END_AYAH, endAyah)
                putExtra(EXTRA_RECITER_ID, reciterId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel(this)

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = exoPlayer

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                AudioPlaybackState.update {
                    it.copy(isPlaying = isPlaying, isBuffering = false)
                }
                if (isPlaying) {
                    startProgressUpdates()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                AudioPlaybackState.update {
                    it.copy(isBuffering = playbackState == Player.STATE_BUFFERING)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val meta = mediaItem?.mediaMetadata
                val ayah = meta?.description?.toString()?.toIntOrNull()
                if (ayah != null) {
                    AudioPlaybackState.update { it.copy(currentAyah = ayah) }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                AudioPlaybackState.update { it.copy(isBuffering = false) }
            }
        })

        val session = MediaSession.Builder(this, exoPlayer).build()
        mediaSession = session
        addSession(session)
        val provider = UrwahNotificationProvider(this, exoPlayer)
        setMediaNotificationProvider(provider)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val exoPlayer = player ?: return super.onStartCommand(intent, flags, startId)

        when (action) {
            ACTION_PLAY -> {
                val surah = intent.getIntExtra(EXTRA_SURAH, 1)
                val startAyah = intent.getIntExtra(EXTRA_START_AYAH, 1)
                val totalAyahs = intent.getIntExtra(EXTRA_TOTAL_AYAHS, 7)
                val reciterId = intent.getIntExtra(EXTRA_RECITER_ID, 0)
                setupPlaylist(exoPlayer, surah, startAyah, totalAyahs, reciterId)
                exoPlayer.prepare()
                exoPlayer.play()
            }

            ACTION_PLAY_RANGE -> {
                val surah = intent.getIntExtra(EXTRA_SURAH, 1)
                val startAyah = intent.getIntExtra(EXTRA_START_AYAH, 1)
                val endAyah = intent.getIntExtra(EXTRA_END_AYAH, startAyah)
                val reciterId = intent.getIntExtra(EXTRA_RECITER_ID, 0)
                val count = (endAyah - startAyah + 1).coerceAtLeast(1)
                setupPlaylist(exoPlayer, surah, startAyah, count, reciterId, rangeStart = startAyah)
                exoPlayer.prepare()
                exoPlayer.play()
            }

            ACTION_PAUSE -> if (exoPlayer.isPlaying) exoPlayer.pause()

            ACTION_RESUME -> exoPlayer.play()

            ACTION_NEXT -> {
                if (exoPlayer.hasNextMediaItem()) {
                    exoPlayer.seekToNextMediaItem()
                } else {
                    exoPlayer.seekTo(0)
                }
            }

            ACTION_PREVIOUS -> {
                if (exoPlayer.hasPreviousMediaItem()) {
                    exoPlayer.seekToPreviousMediaItem()
                } else {
                    exoPlayer.seekTo(0)
                }
            }

            ACTION_SPEED -> {
                val speed = intent.getFloatExtra(EXTRA_SPEED, 1f)
                exoPlayer.setPlaybackSpeed(speed.coerceIn(0.25f, 3f))
                AudioPlaybackState.update { it.copy(speed = speed.coerceIn(0.25f, 3f)) }
            }

            ACTION_REPEAT -> {
                val mode = intent.getIntExtra(EXTRA_REPEAT_MODE, Player.REPEAT_MODE_OFF)
                exoPlayer.repeatMode = mode
                AudioPlaybackState.update { it.copy(repeatMode = mode) }
            }

            ACTION_SEEK -> {
                val position = intent.getLongExtra(EXTRA_POSITION, 0L)
                if (position >= 0 && exoPlayer.duration > 0) {
                    exoPlayer.seekTo(position.coerceAtMost(exoPlayer.duration))
                    AudioPlaybackState.update { it.copy(positionMs = position) }
                }
            }

            ACTION_STOP -> stopPlayback()
        }

        return START_NOT_STICKY
    }

    private fun setupPlaylist(
        exoPlayer: ExoPlayer,
        surah: Int,
        startAyah: Int,
        totalAyahs: Int,
        reciterId: Int,
        rangeStart: Int = 1
    ) {
        val reciter = ReciterCatalog.getById(reciterId)
        val surahName = SurahDataProvider.allSurahs
            .find { it.number == surah }?.name ?: "سورة $surah"

        // حماية من نطاق فارغ (مثلاً totalAyahs=0): coerceIn(0, -1) كان يرمي IllegalArgumentException
        if (totalAyahs <= 0) {
            AudioPlaybackState.update {
                AudioPlaybackState.PlaybackUiState(
                    isActive = false,
                    isPlaying = false,
                    surahNumber = surah,
                    currentAyah = 0,
                    totalAyahs = 0,
                    reciterId = reciterId,
                    reciterName = reciter.nameArabic,
                    speed = it.speed,
                    repeatMode = it.repeatMode
                )
            }
            return
        }

        val items = (rangeStart..(rangeStart + totalAyahs - 1)).map { ayah ->
            MediaItem.Builder()
                .setUri(reciter.ayahUrl(surah, ayah))
                .setMediaId("$surah:$ayah")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(surahName)
                        .setArtist(reciter.nameArabic)
                        .setDescription(ayah.toString())
                        .build()
                )
                .build()
        }

        val startIndex = (startAyah - rangeStart).coerceIn(0, items.lastIndex)
        exoPlayer.setMediaItems(items, startIndex, 0L)

        AudioPlaybackState.update {
            AudioPlaybackState.PlaybackUiState(
                isActive = true,
                isPlaying = false,
                surahNumber = surah,
                currentAyah = startAyah,
                totalAyahs = totalAyahs,
                reciterId = reciterId,
                reciterName = reciter.nameArabic,
                speed = it.speed,
                repeatMode = it.repeatMode
            )
        }
    }

    private fun startProgressUpdates() {
        if (progressHandler == null) {
            progressHandler = android.os.Handler(android.os.Looper.getMainLooper())
        }
        progressHandler?.removeCallbacks(progressRunnable)
        progressHandler?.post(progressRunnable)
    }

    private fun stopPlayback() {
        player?.let { p ->
            p.stop()
            p.clearMediaItems()
        }
        progressHandler?.removeCallbacks(progressRunnable)
        AudioPlaybackState.reset()
        stopSelf()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        progressHandler?.removeCallbacks(progressRunnable)
        progressHandler = null
        mediaSession?.run {
            player?.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }
}
