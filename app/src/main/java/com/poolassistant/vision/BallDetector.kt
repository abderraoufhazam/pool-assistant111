package com.poolassistant.vision

import android.graphics.Bitmap
import com.poolassistant.models.Ball
import com.poolassistant.models.Vector2D
import com.poolassistant.utils.CoordinateMapper
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Detects billiards balls within an already-detected table.
 *
 * Approach (pure-Kotlin):
 *  1. Isolate the table surface region from the frame.
 *  2. Find bright (white/coloured) circular blobs that contrast the felt.
 *  3. Classify each blob as cue (white), solid (solid colour), striped, or the
 *     8-ball (black), with a per-ball confidence.
 *
 * Classic 8-ball colours (RGB) drive solid/stripe classification. Striped
 * balls are recognized by a dominant colour interrupted by white bands, which
 * this simple blob classifier represents with lowered confidence.
 */
class BallDetector {

    data class BallDetection(
        val ball: Ball,
        val confidence: Double
    )

    private val solidColors = mapOf(
        1 to VisionCommon.Color(255, 210, 50),   // yellow
        2 to VisionCommon.Color(30, 70, 200),    // blue
        3 to VisionCommon.Color(220, 40, 40),    // red
        4 to VisionCommon.Color(140, 60, 180),   // purple
        5 to VisionCommon.Color(240, 120, 40),   // orange
        6 to VisionCommon.Color(40, 150, 60),    // green
        7 to VisionCommon.Color(130, 40, 40),    // maroon
        8 to VisionCommon.Color(20, 20, 25)      // black
    )

    /**
     * Detects balls on the felt and maps their screen positions to world space.
     *
     * @param bmp full/downscaled frame.
     * @param table the detected table world geometry.
     * @param mapper screen<->world mapping.
     * @param cuePositionScreen optional manual correction (overrides detection).
     */
    fun detect(
        bmp: Bitmap,
        table: com.poolassistant.models.TableState,
        mapper: CoordinateMapper,
        cuePositionScreen: Vector2D? = null
    ): List<BallDetection> {

        // Crop to the table surface bounds (padded slightly).
        val mapperRect = mapper.toScreen(table)
        val left = mapperRect.topLeft.x.toInt().coerceIn(0, bmp.width - 1)
        val top = mapperRect.topLeft.y.toInt().coerceIn(0, bmp.height - 1)
        val right = mapperRect.bottomRight.x.toInt().coerceIn(0, bmp.width)
        val bottom = mapperRect.bottomRight.y.toInt().coerceIn(0, bmp.height)
        if (right - left < 4 || bottom - top < 4) return emptyList()

        // Ball blobs: pixels notably brighter than the green felt, grouped.
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return emptyList()
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, left, top, w, h)

        // Threshold: bright + low green dominance (not felt).
        val mask = BooleanArray(w * h)
        for (i in px.indices) {
            val r = (px[i] shr 16) and 0xFF
            val g = (px[i] shr 8) and 0xFF
            val b = px[i] and 0xFF
            val brightness = (r + g + b) / 3
            val saturated = (abs(r - g) + abs(g - b) + abs(r - b)) / 3
            if (brightness > 90 && !(g > r + 20 && g > b + 15)) {
                mask[i] = true
            }
        }

        val minBallPixels = (w * h * 0.00005).toInt().coerceAtLeast(3)
        var blobs = VisionCommon.extractBlobs(mask, w, h, minPixels = minBallPixels)

        // Filter to near-circular blobs of plausible ball size.
        val minSide = minOf(w, h).toDouble()
        val ballRadiusMin = minSide * 0.005
        val ballRadiusMax = minSide * 0.25
        blobs = blobs.filter { blob ->
            val r = blob.radius.toDouble()
            r in ballRadiusMin..ballRadiusMax &&
            blob.bounds.width() in 1..w &&
            blob.bounds.height() in 1..h
        }

