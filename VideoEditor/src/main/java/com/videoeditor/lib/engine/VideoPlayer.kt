package com.videoeditor.lib.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class VideoPlayer(context: Context) {
    private var exoPlayer: ExoPlayer? = ExoPlayer.Builder(context).build()

    var onPrepared: (() -> Unit)? = null
    var onCompletion: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onProgressUpdate: ((Long, Long) -> Unit)? = null

    val isPlaying: Boolean get() = exoPlayer?.isPlaying == true
    val duration: Long get() = exoPlayer?.duration ?: 0L
    val currentPosition: Long get() = exoPlayer?.currentPosition ?: 0L

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    onPrepared?.invoke()
                }
                Player.STATE_ENDED -> {
                    onCompletion?.invoke()
                }
                Player.STATE_IDLE -> {}
                Player.STATE_BUFFERING -> {}
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
                events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
            ) {
                updateProgress()
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e("VideoPlayer", "ExoPlayer error", error)
            onError?.invoke(error.message ?: "Unknown ExoPlayer error")
        }
    }

    init {
        exoPlayer?.addListener(listener)
    }

    fun prepare(uri: Uri, surface: Surface) {
        exoPlayer?.apply {
            setVideoSurface(surface)
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }

    fun play() {
        exoPlayer?.playWhenReady = true
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun seekTo(ms: Long) {
        exoPlayer?.seekTo(ms)
    }

    private fun updateProgress() {
        exoPlayer?.let {
            onProgressUpdate?.invoke(it.currentPosition, it.duration)
        }
    }

    fun release() {
        exoPlayer?.removeListener(listener)
        exoPlayer?.release()
        exoPlayer = null
    }
}
