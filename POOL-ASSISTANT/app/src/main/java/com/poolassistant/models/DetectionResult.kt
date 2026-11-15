package com.poolassistant.models

/**
 * Aggregated result of one computer-vision pass over a captured frame.
 *
 * Confidence values are normalized to [0.0, 1.0]. A null [table] or under-
 * threshold [balls] indicates the detector could not reliably localize the
 * scene, and callers MUST NOT fabricate predictions from such a result.
 */
data class DetectionResult(
    val table: TableState? = null,
    val balls: List<Ball> = emptyList(),
    val cueBall: Ball? = null,
    val cueDirection: Vector2D? = null,   // unit vector, null when cue not visible
    val cueConfidence: Double = 0.0,
    val powerPercent: Double? = null,     // 0..100, null when indicator not readable
    val powerConfidence: Double = 0.0,
    val overallConfidence: Double = 0.0,
    /** Detected table rectangle in captured-frame pixel space (for overlay mapping). */
    val screenRect: ScreenRect? = null,
    val timestampMs: Long = 0L
) {
    data class ScreenRect(
        val left: Double,
        val top: Double,
        val right: Double,
        val bottom: Double
    )

    val isReliable: Boolean
        get() = table != null &&
            table.isValid &&
            overallConfidence >= MIN_ACCEPTABLE_CONFIDENCE

    companion object {
        const val MIN_ACCEPTABLE_CONFIDENCE = 0.55
        const val MIN_BALL_CONFIDENCE = 0.6
    }
}
