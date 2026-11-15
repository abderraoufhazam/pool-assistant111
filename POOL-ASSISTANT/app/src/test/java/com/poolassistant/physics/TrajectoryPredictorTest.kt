package com.poolassistant.physics

import com.poolassistant.models.Ball
import com.poolassistant.models.TableState
import com.poolassistant.models.Vector2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrajectoryPredictorTest {

    private fun table(): TableState = TableState(
        left = 0.0, top = 0.0, right = 2.0, bottom = 1.0,
        cushionInset = 0.05,
        pockets = emptyList()
    )

    @Test
    fun `power converts linearly to speed`() {
        val p = PhysicsParameters().apply { maxCueSpeedAtFullPower = 6.0 }
        val predictor = TrajectoryPredictor(p)
        assertEquals(0.0, predictor.powerToSpeed(0.0), 1e-9)
        assertEquals(3.0, predictor.powerToSpeed(50.0), 1e-9)
        assertEquals(6.0, predictor.powerToSpeed(100.0), 1e-9)
    }

    @Test
    fun `prediction produces cue path and final positions`() {
        val predictor = TrajectoryPredictor(
            PhysicsParameters().apply {
                rollingFriction = 0.5
                maxCollisionEvents = 4
                maxSimulationSeconds = 3.0
            }
        )
        val cue = Ball(id = 0, number = 0, position = Vector2D(0.5, 0.5))
        val target = Ball(id = 1, number = 1, position = Vector2D(1.5, 0.5))

        val pred = predictor.predict(
            balls = listOf(cue, target),
            table = table(),
            cueDirection = Vector2D(1.0, 0.0),
            powerPercent = 70.0,
            confidence = 0.9
        )

        assertTrue("cue path should have many points", pred.cuePath.size > 10)
        assertNotNull(pred.finalPositions[0])
        assertNotNull(pred.finalPositions[1])
        assertEquals(0.9, pred.confidence, 1e-9)
    }

    @Test
    fun `prediction with zero direction returns empty`() {
        val predictor = TrajectoryPredictor()
        val cue = Ball(id = 0, number = 0, position = Vector2D(0.5, 0.5))
        val pred = predictor.predict(
            listOf(cue), table(), Vector2D.ZERO, 50.0, 0.9
        )
        assertTrue(pred.cuePath.isEmpty())
        assertEquals(0.0, pred.confidence, 1e-9)
    }

    @Test
    fun `cue hits target producing a collision point`() {
        val predictor = TrajectoryPredictor(
            PhysicsParameters().apply {
                rollingFriction = 0.1
                maxCollisionEvents = 8
                maxSimulationSeconds = 5.0
                timeStep = 1.0 / 500.0
            }
        )
        val cue = Ball(id = 0, number = 0, position = Vector2D(0.5, 0.5))
        val target = Ball(id = 1, number = 1, position = Vector2D(1.0, 0.5))

        val pred = predictor.predict(
            listOf(cue, target), table(), Vector2D(1.0, 0.0), 100.0, 0.9
        )
        assertTrue("should record at least one collision", pred.collisionPoints.isNotEmpty())
        assertTrue("target ball should have a path after impact", pred.objectPaths.containsKey(1))
    }
}
