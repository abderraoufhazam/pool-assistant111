package com.poolassistant.ui

import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.poolassistant.PoolAssistantApp
import com.poolassistant.R
import java.util.Locale

/**
 * Settings screen: overlay visibility/alpha, detection sensitivity, processing
 * frequency, and physics-calibration sliders. Values are persisted via the
 * application calibration store.
 *
 * Slider progress is mapped into physically meaningful ranges:
 *  - Rolling friction  0..100  -> 0.00..1.00 m/s^2
 *  - Max cue speed     0..100  -> 1.0..10.0 m/s
 *  - Ball restitution  0..100  -> 0.50..1.00
 * The sliders initialize to the currently loaded values so changes are visible.
 */
class SettingsActivity : AppCompatActivity() {

    private val app: PoolAssistantApp
        get() = application as PoolAssistantApp

    private val prefs by lazy { getSharedPreferences("pool_assistant_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        bindSeekBar(
            seekBarId = R.id.sb_line_alpha,
            maxProgress = 255,
            initialProgress = prefs.getInt("line_alpha", 180),
            format = "%d",
            map = { it }
        ) { value ->
            prefs.edit().putInt("line_alpha", value.toInt()).apply()
        }

        bindSeekBar(
            seekBarId = R.id.sb_processing_freq,
            maxProgress = 490,
            initialProgress = prefs.getInt("processing_ms", 200).coerceIn(10, 500) - 10,
            format = "%d ms",
            map = { it + 10.0 }
        ) { value ->
            prefs.edit().putInt("processing_ms", value.toInt()).apply()
        }

        bindSeekBar(
            seekBarId = R.id.sb_rolling_friction,
            maxProgress = 100,
            initialProgress = (app.physicsParameters.rollingFriction * 100.0).toInt().coerceIn(0, 100),
            format = "%.2f m/s²",
            map = { it / 100.0 }
        ) { value ->
            app.physicsParameters.rollingFriction = value
        }

        bindSeekBar(
            seekBarId = R.id.sb_max_speed,
            maxProgress = 90,
            initialProgress = ((app.physicsParameters.maxCueSpeedAtFullPower - 1.0) * 10.0).toInt().coerceIn(0, 90),
            format = "%.1f m/s",
            map = { 1.0 + it / 10.0 }
        ) { value ->
            app.physicsParameters.maxCueSpeedAtFullPower = value
        }

        bindSeekBar(
            seekBarId = R.id.sb_ball_restitution,
            maxProgress = 50,
            initialProgress = ((app.physicsParameters.ballRestitution - 0.5) * 100.0).toInt().coerceIn(0, 50),
            format = "%.2f",
            map = { 0.5 + it / 100.0 }
        ) { value ->
            app.physicsParameters.ballRestitution = value
        }

        findViewById<android.widget.Button>(R.id.btn_save_calibration).setOnClickListener {
            app.savePhysicsParameters()
            android.widget.Toast.makeText(this, R.string.calibration_saved, android.widget.Toast.LENGTH_SHORT).show()
        }

        findViewById<android.widget.Button>(R.id.btn_reset_calibration).setOnClickListener {
            app.physicsCalibration.reset()
            app.reloadPhysicsParameters()
            refreshPhysicsSliders()
            android.widget.Toast.makeText(this, R.string.calibration_reset, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Binds a [SeekBar] to a label, mapping raw progress [0..maxProgress] into a
     * physical value via [map] (raw -> value), displaying via [format], and
     * invoking [onChange] with the mapped value.
     */
    private fun bindSeekBar(
        seekBarId: Int,
        maxProgress: Int,
        initialProgress: Int,
        format: String,
        map: (Double) -> Double,
        onChange: (Double) -> Unit
    ) {
        val seek = findViewById<SeekBar>(seekBarId)
        val label = findLabelFor(seekBarId)
        seek.max = maxProgress

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = map(progress.toDouble())
                label?.text = String.format(Locale.US, format, value)
                onChange(value)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Trigger initial render to reflect loaded values.
        seek.progress = initialProgress
    }

    private fun refreshPhysicsSliders() {
        findViewById<SeekBar>(R.id.sb_rolling_friction).progress =
            (app.physicsParameters.rollingFriction * 100.0).toInt().coerceIn(0, 100)
        findViewById<SeekBar>(R.id.sb_max_speed).progress =
            ((app.physicsParameters.maxCueSpeedAtFullPower - 1.0) * 10.0).toInt().coerceIn(0, 90)
        findViewById<SeekBar>(R.id.sb_ball_restitution).progress =
            ((app.physicsParameters.ballRestitution - 0.5) * 100.0).toInt().coerceIn(0, 50)
    }

    private fun findLabelFor(seekBarId: Int): TextView? = when (seekBarId) {
        R.id.sb_line_alpha -> findViewById(R.id.tv_line_alpha_value)
        R.id.sb_processing_freq -> findViewById(R.id.tv_processing_freq_value)
        R.id.sb_rolling_friction -> findViewById(R.id.tv_rolling_friction_value)
        R.id.sb_max_speed -> findViewById(R.id.tv_max_speed_value)
        R.id.sb_ball_restitution -> findViewById(R.id.tv_ball_restitution_value)
        else -> null
    }
}
