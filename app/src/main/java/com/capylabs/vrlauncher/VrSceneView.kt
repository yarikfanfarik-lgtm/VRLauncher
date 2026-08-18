package com.capylabs.vrlauncher

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

class VrSceneView(context: Context) : View(context) {
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panel = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val small = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val apps = ArrayList<AppItem>()
    private var yaw = 0f
    private var pitch = 0f
    private var selectedApp: AppItem? = null
    private var showKeyboard = false
    private var gameMode = false
    private var dragX = 0f
    private var dragY = 0f
    private var windowX = 0f
    private var windowY = 0f
    private var dragging = false

    data class AppItem(val label: String, val packageName: String, val icon: Drawable?)

    init {
        isFocusable = true
        text.typeface = Typeface.create("sans", Typeface.NORMAL)
        small.typeface = Typeface.create("sans", Typeface.NORMAL)
        loadApps()
    }

    private fun loadApps() {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        pm.queryIntentActivities(intent, 0)
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
            .take(24)
            .forEach { info ->
                apps += AppItem(info.loadLabel(pm).toString(), info.activityInfo.packageName, info.loadIcon(pm))
            }
    }

    fun setHead(yaw: Float, pitch: Float) {
        this.yaw = yaw
        this.pitch = pitch
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val half = width / 2f
        drawEye(canvas, 0f, half, -0.018f)
        drawEye(canvas, half, half, 0.018f)
    }

    private fun drawEye(canvas: Canvas, left: Float, eyeWidth: Float, stereo: Float) {
        canvas.save()
        canvas.clipRect(left, 0f, left + eyeWidth, height.toFloat())
        canvas.translate(left, 0f)

        val w = eyeWidth
        val h = height.toFloat()
        val parallax = stereo * w + yaw * 42f
        val vertical = pitch * 24f

        drawBackground(canvas, w, h, parallax, vertical)
        drawSidebar(canvas, w, h, parallax, vertical)
        drawQuickActions(canvas, w, parallax, vertical)
        drawLibrary(canvas, w, h, parallax, vertical)
        drawActiveWindows(canvas, w, h, parallax, vertical)
        drawDock(canvas, w, h, parallax)
        selectedApp?.let { drawFloatingApp(canvas, w, h, parallax, vertical, it) }
        if (showKeyboard) drawKeyboard(canvas, w, h, parallax, vertical)

        canvas.restore()
    }

