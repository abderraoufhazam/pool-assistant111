package com.poolassistant.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import kotlin.math.max

/**
 * Image helpers: rotation/flip normalization and cheap downscaling for the CV
 * pipeline. Downscaling drastically reduces processing cost and memory while
 * preserving enough detail for ball/table detection.
 */
object ImageUtils {

    /**
     * Downscales [src] to fit within [maxWidth]x[maxHeight] keeping aspect
     * ratio. Returns the same instance when already small enough (no copy).
     */
    fun downscale(src: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxWidth && h <= maxHeight) return src

        val scale = minOf(maxWidth.toFloat() / w, maxHeight.toFloat() / h)
        val newW = (w * scale).toInt().coerceAtLeast(1)
        val newH = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, newW, newH, true)
    }

    /**
     * Rotates [src] by [degrees] (multiple of 90) and mirrors horizontally
     * when [flipHorizontally] is true. Used to normalize captures from a
     * virtual display whose orientation may differ from the physical screen.
     */
    fun transform(src: Bitmap, degrees: Int, flipHorizontally: Boolean): Bitmap {
        if (degrees == 0 && !flipHorizontally) return src
        val matrix = Matrix()
        if (degrees != 0) matrix.postRotate(degrees.toFloat())
        if (flipHorizontally) matrix.postScale(-1f, 1f)
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    /**
     * Draws an arbitrary [Bitmap] into an ARGB_8888 canvas and returns it.
     * Useful when a source bitmap is immutable and needs editing.
     */
    fun toMutable(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(max(1, src.width), max(1, src.height), Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, null)
        return out
    }
}
