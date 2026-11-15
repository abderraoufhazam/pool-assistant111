package com.poolassistant.vision

import android.graphics.Bitmap
import com.poolassistant.models.TableState
import com.poolassistant.models.Vector2D

/**
 * Detects the billiards table surface and its six pockets from a frame.
 *
 * Strategy (pure-Kotlin):
 *  1. The dominant green felt region yields the playing-surface rectangle and
 *     a confidence proportional to its fill ratio and rectangularity.
 *  2. Pockets are estimated geometrically from the surface corners and edge
 *     midpoints, since physical pockets are fixed relative to the cushion.
 *
 * The produced [TableState] uses normalized world units (surface = 0..1 in the
 * long dimension) with pockets expressed in the same space.
 */
class TableDetector {

    data class TableDetection(
        val table: TableState,
        val screenRect: ScreenRect
    ) {
        data class ScreenRect(
            val left: Double,
            val top: Double,
            val right: Double,
            val bottom: Double
        )
    }

    /**
     * @param bmp downscaled frame.
     * @return detection or null when no confident table is found.
     */
    fun detect(bmp: Bitmap): TableDetection? {
        val w = bmp.width
        val h = bmp.height
        if (w < 8 || h < 8) return null

        val (mask, feltCount) = VisionCommon.buildMask(bmp) { VisionCommon.isFeltGreen(it) }
        val feltRatio = feltCount.toDouble() / (w * h)

        // The felt should occupy a meaningful but not total fraction of frame.
        if (feltRatio < 0.15 || feltRatio > 0.95) return null

        // Largest felt blob = playing surface.
        val blob = VisionCommon.extractBlobs(mask, w, h, minPixels = (w * h * 0.05).toInt())
            .firstOrNull()
            ?: return null

        val bw = blob.bounds.width()
        val bh = blob.bounds.height()
        val aspect = bw.toDouble() / bh.toDouble()

        // A billiards surface aspect is roughly 2:1 (landscape) with tolerance.
        if (aspect < 1.6 || aspect > 2.4) return null

        // Rectangularity: how well the blob fills its bounding box.
        val rectangularity = blob.pixelCount.toDouble() / (bw * bh)
        if (rectangularity < 0.75) return null

        val confidence = (
            (feltRatio * 0.4) +
            (rectangularity * 0.4) +
            (0.2 * when { aspect in 1.9..2.1 -> 1.0; aspect in 1.7..2.3 -> 0.7; else -> 0.4 })
        ).coerceIn(0.0, 1.0)

        if (confidence < 0.45) return null

        // Normalize to world units: long side = 2.0, short side = 1.0.
        val longAxis = if (aspect >= 1.0) 2.0 else 1.0
        val shortAxis = if (aspect >= 1.0) 1.0 else 2.0

        val worldLeft = 0.0
        val worldTop = 0.0
        val worldRight = longAxis
        val worldBottom = shortAxis

        val cushionInset = 0.04 // tuned estimate for pocket geometry

        val pockets = buildPockets(worldLeft, worldTop, worldRight, worldBottom, cushionInset)

        val table = TableState(
            left = worldLeft,
            top = worldTop,
            right = worldRight,
            bottom = worldBottom,
            cushionInset = cushionInset,
            pockets = pockets,
            confidence = confidence
        )

        return TableDetection(
            table = table,
            screenRect = TableDetection.ScreenRect(
                left = blob.bounds.left.toDouble(),
                top = blob.bounds.top.toDouble(),
                right = blob.bounds.right.toDouble(),
                bottom = blob.bounds.bottom.toDouble()
            )
        )
    }

    /**
     * Six pockets: four corner + two side midpoints, positioned just outside
     * the cushion rails, with a canonical radius.
     */
    private fun buildPockets(
        l: Double, t: Double, r: Double, b: Double, inset: Double
    ): List<TableState.Pocket> {
        val pocketR = 0.06
        val innerL = l + inset
        val innerR = r - inset
        val innerT = t + inset
        val innerB = b - inset
        val midX = (l + r) / 2.0
        val midY = (t + b) / 2.0
        return listOf(
            TableState.Pocket(Vector2D(innerL, innerT), pocketR),
            TableState.Pocket(Vector2D(midX, innerT), pocketR),
            TableState.Pocket(Vector2D(innerR, innerT), pocketR),
            TableState.Pocket(Vector2D(innerL, innerB), pocketR),
            TableState.Pocket(Vector2D(midX, innerB), pocketR),
            TableState.Pocket(Vector2D(innerR, innerB), pocketR)
        )
    }
}
