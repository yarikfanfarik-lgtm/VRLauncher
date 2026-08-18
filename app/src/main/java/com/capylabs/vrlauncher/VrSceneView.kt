package com.capylabs.vrlauncher

import android.content.Context
import android.graphics.*
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import kotlin.math.tan

/**
 * Real Cardboard stereo scene.
 * The desktop is a textured 3D plane in world space. Each eye has its own
 * asymmetric projection and camera position, giving real binocular depth.
 * 8x8 reveal animation is intentionally not implemented.
 */
class VrSceneView(context: Context) : GLSurfaceView(context) {
    private val renderer = VrRenderer()

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
    }

    fun setHead(yaw: Float, pitch: Float) = renderer.setHead(yaw, pitch)

    private class VrRenderer : Renderer {
        private val projection = FloatArray(16)
        private val view = FloatArray(16)
        private val model = FloatArray(16)
        private val mvp = FloatArray(16)
        private val head = FloatArray(16)
        @Volatile private var yaw = 0f
        @Volatile private var pitch = 0f
        private var texture = 0
        private var program = 0
        private var width = 1
        private var height = 1
        private var bitmap: Bitmap? = null

        fun setHead(newYaw: Float, newPitch: Float) {
            yaw = newYaw
            pitch = newPitch
        }

        override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
            GLES20.glClearColor(0.003f, 0.006f, 0.015f, 1f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glDisable(GLES20.GL_CULL_FACE)
            program = createProgram(VERTEX, FRAGMENT)
            texture = createTexture()
        }

        override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
            this.width = width
            this.height = height.coerceAtLeast(1)
            bitmap?.recycle()
            bitmap = makeDesktopBitmap(1280, 720)
            uploadTexture(bitmap!!)
        }

        override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            val half = (width / 2).coerceAtLeast(1)
            val aspect = half.toFloat() / height.toFloat()
            val near = 0.08f
            val far = 100f
            val top = tan(Math.toRadians(74.0).toFloat() / 2f) * near
            val right = top * aspect
            val ipd = 0.064f
            val convergence = 3.8f

            Matrix.setIdentityM(head, 0)
            Matrix.rotateM(head, 0, Math.toDegrees(-pitch.toDouble()).toFloat(), 1f, 0f, 0f)
            Matrix.rotateM(head, 0, Math.toDegrees(-yaw.toDouble()).toFloat(), 0f, 1f, 0f)

            for (eyeIndex in 0..1) {
                val eyeX = if (eyeIndex == 0) -ipd / 2f else ipd / 2f
                val shift = eyeX * near / convergence
                Matrix.frustumM(projection, 0, -right + shift, right + shift, -top, top, near, far)
                GLES20.glViewport(eyeIndex * half, 0, half, height)
                GLES20.glScissor(eyeIndex * half, 0, half, height)
                GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
                GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT)

                Matrix.setIdentityM(view, 0)
                Matrix.translateM(view, 0, -eyeX, 0f, 0f)
                Matrix.multiplyMM(view, 0, head, 0, view, 0)

                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, 0f, -0.05f, -3.8f)
                Matrix.scaleM(model, 0, 3.75f, 2.11f, 1f)
                Matrix.multiplyMM(mvp, 0, view, 0, model, 0)
                Matrix.multiplyMM(mvp, 0, projection, 0, mvp, 0)
                drawQuad(mvp)
            }
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        }

        private fun drawQuad(mvp: FloatArray) {
            GLES20.glUseProgram(program)
            val pos = GLES20.glGetAttribLocation(program, "aPosition")
            val uv = GLES20.glGetAttribLocation(program, "aTexCoord")
            val matrix = GLES20.glGetUniformLocation(program, "uMvp")
            val tex = GLES20.glGetUniformLocation(program, "uTexture")
            GLES20.glUniformMatrix4fv(matrix, 1, false, mvp, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glUniform1i(tex, 0)

            val vertices = floatArrayOf(
                -1f, 1f, 0f, 0f, 0f,
                -1f, -1f, 0f, 0f, 1f,
                1f, 1f, 0f, 1f, 0f,
                1f, -1f, 0f, 1f, 1f
            )
            val buffer = java.nio.ByteBuffer.allocateDirect(vertices.size * 4)
                .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
            buffer.put(vertices).position(0)
            GLES20.glEnableVertexAttribArray(pos)
            GLES20.glVertexAttribPointer(pos, 3, GLES20.GL_FLOAT, false, 20, buffer)
            buffer.position(3)
            GLES20.glEnableVertexAttribArray(uv)
            GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 20, buffer)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(pos)
            GLES20.glDisableVertexAttribArray(uv)
        }

        private fun createTexture(): Int {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            return ids[0]
        }

        private fun uploadTexture(b: Bitmap) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0)
        }

        private fun makeDesktopBitmap(w: Int, h: Int): Bitmap {
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            val bg = Paint(Paint.ANTI_ALIAS_FLAG)
            bg.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), Color.rgb(3, 8, 18), Color.rgb(16, 8, 35), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
            bg.shader = RadialGradient(w * .55f, h * .72f, w * .7f, Color.argb(130, 34, 95, 165), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
            bg.shader = null
            bg.color = Color.argb(150, 190, 215, 255)
            for (i in 0 until 60) c.drawCircle(((i * 197) % w).toFloat(), ((i * 83) % 420 + 12).toFloat(), if (i % 7 == 0) 2f else 1f, bg)

            glass(c, 30f, 36f, 220f, 680f, 30f)
            val menu = arrayOf("⌂   Домой", "▦   Приложения", "▣   Рабочий стол", "◈   Игры", "▤   Обои", "⚙   Настройки")
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 23f }
            val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(230, 242, 244, 250); textSize = 19f }
            menu.forEachIndexed { i, s ->
                val y = 78f + i * 72f
                if (i == 0) {
                    tp.color = Color.argb(130, 95, 88, 235)
                    c.drawRoundRect(43f, y - 29f, 207f, y + 23f, 18f, 18f, tp)
                    tp.color = Color.WHITE
                }
                c.drawText(s, 58f, y + 3f, if (i == 0) tp else lp)
            }

            glass(c, 275f, 30f, 1210f, 138f, 28f)
            val q = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 17f; typeface = Typeface.DEFAULT_BOLD }
            c.drawText("БЫСТРЫЕ ДЕЙСТВИЯ", 300f, 59f, q)
            val actions = arrayOf("↶" to "Назад", "⌂" to "Домой", "◷" to "Недавние", "▣" to "Окна", "◉" to "Скриншот", "●" to "Запись", "◌" to "Микрофон", "☼" to "Яркость")
            actions.forEachIndexed { i, a ->
                val x = 290f + i * 112f
                q.color = Color.argb(58, 255, 255, 255)
                c.drawRoundRect(x, 72f, x + 100f, 126f, 15f, 15f, q)
                q.color = Color.WHITE; q.textSize = 20f
                c.drawText(a.first, x + 40f, 96f, q)
                q.textSize = 11f
                c.drawText(a.second, x + 27f, 116f, q)
            }

            glass(c, 275f, 160f, 875f, 640f, 30f)
            q.color = Color.WHITE; q.textSize = 22f
            c.drawText("БИБЛИОТЕКА ПРИЛОЖЕНИЙ", 302f, 200f, q)
            q.color = Color.argb(48, 255, 255, 255)
            c.drawRoundRect(300f, 220f, 850f, 258f, 15f, 15f, q)
            q.color = Color.argb(160, 235, 238, 247); q.textSize = 14f
            c.drawText("⌕  Поиск приложений...", 320f, 245f, q)
            val names = arrayOf("YouTube", "Chrome", "Telegram", "VK", "Spotify", "TikTok", "Instagram", "WhatsApp", "Discord", "Gmail", "Maps", "Фото", "Play", "Камера", "Календарь", "Настройки")
            names.forEachIndexed { i, n ->
                val col = i % 4; val row = i / 4
                val x = 355f + col * 125f; val y = 310f + row * 78f
                q.color = Color.argb(100, 70 + (i * 23) % 130, 110, 240)
                c.drawRoundRect(x - 27f, y - 27f, x + 27f, y + 27f, 16f, 16f, q)
                q.color = Color.WHITE; q.textSize = 12f
                c.drawText(n, x - q.measureText(n) / 2f, y + 45f, q)
            }

            glass(c, 900f, 160f, 1248f, 640f, 30f)
            q.color = Color.WHITE; q.textSize = 20f
            c.drawText("АКТИВНЫЕ ОКНА", 925f, 200f, q)
            mini(c, 920f, 220f, 308f, 170f, "YouTube")
            mini(c, 920f, 410f, 308f, 170f, "Chrome")

            glass(c, 690f, 525f, 1040f, 695f, 24f)
            q.color = Color.WHITE; q.textSize = 13f
            c.drawText("VR КЛАВИАТУРА     🌐  Русский", 715f, 550f, q)
            val rows = arrayOf("Й Ц У К Е Н Г Ш Щ З Х Ъ", "Ф Ы В А П Р О Л Д Ж Э", "Я Ч С М И Т Ь Б Ю")
            rows.forEachIndexed { r, row ->
                row.split(' ').forEachIndexed { col, key ->
                    val x = 710f + col * 39f + if (r == 1) 15f else 0f; val y = 575f + r * 36f
                    q.color = Color.argb(70, 255, 255, 255)
                    c.drawRoundRect(x, y, x + 33f, y + 28f, 8f, 8f, q)
                    q.color = Color.WHITE; q.textSize = 11f
                    c.drawText(key, x + 10f, y + 19f, q)
                }
            }
            q.textSize = 12f
            c.drawText("🌐    ПРОБЕЛ                 ⌫", 730f, 688f, q)
            return b
        }

        private fun glass(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.argb(150, 10, 17, 30)
            p.setShadowLayer(24f, 0f, 8f, Color.argb(100, 0, 0, 0))
            c.drawRoundRect(l, t, r, b, radius, radius, p)
            p.clearShadowLayer(); p.style = Paint.Style.STROKE; p.strokeWidth = 2f
            p.color = Color.argb(135, 210, 225, 255)
            c.drawRoundRect(l, t, r, b, radius, radius, p)
        }

        private fun mini(c: Canvas, x: Float, y: Float, w: Float, h: Float, name: String) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.argb(65, 255, 255, 255)
            c.drawRoundRect(x, y, x + w, y + h, 16f, 16f, p)
            p.color = Color.WHITE; p.textSize = 15f
            c.drawText(name, x + 15f, y + 25f, p)
            p.color = Color.argb(35, 255, 255, 255)
            c.drawRect(x + 12f, y + 38f, x + w - 12f, y + h - 12f, p)
        }

        private fun createProgram(vs: String, fs: String): Int {
            fun compile(type: Int, source: String): Int {
                val shader = GLES20.glCreateShader(type)
                GLES20.glShaderSource(shader, source)
                GLES20.glCompileShader(shader)
                return shader
            }
            val v = compile(GLES20.GL_VERTEX_SHADER, vs)
            val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, v); GLES20.glAttachShader(p, f); GLES20.glLinkProgram(p)
            GLES20.glDeleteShader(v); GLES20.glDeleteShader(f)
            return p
        }

        companion object {
            private const val VERTEX = """
                attribute vec4 aPosition;
                attribute vec2 aTexCoord;
                uniform mat4 uMvp;
                varying vec2 vTexCoord;
                void main() { gl_Position = uMvp * aPosition; vTexCoord = aTexCoord; }
            """
            private const val FRAGMENT = """
                precision mediump float;
                uniform sampler2D uTexture;
                varying vec2 vTexCoord;
                void main() { gl_FragColor = texture2D(uTexture, vTexCoord); }
            """
        }
    }
}
