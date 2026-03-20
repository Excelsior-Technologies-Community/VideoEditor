package com.videoeditor.lib.engine

import android.opengl.GLES20
import android.util.Log

class GlShaderProgram(vertexSrc: String, fragmentSrc: String) {
    val programId: Int
    private val uniformCache = HashMap<String, Int>()

    init {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        programId = GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vs)
            GLES20.glAttachShader(prog, fs)
            GLES20.glLinkProgram(prog)
            val status = IntArray(1)
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                val log = GLES20.glGetProgramInfoLog(prog)
                GLES20.glDeleteProgram(prog)
                Log.e("GlShaderProgram", "Program link failed: $log")
                throw RuntimeException("Program link failed: $log")
            }
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
    }

    private fun compileShader(type: Int, src: String): Int {
        val cleanSrc = src.replace("\r", "")
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, cleanSrc)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            Log.e("GlShaderProgram", "Shader compile failed (type=$type): $log\nSource:\n$src")
            throw RuntimeException("Shader compile failed (type=$type): $log")
        }
        return shader
    }

    fun use() = GLES20.glUseProgram(programId)
    fun getAttribLocation(name: String): Int = GLES20.glGetAttribLocation(programId, name)
    private fun uniformLocation(name: String): Int = uniformCache.getOrPut(name) { GLES20.glGetUniformLocation(programId, name) }
    fun setUniformMat4(name: String, matrix: FloatArray) { GLES20.glUniformMatrix4fv(uniformLocation(name), 1, false, matrix, 0) }
    fun setUniform1i(name: String, value: Int) { GLES20.glUniform1i(uniformLocation(name), value) }
    fun setUniform1f(name: String, value: Float) { GLES20.glUniform1f(uniformLocation(name), value) }
    fun delete() { GLES20.glDeleteProgram(programId); uniformCache.clear() }
}
