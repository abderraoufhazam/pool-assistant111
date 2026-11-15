package com.poolassistant.models

/**
 * A single billiards ball in "world" (table-relative) coordinates.
 *
 * Positions are in metres, velocities in m/s. The simulation is calibrated so
 * a normalized table maps to a real table via
 * [com.poolassistant.calibration.PhysicsCalibration].
 */
data class Ball(
    val id: Int,
    val number: Int = 0,        // 0 = cue ball, 1..7 solid, 8 black, 9..15 striped
    val position: Vector2D = Vector2D.ZERO,
    val velocity: Vector2D = Vector2D.ZERO,
    val radius: Double = 0.0286,  // standard 2 1/4" ball radius in metres
    var mass: Double = 0.170,      // ~170 g
    var isMoving: Boolean = false,
    val type: BallType = BallType.UNKNOWN
) {
    enum class BallType { CUE, SOLID, STRIPED, EIGHT, UNKNOWN }

    fun speed(): Double = velocity.length()

    fun copyMoving(position: Vector2D = this.position, velocity: Vector2D = this.velocity): Ball =
        copy(position = position, velocity = velocity, isMoving = !velocity.isZero())
}
