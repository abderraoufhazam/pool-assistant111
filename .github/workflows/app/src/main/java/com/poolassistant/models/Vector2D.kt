package com.poolassistant.models

import kotlin.math.hypot

/**
 * Immutable 2D vector used throughout the physics and vision pipeline.
 * All coordinates are expressed in table-relative "world" units unless
 * otherwise documented (screen pixels are mapped via
 * [com.poolassistant.utils.CoordinateMapper]).
 */
data class Vector2D(val x: Double, val y: Double) {

    operator fun plus(other: Vector2D): Vector2D = Vector2D(x + other.x, y + other.y)
    operator fun minus(other: Vector2D): Vector2D = Vector2D(x - other.x, y - other.y)
    operator fun times(scalar: Double): Vector2D = Vector2D(x * scalar, y * scalar)
    operator fun div(scalar: Double): Vector2D =
        if (scalar == 0.0) Vector2D(0.0, 0.0) else Vector2D(x / scalar, y / scalar)
    operator fun unaryMinus(): Vector2D = Vector2D(-x, -y)

    fun dot(other: Vector2D): Double = x * other.x + y * other.y

    /** 2D cross product magnitude (z component). */
    fun cross(other: Vector2D): Double = x * other.y - y * other.x

    fun length(): Double = hypot(x, y)

    fun lengthSquared(): Double = x * x + y * y

    fun normalized(): Vector2D {
        val len = length()
        return if (len < 1e-12) Vector2D(0.0, 0.0) else this / len
    }

    fun distanceTo(other: Vector2D): Double = (this - other).length()

    /** Rotate by [angleRad] radians (counter-clockwise). */
    fun rotated(angleRad: Double): Vector2D {
        val c = kotlin.math.cos(angleRad)
        val s = kotlin.math.sin(angleRad)
        return Vector2D(x * c - y * s, x * s + y * c)
    }

    fun isZero(eps: Double = 1e-9): Boolean = lengthSquared() < eps * eps

    companion object {
        val ZERO = Vector2D(0.0, 0.0)

        /** Reflects vector [v] about the plane with unit normal [normal]. */
        fun reflect(v: Vector2D, normal: Vector2D): Vector2D {
            val n = normal.normalized()
            return v - n * (2.0 * v.dot(n))
        }

        /** Scalar projection of [v] onto unit vector [axis]. */
        fun project(v: Vector2D, axis: Vector2D): Double = v.dot(axis)
    }
}
