package com.poolassistant.vision

import android.graphics.Bitmap
import com.poolassistant.models.DetectionResult
import com.poolassistant.models.Vector2D
import com.poolassistant.utils.CoordinateMapper

/**
 * Orchestrates the individual detectors into a single [DetectionResult].
 *
 * The pipeline enforces the hard rule that unreliable detections must not be
 * turned into fabricated predictions: if any core stage is missing or below
 * threshold, [overallConfidence] reflects it and downstream callers drop the
 * result.
 */
class VisionPipeline(
    private val tableDetector: TableDetector = TableDetector(),
    private val ballDetector: BallDetector = BallDetector(),
    private val cueDetector: CueDetector = CueDetector(),
    private val powerDetector: PowerDetector = PowerDetector()
) {

    /**
     * Runs all detectors over [bmp].
     *
     * @param bmp the (ideally downscaled) frame.
     * @param manualCueScreen optional manually-corrected cue-ball screen position.
     * @param manualDirection optional manually-set shot direction (world unit).
     */
    fun process(
        bmp: Bitmap,
        manualCueScreen: Vector2D? = null,
        manualDirection: Vector2D? = null
    ): DetectionResult {
        val timestamp = System.currentTimeMillis()

        val tableDetection = tableDetector.detect(bmp)
            ?: return DetectionResult(overallConfidence = 0.0, timestampMs = timestamp)

        val table = tableDetection.table
        val mapper = CoordinateMapper.from(
            CoordinateMapper.ScreenRect(
                Vector2D(tableDetection.screenRect.left, tableDetection.screenRect.top),
                Vector2D(tableDetection.screenRect.right, tableDetection.screenRect.bottom)
            ),
            table
        )

        val ballDetections = ballDetector.detect(bmp, table, mapper, manualCueScreen)
        if (ballDetections.isEmpty()) {
            return DetectionResult(
                table = table,
                overallConfidence = 0.1,
                timestampMs = timestamp
            )
        }

        val balls = ballDetections.map { it.ball }
        val cueBall = balls.firstOrNull { it.number == 0 }
        val ballConf = ballDetections.map { it.confidence }.average()

        // Cue direction (manual overrides detection).
        val cueDetection = cueDetector.detect(bmp, table, mapper, cueBall?.position)
        val dir = manualDirection ?: cueDetection?.direction
        val dirConf = if (manualDirection != null) 1.0 else (cueDetection?.confidence ?: 0.0)

        // Power.
        val power = powerDetector.detect(bmp)
        val powerPct = power?.powerPercent
        val powerConf = power?.confidence ?: 0.0

        // Composite confidence: table + balls + (dir + power) weighted.
        val hasDirection = dir != null && !dir.isZero()
        val overall = (
            table.confidence * 0.35 +
            ballConf * 0.30 +
            (if (hasDirection) 1.0 else 0.0) * 0.20 +
            (if (powerPct != null) 1.0 else 0.0) * 0.15
        ).coerceIn(0.0, 1.0)

        return DetectionResult(
            table = table,
            balls = balls,
            cueBall = cueBall,
            cueDirection = dir,
            cueConfidence = dirConf,
            powerPercent = powerPct,
            powerConfidence = powerConf,
            overallConfidence = overall,
            screenRect = DetectionResult.ScreenRect(
                left = tableDetection.screenRect.left,
                top = tableDetection.screenRect.top,
                right = tableDetection.screenRect.right,
                bottom = tableDetection.screenRect.bottom
            ),
            timestampMs = timestamp
        )
    }
}
