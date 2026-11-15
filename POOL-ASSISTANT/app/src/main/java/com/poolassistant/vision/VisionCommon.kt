package com.poolassistant.vision

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.abs

/**
 * Shared low-level helpers for the pure-Kotlin CV pipeline.
 *
 * The detectors operate on downscaled ARGB_8888 bitmaps and use color
 * thresholds in RGB space tuned for the felt/cloth colours typical of 8 Ball
 * Pool. The thresholds are intentionally lenient and paired with a confidence
 * score so callers can reject unreliable results instead of guessing.
 */
internal object VisionCommon {

    /** A blob (connected/grouped region) detected in the frame. */
    data class Blob(
        val bounds: Rect,
        val cx: Int,
        val cy: Int,
        val radius: Int,
        val pixelCount: Int
    )

    data class Color {
        val r: Int
        val g: Int
        val b: Int
        val rgb: Int
            get() = (r shl 16) or (g shl 8) or b
    }

    val TABLE_FELT_GREEN = Color(40, 90, 40)   // representative table felt
    val BALL_WHITE = Color(255, 255, 255)
    val BALL_BLACK = Color(20, 20, 30)
    val CUE_TAN = Color(180, 140, 90)          // wooden cue shaft hue

    /** Returns true when the pixel is close to [ref] within [tolerance] per channel. */
    fun matchesChannel(pixel: Int, ref: Color, tolerance: Int): Boolean {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return abs(r - ref.r) <= tolerance &&
            abs(g - ref.g) <= tolerance &&
            abs(b - ref.b) <= tolerance
    }

    /** Dominant-cloth test: a green felt pixel. */
    fun isFeltGreen(pixel: Int): Boolean {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        // Green dominates and is not too dark/bright.
        return g > 60 && g > r + 20 && g > b + 15 && r < 120 && b < 120
    }

    /**
     * Marks pixels matching [classifier] into a binary mask. Returns a boolean
     * row-major array of size width*height, plus the count of marked pixels.
     */
    fun buildMask(bmp: Bitmap, classifier: (Int) -> Boolean): Pair<BooleanArray, Int> {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val mask = BooleanArray(w * h)
        var count = 0
        for (i in px.indices) {
            if (classifier(px[i])) {
                mask[i] = true
                count++
            }
        }
        return Pair(mask, count)
    }

    /**
     * Groups a boolean mask into axis-aligned bounding "blobs" using a simple
     * row-run label merge. Good enough for convex ball shapes and the table
     * surface. Returns blobs ordered by largest pixel count first.
     */
    fun extractBlobs(mask: BooleanArray, width: Int, height: Int, minPixels: Int): List<Blob> {
        // Flood-fill labeling via stack, iterating over unvisited mask pixels.
        val visited = BooleanArray(mask.size)
        val blobs = ArrayList<Blob>()
        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            // BFS
            val stack = ArrayDeque<Int>()
            stack.addLast(start)
            visited[start] = true
            var minX = Int.MAX_VALUE; var maxX = Int.MIN_VALUE
            var minY = Int.MAX_VALUE; var maxY = Int.MIN_VALUE
            var count = 0
            var sumX = 0L; var sumY = 0L
            while (stack.isNotEmpty()) {
                val idx = stack.removeLast()
                val x = idx % width
                val y = idx / width
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
                count++
                sumX += x; sumY += y
                // 4-neighbours
                if (x > 0 && mask[idx - 1] && !visited[idx - 1]) { visited[idx - 1] = true; stack.addLast(idx - 1) }
                if (x < width - 1 && mask[idx + 1] && !visited[idx + 1]) { visited[idx + 1] = true; stack.addLast(idx + 1) }
                if (y > 0 && mask[idx - width] && !visited[idx - width]) { visited[idx - width] = true; stack.addLast(idx - width) }
                if (y < height - 1 && mask[idx + width] && !visited[idx + width]) { visited[idx + width] = true; stack.addLast(idx + width) }
            }
            if (count >= minPixels) {
                val cx = (sumX / count).toInt()
                val cy = (sumY / count).toInt()
                val rw = (maxX - minX + 1)
                val rh = (maxY - minY + 1)
                val radius = (maxOf(rw, rh) / 2)
                blobs.add(
                    Blob(Rect(minX, minY, maxX, maxY), cx, cy, radius, count)
                )
            }
        }
        return blobs.sortedByDescending { it.pixelCount }
    }
}
