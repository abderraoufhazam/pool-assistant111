package com.poolassistant.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Binder
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import com.poolassistant.capture.ScreenCaptureManager
import com.poolassistant.models.Ball
import com.poolassistant.models.DetectionResult
import com.poolassistant.models.TableState
import com.poolassistant.models.Vector2D
import com.poolassistant.physics.PhysicsParameters
import com.poolassistant.physics.TrajectoryPredictor
import com.poolassistant.utils.CoordinateMapper
import com.poolassistant.utils.ImageUtils
import com.poolassistant.vision.VisionPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Floating overlay service: hosts the draggable button and the transparent
 * trajectory surface, and drives the detect -> predict -> draw loop.
 *
 * The overlay consumes captured frames (from [ScreenCaptureManager]) when the
 * visualization is active, runs the [VisionPipeline], predicts trajectories via
 * [TrajectoryPredictor], and draws them. Frames are skipped when the scene is
 * unchanged to limit CPU/battery.
 */
class FloatingOverlayService : Service() {

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var windowManager: WindowManager
    private var floatingButton: FloatingButtonView? = null
    private var trajectoryOverlay: TrajectoryOverlayView? = null
    private var buttonParams: WindowManager.LayoutParams? = null

    private val pipeline = VisionPipeline()
    private val predictor = TrajectoryPredictor(PhysicsParameters())

    private var visualizationActive = false
    private var frameJob: Job? = null

    @Volatile
    private var captureManager: ScreenCaptureManager? = null

    /** Frequency control: process frames at most every N ms. */
    @Volatile
    var processingIntervalMs: Long = 200

    inner class LocalBinder : Binder() {
        fun service(): FloatingOverlayService = this@FloatingOverlayService
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createOverlayViews()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        scope.cancel()
        removeAllViews()
        super.onDestroy()
    }

    /** Attaches the capture manager (owned by the foreground capture service). */
    fun attachCaptureManager(manager: ScreenCaptureManager) {
        captureManager = manager
    }

    fun detachCaptureManager() {
        frameJob?.cancel()
        frameJob = null
        visualizationActive = false
        captureManager = null
        trajectoryOverlay?.clear()
    }

    /** Toggles trajectory visualization on/off. */
    fun toggleVisualization() {
        if (visualizationActive) {
            stopVisualization()
        } else {
            startVisualization()
        }
    }

    private fun startVisualization() {
        // Prefer the attached manager; fall back to the static handle so the
        // overlay keeps working even if the Activity-based binding was lost.
        val cm = captureManager ?: com.poolassistant.capture.ScreenCaptureService.activeManager
        if (cm == null || !cm.isCapturing) {
            // Signal the problem visually so the user can report it.
            floatingButton?.showError()
            return
        }
        captureManager = cm
        visualizationActive = true
        floatingButton?.isActive = true

        // Draw a demo trajectory immediately so the user can verify that the
        // overlay rendering works, before real detection kicks in.
        postDemoPrediction()

        frameJob = scope.launch(Dispatchers.Default) {
            var lastProcessedAt = 0L
            try {
                cm.frames.collect { frame ->
                    if (!visualizationActive) return@collect
                    val now = System.currentTimeMillis()
                    if (now - lastProcessedAt < processingIntervalMs) return@collect
                    lastProcessedAt = now

                    try {
                        val downscaled = ImageUtils.downscale(frame, MAX_FRAME_W, MAX_FRAME_H)
                        val scaleX = frame.width.toDouble() / downscaled.width.toDouble()
                        val scaleY = frame.height.toDouble() / downscaled.height.toDouble()
                        val result = pipeline.process(downscaled)

                        // Analyse frame colours for diagnostics.
                        val wpx = downscaled.width
                        val hpx = downscaled.height
                        val pxArr = IntArray(wpx * hpx)
                        downscaled.getPixels(pxArr, 0, wpx, 0, 0, wpx, hpx)
                        var rSum = 0L; var gSum = 0L; var bSum = 0L
                        var greenCount = 0
                        val colorCounts = HashMap<Int, Int>()
                        for (p in pxArr) {
                            val r = (p shr 16) and 0xFF
                            val g = (p shr 8) and 0xFF
                            val b = p and 0xFF
                            rSum += r; gSum += g; bSum += b
                            if (g > 50 && g > r + 10 && g > b + 5 && r < 150 && b < 150) greenCount++
                            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
                            colorCounts[key] = (colorCounts[key] ?: 0) + 1
                        }
                        val npx = pxArr.size
                        val avgR = (rSum / npx).toInt()
                        val avgG = (gSum / npx).toInt()
                        val avgB = (bSum / npx).toInt()
                        val feltPct = greenCount * 100 / npx
                        val top = colorCounts.entries.sortedByDescending { it.value }.take(3)
                            .joinToString(" ") { e ->
                                val cr = ((e.key shr 8) and 0xF) * 16
                                val cg = ((e.key shr 4) and 0xF) * 16
                                val cb = (e.key and 0xF) * 16
                                "($cr,$cg,$cb):${e.value * 100 / npx}%"
                            }

                        val debug = "Table:${result.table != null} felt:$feltPct% avg:($avgR,$avgG,$avgB) top:$top Balls:${result.balls.size} " +
                            "Cue:${result.cueBall != null} Dir:${result.cueDirection != null} " +
                            "Power:${result.powerPercent?.toInt() ?: -1}"
                        try {
                            getSharedPreferences("pool_assistant_prefs", Context.MODE_PRIVATE)
                                .edit().putString("debug_status", debug).apply()
                        } catch (_: Throwable) {
                        }

                        val prediction = buildPrediction(result)
                        val mapper = buildMapper(result, scaleX, scaleY)
                        if (prediction != null && mapper != null) {
                            postPrediction(prediction, mapper)
                        }
                        // If detection failed, keep the demo prediction visible
                        // instead of clearing the overlay.
                    } catch (t: Throwable) {
                        // Never crash the app on a bad frame; keep whatever is shown.
                    }
                }
            } catch (t: Throwable) {
                // Frame channel closed or capture stopped; leave visualization off.
                visualizationActive = false
                floatingButton?.isActive = false
            }
        }
    }

