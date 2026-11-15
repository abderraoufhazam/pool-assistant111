package com.poolassistant.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class Vector2DTest {

    @Test
    fun `addition and subtraction`() {
        val a = Vector2D(3.0, 4.0)
        val b = Vector2D(1.0, 2.0)
        assertEquals(Vector2D(4.0, 6.0), a + b)
        assertEquals(Vector2D(2.0, 2.0), a - b)
    }

    @Test
    fun `scalar multiply and divide`() {
        val a = Vector2D(3.0, 4.0)
        assertEquals(Vector2D(6.0, 8.0), a * 2.0)
        assertEquals(Vector2D(1.5, 2.0), a / 2.0)
    }

    @Test
    fun `length matches pythagorean`() {
        val v = Vector2D(3.0, 4.0)
        assertEquals(5.0, v.length(), 1e-9)
    }

    @Test
    fun `normalized is unit`() {
        val v = Vector2D(3.0, 4.0).normalized()
        assertEquals(1.0, v.length(), 1e-9)
    }

    @Test
    fun `dot product`() {
        val a = Vector2D(1.0, 2.0)
        val b = Vector2D(3.0, 4.0)
        assertEquals(11.0, a.dot(b), 1e-9)
    }

    @Test
    fun `reflect across normal`() {
        val v = Vector2D(1.0, -1.0)
        val n = Vector2D(1.0, 0.0)
        val reflected = Vector2D.reflect(v, n)
        assertEquals(-1.0, reflected.x, 1e-9)
        assertEquals(-1.0, reflected.y, 1e-9)
    }

    @Test
    fun `rotation ninety degrees`() {
        val v = Vector2D(1.0, 0.0)
        val rotated = v.rotated(Math.PI / 2.0)
        assertEquals(0.0, rotated.x, 1e-9)
        assertEquals(1.0, rotated.y, 1e-9)
    }
}