        // Enforce minimum separation to avoid duplicate nested blobs.
        val accepted = ArrayList<VisionCommon.Blob>()
        for (blob in blobs) {
            val overlaps = accepted.any { a ->
                hypot((a.cx - blob.cx).toDouble(), (a.cy - blob.cy).toDouble()) < (a.radius + blob.radius).toDouble()
            }
            if (!overlaps) accepted.add(blob)
        }

        // Classify each accepted blob into a Ball.
        val result = ArrayList<BallDetection>()
        var id = 0
        for (blob in accepted) {
            // Screen center of the ball.
            val screenCx = (left + blob.cx).toDouble()
            val screenCy = (top + blob.cy).toDouble()
            val worldPos = mapper.toWorld(Vector2D(screenCx, screenCy))

            // Average colour in the blob core.
            val coreColor = averageCoreColor(px, w, h, blob)

            val (number, type, conf) = classify(coreColor, blob)

            // Manual cue override: if provided and this blob is the closest,
            // treat as cue ball at manual position.
            val isCueOverride = cuePositionScreen != null &&
                hypot(screenCx - cuePositionScreen.x, screenCy - cuePositionScreen.y) < blob.radius * 2.0

            val ballNumber = if (isCueOverride) 0 else number
            val ballType = if (isCueOverride) Ball.BallType.CUE else type

            result.add(
                BallDetection(
                    ball = Ball(
                        id = id++,
                        number = ballNumber,
                        position = worldPos,
                        radius = table.cushionInset * 0.7,
                        type = ballType
                    ),
                    confidence = if (isCueOverride) 0.95 else conf
                )
            )
        }

        return result
    }

    private fun averageCoreColor(px: IntArray, w: Int, h: Int, blob: VisionCommon.Blob): DoubleArray {
        var rSum = 0L; var gSum = 0L; var bSum = 0L; var n = 0
        val cx = blob.cx; val cy = blob.cy
        val rad = maxOf(1, blob.radius / 2)
        for (dy in -rad..rad) {
            for (dx in -rad..rad) {
                val x = cx + dx; val y = cy + dy
                if (x in 0 until w && y in 0 until h) {
                    val p = px[y * w + x]
                    rSum += (p shr 16) and 0xFF
                    gSum += (p shr 8) and 0xFF
                    bSum += p and 0xFF
                    n++
                }
            }
        }
        if (n == 0) return doubleArrayOf(0.0, 0.0, 0.0)
        return doubleArrayOf(rSum / n.toDouble(), gSum / n.toDouble(), bSum / n.toDouble())
    }

    private fun classify(
        core: DoubleArray,
        blob: VisionCommon.Blob
    ): Triple<Int, Ball.BallType, Double> {
        val r = core[0]; val g = core[1]; val b = core[2]
        val brightness = (r + g + b) / 3.0

        // White cue ball.
        if (brightness > 200.0 && abs(r - g) < 25.0 && abs(g - b) < 25.0) {
            return Triple(0, Ball.BallType.CUE, 0.9)
        }
        // Black 8-ball.
        if (brightness < 60.0) {
            return Triple(8, Ball.BallType.EIGHT, 0.85)
        }

        // Nearest solid colour (excluding black).
        var bestNumber = -1
        var bestDist = Double.MAX_VALUE
        for ((num, color) in solidColors) {
            if (num == 8) continue
            val d = hypot(hypot(r - color.r, g - color.g), b - color.b)
            if (d < bestDist) { bestDist = d; bestNumber = num }
        }

        val bestConf = if (bestDist < 90.0) 0.75 else 0.5
        // Striped vs solid: low color dominance across the blob suggests stripes.
        val isSolid = bestDist < 90.0
        val type = when {
            bestNumber == -1 -> Ball.BallType.UNKNOWN
            isSolid -> Ball.BallType.SOLID
            else -> Ball.BallType.STRIPED
        }
        return Triple(bestNumber.coerceAtLeast(1), type, bestConf)
    }
}
