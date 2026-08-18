package com.capylabs.vrlauncher

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import kotlin.math.max

class VrSceneView(context: Context) : GLSurfaceView(context) {
    private val renderer = Renderer()
    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
    fun setHead(yaw: Float, pitch: Float) = queueEvent { renderer.yaw = yaw; renderer.pitch = pitch }

    private class Renderer : GLSurfaceView.Renderer {
        var yaw = 0f
        var pitch = 0f
        private var t = 0f
        override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
            GLES20.glClearColor(0.008f, 0.012f, 0.022f, 1f)
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        }
        override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
        }
        override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
            t += 0.016f
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            // Cardboard-style side-by-side stereo foundation. Geometry is intentionally minimal
            // in this first commit; the compositor will be replaced by textured scene rendering.
            val vp = IntArray(4)
            GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vp, 0)
            val eyeWidth = max(1, vp[2] / 2)
            for (eye in 0..1) {
                GLES20.glViewport(eye * eyeWidth, 0, eyeWidth, vp[3])
                GLES20.glClearColor(0.008f + eye * 0.002f, 0.012f, 0.022f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            }
        }
    }
}
