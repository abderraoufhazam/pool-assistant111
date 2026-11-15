package com.poolassistant.physics

import com.poolassistant.models.Ball
import com.poolassistant.models.TableState
import com.poolassistant.models.Vector2D

/**
 * Computes predicted trajectories for the cue ball and impacted object balls
 * using the deterministic [PhysicsEngine].
 *
 * The predictor takes a static scene (positions, a cue direction, and a power
 * percentage) and simulates forward in time, recording each ball's path and
 * the collision marker points, up to the configured safety limits.
 */
class TrajectoryPredictor(
    private val params: PhysicsParameters = PhysicsParameters()
) {

    data class Prediction(
        val cuePath: List<Vector2D> = emptyList(),
        val objectPaths: Map<Int, List<Vector2D>> = emptyMap(),
        val collisionPoints: List<Vector2D> = emptyList(),
        val reboundPoints: List<Vector2D> = emptyList(),
        val finalPositions: Map<Int, Vector2D> = emptyMap(),
        val confidence: Double = 0.0,
        val simulatedSeconds: Double = 0.0
    )

    private val engine = PhysicsEngine(params)

    /**
     * Runs a forward simulation.
     *
     * @param balls the static scene balls (cue ball must be present).
     * @param table the table geometry (pockets included).
     * @param cueDirection unit vector of the intended cue-ball travel.
     * @param powerPercent 0..100 from the power indicator.
     * @param confidence 0..1 scene detection confidence (propagated to result).
     */
    fun predict(
        balls: List<Ball>,
        table: TableState,
        cueDirection: Vector2D,
        powerPercent: Double,
        confidence: Double
    ): Prediction {
        val dir = cueDirection.normalized()
        if (dir.isZero() || balls.isEmpty()) {
            return Prediction(confidence = 0.0)
        }

        val initialSpeed = powerToSpeed(powerPercent)

        // Start moving balls: the cue ball receives the shot velocity.
        val startBalls = balls.map { b ->
            if (b.number == 0) b.copy(velocity = dir * initialSpeed, isMoving = true)
            else b.copy(velocity = Vector2D.ZERO, isMoving = false)
        }

        var state = PhysicsEngine.WorldState(startBalls, 0.0)

        val cuePath = ArrayList<Vector2D>(256)
        val objectPaths = HashMap<Int, MutableList<Vector2D>>()
        val collisionPoints = ArrayList<Vector2D>()
        val reboundPoints = ArrayList<Vector2D>()

        val maxSteps = (params.maxSimulationSeconds / params.timeStep).toInt().coerceAtLeast(1)
        var collisionEventCount = 0

        for (stepIndex in 0 until maxSteps) {
            cuePath.add(state.balls.firstOrNull { it.number == 0 }?.position ?: Vector2D.ZERO)
            for (b in state.balls) {
                if (b.number != 0 && b.isMoving) {
                    objectPaths.getOrPut(b.id) { ArrayList() }.add(b.position)
                }
            }

            state = engine.step(state, table)

            for (event in state.events) {
                when (event) {
                    is PhysicsEngine.Event.BallCollision -> {
                        collisionPoints.add(event.at)
                        collisionEventCount++
                    }
                    is PhysicsEngine.Event.CushionRebound -> reboundPoints.add(event.at)
                    else -> {}
                }
            }

            // Safety: stop early if collision-event budget is exhausted and nothing moves.
            if (collisionEventCount >= params.maxCollisionEvents) break
            if (state.balls.none { it.isMoving }) break
        }

        val finalPositions = state.balls.associate { it.id to it.position }

        return Prediction(
            cuePath = cuePath,
            objectPaths = objectPaths.mapValues { it.value.toList() },
            collisionPoints = collisionPoints,
            reboundPoints = reboundPoints,
            finalPositions = finalPositions,
            confidence = confidence,
            simulatedSeconds = state.time
        )
    }

    /**
     * Converts a 0..100 power percentage to an initial cue speed using the
     * calibrated [PhysicsParameters.maxCueSpeedAtFullPower]. Linear model.
     */
    fun powerToSpeed(powerPercent: Double): Double {
        val p = powerPercent.coerceIn(0.0, 100.0)
        return params.maxCueSpeedAtFullPower * (p / 100.0)
    }
}
