package com.poolassistant.calibration

import com.poolassistant.physics.PhysicsParameters

/**
 * Persistent, user-tunable calibration for the physics model.
 *
 * The ship-with values are reasonable *estimates*, clearly marked as such.
 * The user adjusts them from the Settings -> Physics calibration screen; the
 * stored values are read back into a [PhysicsParameters] at runtime.
 *
 * Stored via Android SharedPreferences (a thin wrapper is provided by the app
 * layer and injected as [store]); this keeps the class pure & testable.
 */
class PhysicsCalibration(
    private val store: CalibrationStore
) {
    fun load(): PhysicsParameters {
        val p = PhysicsParameters()
        p.ballRestitution = store.getDouble(KEY_BALL_RESTITUTION, p.ballRestitution)
        p.cushionRestitution = store.getDouble(KEY_CUSHION_RESTITUTION, p.cushionRestitution)
        p.rollingFriction = store.getDouble(KEY_ROLLING_FRICTION, p.rollingFriction)
        p.slidingFriction = store.getDouble(KEY_SLIDING_FRICTION, p.slidingFriction)
        p.maxCueSpeedAtFullPower = store.getDouble(KEY_MAX_CUE_SPEED, p.maxCueSpeedAtFullPower)
        p.spinCushionFactor = store.getDouble(KEY_SPIN_FACTOR, p.spinCushionFactor)
        return p
    }

    fun save(p: PhysicsParameters) {
        store.putDouble(KEY_BALL_RESTITUTION, p.ballRestitution)
        store.putDouble(KEY_CUSHION_RESTITUTION, p.cushionRestitution)
        store.putDouble(KEY_ROLLING_FRICTION, p.rollingFriction)
        store.putDouble(KEY_SLIDING_FRICTION, p.slidingFriction)
        store.putDouble(KEY_MAX_CUE_SPEED, p.maxCueSpeedAtFullPower)
        store.putDouble(KEY_SPIN_FACTOR, p.spinCushionFactor)
    }

    fun reset() {
        save(PhysicsParameters())
    }

    interface CalibrationStore {
        fun getDouble(key: String, default: Double): Double
        fun putDouble(key: String, value: Double)
    }

    companion object {
        const val KEY_BALL_RESTITUTION = "phys_ball_restitution"
        const val KEY_CUSHION_RESTITUTION = "phys_cushion_restitution"
        const val KEY_ROLLING_FRICTION = "phys_rolling_friction"
        const val KEY_SLIDING_FRICTION = "phys_sliding_friction"
        const val KEY_MAX_CUE_SPEED = "phys_max_cue_speed"
        const val KEY_SPIN_FACTOR = "phys_spin_factor"
    }
}
