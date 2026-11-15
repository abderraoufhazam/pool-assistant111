package com.poolassistant.vision

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Detects the shot-power indicator and estimates the current power percentage.
 *
 * In 8 Ball Pool the power indicator is typically a vertical or arced gauge
 * that fills with a saturated colour (often green/blue) that oscillates. This
 * detector looks for a thin elongated saturated region and estimates fill by
 * locating the current brightest/most-saturated edge.
 *
 * Because indicator appearance varies by game build and theme, this detector
 * returns a low-confidence estimate; a null result means "indicator not
 * readable". Power estimation is intentionally conservative.
 */
class PowerDetector {

    data class PowerDetection(
        val powerPercent: Double,
        val confidence: Double,
        val indicatorRect: Rect
    )

    fun detect(bmp: Bitmap): PowerDetection? {
        val w = bmp.width
        val h = bmp.height
        if (w < 8 || h < 8) return null

        // Look for a saturated-colour elongated region in the lower/right area.
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)

        // Candidate mask: strongly saturated colour (not felt, not neutral).
        val mask = BooleanArray(w * h)
        var count = 0
        for (i in px.indices) {
            val r = (px[i] shr 16) and 0xFF
            val g = (px[i] shr 8) and 0xFF
            val b = px[i] and 0xFF
            val saturation = maxOf(r, g, b) - minOf(r, g, b)
            val brightness = maxOf(r, g, b)
            if (saturation > 100 && brightness > 120) {
                mask[i] = true
                count++
            }
        }

        val totalPixels = w * h
        if (count < totalPixels * 0.0008) return null

        val blobs = VisionCommon.extractBlobs(mask, w, h, minPixels = (totalPixels * 0.0004).toInt())
        val gauge = blobs.firstOrNull { blob ->
            val bw = blob.bounds.width()
            val bh = blob.bounds.height()
            val longer = maxOf(bw, bh)
            val shorter = minOf(bw, bh)
            longer > minOf(w, h) * 0.08 && shorter > 0 && (longer.toDouble() / shorter) >= 2.0
        } ?: return null

        // Estimate fill fraction from the blob's extent relative to a presumed
        // full-length gauge. Use the vertical extent if vertical, else horizontal.
        val vertical = gauge.bounds.height() >= gauge.bounds.width()
        val fullLength = if (vertical) gauge.bounds.height() else gauge.bounds.width()
        val filledLength = blobLengthEstimate(gauge, vertical)

        val fill = (filledLength.toDouble() / fullLength.toDouble()).coerceIn(0.0, 1.0)
        val power = fill * 100.0

        return PowerDetection(
            powerPercent = power,
            confidence = 0.5,
            indicatorRect = gauge.bounds
        )
    }

    /** Best-effort fill length: blob bounding extent (indicator assumed upright). */
    private fun blobLengthEstimate(gauge: VisionCommon.Blob, vertical: Boolean): Int {
        return if (vertical) gauge.bounds.height() else gauge.bounds.width()
    }
}
