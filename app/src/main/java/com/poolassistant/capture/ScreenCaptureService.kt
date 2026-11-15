package com.poolassistant.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.poolassistant.R

/**
 * Foreground service that owns the live [ScreenCaptureManager].
 *
 * Android requires MediaProjection capture to run from a foreground service
 * with the mediaProjection type, keeping a persistent notification. The
 * service exposes the capture manager via its binder so [MainActivity] and the
 * overlay can start/stop capture and observe frames.
 */
class ScreenCaptureService : Service() {

    private val binder = LocalBinder()

    lateinit var captureManager: ScreenCaptureManager
        private set

    inner class LocalBinder : Binder() {
        fun service(): ScreenCaptureService = this@ScreenCaptureService
    }

    override fun onCreate() {
        super.onCreate()
        captureManager = ScreenCaptureManager(this)
        activeManager = captureManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // NOTE: startForeground() is NOT called here — it is called inside
        // startCapture() after the MediaProjection is obtained, which is the
        // required order for the mediaProjection foreground service type.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        captureManager.release()
        activeManager = null
        super.onDestroy()
    }

    /** Start/continue capture with the projection obtained from consent result. */
    fun startCapture(resultCode: Int, data: Intent) {
        try {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mgr.getMediaProjection(resultCode, data) ?: return

            // Promote to a foreground service BEFORE creating the virtual display.
            // Android 10+ requires MediaProjection to run from a foreground service
            // of type mediaProjection; use the explicit-type overload on Q+.
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
            } catch (t: Throwable) {
                try {
                    startForeground(NOTIFICATION_ID, buildNotification())
                } catch (t2: Throwable) {
                    // Continue anyway; some devices are lenient about this.
                }
            }

            val metrics = resources.displayMetrics
            captureManager.start(projection, metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
        } catch (t: Throwable) {
            // Never crash the app when capture fails; just stop the service.
            stopSelf()
        }
    }

    private fun buildNotification(): Notification {
        createChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.capture_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "pool_assistant_capture"
        const val NOTIFICATION_ID = 1001

        /**
         * Static handle to the live capture manager so the overlay service can
         * reach it even if the Activity-based binding never completed.
         */
        @Volatile
        var activeManager: ScreenCaptureManager? = null
            private set
    }
}