    private fun drawBackground(c: Canvas, w: Float, h: Float, px: Float, py: Float) {
        bg.shader = LinearGradient(0f, 0f, w, h, Color.rgb(5, 9, 20), Color.rgb(16, 8, 35), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, bg)
        bg.shader = null

        val horizon = h * 0.68f + py
        bg.shader = RadialGradient(w * .52f + px, horizon, w * .65f, Color.argb(90, 75, 95, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, bg)
        bg.shader = null

        stroke.color = Color.argb(32, 150, 180, 255)
        stroke.strokeWidth = 1f
        for (i in 0..8) c.drawLine(0f, horizon + i * 26f, w, horizon + i * 26f, stroke)
    }

    private fun drawSidebar(c: Canvas, w: Float, h: Float, px: Float, py: Float) {
        val x = 24f + px * .25f
        val y = 36f + py
        val sw = 142f
        val sh = h - 92f
        glass(c, x, y, x + sw, y + sh, 24f, .72f)
        val items = listOf("⌂  Домой", "▦  Приложения", "▣  Рабочий стол", "◈  Игры", "▤  Обои", "⚙  Настройки")
        text.textSize = 15f
        items.forEachIndexed { i, label ->
            val iy = y + 22f + i * 55f
            if (i == 0) {
                panel.color = Color.argb(100, 110, 100, 255)
                c.drawRoundRect(x + 8f, iy, x + sw - 8f, iy + 43f, 14f, 14f, panel)
            }
            text.color = if (i == 0) Color.WHITE else Color.argb(225, 230, 235, 245)
            c.drawText(label, x + 20f, iy + 27f, text)
        }
        small.color = Color.argb(160, 220, 225, 240)
        small.textSize = 12f
        c.drawText("VR LAUNCHER", x + 20f, y + sh - 54f, small)
        c.drawText("18:42   •   89%", x + 20f, y + sh - 28f, small)
    }

    private fun drawQuickActions(c: Canvas, w: Float, px: Float, py: Float) {
        val x = 185f + px * .45f
        val y = 24f + py
        val aw = min(w - 210f, 700f)
        glass(c, x, y, x + aw, y + 74f, 20f, .76f)
        small.color = Color.argb(175, 230, 235, 250)
        small.textSize = 11f
        c.drawText("БЫСТРЫЕ ДЕЙСТВИЯ", x + 18f, y + 18f, small)
        val actions = arrayOf("↶", "⌂", "◷", "▣", "◉", "●", "☼")
        val names = arrayOf("Назад", "Домой", "Недавние", "Окна", "Снимок", "Микрофон", "Яркость")
        val cell = min(74f, (aw - 28f) / actions.size)
        actions.indices.forEach { i ->
            val ax = x + 12f + i * cell
            panel.color = Color.argb(45, 255, 255, 255)
            c.drawRoundRect(ax, y + 25f, ax + cell - 6f, y + 66f, 12f, 12f, panel)
            text.color = Color.WHITE
            text.textSize = 17f
            c.drawText(actions[i], ax + 13f, y + 45f, text)
            small.textSize = 7.5f
            small.color = Color.argb(180, 220, 225, 240)
            c.drawText(names[i], ax + 5f, y + 59f, small)
        }
    }

    private fun drawLibrary(c: Canvas, w: Float, h: Float, px: Float, py: Float) {
        val x = 185f + px * .7f
        val y = 120f + py
        val lw = min(570f, w - 385f)
        val lh = min(420f, h - 175f)
        glass(c, x, y, x + lw, y + lh, 26f, .82f)
        text.color = Color.WHITE
        text.textSize = 18f
        c.drawText("БИБЛИОТЕКА ПРИЛОЖЕНИЙ", x + 22f, y + 32f, text)
        small.color = Color.argb(135, 220, 225, 240)
        small.textSize = 11f
        c.drawText("Все приложения", x + 22f, y + 51f, small)

        val cols = 6
        val cellW = (lw - 44f) / cols
        val startY = y + 78f
        apps.take(18).forEachIndexed { index, app ->
            val col = index % cols
            val row = index / cols
            val ix = x + 14f + col * cellW
            val iy = startY + row * 92f
            drawAppIcon(c, app, ix + cellW / 2f, iy + 24f, 28f)
            small.color = Color.argb(220, 240, 242, 248)
            small.textSize = 9.5f
            val label = if (app.label.length > 12) app.label.take(11) + "…" else app.label
            c.drawText(label, ix + cellW / 2f - small.measureText(label) / 2f, iy + 66f, small)
        }
    }

    private fun drawActiveWindows(c: Canvas, w: Float, h: Float, px: Float, py: Float) {
        val x = w - 188f + px
        val y = 124f + py
        if (x < 0f) return
        val rw = 166f
        val rh = 105f
        glass(c, x, y, x + rw, y + 2 * rh + 16f, 22f, .78f)
        text.color = Color.WHITE
        text.textSize = 14f
        c.drawText("АКТИВНЫЕ ОКНА", x + 15f, y + 25f, text)
        drawMiniWindow(c, x + 10f, y + 37f, rw - 20f, rh, "YouTube")
        drawMiniWindow(c, x + 10f, y + 45f + rh, rw - 20f, rh, "Chrome")
    }

    private fun drawMiniWindow(c: Canvas, x: Float, y: Float, w: Float, h: Float, title: String) {
        panel.color = Color.argb(100, 8, 12, 22)
        c.drawRoundRect(x, y, x + w, y + h, 14f, 14f, panel)
        stroke.color = Color.argb(75, 180, 190, 255)
        stroke.style = Paint.Style.STROKE
        c.drawRoundRect(x, y, x + w, y + h, 14f, 14f, stroke)
        stroke.style = Paint.Style.FILL
        small.color = Color.WHITE
        small.textSize = 9f
        c.drawText(title, x + 10f, y + 17f, small)
        panel.color = Color.argb(35, 255, 255, 255)
        c.drawRect(x + 8f, y + 27f, x + w - 8f, y + h - 9f, panel)
        small.color = Color.argb(170, 220, 225, 240)
        c.drawText("закреплено", x + 10f, y + h - 14f, small)
    }

    private fun drawFloatingApp(c: Canvas, w: Float, h: Float, px: Float, py: Float, app: AppItem) {
        if (windowX == 0f) windowX = w * .28f
        if (windowY == 0f) windowY = h * .28f
        val x = windowX + px
        val y = windowY + py
        val ww = min(w * .58f, 500f)
        val hh = min(h * .52f, 310f)
        glass(c, x, y, x + ww, y + hh, 22f, .90f)
        drawAppIcon(c, app, x + 26f, y + 24f, 16f)
        text.color = Color.WHITE
        text.textSize = 14f
        c.drawText(app.label, x + 50f, y + 30f, text)
        small.color = Color.argb(180, 230, 235, 245)
        small.textSize = 18f
        c.drawText("−   □   ×", x + ww - 78f, y + 28f, small)
        panel.color = Color.argb(30, 255, 255, 255)
        c.drawRoundRect(x + 12f, y + 48f, x + ww - 12f, y + hh - 12f, 14f, 14f, panel)
        small.color = Color.argb(150, 225, 230, 240)
        small.textSize = 12f
        c.drawText("Приложение находится поверх VR Desktop", x + 28f, y + 80f, small)
        c.drawText("Главное меню остаётся видимым", x + 28f, y + 100f, small)
        if (gameMode) drawGameControls(c, x, y + hh - 72f, ww)
    }

    private fun drawGameControls(c: Canvas, x: Float, y: Float, w: Float) {
        panel.color = Color.argb(100, 10, 14, 24)
        c.drawRoundRect(x + 12f, y, x + w - 12f, y + 56f, 16f, 16f, panel)
        small.color = Color.WHITE
        small.textSize = 10f
        c.drawText("AIR MOUSE", x + 25f, y + 23f, small)
        c.drawText("PINCH = CLICK", x + 25f, y + 40f, small)
        c.drawText("VR GAME", x + w - 80f, y + 31f, small)
    }

    private fun drawKeyboard(c: Canvas, w: Float, h: Float, px: Float, py: Float) {
        val kw = min(390f, w * .56f)
        val kh = 176f
        val x = w * .50f - kw / 2f + px
        val y = h * .61f + py
        glass(c, x, y, x + kw, y + kh, 22f, .92f)
        text.color = Color.WHITE
        text.textSize = 12f
        c.drawText("VR КЛАВИАТУРА", x + 18f, y + 22f, text)
        small.color = Color.argb(160, 220, 225, 240)
        small.textSize = 9f
        c.drawText("3D-панель • закреплена в пространстве • не приклеена к голове", x + 18f, y + 38f, small)
        val rows = arrayOf("Й Ц У К Е Н Г Ш Щ З Х", "Ф Ы В А П Р О Л Д Ж Э", "Я Ч С М И Т Ь Б Ю")
        rows.forEachIndexed { r, line ->
            val keys = line.split(" ")
            val bw = (kw - 32f) / keys.size - 3f
            keys.forEachIndexed { i, key ->
                val bx = x + 16f + i * (bw + 3f)
                val by = y + 51f + r * 31f
                panel.color = Color.argb(72, 255, 255, 255)
                c.drawRoundRect(bx, by, bx + bw, by + 26f, 7f, 7f, panel)
                small.color = Color.WHITE
                small.textSize = 10f
                c.drawText(key, bx + bw / 2f - small.measureText(key) / 2f, by + 17f, small)
            }
        }
        panel.color = Color.argb(95, 90, 110, 210)
        c.drawRoundRect(x + 16f, y + 146f, x + 64f, y + 168f, 7f, 7f, panel)
        c.drawRoundRect(x + 69f, y + 146f, x + kw - 70f, y + 168f, 7f, 7f, panel)
        c.drawRoundRect(x + kw - 64f, y + 146f, x + kw - 16f, y + 168f, 7f, 7f, panel)
        small.color = Color.WHITE
        small.textSize = 9f
        c.drawText("🌐", x + 31f, y + 162f, small)
        c.drawText("ПРОБЕЛ", x + kw / 2f - 22f, y + 162f, small)
        c.drawText("⌫", x + kw - 48f, y + 162f, small)
    }

    private fun drawDock(c: Canvas, w: Float, h: Float, px: Float) {
        val dw = min(360f, w - 260f)
        val x = w / 2f - dw / 2f + px
        val y = h - 58f
        glass(c, x, y, x + dw, y + 42f, 20f, .82f)
        val icons = arrayOf("▦", "◉", "◎", "◈", "⚙", "18:42")
        icons.forEachIndexed { i, s ->
            small.color = Color.WHITE
            small.textSize = if (i == 5) 11f else 17f
            c.drawText(s, x + 22f + i * (dw - 44f) / 5f, y + 27f, small)
        }
    }

    private fun drawAppIcon(c: Canvas, app: AppItem, cx: Float, cy: Float, radius: Float) {
        app.icon?.let {
            it.setBounds((cx - radius).toInt(), (cy - radius).toInt(), (cx + radius).toInt(), (cy + radius).toInt())
            it.draw(c)
        } ?: run {
            iconPaint.color = Color.argb(180, 90, 100, 150)
            c.drawRoundRect(cx - radius, cy - radius, cx + radius, cy + radius, radius * .3f, radius * .3f, iconPaint)
        }
    }

    private fun glass(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, alpha: Float) {
        panel.color = Color.argb((alpha * 235).toInt(), 18, 23, 38)
        panel.setShadowLayer(18f, 0f, 7f, Color.argb(100, 0, 0, 0))
        c.drawRoundRect(l, t, r, b, radius, radius, panel)
        panel.clearShadowLayer()
        stroke.color = Color.argb(80, 205, 220, 255)
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 1.2f
        c.drawRoundRect(l, t, r, b, radius, radius, stroke)
        stroke.style = Paint.Style.FILL
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragX = event.x
                dragY = event.y
                dragging = selectedApp != null && event.x > width * .35f
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging && selectedApp != null) {
                    windowX += event.x - dragX
                    windowY += event.y - dragY
                    dragX = event.x
                    dragY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    dragging = false
                    return true
                }
                val half = width / 2f
                val x = if (event.x >= half) event.x - half else event.x
                val y = event.y
                val libLeft = 185f
                val libTop = 120f
                val libWidth = min(570f, half - 385f)
                if (x in libLeft..(libLeft + libWidth) && y > libTop + 65f) {
                    val cols = 6
                    val cellW = (libWidth - 44f) / cols
                    val col = ((x - libLeft - 14f) / cellW).toInt()
                    val row = ((y - (libTop + 78f)) / 92f).toInt()
                    val idx = row * cols + col
                    if (idx in apps.indices) openVrWindow(apps[idx])
                }
                return true
            }
        }
        return true
    }

    private fun openVrWindow(app: AppItem) {
        selectedApp = app
        if (windowX == 0f) windowX = width / 2f - 190f
        if (windowY == 0f) windowY = height / 2f - 130f
        invalidate()
    }
}
