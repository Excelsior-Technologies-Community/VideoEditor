package com.videoeditor.lib.engine

import android.opengl.*

class EglCore(sharedContext: EGLContext = EGL14.EGL_NO_CONTEXT) {
    val eglDisplay: EGLDisplay
    val eglContext: EGLContext
    private val eglConfig: EGLConfig

    init {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY).also {
            check(it != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
        }
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
        eglConfig = configs[0]!!

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, sharedContext, ctxAttribs, 0)
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "Context creation failed" }
    }

    fun createWindowSurface(surface: Any): EGLSurface {
        val attribs = intArrayOf(EGL14.EGL_NONE)
        return EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, attribs, 0)
    }

    fun makeCurrent(surface: EGLSurface) {
        EGL14.eglMakeCurrent(eglDisplay, surface, surface, eglContext)
    }

    fun swapBuffers(surface: EGLSurface): Boolean = EGL14.eglSwapBuffers(eglDisplay, surface)

    fun setPresentationTime(surface: EGLSurface, nsecs: Long) {
        EGLExt.eglPresentationTimeANDROID(eglDisplay, surface, nsecs)
    }

    fun destroySurface(surface: EGLSurface) { EGL14.eglDestroySurface(eglDisplay, surface) }

    fun release() {
        EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglTerminate(eglDisplay)
    }
}
