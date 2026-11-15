package com.poolassistant.utils

import com.poolassistant.models.TableState
import com.poolassistant.models.Vector2D
import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateMapperTest {

    @Test
    fun `round-trips screen to world`() {
        val table = TableState(left = 0.0, top = 0.0, right = 2.0, bottom = 1.0)
        val mapper = CoordinateMapper.from(
            CoordinateMapper.ScreenRect(Vector2D(0.0, 0.0), Vector2D(1280.0, 720.0)),
            table
        )

        val world = Vector2D(1.0, 0.5)
        val screen = mapper.toScreen(world)
        val back = mapper.toWorld(screen)

        assertEquals(world.x, back.x, 1e-6)
        assertEquals(world.y, back.y, 1e-6)
    }

    @Test
    fun `maps center correctly`() {
        val table = TableState(left = 0.0, top = 0.0, right = 2.0, bottom = 1.0)
        val mapper = CoordinateMapper.from(
            CoordinateMapper.ScreenRect(Vector2D(100.0, 50.0), Vector2D(900.0, 450.0)),
            table
        )
        // Screen center (500, 250) -> world center (1.0, 0.5)
        val world = mapper.toWorld(Vector2D(500.0, 250.0))
        assertEquals(1.0, world.x, 1e-4)
        assertEquals(0.5, world.y, 1e-4)
    }

    @Test
    fun `scaleX and scaleY are consistent`() {
        val table = TableState(left = 0.0, top = 0.0, right = 2.0, bottom = 1.0)
        val mapper = CoordinateMapper.from(
            CoordinateMapper.ScreenRect(Vector2D(0.0, 0.0), Vector2D(1000.0, 1000.0)),
            table
        )
        assertEquals(2.0 / 1000.0, mapper.scaleX, 1e-9)
        assertEquals(1.0 / 1000.0, mapper.scaleY, 1e-9)
    }
}
