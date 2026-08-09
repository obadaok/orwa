package com.urwah.dhikr.audio

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.google.common.collect.ImmutableList
import com.urwah.dhikr.R

/**
 * إشعار Media مخصص بهوية عروة — سورة، قارئ، شريط تقدم، وأزرار
 * (سابق / تشغيل / تالٍ / إيقاف) مع دعم شاشة القفل والسماعات والسيارة.
 */
@UnstableApi
class UrwahNotificationProvider(
    private val context: android.content.Context,
    private val player: Player
) : MediaNotification.Provider {

    private var callback: MediaNotification.Provider.Callback? = null
    private var session: MediaSession? = null
    private var actionFactory: MediaNotification.ActionFactory? = null

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (player.isPlaying) {
                refresh()
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            refresh()
            mainHandler.removeCallbacks(progressRunnable)
            if (isPlaying) {
                mainHandler.postDelayed(progressRunnable, 1000L)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            refresh()
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            refresh()
        }
    }

    init {
        player.addListener(playerListener)
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        this.callback = onNotificationChangedCallback
        this.session = mediaSession
        this.actionFactory = actionFactory
        mainHandler.removeCallbacks(progressRunnable)
        if (player.isPlaying) {
            mainHandler.postDelayed(progressRunnable, 1000L)
        }
        return buildNotification(mediaSession, actionFactory)
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle
    ): Boolean = false

    private fun buildNotification(
        session: MediaSession,
        actionFactory: MediaNotification.ActionFactory
    ): MediaNotification {
        val metadata = player.mediaMetadata
        val surahName = metadata.title?.toString() ?: "التلاوة"
        val reciterName = metadata.artist?.toString() ?: ""
        val ayahNum = metadata.description?.toString()?.toIntOrNull()
        val contentText = buildString {
            append(reciterName)
            if (ayahNum != null) {
                append(" • الآية $ayahNum")
            }
        }

        val showPause = player.isPlaying || player.playWhenReady
        val prevAction = actionFactory.createMediaAction(
            session,
            IconCompat.createWithResource(context, R.drawable.ic_media_previous),
            "الآية السابقة",
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        )
        val playPauseAction = actionFactory.createMediaAction(
            session,
            IconCompat.createWithResource(
                context,
                if (showPause) R.drawable.ic_media_pause else R.drawable.ic_play_arrow
            ),
            if (showPause) "إيقاف مؤقت" else "تشغيل",
            Player.COMMAND_PLAY_PAUSE
        )
        val nextAction = actionFactory.createMediaAction(
            session,
            IconCompat.createWithResource(context, R.drawable.ic_media_next),
            "الآية التالية",
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
        )
        val stopAction = actionFactory.createMediaAction(
            session,
            IconCompat.createWithResource(context, R.drawable.ic_media_stop),
            "إيقاف",
            Player.COMMAND_STOP
        )

        val builder = NotificationCompat.Builder(context, AudioPlayerService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(surahName)
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFF8B6F5E.toInt())
            .setDeleteIntent(
                actionFactory.createMediaActionPendingIntent(
                    session,
                    Player.COMMAND_STOP.toLong()
                )
            )
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(stopAction)
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        val largeIcon = try {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        } catch (_: Exception) {
            null
        }
        if (largeIcon != null) builder.setLargeIcon(largeIcon)

        val duration = player.duration
        if (duration > 0) {
            val progress = (player.currentPosition * 100 / duration).toInt().coerceIn(0, 100)
            builder.setProgress(100, progress, false)
        }

        return MediaNotification(NotificationId, builder.build())
    }

    private fun refresh() {
        val cb = callback ?: return
        val s = session ?: return
        val af = actionFactory ?: return
        val notification = buildNotification(s, af)
        cb.onNotificationChanged(notification)
        mainHandler.removeCallbacks(progressRunnable)
        if (player.isPlaying) {
            mainHandler.postDelayed(progressRunnable, 1000L)
        }
    }

    private companion object {
        const val NotificationId = 1001
    }
}
