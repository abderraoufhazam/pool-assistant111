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
import com.poolassistant.models.DetectionResult
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
                        val result = pipeline.process(downscaled)
                        if (result.isReliable && result.cueBall != null && result.cueDirection != null) {
                            val prediction = predictor.predict(
                                balls = result.balls,
                                table = result.table!!,
                                cueDirection = result.cueDirection,
                                powerPercent = result.powerPercent ?: 50.0,
                                confidence = result.overallConfidence
                            )
                            val mapper = buildMapper(result)
                            postPrediction(prediction, mapper)
                        } else {
                            postPrediction(null, null)
                        }
                    } catch (t: Throwable) {
                        // Never crash the app on a bad frame; just clear and continue.
                        postPrediction(null, null)
                    }
                }
            } catch (t: Throwable) {
                // Frame channel closed or capture stopped; leave visualization off.
                visualizationActive = false
                floatingButton?.isActive = false
            }
        }
    }

    private fun stopVisualization() {
        visualizationActive = false
        floatingButton?.isActive = false
        frameJob?.cancel()
        frameJob = null
        postPrediction(null, null)
    }

    private fun buildMapper(result: DetectionResult): CoordinateMapper? {
        val table = result.table ?: return null
        val rect = result.screenRect ?: return null
        val screenRect = CoordinateMapper.ScreenRect(
            com.poolassistant.models.Vector2D(rect.left, rect.top),
            com.poolassistant.models.Vector2D(rect.right, rect.bottom)
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
