package com.poolassistant.physics

import com.poolassistant.models.Ball
import com.poolassistant.models.TableState
import com.poolassistant.models.Vector2D

/**
 * Discrete-time 2D billiards physics engine.
 *
 * Integrates ball motion under rolling friction, resolves ball-ball and
 * ball-cushion collisions, and reports discrete events. The engine is
 * stateless per [step] call and returns a new [WorldState], making it
 * trivially testable and usable by the offline predictor.
 */
class PhysicsEngine(
    private val params: PhysicsParameters = PhysicsParameters()
) {

    data class WorldState(
        val balls: List<Ball>,
        val time: Double,
        val events: List<Event> = emptyList()
    )

    sealed class Event {
        data class BallCollision(val aId: Int, val bId: Int, val at: Vector2D) : Event()
        data class CushionRebound(val ballId: Int, val normal: Vector2D, val at: Vector2D) : Event()
        data class Potted(val ballId: Int, val at: Vector2D) : Event()
        data class Stopped(val ballId: Int) : Event()
    }

    /**
     * Advances the simulation by a single fixed time step.
     */
    fun step(state: WorldState, table: TableState): WorldState {
        val dt = params.timeStep
        if (dt <= 0.0) return state

        val events = ArrayList<Event>(4)

        // 1. Integrate positions and velocities (friction + Euler).
        var balls = state.balls.map { integrate(it, dt) }

        // 2. Resolve ball-ball collisions iteratively for stability.
        balls = resolveBallCollisions(balls, events)

        // 3. Resolve cushion rebounds and pockets.
        balls = resolveCushions(balls, table, events)

        // 4. Freeze balls that dropped below the stop threshold.
        balls = balls.map { b ->
            if (b.isMoving && b.velocity.length() < params.stopSpeedThreshold) {
                b.copy(velocity = Vector2D.ZERO, isMoving = false)
            } else b
        }

        return WorldState(balls, state.time + dt, events)
    }

    private fun resolveBallCollisions(balls: List<Ball>, events: MutableList<Event>): List<Ball> {
        var current = balls
        // A few relaxation iterations approximate simultaneous contacts well enough.
        repeat(3) { iter ->
            val pairs = CollisionEngine.findBallCollisions(current)
            if (pairs.isEmpty()) return@repeat
            val updated = current.toMutableList()
            for (pair in pairs) {
                val idxA = updated.indexOfFirst { it.id == pair.a.id }
                val idxB = updated.indexOfFirst { it.id == pair.b.id }
                if (idxA < 0 || idxB < 0) continue
                val (na, nb) = CollisionEngine.resolveBallCollision(
                    updated[idxA], updated[idxB], params.ballRestitution
                )
                updated[idxA] = na
                updated[idxB] = nb
                if (iter == 0) {
                    events.add(Event.BallCollision(na.id, nb.id, na.position))
                }
            }
            current = updated
        }
        return current
    }

    private fun resolveCushions(balls: List<Ball>, table: TableState, events: MutableList<Event>): List<Ball> {
        return balls.map { ball ->
            val (resolved, normal) = CollisionEngine.resolveCushionCollision(
                ball, table, params.cushionRestitution
            )
            if (normal != null) {
                events.add(Event.CushionRebound(ball.id, normal, resolved.position))
            }
            if (CollisionEngine.isPotted(ball, table)) {
                events.add(Event.Potted(ball.id, ball.position))
                ball.copy(velocity = Vector2D.ZERO, isMoving = false)
            } else {
                resolved
            }
        }
    }

    private fun integrate(ball: Ball, dt: Double): Ball {
        if (!ball.isMoving) return ball
        val speed = ball.velocity.length()
        if (speed < params.stopSpeedThreshold) {
            return ball.copy(velocity = Vector2D.ZERO, isMoving = false)
        }
        val newSpeed = (speed - params.rollingFriction * dt).coerceAtLeast(0.0)
        val dir = ball.velocity.normalized()
        val newVel = dir * newSpeed
        val newPos = ball.position + ball.velocity * dt
        return ball.copy(position = newPos, velocity = newVel, isMoving = newSpeed > 0.0)
    }
}
