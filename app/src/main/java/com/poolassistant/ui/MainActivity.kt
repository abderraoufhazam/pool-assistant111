package com.poolassistant.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.poolassistant.capture.ScreenCaptureManager
import com.poolassistant.capture.ScreenCaptureService
import com.poolassistant.overlay.FloatingOverlayService
import com.poolassistant.R

/**
 * Main screen: a minimal dark control panel with:
 *   - Start/Stop screen analysis.
 *   - Overlay + capture permission status.
 *   - Settings entry.
 *   - Detection status readout.
 *
 * MediaProjection consent is requested here; the result is handed to the
 * capture foreground service, whose frames are consumed by the overlay.
 */
class MainActivity : AppCompatActivity() {

    private val vm: MainViewModel by viewModels()

    private var captureService: ScreenCaptureService? = null
    private var overlayService: FloatingOverlayService? = null
    private var captureBound = false
    private var overlayBound = false

    private val captureConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            captureService = (service as ScreenCaptureService.LocalBinder).service()
            captureBound = true
            attachOverlayToCapture()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            captureService = null
            captureBound = false
        }
    }

    private val overlayConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            overlayService = (service as FloatingOverlayService.LocalBinder).service()
            overlayBound = true
            attachOverlayToCapture()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            overlayService = null
            overlayBound = false
        }
    }

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val svc = captureService
                if (svc != null) {
                    svc.startCapture(result.resultCode, result.data!!)
                    vm.setStatus(MainViewModel.Status.CAPTURING, getString(R.string.status_capturing))
                    startOverlayService()
                    launchGame()
                } else {
                    vm.setStatus(MainViewModel.Status.ERROR, getString(R.string.error_capture_service))
                }
            } else {
                vm.setStatus(MainViewModel.Status.IDLE, getString(R.string.status_capture_cancelled))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindServices()

        findViewById<android.widget.Button>(R.id.btn_start).setOnClickListener { startAnalysis() }
        findViewById<android.widget.Button>(R.id.btn_stop).setOnClickListener { stopAnalysis() }
        findViewById<android.widget.Button>(R.id.btn_overlay_permission).setOnClickListener { requestOverlayPermission() }
        findViewById<android.widget.Button>(R.id.btn_capture_permission).setOnClickListener { requestCaptureConsent() }
        findViewById<android.widget.Button>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        observeStatus()
    }

    private fun observeStatus() {
        vm.status.observe(this) { status ->
            findViewById<android.widget.TextView>(R.id.tv_status).text =
                when (status) {
                    MainViewModel.Status.IDLE -> getString(R.string.state_idle)
                    MainViewModel.Status.CAPTURING -> getString(R.string.state_capturing)
                    MainViewModel.Status.ANALYZING -> getString(R.string.state_analyzing)
                    MainViewModel.Status.ERROR -> getString(R.string.state_error)
                }
        }
        vm.statusMessage.observe(this) { msg ->
            findViewById<android.widget.TextView>(R.id.tv_status_detail).text = msg
        }
    }

    private fun bindServices() {
        val intent = Intent(this, ScreenCaptureService::class.java)
        // Start (not just bind) so the capture service survives when this
        // activity is backgrounded or destroyed while the game is open.
        startService(intent)
        bindService(intent, captureConnection, Context.BIND_AUTO_CREATE)
    }

    private fun startOverlayService() {
        val intent = Intent(this, FloatingOverlayService::class.java)
        startService(intent)
        bindService(intent, overlayConnection, Context.BIND_AUTO_CREATE)
    }

    private fun attachOverlayToCapture() {
        val capture = captureService ?: return
        val overlay = overlayService ?: return
        if (captureBound && overlayBound) {
            overlay.attachCaptureManager(capture.captureManager)
        }
    }

    private fun startAnalysis() {
        if (!hasOverlayPermission()) {
            requestOverlayPermission()
            return
        }
        vm.setStatus(MainViewModel.Status.ANALYZING, getString(R.string.status_requesting_capture))
        requestCaptureConsent()
    }

    private fun stopAnalysis() {
        overlayService?.detachCaptureManager()
        captureService?.stopSelf()
        overlayService?.stopSelf()
        captureBound = false
        overlayBound = false
        vm.setStatus(MainViewModel.Status.IDLE, getString(R.string.status_stopped))
    }

    private fun requestCaptureConsent() {
        val intent = ScreenCaptureManager.consentIntent(this)
        projectionLauncher.launch(intent)
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasOverlayPermission()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    /** Opens 8 Ball Pool automatically after capture starts. */
    private fun launchGame() {
        val pkg = "com.miniclip.eightballpool"
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            Settings.canDrawOverlays(this)
    }

    override fun onResume() {
        super.onResume()
        // Show the latest detection diagnostics written by the overlay service.
        val debug = getSharedPreferences("pool_assistant_prefs", MODE_PRIVATE)
            .getString("debug_status", "")
        if (!debug.isNullOrEmpty()) {
            findViewById<android.widget.TextView>(R.id.tv_status_detail).text = debug
        }
    }

    override fun onDestroy() {
        runCatching { if (captureBound) unbindService(captureConnection) }
        runCatching { if (overlayBound) unbindService(overlayConnection) }
        super.onDestroy()
    }
}
