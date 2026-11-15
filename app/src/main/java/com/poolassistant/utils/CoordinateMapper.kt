package com.poolassistant.utils

import com.poolassistant.models.TableState
import com.poolassistant.models.Vector2D

/**
 * Maps between the two coordinate spaces used by Pool Assistant:
 *
 *  1. "Screen" space — pixels in the captured virtual display, with origin at
 *     the top-left, x growing right, y growing *downward* (Android default).
 *  2. "World" space — the physics simulation space. The table rectangle is
 *     normalized to a canonical width so the physics constants are resolution
 *     independent. World space may optionally flip y so that "up" is positive.
 *
 * The mapper is created once per table detection from the detected
 * [TableState] (which is expressed in world units) and its screen rectangle.
 */
class CoordinateMapper private constructor(
    private val screenLeft: Double,
    private val screenTop: Double,
    private val screenRight: Double,
    private val screenBottom: Double,
    private val worldLeft: Double,
    private val worldTop: Double,
    private val worldRight: Double,
    private val worldBottom: Double
) {

    val scaleX: Double
        get() = (worldRight - worldLeft) / (screenRight - screenLeft)

    val scaleY: Double
        get() = (worldBottom - worldTop) / (screenBottom - screenTop)

    /** World -> screen. */
    fun toScreen(world: Vector2D): Vector2D {
        val sx = screenLeft + (world.x - worldLeft) / scaleX
        val sy = screenTop + (world.y - worldTop) / scaleY
        return Vector2D(sx, sy)
    }

    /** Screen -> world. */
    fun toWorld(screen: Vector2D): Vector2D {
        val wx = worldLeft + (screen.x - screenLeft) * scaleX
        val wy = worldTop + (screen.y - screenTop) * scaleY
        return Vector2D(wx, wy)
    }

    fun toScreen(table: TableState): ScreenRect =
        ScreenRect(
            toScreen(Vector2D(table.left, table.top)),
            toScreen(Vector2D(table.right, table.bottom))
        )

    data class ScreenRect(val topLeft: Vector2D, val bottomRight: Vector2D)

    companion object {
        /**
         * Builds a mapper from a screen rectangle (detected table in pixels)
         * and the equivalent [TableState] world rectangle.
         */
        fun from(
            screenRect: ScreenRect,
            table: TableState
        ): CoordinateMapper = CoordinateMapper(
            screenLeft = screenRect.topLeft.x,
            screenTop = screenRect.topLeft.y,
            screenRight = screenRect.bottomRight.x,
            screenBottom = screenRect.bottomRight.y,
            worldLeft = table.left,
            worldTop = table.top,
            worldRight = table.right,
            worldBottom = table.bottom
        )
    }
}
