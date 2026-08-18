package com.capylabs.vrlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Lightweight HUD for debugging/interaction feedback. The actual scene remains stereo 3D. */
class HandOverlayView(context: Context) : View(context) {
    private var hands: List<HandPoint> = emptyList()
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 7f }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val skeleton = arrayOf(
        intArrayOf(0, 1, 2, 3, 4),
        intArrayOf(0, 5, 6, 7, 8),
        intArrayOf(0, 9, 10, 11, 12),
        intArrayOf(0, 13, 14, 15, 16),
        intArrayOf(0, 17, 18, 19, 20)
    )

    fun setHands(value: List<HandPoint>) {
        hands = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        hands.forEach { hand ->
            val x = hand.indexX * width
            val y = hand.indexY * height
            glow.color = if (hand.pinch) 0xFF7CE7FF.toInt() else 0xFFBFD7FF.toInt()
            canvas.drawCircle(x, y, if (hand.pinch) 27f else 20f, glow)
            dot.color = if (hand.pinch) 0xFF7CE7FF.toInt() else 0xFFFFFFFF.toInt()
            canvas.drawCircle(x, y, 6f, dot)
        }
    }
}
