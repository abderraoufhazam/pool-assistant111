package com.poolassistant.models

/**
 * Geometric state of the detected billiards table.
 *
 * The playing surface is an axis-aligned rectangle in world coordinates with
 * [left] < [right] and [top] < [bottom]. [cushionInset] is the distance from
 * the outer boundary to the inner cushion rail where balls rebound.
 */
data class TableState(
    val left: Double = 0.0,
    val top: Double = 0.0,
    val right: Double = 1.0,
    val bottom: Double = 1.0,
    val cushionInset: Double = 0.05,
    val pockets: List<Pocket> = emptyList(),
    val confidence: Double = 0.0
) {
    data class Pocket(
        val position: Vector2D,
        val radius: Double
    )

    val width: Double get() = right - left
    val height: Double get() = bottom - top

    /** Inner playing bounds (inside the cushions). */
    val innerLeft: Double get() = left + cushionInset
    val innerRight: Double get() = right - cushionInset
    val innerTop: Double get() = top + cushionInset
    val innerBottom: Double get() = bottom - cushionInset

    val isValid: Boolean get() = width > 0.0 && height > 0.0

    fun contains(p: Vector2D): Boolean =
        p.x in innerLeft..innerRight && p.y in innerTop..innerBottom
}
