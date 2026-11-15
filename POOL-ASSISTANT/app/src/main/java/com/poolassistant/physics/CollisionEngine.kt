package com.poolassistant.physics

import com.poolassistant.models.Ball
import com.poolassistant.models.TableState
import com.poolassistant.models.Vector2D

/**
 * Collision detection & resolution between balls and between balls and
 * cushions. Pure functions; no mutable state. This is the deterministic core
 * used by both the live [PhysicsEngine] and the offline [TrajectoryPredictor].
 */
object CollisionEngine {

    data class BallPair(val a: Ball, val b: Ball) {
        /** Signed overlap depth along the collision normal (positive = overlapping). */
        val overlap: Double
            get() = (a.radius + b.radius) - a.position.distanceTo(b.position)
    }

    /** Returns colliding pairs among [balls]; empty when none overlap. */
    fun findBallCollisions(balls: List<Ball>): List<BallPair> {
        val result = ArrayList<BallPair>(balls.size)
        for (i in balls.indices) {
            for (j in i + 1 until balls.size) {
                val a = balls[i]
                val b = balls[j]
                val overlap = (a.radius + b.radius) - a.position.distanceTo(b.position)
                if (overlap > 0.0) result.add(BallPair(a, b))
            }
        }
        return result
    }

    /**
     * Resolves an elastic (with restitution) collision between two equal-mass
     * balls. Returns the pair with updated velocities. Positions are not
     * corrected here (numerical separation is handled by the integrator).
     */
    fun resolveBallCollision(a: Ball, b: Ball, restitution: Double): Pair<Ball, Ball> {
        val normal = (b.position - a.position).normalized()
        val n = if (normal.isZero()) Vector2D(1.0, 0.0) else normal

        val relVel = a.velocity - b.velocity
        val velAlongNormal = relVel.dot(n)

        // Only resolve if approaching; otherwise leave unchanged.
        if (velAlongNormal <= 0.0) return Pair(a, b)

        val impulse = -(1.0 + restitution) * velAlongNormal / (1.0 / a.mass + 1.0 / b.mass)

        val impulseVec = n * impulse
        val newA = a.copy(velocity = a.velocity + impulseVec / a.mass, isMoving = true)
        val newB = b.copy(velocity = b.velocity - impulseVec / b.mass, isMoving = true)
        return Pair(newA, newB)
    }

    /**
     * Resolves a ball's collision with the [table] cushions. Returns the ball
     * with reflected velocity and a corrected position clamped inside bounds,
     * plus the unit normal of the cushion that was hit (null if no impact).
     */
    fun resolveCushionCollision(
        ball: Ball,
        table: TableState,
        restitution: Double
    ): Pair<Ball, Vector2D?> {
        var pos = ball.position
        var vel = ball.velocity
        var hitNormal: Vector2D? = null

        val minX = table.innerLeft + ball.radius
        val maxX = table.innerRight - ball.radius
        val minY = table.innerTop + ball.radius
        val maxY = table.innerBottom - ball.radius

        if (pos.x < minX) {
            pos = Vector2D(minX, pos.y)
            if (vel.x < 0.0) { vel = Vector2D(-vel.x * restitution, vel.y); hitNormal = Vector2D(1.0, 0.0) }
        } else if (pos.x > maxX) {
            pos = Vector2D(maxX, pos.y)
            if (vel.x > 0.0) { vel = Vector2D(-vel.x * restitution, vel.y); hitNormal = Vector2D(-1.0, 0.0) }
        }

        if (pos.y < minY) {
            pos = Vector2D(pos.x, minY)
            if (vel.y < 0.0) { vel = Vector2D(vel.x, -vel.y * restitution); hitNormal = Vector2D(0.0, 1.0) }
        } else if (pos.y > maxY) {
            pos = Vector2D(pos.x, maxY)
            if (vel.y > 0.0) { vel = Vector2D(vel.x, -vel.y * restitution); hitNormal = Vector2D(0.0, -1.0) }
        }

        return Pair(ball.copy(position = pos, velocity = vel, isMoving = !vel.isZero()), hitNormal)
    }

    /**
     * Returns true when a ball's center is within any pocket capture radius,
     * meaning it should be considered potted.
     */
    fun isPotted(ball: Ball, table: TableState): Boolean {
        return table.pockets.any { p ->
            ball.position.distanceTo(p.position) <= (p.radius + ball.radius)
        }
    }
}
