package com.capylabs.vrlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/** Premium Cardboard desktop. No 8x8 reveal animation. */
class VrSceneView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val title = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconFallback = Paint(Paint.ANTI_ALIAS_FLAG)

    private data class App(val name: String, val packageName: String, val activityName: String, val icon: Drawable?)
    private val apps = mutableListOf<App>()
    private var opened: App? = null
    private var keyboard = false
    private var gameMode = false
    private var keyboardX = 0f
    private var keyboardY = 0f
    private var draggingKeyboard = false
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var headYaw = 0f
    private var headPitch = 0f

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        title.typeface = Typeface.create("sans", Typeface.BOLD)
        label.typeface = Typeface.create("sans", Typeface.NORMAL)
        loadApps()
    }

    private fun loadApps() {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
            .take(30)
            .forEach { info ->
                apps += App(info.loadLabel(pm).toString(), info.activityInfo.packageName, info.activityInfo.name, info.loadIcon(pm))
            }
    }

    fun setHead(yaw: Float, pitch: Float) {
        headYaw = yaw
        headPitch = pitch
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        val eye = width / 2f
        drawEye(canvas, 0f, eye, -0.014f)
        drawEye(canvas, eye, eye, 0.014f)
    }

    private fun drawEye(c: Canvas, left: Float, eyeW: Float, stereo: Float) {
        c.save()
        c.clipRect(left, 0f, left + eyeW, height.toFloat())
        c.translate(left, 0f)
        val px = stereo * eyeW + headYaw * 34f
        val py = headPitch * 18f
        val w = eyeW
        val h = height.toFloat()
        drawBackground(c, w, h, px, py)
        drawSidebar(c, h, px, py)
        drawQuickActions(c, w, px, py)
        drawLibrary(c, w, h, px, py)
        drawActiveWindows(c, w, h, px, py)
        drawDock(c, w, h, px)
        opened?.let { drawFloatingApp(c, w, h, px, py, it) }
        if (keyboard) drawKeyboard(c, w, h, px, py)
        c.restore()
    }

    private fun drawBackground(c: Canvas, w: Float, h: Float, px: Float, py: Float) {
        p.shader = LinearGradient(0f, 0f, w, h, Color.rgb(3, 7, 17), Color.rgb(12, 7, 31), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        val horizon = h * .70f + py
        p.shader = RadialGradient(w * .58f + px, horizon, w * .70f, Color.argb(110, 56, 93, 170), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        p.color = Color.argb(150, 190, 210, 255)
        val stars = intArrayOf(48, 90, 136, 202, 271, 335, 402, 461, 523, 590)
        stars.forEachIndexed { i, sx0 ->
            val sx = (sx0 % w.toInt()).toFloat()
            val sy = 18f + (i * 31f) % (h * .48f)
            c.drawCircle(sx + px * .12f, sy + py * .08f, if (i % 3 == 0) 1.4f else .8f, p)
        }
        line.color = Color.argb(28, 150, 180, 255)
        line.strokeWidth = 1f
        for (i in 0..7) c.drawLine(0f, horizon + i * 27f, w, horizon + i * 27f, line)
    }

    private fun drawSidebar(c: Canvas, h: Float, px: Float, py: Float) {
        val x = 20f + px * .18f
        val y = 32f + py
        val sw = 145f
        val sh = h - 82f
        glass(c, x, y, x + sw, y + sh, 24f, 220)
        val icons = arrayOf("⌂", "▦", "▣", "◈", "▤", "⚙")
        val names = arrayOf("Домой", "Приложения", "Рабочий стол", "Игры", "Обои", "Настройки")
        for (i in icons.indices) {
            val iy = y + 18f + i * 55f
            if (i == 0) {
                p.color = Color.argb(115, 95, 88, 235)
                c.drawRoundRect(x + 8f, iy, x + sw - 8f, iy + 42f, 14f, 14f, p)
            }
            title.color = Color.WHITE
            title.textSize = 17f
            c.drawText(icons[i], x + 18f, iy + 27f, title)
            label.color = Color.argb(232, 242, 244, 250)
            label.textSize = 14f
            c.drawText(names[i], x + 48f, iy + 26f, label)
        }
        label.color = Color.argb(170, 220, 225, 240)
        label.textSize = 11f
        c.drawText("VR LAUNCHER", x + 18f, y + sh - 54f, label)
        c.drawText("18:42   •   Wi-Fi   •   89%", x + 18f, y + sh - 30f, label)
    }

    private fun drawQuickActions(c: Canvas, w: Float, px: Float, py: Float) {
        val x = 180f + px * .38f
        val y = 20f + py
        val rw = min(690f, w - 195f)
        glass(c, x, y, x + rw, y + 78f, 22f, 228)
        label.color = Color.argb(190, 245, 247, 255)
        label.textSize = 11f
        c.drawText("БЫСТРЫЕ ДЕЙСТВИЯ", x + 18f, y + 18f, label)
        val icons = arrayOf("↶", "⌂", "◷", "▣", "◉", "●", "◌", "☼")
        val names = arrayOf("Назад", "Домой", "Недавние", "Окна", "Скриншот", "Запись", "Микрофон", "Яркость")
        val cell = min(78f, (rw - 28f) / icons.size)
        icons.indices.forEach { i ->
            val bx = x + 10f + i * cell
            p.color = Color.argb(54, 255, 255, 255)
            c.drawRoundRect(bx, y + 25f, bx + cell - 5f, y + 68f, 12f, 12f, p)
            title.color = if (i == 6) Color.rgb(100, 240, 190) else Color.WHITE
            title.textSize = 18f
            c.drawText(icons[i], bx + (cell - 5f) / 2f - 8f, y + 46f, title)
            label.color = Color.argb(180, 225, 230, 242)
            label.textSize = 7.5f
            c.drawText(names[i], bx + 4f, y + 61f, label)
        }
    }

    private fun drawLibrary(c: Canvas, w: Float, h: Float, px: Float, py: Float) {
        val x = 180f + px * .62f
        val y = 116f + py
        val rw = min(570f, w - 380f)
        val rh = min(410f, h - 166f)
        glass(c, x, y, x + rw, y + rh, 26f, 226)
        title.color = Color.WHITE
        title.textSize = 17f
        c.drawText("БИБЛИОТЕКА ПРИЛОЖЕНИЙ", x + 20f, y + 31f, title)
        p.color = Color.argb(50, 255, 255, 255)
        c.drawRoundRect(x + 18f, y + 44f, x + rw - 18f, y + 72f, 12f, 12f, p)
        label.color = Color.argb(145, 225, 230, 242)
        label.textSize = 10f
        c.drawText("⌕  Поиск приложений...", x + 30f, y + 62f, label)
        val cols = 6
        val cell = (rw - 36f) / cols
        apps.take(18).forEachIndexed { index, app ->
            val col = index % cols
            val row = index / cols
            val cx = x + 18f + cell * col + cell / 2f
            val cy = y + 105f + row * 88f
            drawAppIcon(c, app, cx, cy, 25f)
            label.color = Color.argb(235, 245, 246, 250)
            label.textSize = 9f
            val n = if (app.name.length > 11) app.name.take(10) + "…" else app.name
            c.drawText(n, cx - label.measureText(n) / 2f, cy + 42f, label)
        }
    }

    private fun drawActiveWindows(c: Canvas, w: Float, h: Float, px: Float, py: Float) {
        val rw = 172f
        val x = w - rw - 16f + px
        val y = 116f + py
        if (x < 340f) return
        glass(c, x, y, x + rw, y + 300f, 22f, 224)
        title.color = Color.WHITE
        title.textSize = 14f
        c.drawText("АКТИВНЫЕ ОКНА", x + 14f, y + 25f, title)
        drawMiniWindow(c, x + 9f, y + 37f, rw - 18f, 110f, "YouTube")
        drawMiniWindow(c, x + 9f, y + 157f, rw - 18f, 110f, "Chrome")
    }

    private fun drawMiniWindow(c: Canvas, x: Float, y: Float, w: Float, h: Float, name: String) {
        p.color = Color.argb(100, 7, 11, 22)
        c.drawRoundRect(x, y, x + w, y + h, 13f, 13f, p)
        line.color = Color.argb(90, 180, 195, 255)
        line.style = Paint.Style.STROKE
        c.drawRoundRect(x, y, x + w, y + h, 13f, 13f, line)
        line.style = Paint.Style.FILL
        label.color = Color.WHITE
        label.textSize = 9f
        c.drawText(name, x + 9f, y + 16f, label)
        c.drawText("⌖", x + w - 33f, y + 16f, label)
        c.drawText("×", x + w - 17f, y + 16f, label)
        p.color = Color.argb(38, 255, 255, 255)
        c.drawRect(x + 8f, y + 25f, x + w - 8f, y + h - 9f, p)
        label.color = Color.argb(155, 225, 230, 242)
        c.drawText("закреплено", x + 9f, y + h - 14f, label)
    }

    private fun drawFloatingApp(c: Canvas, w: Float, h: Float, px: Float, py: Float, app: App) {
        val rw = min(520f, w * .62f)
        val rh = min(320f, h * .56f)
        val x = w * .25f + px
        val y = h * .25f + py
        glass(c, x, y, x + rw, y + rh, 23f, 242)
        drawAppIcon(c, app, x + 25f, y + 23f, 15f)
        title.color = Color.WHITE
        title.textSize = 14f
        c.drawText(app.name, x + 48f, y + 28f, title)
        label.color = Color.WHITE
        label.textSize = 18f
        c.drawText("−", x + rw - 66f, y + 27f, label)
        c.drawText("□", x + rw - 43f, y + 27f, label)
        c.drawText("×", x + rw - 20f, y + 27f, label)
        p.color = Color.argb(38, 255, 255, 255)
        c.drawRoundRect(x + 12f, y + 45f, x + rw - 12f, y + rh - 12f, 15f, 15f, p)
        label.color = Color.argb(175, 235, 238, 247)
        label.textSize = 12f
        c.drawText("ПРИЛОЖЕНИЕ ОТКРЫТО В VR", x + 28f, y + 77f, label)
        c.drawText("Главное меню остаётся видимым поверх рабочего пространства.", x + 28f, y + 100f, label)
        if (gameMode) drawGameOverlay(c, x, y, rw, rh)
    }

    private fun drawGameOverlay(c: Canvas, x: Float, y: Float, rw: Float, rh: Float) {
        p.color = Color.argb(150, 4, 7, 14)
        c.drawRoundRect(x + 14f, y + rh - 67f, x + rw - 14f, y + rh - 14f, 15f, 15f, p)
        label.color = Color.WHITE
        label.textSize = 9f
        c.drawText("AIR MOUSE", x + 28f, y + rh - 43f, label)
        c.drawText("движение руки = обзор", x + 28f, y + rh - 27f, label)
        c.drawText("PINCH = CLICK", x + rw - 125f, y + rh - 35f, label)
        c.drawText("VR GAME", x + rw - 72f, y + rh - 52f, label)
    }

    private fun drawKeyboard(c: Canvas, w: Float, h: Float, px: Float, py: Float) {
        val kw = min(400f, w * .58f)
        val kh = 188f
        val baseX = if (keyboardX == 0f) w * .50f - kw / 2f else keyboardX
        val baseY = if (keyboardY == 0f) h * .61f else keyboardY
        val x = baseX + px
        val y = baseY + py
        glass(c, x + 8f, y + 10f, x + kw + 8f, y + kh + 10f, 22f, 85)
        glass(c, x, y, x + kw, y + kh, 22f, 242)
        title.color = Color.WHITE
        title.textSize = 12f
        c.drawText("VR КЛАВИАТУРА", x + 16f, y + 21f, title)
        label.color = Color.argb(160, 225, 230, 242)
        label.textSize = 8.5f
        c.drawText("закреплена • можно перемещать", x + kw - 148f, y + 21f, label)
        val rows = arrayOf("Й Ц У К Е Н Г Ш Щ З Х Ъ", "Ф Ы В А П Р О Л Д Ж Э", "Я Ч С М И Т Ь Б Ю")
        rows.forEachIndexed { row, keys ->
            val a = keys.split(" ")
            val gap = 3f
            val bw = (kw - 30f - gap * (a.size - 1)) / a.size
            a.forEachIndexed { i, key ->
                val bx = x + 15f + i * (bw + gap)
                val by = y + 34f + row * 32f
                p.color = Color.argb(75, 255, 255, 255)
                c.drawRoundRect(bx, by, bx + bw, by + 27f, 7f, 7f, p)
                label.color = Color.WHITE
                label.textSize = 9f
                c.drawText(key, bx + bw / 2f - label.measureText(key) / 2f, by + 18f, label)
            }
        }
        val bottom = y + 132f
        key(c, x + 15f, bottom, 52f, "🌐")
        key(c, x + 71f, bottom, kw - 142f, "ПРОБЕЛ")
        key(c, x + kw - 67f, bottom, 52f, "⌫")
        label.color = Color.argb(155, 225, 230, 242)
        label.textSize = 8f
        c.drawText("Русский", x + 28f, y + 178f, label)
    }

    private fun key(c: Canvas, x: Float, y: Float, w: Float, value: String) {
        p.color = Color.argb(95, 80, 95, 205)
        c.drawRoundRect(x, y, x + w, y + 28f, 8f, 8f, p)
        label.color = Color.WHITE
        label.textSize = 9f
        c.drawText(value, x + w / 2f - label.measureText(value) / 2f, y + 18f, label)
    }

    private fun drawDock(c: Canvas, w: Float, h: Float, px: Float) {
        val dw = min(370f, w - 240f)
        val x = w / 2f - dw / 2f + px
        val y = h - 58f
        glass(c, x, y, x + dw, y + 40f, 20f, 225)
        val items = arrayOf("▦", "◎", "◉", "◈", "⚙", "18:42", "89%")
        items.forEachIndexed { i, s ->
            label.color = Color.WHITE
            label.textSize = if (i > 4) 10f else 17f
            c.drawText(s, x + 18f + i * (dw - 36f) / (items.size - 1), y + 26f, label)
        }
    }

    private fun drawAppIcon(c: Canvas, app: App, cx: Float, cy: Float, radius: Float) {
        app.icon?.let {
            it.setBounds((cx - radius).toInt(), (cy - radius).toInt(), (cx + radius).toInt(), (cy + radius).toInt())
            it.draw(c)
        } ?: run {
            iconFallback.color = Color.rgb(70, 80, 125)
            c.drawRoundRect(cx - radius, cy - radius, cx + radius, cy + radius, radius * .28f, radius * .28f, iconFallback)
        }
    }

    private fun glass(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, alpha: Int) {
        p.color = Color.argb(alpha, 15, 20, 34)
        p.setShadowLayer(20f, 0f, 8f, Color.argb(95, 0, 0, 0))
        c.drawRoundRect(l, t, r, b, radius, radius, p)
        p.clearShadowLayer()
        line.color = Color.argb(95, 200, 215, 255)
        line.style = Paint.Style.STROKE
        line.strokeWidth = 1.2f
        c.drawRoundRect(l, t, r, b, radius, radius, line)
        line.style = Paint.Style.FILL
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val eyeW = width / 2f
        val x = if (e.x >= eyeW) e.x - eyeW else e.x
        val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (keyboard && hitKeyboard(x, y)) {
                    draggingKeyboard = true
                    val bx = if (keyboardX == 0f) eyeW / 2f - min(400f, eyeW * .58f) / 2f else keyboardX
                    val by = if (keyboardY == 0f) height * .61f else keyboardY
                    dragOffsetX = x - bx
                    dragOffsetY = y - by
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingKeyboard) {
                    keyboardX = x - dragOffsetX
                    keyboardY = y - dragOffsetY
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (draggingKeyboard) {
                    draggingKeyboard = false
                    return true
                }
                handleTap(x, y)
                return true
            }
        }
        return true
    }

    private fun hitKeyboard(x: Float, y: Float): Boolean {
        val kw = min(400f, width / 2f * .58f)
        val bx = if (keyboardX == 0f) width / 4f - kw / 2f else keyboardX
        val by = if (keyboardY == 0f) height * .61f else keyboardY
        return x in bx..(bx + kw) && y in by..(by + 188f)
    }

    private fun handleTap(x: Float, y: Float) {
        val eyeW = width / 2f
        val lx = 180f
        val ly = 116f
        val rw = min(570f, eyeW - 380f)
        if (rw > 200f && x in lx..(lx + rw) && y in (ly + 80f)..(ly + 395f)) {
            val cell = (rw - 36f) / 6f
            val col = ((x - lx - 18f) / cell).toInt().coerceIn(0, 5)
            val row = ((y - ly - 80f) / 88f).toInt().coerceIn(0, 2)
            val index = row * 6 + col
            if (index in apps.indices) {
                opened = apps[index]
                gameMode = false
                keyboard = false
                invalidate()
                return
            }
        }
        if (opened != null) {
            val rw2 = min(520f, eyeW * .62f)
            val rh2 = min(320f, height * .56f)
            val ox = eyeW * .25f
            val oy = height * .25f
            if (x in (ox + rw2 - 55f)..(ox + rw2) && y in oy..(oy + 48f)) {
                keyboard = !keyboard
                invalidate()
                return
            }
            if (x in (ox + rw2 - 82f)..(ox + rw2 - 45f) && y in oy..(oy + 48f)) {
                gameMode = !gameMode
                keyboard = gameMode
                invalidate()
            }
        }
    }
}