    /**
     * Draws a sample trajectory mapped onto the full screen so the user can
     * verify that the overlay rendering works, before real detection replaces
     * it. This is replaced by real predictions as soon as detection succeeds.
     */
    private fun postDemoPrediction() {
        val table = TableState(
            left = 0.0, top = 0.0, right = 2.0, bottom = 1.0,
            cushionInset = 0.05, pockets = emptyList(), confidence = 1.0
        )
        val cue = Ball(id = 0, number = 0, position = Vector2D(0.5, 0.5))
        val target = Ball(id = 1, number = 1, position = Vector2D(1.5, 0.5))
        val prediction = predictor.predict(
            balls = listOf(cue, target),
            table = table,
            cueDirection = Vector2D(1.0, 0.0),
            powerPercent = 70.0,
            confidence = 1.0
        )
        val w = resources.displayMetrics.widthPixels.toDouble()
        val h = resources.displayMetrics.heightPixels.toDouble()
        val mapper = CoordinateMapper.from(
            CoordinateMapper.ScreenRect(Vector2D(0.0, 0.0), Vector2D(w, h)),
            table
        )
        postPrediction(prediction, mapper)
    }

    /**
     * Builds a prediction from the detection result. Falls back to aiming the
     * cue ball at the nearest object ball when the cue stick is not visible,
     * and to 50% power when the gauge is not readable, so the overlay shows
     * useful guidance even with partial detection.
     */
    private fun buildPrediction(result: DetectionResult): TrajectoryPredictor.Prediction? {
        val table = result.table ?: return null
        val cueBall = result.cueBall ?: return null

        var direction = result.cueDirection
        if (direction == null || direction.isZero()) {
            val nearest = result.balls
                .filter { it.id != cueBall.id }
                .minByOrNull { it.position.distanceTo(cueBall.position) }
            direction = nearest?.let { (it.position - cueBall.position).normalized() }
        }
        if (direction == null || direction.isZero()) return null

        val power = result.powerPercent ?: 50.0
        return predictor.predict(
            balls = result.balls,
            table = table,
            cueDirection = direction,
            powerPercent = power,
            confidence = result.overallConfidence
        )
    }

    private fun stopVisualization() {
        visualizationActive = false
        floatingButton?.isActive = false
        frameJob?.cancel()
        frameJob = null
        postPrediction(null, null)
    }

    private fun buildMapper(
        result: DetectionResult,
        scaleX: Double,
        scaleY: Double
    ): CoordinateMapper? {
        val table = result.table ?: return null
        val rect = result.screenRect ?: return null
        // Detection ran on the downscaled frame; scale the detected rectangle
        // back to full-screen coordinates for the overlay view.
        val screenRect = CoordinateMapper.ScreenRect(
            com.poolassistant.models.Vector2D(rect.left * scaleX, rect.top * scaleY),
            com.poolassistant.models.Vector2D(rect.right * scaleX, rect.bottom * scaleY)
        )
        return CoordinateMapper.from(screenRect, table)
    }

    private fun postPrediction(
        prediction: TrajectoryPredictor.Prediction?,
        mapper: CoordinateMapper?
    ) {
        // Must touch the overlay from the main thread.
        val overlay = trajectoryOverlay ?: return
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post {
            overlay.setPrediction(prediction, mapper)
        }
    }

    private fun createOverlayViews() {
        removeAllViews()

        // 1. Full-screen transparent trajectory surface (touch-through).
        val overlay = TrajectoryOverlayView(this)
        val overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        overlayParams.gravity = Gravity.TOP or Gravity.START
        windowManager.addView(overlay, overlayParams)
        trajectoryOverlay = overlay

        // Apply user-configured line transparency.
        val prefs = getSharedPreferences("pool_assistant_prefs", Context.MODE_PRIVATE)
        overlay.setLineAlpha(prefs.getInt("line_alpha", 180))
        processingIntervalMs = prefs.getInt("processing_ms", 200).toLong().coerceIn(10, 500)

        // 2. Draggable floating button (touchable).
        val button = FloatingButtonView(this)
        val size = (56 * resources.displayMetrics.density).toInt()
        val btnParams = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        btnParams.gravity = Gravity.TOP or Gravity.START
        btnParams.x = 32
        btnParams.y = 300
        button.onTap = { toggleVisualization() }
        button.windowManager = windowManager
        button.windowParams = btnParams
        windowManager.addView(button, btnParams)
        floatingButton = button
        buttonParams = btnParams
    }

    private fun removeAllViews() {
        runCatching { floatingButton?.let { windowManager.removeView(it) } }
        runCatching { trajectoryOverlay?.let { windowManager.removeView(it) } }
        floatingButton = null
        trajectoryOverlay = null
        buttonParams = null
    }

    companion object {
        private const val MAX_FRAME_W = 640
        private const val MAX_FRAME_H = 360
    }
}
