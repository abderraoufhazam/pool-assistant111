package com.poolassistant.physics

/**
 * Tunable physics parameters for the 2D billiards simulation.
 *
 * IMPORTANT: these are *estimates/assumptions*, not exact values claimed from
 * the internals of 8 Ball Pool. They are exposed through the calibration UI so
 * the user can tune them against observed game behaviour. See
 * [com.poolassistant.calibration.PhysicsCalibration].
 */
data class PhysicsParameters(
    /** Coefficient of restitution for ball-ball impacts (energy retention, 0..1). */
    var ballRestitution: Double = 0.95,

    /** Coefficient of restitution for ball-cushion impacts. */
    var cushionRestitution: Double = 0.75,

    /** Rolling friction deceleration in m/s^2 (Coulomb rolling resistance). */
    var rollingFriction: Double = 0.35,

    /** Sliding friction deceleration used while a ball is sliding. */
    var slidingFriction: Double = 0.55,

    /** Cue-ball initial speed (m/s) at 100% power indicator. */
    var maxCueSpeedAtFullPower: Double = 6.0,

    /** Minimum cue speed below which a ball is treated as stopped. */
    var stopSpeedThreshold: Double = 0.01,

    /** Additional speed transfer factor for spin (top/back) cushion kick. */
    var spinCushionFactor: Double = 0.1,

    /** Simulation fixed time step in seconds. */
    var timeStep: Double = 1.0 / 240.0,

    /** Maximum number of simulated collision events per prediction (safety). */
    var maxCollisionEvents: Int = 8,

    /** Maximum simulated wall-clock seconds per prediction (safety). */
    var maxSimulationSeconds: Double = 6.0
)
