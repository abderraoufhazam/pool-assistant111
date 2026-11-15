package com.poolassistant.physics

import com.poolassistant.models.Ball
import com.poolassistant.models.TableState
import com.poolassistant.models.Vector2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollisionEngineTest {

    @Test
    fun `head-on ball collision transfers velocity to equal-mass target`() {
        val a = Ball(id = 0, number = 0, position = Vector2D(0.0, 0.0), velocity = Vector2D(5.0, 0.0), isMoving = true)
        val b = Ball(id = 1, number = 1, position = Vector2D(0.1, 0.0), velocity = Vector2D(0.0, 0.0), isMoving = false)

        val (na, nb) = CollisionEngine.resolveBallCollision(a, b, restitution = 1.0)

        // Elastic, equal masses: cue ball nearly stops, target takes the velocity.
        assertTrue("cue ball should slow down", na.velocity.length() < 0.1)
        assertTrue("target should move", nb.velocity.length() > 4.0)
        assertEquals(5.0, nb.velocity.x, 0.2)
    }

    @Test
    fun `findBallCollisions detects overlap`() {
        val a = Ball(id = 0, position = Vector2D(0.0, 0.0))
        val b = Ball(id = 1, position = Vector2D(0.03, 0.0)) // overlapping (radii ~0.0286 each)
        val collisions = CollisionEngine.findBallCollisions(listOf(a, b))
        assertEquals(1, collisions.size)
    }

    @Test
    fun `findBallCollisions ignores non-overlap`() {
        val a = Ball(id = 0, position = Vector2D(0.0, 0.0))
        val b = Ball(id = 1, position = Vector2D(1.0, 0.0))
        assertEquals(0, CollisionEngine.findBallCollisions(listOf(a, b)).size)
    }

    @Test
    fun `cushion collision reflects horizontal velocity`() {
        val table = TableState(
            left = 0.0, top = 0.0, right = 2.0, bottom = 1.0,
            cushionInset = 0.05
        )
        val ball = Ball(
            id = 0,
            position = Vector2D(table.innerLeft - 0.001, 0.5),
            velocity = Vector2D(-3.0, 0.0),
            isMoving = true
        )
        val (resolved, normal) = CollisionEngine.resolveCushionCollision(ball, table, restitution = 0.8)
        assertEquals(2.4, resolved.velocity.x, 1e-6) // -3 * -0.8
        assertTrue(resolved.velocity.x > 0.0)
        assertEquals(Vector2D(1.0, 0.0), normal)
    }

    @Test
    fun `pocket detection`() {
        val pocket = TableState.Pocket(Vector2D(0.05, 0.05), 0.06)
        val table = TableState(pockets = listOf(pocket))
        val ball = Ball(id = 0, position = Vector2D(0.05, 0.05))
        assertTrue(CollisionEngine.isPotted(ball, table))
    }
}
