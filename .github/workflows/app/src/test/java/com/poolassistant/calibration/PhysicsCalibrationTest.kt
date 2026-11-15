package com.poolassistant.calibration

import com.poolassistant.physics.PhysicsParameters
import org.junit.Assert.assertEquals
import org.junit.Test

class PhysicsCalibrationTest {

    private class InMemoryStore : PhysicsCalibration.CalibrationStore {
        val values = HashMap<String, Double>()
        override fun getDouble(key: String, default: Double): Double = values[key] ?: default
        override fun putDouble(key: String, value: Double) { values[key] = value }
    }

    @Test
    fun `save then load round-trips`() {
        val store = InMemoryStore()
        val cal = PhysicsCalibration(store)
        val p = PhysicsParameters().apply {
            rollingFriction = 0.42
            maxCueSpeedAtFullPower = 7.5
            ballRestitution = 0.88
        }
        cal.save(p)

        val loaded = cal.load()
        assertEquals(0.42, loaded.rollingFriction, 1e-9)
        assertEquals(7.5, loaded.maxCueSpeedAtFullPower, 1e-9)
        assertEquals(0.88, loaded.ballRestitution, 1e-9)
    }

    @Test
    fun `defaults when nothing saved`() {
        val store = InMemoryStore()
        val cal = PhysicsCalibration(store)
        val loaded = cal.load()
        val defaults = PhysicsParameters()
        assertEquals(defaults.rollingFriction, loaded.rollingFriction, 1e-9)
        assertEquals(defaults.maxCueSpeedAtFullPower, loaded.maxCueSpeedAtFullPower, 1e-9)
    }

    @Test
    fun `reset restores defaults`() {
        val store = InMemoryStore()
        val cal = PhysicsCalibration(store)
        val p = PhysicsParameters().apply { rollingFriction = 0.99 }
        cal.save(p)
        cal.reset()
        val loaded = cal.load()
        assertEquals(PhysicsParameters().rollingFriction, loaded.rollingFriction, 1e-9)
    }
}
