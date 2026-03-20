package com.videoeditor.lib.engine

import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.util.Log

class VideoPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    var onPrepared: (() -> Unit)? = null
    var onCompletion: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onProgressUpdate: ((Long, Long) -> Unit)? = null

    private var progressRunnable: Runnable? = null

    val isPlaying: Boolean get() = mediaPlayer?.isPlaying == true
    val duration:  Long    get() = mediaPlayer?.duration?.toLong() ?: 0L
    val currentPosition: Long get() = mediaPlayer?.currentPosition?.toLong() ?: 0L

    fun prepare(uri: Uri, context: android.content.Context, surface: Surface) {
        release()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setSurface(surface)
                isLooping = false
                setOnPreparedListener {
                    onPrepared?.invoke()
                    startProgressTracking()
                }
                setOnCompletionListener { onCompletion?.invoke() }
                setOnErrorListener { _, what, extra ->
                    val msg = "MediaPlayer error: what=$what, extra=$extra"
                    Log.e("VideoPlayer", msg)
                    onError?.invoke(msg)
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("VideoPlayer", "Failed to prepare MediaPlayer", e)
            onError?.invoke(e.message ?: "Unknown error")
        }
    }

    fun play()  { mediaPlayer?.start() }
    fun pause() { mediaPlayer?.pause() }
    fun seekTo(ms: Long) { mediaPlayer?.seekTo(ms.toInt()) }

    private fun startProgressTracking() {
        progressRunnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        onProgressUpdate?.invoke(it.currentPosition.toLong(), it.duration.toLong())
                    }
                }
                mainHandler.postDelayed(this, 100)
            }
        }
        mainHandler.post(progressRunnable!!)
    }

    fun release() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        mediaPlayer?.apply {
            try {
                if (isPlaying) stop()
                release()
            } catch (e: Exception) {
                Log.e("VideoPlayer", "Error releasing MediaPlayer", e)
            }
        }
        mediaPlayer = null
    }
}
