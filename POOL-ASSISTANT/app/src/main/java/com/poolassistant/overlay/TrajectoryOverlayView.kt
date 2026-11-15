package com.poolassistant.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.poolassistant.models.Vector2D
import com.poolassistant.physics.TrajectoryPredictor
import com.poolassistant.utils.CoordinateMapper

/**
 * Transparent overlay that draws predicted trajectories directly over the game.
 *
 * Visual scheme (as specified):
 *  - White line: cue-ball predicted path.
 *  - Red line:   object-ball predicted path.
 *  - Yellow:     cushion rebound markers/segments.
 *  - Small circles: collision positions.
 *
 * The view is a full-screen transparent surface with no hardware layers and no
 * continuous animation; it only redraws when [setPrediction] updates the model.
 */
class TrajectoryOverlayView(context: Context) : View(context) {

    private var prediction: TrajectoryPredictor.Prediction? = null
    private var mapper: CoordinateMapper? = null

    private var lineAlpha: Int = 180

    private val cuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
    }
    private val objectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val reboundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val collisionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    init {
        setWillNotDraw(false)
        alpha = 1f
    }

    fun setLineAlpha(alpha: Int) {
        lineAlpha = alpha.coerceIn(0, 255)
        cuePaint.alpha = lineAlpha
        objectPaint.alpha = lineAlpha
        reboundPaint.alpha = lineAlpha
        invalidate()
    }

    fun setPrediction(prediction: TrajectoryPredictor.Prediction?, mapper: CoordinateMapper?) {
        this.prediction = prediction
        this.mapper = mapper
        invalidate()
    }

    fun clear() {
        prediction = null
        mapper = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pred = prediction ?: return
        val m = mapper ?: return

        // Cue path (white).
        drawPath(canvas, pred.cuePath, m, cuePaint)

        // Object paths (red).
        for (path in pred.objectPaths.values) {
            drawPath(canvas, path, m, objectPaint)
        }

        // Rebound points (yellow dots).
        for (p in pred.reboundPoints) {
            val s = m.toScreen(p)
            canvas.drawCircle(s.x.toFloat(), s.y.toFloat(), dp(5f), reboundPaint)
        }

        // Collision points (small white circles).
        for (p in pred.collisionPoints) {
            val s = m.toScreen(p)
            canvas.drawCircle(s.x.toFloat(), s.y.toFloat(), dp(6f), collisionPaint)
        }
    }

    private fun drawPath(canvas: Canvas, path: List<Vector2D>, m: CoordinateMapper, paint: Paint) {
        if (path.size < 2) return
        var prev = m.toScreen(path[0])
        for (i in 1 until path.size) {
            val cur = m.toScreen(path[i])
            canvas.drawLine(prev.x.toFloat(), prev.y.toFloat(), cur.x.toFloat(), cur.y.toFloat(), paint)
            prev = cur
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
