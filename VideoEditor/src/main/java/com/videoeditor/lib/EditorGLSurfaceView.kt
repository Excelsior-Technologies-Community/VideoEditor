package com.videoeditor.lib

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.Surface
import com.videoeditor.lib.engine.VideoRenderer
import com.videoeditor.lib.engine.VideoTexture
import com.videoeditor.lib.filters.FilterParams
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class EditorGLSurfaceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private var renderer: VideoRenderer? = null
    private var videoTexture: VideoTexture? = null
    private var pendingVertexSrc: String? = null
    private var pendingFragmentSrc: String? = null

    @Volatile var filterParams = FilterParams()
    var onSurfaceReady: ((Surface) -> Unit)? = null

    private val glRenderer = object : Renderer {
        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
            val vSrc = pendingVertexSrc
            val fSrc = pendingFragmentSrc
            if (vSrc != null && fSrc != null) {
                renderer = VideoRenderer(context, vSrc, fSrc).apply { init() }
            }
            
            videoTexture = VideoTexture().also { vt ->
                vt.surfaceTexture.setOnFrameAvailableListener { requestRender() }
                post { onSurfaceReady?.invoke(Surface(vt.surfaceTexture)) }
            }
        }

        override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
            renderer?.onSurfaceChanged(width, height)
        }

        override fun onDrawFrame(gl: GL10) {
            videoTexture?.let { vt ->
                vt.updateTexImage()
                renderer?.drawFrame(vt, filterParams)
            }
        }
    }

    init {
        setEGLContextClientVersion(2)
        setRenderer(glRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setShaders(vertexSrc: String, fragmentSrc: String) {
        val requireReinit = pendingVertexSrc != null
        pendingVertexSrc = vertexSrc
        pendingFragmentSrc = fragmentSrc
        if (requireReinit) {
            queueEvent {
                renderer?.release()
                renderer = VideoRenderer(context, vertexSrc, fragmentSrc).apply { init() }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        queueEvent { renderer?.release() }
    }
}
