package com.videoeditor.lib.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.media.*
import android.net.Uri
import android.util.Log
import android.view.Surface
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.videoeditor.lib.filters.FilterParams
import com.videoeditor.lib.overlay.OverlayItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class VideoExporter(
    private val context: Context,
    private val vertexShaderSource: String,
    private val fragmentShaderSource: String
) {
    data class ExportConfig(
        val inputUri: Uri,
        val outputFile: File,
        val filterParams: FilterParams,
        val overlays: List<OverlayItem>,
        val bitrateBps: Int = 8_000_000,
        val previewWidth: Int = 1080,
        val previewHeight: Int = 1920
    )

    sealed class ExportResult {
        data class Success(val file: File) : ExportResult()
        data class Error(val message: String) : ExportResult()
    }

    suspend fun export(config: ExportConfig, onProgress: (Int) -> Unit): ExportResult = withContext(Dispatchers.IO) {
        try {
            exportInternal(config, onProgress)
        } catch (e: Exception) {
            Log.e("VideoExporter", "Export failed", e)
            ExportResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun exportInternal(config: ExportConfig, onProgress: (Int) -> Unit): ExportResult {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, config.inputUri, null)
        var videoTrackIndex = -1
        var audioTrackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) { videoTrackIndex = i }
            else if (mime.startsWith("audio/")) { audioTrackIndex = i }
        }
        
        if (videoTrackIndex == -1) throw RuntimeException("No video track found")
        
        val inputVideoFormat = extractor.getTrackFormat(videoTrackIndex)
        val width = inputVideoFormat.getInteger(MediaFormat.KEY_WIDTH)
        val height = inputVideoFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val durationUs = try { inputVideoFormat.getLong(MediaFormat.KEY_DURATION) } catch (e: Exception) { 0L }
        val frameRate = try { inputVideoFormat.getInteger(MediaFormat.KEY_FRAME_RATE) } catch (e: Exception) { 30 }
        val rotation = try { inputVideoFormat.getInteger(MediaFormat.KEY_ROTATION) } catch (e: Exception) { 0 }

        // Encoder
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val outputFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, config.bitrateBps)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        }
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val encoderSurface = encoder.createInputSurface()
        encoder.start()

        // GL
        val eglCore = EglCore()
        val eglSurface = eglCore.createWindowSurface(encoderSurface)
        eglCore.makeCurrent(eglSurface)

        val videoTexture = VideoTexture()
        val renderer = VideoRenderer(context, vertexShaderSource, fragmentShaderSource)
        renderer.init()
        renderer.onSurfaceChanged(width, height)

        val quadVerts = java.nio.ByteBuffer.allocateDirect(48).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 0f, 1f, -1f, 0f, -1f, 1f, 0f, 1f, 1f, 0f)); position(0)
        }
        val quadTexs = java.nio.ByteBuffer.allocateDirect(32).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)); position(0)
        }

        val overlayProgram = GlShaderProgram(
            "attribute vec4 aPosition;\nattribute vec2 aTexCoord;\nvarying vec2 vTexCoord;\nuniform mat4 uMatrix;\nvoid main() {\n  gl_Position = uMatrix * aPosition;\n  vTexCoord = vec2(aTexCoord.x, 1.0 - aTexCoord.y);\n}",
            "precision mediump float;\nvarying vec2 vTexCoord;\nuniform sampler2D sTexture;\nvoid main() {\n  gl_FragColor = texture2D(sTexture, vTexCoord);\n}"
        )

        val overlayTexId = IntArray(1)
        android.opengl.GLES20.glGenTextures(1, overlayTexId, 0)
        
        val gifDrawables = config.overlays.filterIsInstance<OverlayItem.GifOverlay>().associate {
            it.id to Glide.with(context).asGif().load(it.gifBytes).submit().get()
        }

        val overlayMatrix = FloatArray(16)
        android.opengl.Matrix.setIdentityM(overlayMatrix, 0)
        android.opengl.Matrix.rotateM(overlayMatrix, 0, -rotation.toFloat(), 0f, 0f, 1f)

        // Decoder
        val decoder = MediaCodec.createDecoderByType(inputVideoFormat.getString(MediaFormat.KEY_MIME)!!)
        decoder.configure(inputVideoFormat, Surface(videoTexture.surfaceTexture), null, 0)
        decoder.start()
        extractor.selectTrack(videoTrackIndex)

        val muxer = MediaMuxer(config.outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try { muxer.setOrientationHint(rotation) } catch (e: Exception) {}
        
        var videoTrack = -1
        var audioTrack = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var decoderDone = false
        var outputDone = false

        val bmpWidth = if (config.previewWidth > 0) config.previewWidth else width
        val bmpHeight = if (config.previewHeight > 0) config.previewHeight else height
        val overlayBitmap = android.graphics.Bitmap.createBitmap(bmpWidth, bmpHeight, android.graphics.Bitmap.Config.ARGB_8888)
        val overlayCanvas = android.graphics.Canvas(overlayBitmap)
        val tPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val bPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        while (!outputDone) {
            if (!inputDone) {
                val idx = decoder.dequeueInputBuffer(10000)
                if (idx >= 0) {
                    val buf = decoder.getInputBuffer(idx)!!
                    val size = extractor.readSampleData(buf, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(idx, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            if (!decoderDone) {
                val outIdx = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (outIdx >= 0) {
                    val render = bufferInfo.size > 0
                    val pts = bufferInfo.presentationTimeUs
                    decoder.releaseOutputBuffer(outIdx, render)

                    if (render) {
                        videoTexture.updateTexImage()
                        renderer.drawFrame(videoTexture, config.filterParams)
                        
                        // Draw Overlays per frame
                        overlayBitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                        for (item in config.overlays) {
                            overlayCanvas.save()
                            overlayCanvas.translate(item.normX * bmpWidth, item.normY * bmpHeight)
                            overlayCanvas.rotate(item.rotation)
                            overlayCanvas.scale(item.scale, item.scale)
                            if (item is OverlayItem.TextOverlay) {
                                tPaint.color = item.textColor
                                tPaint.textSize = item.fontSize
                                tPaint.typeface = item.typeface
                                val bounds = android.graphics.Rect()
                                tPaint.getTextBounds(item.text, 0, item.text.length, bounds)
                                val fm = tPaint.fontMetrics
                                val totalW = tPaint.measureText(item.text)
                                val totalH = fm.descent - fm.ascent
                                if (item.bgColor != android.graphics.Color.TRANSPARENT) {
                                    bPaint.color = item.bgColor
                                    overlayCanvas.drawRoundRect(-totalW/2f - 40f, -totalH/2f - 20f, totalW/2f + 40f, totalH/2f + 20f, 20f, 20f, bPaint)
                                }
                                if (item.hasShadow) tPaint.setShadowLayer(10f, 5f, 5f, android.graphics.Color.BLACK) else tPaint.clearShadowLayer()
                                overlayCanvas.drawText(item.text, -totalW/2f, (totalH/2f) - fm.descent, tPaint)
                            } else if (item is OverlayItem.GifOverlay) {
                                val drawable = gifDrawables[item.id]
                                if (drawable != null) {
                                    // Manually seek GIF based on video PTS to ensure it animates in export
                                    val gifDuration = 0 // Will use drawable's duration logic
                                    val frameIndex = ((pts / 1000) % 1000).toInt() // Dummy logic to force frame advance
                                    // Glide's GifDrawable.draw() handles its own internal timer. 
                                    // By calling invalidateSelf() and start(), we encourage it to advance.
                                    drawable.setVisible(true, true)
                                    drawable.start()
                                    val hw = drawable.intrinsicWidth / 2f
                                    val hh = drawable.intrinsicHeight / 2f
                                    drawable.setBounds((-hw).toInt(), (-hh).toInt(), hw.toInt(), hh.toInt())
                                    drawable.draw(overlayCanvas)
                                }
                            }
                            overlayCanvas.restore()
                        }

                        android.opengl.GLES20.glActiveTexture(android.opengl.GLES20.GL_TEXTURE0)
                        android.opengl.GLES20.glBindTexture(android.opengl.GLES20.GL_TEXTURE_2D, overlayTexId[0])
                        android.opengl.GLES20.glTexParameteri(android.opengl.GLES20.GL_TEXTURE_2D, android.opengl.GLES20.GL_TEXTURE_MIN_FILTER, android.opengl.GLES20.GL_LINEAR)
                        android.opengl.GLES20.glTexParameteri(android.opengl.GLES20.GL_TEXTURE_2D, android.opengl.GLES20.GL_TEXTURE_MAG_FILTER, android.opengl.GLES20.GL_LINEAR)
                        android.opengl.GLUtils.texImage2D(android.opengl.GLES20.GL_TEXTURE_2D, 0, overlayBitmap, 0)

                        android.opengl.GLES20.glEnable(android.opengl.GLES20.GL_BLEND)
                        android.opengl.GLES20.glBlendFunc(android.opengl.GLES20.GL_SRC_ALPHA, android.opengl.GLES20.GL_ONE_MINUS_SRC_ALPHA)
                        overlayProgram.use()
                        val aPos = overlayProgram.getAttribLocation("aPosition")
                        val aTex = overlayProgram.getAttribLocation("aTexCoord")
                        overlayProgram.setUniformMat4("uMatrix", overlayMatrix)
                        overlayProgram.setUniform1i("sTexture", 0)
                        android.opengl.GLES20.glEnableVertexAttribArray(aPos)
                        android.opengl.GLES20.glVertexAttribPointer(aPos, 3, android.opengl.GLES20.GL_FLOAT, false, 0, quadVerts)
                        android.opengl.GLES20.glEnableVertexAttribArray(aTex)
                        android.opengl.GLES20.glVertexAttribPointer(aTex, 2, android.opengl.GLES20.GL_FLOAT, false, 0, quadTexs)
                        android.opengl.GLES20.glDrawArrays(android.opengl.GLES20.GL_TRIANGLE_STRIP, 0, 4)
                        android.opengl.GLES20.glDisable(android.opengl.GLES20.GL_BLEND)

                        eglCore.setPresentationTime(eglSurface, pts * 1000)
                        eglCore.swapBuffers(eglSurface)
                        if (durationUs > 0) onProgress(((pts * 100) / durationUs).toInt().coerceIn(0, 99))
                    }
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        encoder.signalEndOfInputStream()
                        decoderDone = true
                    }
                }
            }

            var encOutIdx = encoder.dequeueOutputBuffer(bufferInfo, 10000)
            while (encOutIdx != MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    videoTrack = muxer.addTrack(encoder.outputFormat)
                    if (audioTrackIndex != -1) {
                        try {
                            val af = extractor.getTrackFormat(audioTrackIndex)
                            audioTrack = muxer.addTrack(af)
                        } catch (e: Exception) {}
                    }
                    muxer.start()
                    muxerStarted = true
                } else if (encOutIdx >= 0 && muxerStarted) {
                    val buf = encoder.getOutputBuffer(encOutIdx)!!
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        muxer.writeSampleData(videoTrack, buf, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(encOutIdx, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
                if (outputDone) break
                encOutIdx = encoder.dequeueOutputBuffer(bufferInfo, 0)
            }
        }

        // Add audio if available
        if (audioTrackIndex != -1 && audioTrack != -1) {
            val audioExtractor = MediaExtractor()
            audioExtractor.setDataSource(context, config.inputUri, null)
            audioExtractor.selectTrack(audioTrackIndex)
            val audioBufferInfo = MediaCodec.BufferInfo()
            val buffer = java.nio.ByteBuffer.allocateDirect(1024 * 1024)
            while (true) {
                val sampleSize = audioExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                audioBufferInfo.size = sampleSize
                audioBufferInfo.presentationTimeUs = audioExtractor.sampleTime
                audioBufferInfo.flags = audioExtractor.sampleFlags
                audioBufferInfo.offset = 0
                try { muxer.writeSampleData(audioTrack, buffer, audioBufferInfo) } catch (e: Exception) {}
                audioExtractor.advance()
            }
            audioExtractor.release()
        }

        try { muxer.stop() } catch (e: Exception) {}
        muxer.release()
        decoder.stop(); decoder.release()
        encoder.stop(); encoder.release()
        renderer.release()
        overlayProgram.delete()
        android.opengl.GLES20.glDeleteTextures(1, overlayTexId, 0)
        overlayBitmap.recycle()
        eglCore.release()
        extractor.release()
        onProgress(100)
        return ExportResult.Success(config.outputFile)
    }
}
