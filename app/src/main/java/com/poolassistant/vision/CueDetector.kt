package com.poolassistant.vision

import android.graphics.Bitmap
import com.poolassistant.models.Vector2D
import com.poolassistant.utils.CoordinateMapper

/**
 * Detects the cue stick direction when its shaft is visible.
 *
 * The cue shaft is recognized as a long, thin, light-tan region extending away
 * from the cue ball. Its dominant orientation is estimated via the largest
 * thin blob's principal-axis angle, then converted to a table-world unit
 * vector pointing toward the intended shot direction.
 *
 * This is inherently lower-confidence than ball/table detection; a null result
 * means "cue not reliably visible" and callers must fall back to a manual
 * direction or suppress predictions.
 */
class CueDetector {

    data class CueDetection(
        /** Unit vector in WORLD space pointing in the shot direction. */
        val direction: Vector2D,
        val confidence: Double
    )

    fun detect(
        bmp: Bitmap,
        table: com.poolassistant.models.TableState,
        mapper: CoordinateMapper,
        cueBallWorld: Vector2D?
    ): CueDetection? {
        val w = bmp.width
        val h = bmp.height
        if (w < 8 || h < 8 || cueBallWorld == null) return null

        val (mask, tanCount) = VisionCommon.buildMask(bmp) {
            VisionCommon.matchesChannel(it, VisionCommon.CUE_TAN, tolerance = 60)
        }

        val totalPixels = w * h
        if (tanCount < totalPixels * 0.002 || tanCount > totalPixels * 0.25) return null

        val blobs = VisionCommon.extractBlobs(mask, w, h, minPixels = (totalPixels * 0.0005).toInt())
        // A cue shaft is long and thin: aspect ratio well above 1.5, length dominant.
        val cueBlob = blobs.firstOrNull { blob ->
            val bw = blob.bounds.width()
            val bh = blob.bounds.height()
            val longer = maxOf(bw, bh)
            val shorter = minOf(bw, bh)
            longer > minOf(w, h) * 0.15 && shorter > 0 && (longer.toDouble() / shorter) >= 1.5
        } ?: return null

        // Principal direction from bounding-box elongation.
        val horizontal = cueBlob.bounds.width() >= cueBlob.bounds.height()
        val angleRad = if (horizontal) 0.0 else Math.PI / 2.0

        // Screen direction vector (unoriented). Orient toward the ball.
        val screenDir = Vector2D(kotlin.math.cos(angleRad), kotlin.math.sin(angleRad))
        val ballScreen = mapper.toScreen(cueBallWorld)
        val blobCenterScreen = Vector2D(cueBlob.cx.toDouble(), cueBlob.cy.toDouble())

        // Point the direction from the blob (cue butt) toward the cue ball.
        val toBall = ballScreen - blobCenterScreen
        val oriented = if (screenDir.dot(toBall) < 0) -screenDir else screenDir

        // Convert direction delta to world space.
        val worldDir = mapper.toWorld(ballScreen + oriented) - cueBallWorld
        val normalized = worldDir.normalized()
        if (normalized.isZero()) return null

        val confidence = (0.5 + 0.3 * (if (tanCount > totalPixels * 0.01) 1.0 else 0.5)).coerceIn(0.0, 1.0)

        return CueDetection(normalized, confidence)
    }
}
