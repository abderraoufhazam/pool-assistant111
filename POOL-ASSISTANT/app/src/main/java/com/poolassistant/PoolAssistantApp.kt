package com.poolassistant

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.poolassistant.calibration.PhysicsCalibration
import com.poolassistant.physics.PhysicsParameters

/**
 * Application entry point. Holds process-wide singletons (calibration and the
 * current physics parameters) so activities, the capture service, and the
 * overlay share one consistent configuration.
 */
class PoolAssistantApp : Application() {

    lateinit var physicsCalibration: PhysicsCalibration
        private set

    @Volatile
    var physicsParameters: PhysicsParameters = PhysicsParameters()
        private set

    private val prefs: SharedPreferences
        get() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun onCreate() {
        super.onCreate()
        physicsCalibration = PhysicsCalibration(SharedPrefsStore(prefs))
        physicsParameters = physicsCalibration.load()
    }

    /** Reload calibration from storage into [physicsParameters]. */
    fun reloadPhysicsParameters() {
        physicsParameters = physicsCalibration.load()
    }

    /** Persist current parameters. */
    fun savePhysicsParameters() {
        physicsCalibration.save(physicsParameters)
    }

    private class SharedPrefsStore(private val prefs: SharedPreferences) :
        PhysicsCalibration.CalibrationStore {
        override fun getDouble(key: String, default: Double): Double =
            prefs.getFloat(key, default.toFloat()).toDouble()
        override fun putDouble(key: String, value: Double) {
            prefs.edit().putFloat(key, value.toFloat()).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "pool_assistant_prefs"
    }
}
