package com.videoeditor.lib.engine

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import com.videoeditor.lib.filters.FilterParams
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class VideoRenderer(
    private val context: Context,
    private val vertexShaderSource: String,
    private val fragmentShaderSource: String
) {
    private lateinit var shader: GlShaderProgram
    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    private val mvpMatrix  = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)

    private var aPosition    = 0
    private var aTexCoord    = 0

    companion object {
        private val QUAD_VERTS = floatArrayOf(-1f, -1f, 0f, 1f, -1f, 0f, -1f, 1f, 0f, 1f, 1f, 0f)
        private val QUAD_TEX = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
    }

    init {
        vertexBuffer = ByteBuffer.allocateDirect(QUAD_VERTS.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().also { it.put(QUAD_VERTS); it.position(0) }
        texCoordBuffer = ByteBuffer.allocateDirect(QUAD_TEX.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().also { it.put(QUAD_TEX); it.position(0) }
    }

    fun init() {
        shader = GlShaderProgram(vertexShaderSource, fragmentShaderSource)
        aPosition = shader.getAttribLocation("aPosition")
        aTexCoord = shader.getAttribLocation("aTextureCoord")
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.setIdentityM(mvpMatrix, 0)
    }

    fun drawFrame(videoTexture: VideoTexture, params: FilterParams) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        shader.use()
        shader.setUniformMat4("uMVPMatrix", mvpMatrix)
        shader.setUniformMat4("uSTMatrix", videoTexture.getTransformMatrix())
        shader.setUniform1f("uBrightness",  params.brightness)
        shader.setUniform1f("uContrast",    params.contrast)
        shader.setUniform1f("uSaturation",  params.saturation)
        shader.setUniform1i("uFilterType",  params.filterType)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(0x8D65, videoTexture.textureId)
        shader.setUniform1i("sTexture", 0)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aTexCoord)
    }

    fun release() { if (::shader.isInitialized) shader.delete() }
}
