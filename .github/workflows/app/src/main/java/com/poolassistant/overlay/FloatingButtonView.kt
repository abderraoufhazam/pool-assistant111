package com.poolassistant.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/**
 * A small, draggable, semi-transparent circular button that floats above other
 * apps. Tapping toggles trajectory visualization; dragging repositions it.
 *
 * Deliberately minimalist: a filled circle with a subtle pool-ball glyph and a
 * ring that brightens when active.
 */
class FloatingButtonView(context: Context) : View(context) {

    var onTap: (() -> Unit)? = null

    var isActive: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 20, 20, 26)
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = dp(18f)
    }
    private val activeRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 76, 217, 100) // iOS-like green
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
    }

    private var downX = 0f
    private var downY = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var dragged = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(cx, cy) - dp(3f)
        canvas.drawCircle(cx, cy, r, fillPaint)
        if (isActive) canvas.drawCircle(cx, cy, r, activeRingPaint)
        else canvas.drawCircle(cx, cy, r, ringPaint)
        canvas.drawText("8", cx, cy + glyphPaint.textSize / 3f, glyphPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                touchStartX = x
                touchStartY = y
                dragged = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (kotlin.math.abs(dx) > dp(4f) || kotlin.math.abs(dy) > dp(4f)) dragged = true
                x = touchStartX + dx
                y = touchStartY + dy
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragged) onTap?.invoke()
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
