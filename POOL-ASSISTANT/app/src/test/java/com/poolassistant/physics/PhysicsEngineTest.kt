package com.poolassistant.physics

import com.poolassistant.models.Ball
import com.poolassistant.models.TableState
import com.poolassistant.models.Vector2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsEngineTest {

    private fun emptyTable(): TableState = TableState(
        left = 0.0, top = 0.0, right = 2.0, bottom = 1.0,
        cushionInset = 0.05,
        pockets = emptyList()
    )

    private fun params(): PhysicsParameters = PhysicsParameters().apply {
        rollingFriction = 0.0   // isolate motion from friction in these tests
    }

    @Test
    fun `friction decelerates a moving ball`() {
        val p = PhysicsParameters().apply { rollingFriction = 1.0 }
        val engine = PhysicsEngine(p)
        val ball = Ball(id = 0, number = 0, position = Vector2D(0.5, 0.5), velocity = Vector2D(2.0, 0.0), isMoving = true)
        var state = PhysicsEngine.WorldState(listOf(ball), 0.0)

        for (i in 0 until 120) {
            state = engine.step(state, emptyTable())
        }
        // After 0.5s at 1 m/s^2 decel, speed should be ~1.5 m/s.
        val speed = state.balls[0].velocity.length()
        assertTrue("expected deceleration, got $speed", speed in 1.4..1.6)
    }

    @Test
    fun `ball stops below threshold`() {
        val p = PhysicsParameters().apply {
            rollingFriction = 2.0
            stopSpeedThreshold = 0.1
        }
        val engine = PhysicsEngine(p)
        val ball = Ball(id = 0, number = 0, position = Vector2D(0.5, 0.5), velocity = Vector2D(1.0, 0.0), isMoving = true)
        var state = PhysicsEngine.WorldState(listOf(ball), 0.0)
        repeat(1000) { state = engine.step(state, emptyTable()) }
        assertTrue(!state.balls[0].isMoving)
        assertEquals(0.0, state.balls[0].velocity.length(), 1e-9)
    }

    @Test
    fun `cushion rebound emits event`() {
        val p = PhysicsParameters().apply {
            rollingFriction = 0.0
            cushionRestitution = 1.0
        }
        val engine = PhysicsEngine(p)
        val table = emptyTable()
        val ball = Ball(
            id = 0, number = 0,
            position = Vector2D(table.innerLeft, 0.5),
            velocity = Vector2D(-2.0, 0.0),
            isMoving = true
        )
        var state = PhysicsEngine.WorldState(listOf(ball), 0.0)
        var rebound = false
        repeat(300) {
            state = engine.step(state, table)
            if (state.events.any { it is PhysicsEngine.Event.CushionRebound }) rebound = true
        }
        assertTrue("expected a cushion rebound event", rebound)
    }

    @Test
    fun `time advances each step`() {
        val engine = PhysicsEngine(params())
        val ball = Ball(id = 0, number = 0, position = Vector2D(0.5, 0.5), velocity = Vector2D(1.0, 0.0), isMoving = true)
        var state = PhysicsEngine.WorldState(listOf(ball), 0.0)
        state = engine.step(state, emptyTable())
        assertEquals(params().timeStep, state.time, 1e-12)
    }
}
