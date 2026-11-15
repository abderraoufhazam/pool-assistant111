package com.poolassistant.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Owns the MediaProjection virtual display and delivers captured frames to
 * consumers as [Bitmap]s on a [Flow].
 *
 * MediaProjection is requested once from [MainActivity] via the system result
 * intent; this manager receives the [MediaProjection] instance and builds an
 * [ImageReader] surface. Frames are converted to ARGB bitmaps off the caller's
 * thread and pushed through a bounded [Channel] (old frames dropped under load).
 */
class ScreenCaptureManager(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val frameChannel = Channel<Bitmap>(capacity = 2)
    val frames: Flow<Bitmap> = frameChannel.receiveAsFlow()

    @Volatile
    var isCapturing: Boolean = false
        private set

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Starts capture using the [projection] instance obtained from the user
     * consent intent. Captures at native display metrics and 60fps.
     */
    fun start(projection: MediaProjection, width: Int, height: Int, density: Int): Boolean {
        if (isCapturing) return true

        val reader = ImageReader.newInstance(
            width, height, PixelFormat.RGBA_8888, 2
        )
        imageReader = reader

        reader.setOnImageAvailableListener({ r ->
            if (isCapturing) {
                val image: Image? = try { r.acquireLatestImage() } catch (_: Exception) { null }
                if (image != null) {
                    try {
                        val bitmap = imageToBitmap(image)
                        if (bitmap != null) frameChannel.trySend(bitmap)
                    } finally {
                        image.close()
                    }
                }
            }
        }, handler)

        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

        virtualDisplay = projection.createVirtualDisplay(
            "PoolAssistantCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, handler
        )

        mediaProjection = projection
        isCapturing = true
        return true
    }

    fun stop() {
        isCapturing = false
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
    }

    fun release() = stop()

    /** Converts a YUV/RGBA Image plane to an ARGB bitmap (RGB_565 fallback). */
    private fun imageToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val w = image.width
        val h = image.height

        // RGBA_8888: single plane, 4 bytes/pixel.
        if (image.format == PixelFormat.RGBA_8888 && pixelStride == 4) {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(buffer)
            return bmp
        }

        // Generic RGBA fallback with row stride handling.
        val bmp = Bitmap.createBitmap(w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(buffer)
        return if (rowPadding == 0) bmp
        else Bitmap.createBitmap(bmp, 0, 0, w, h)
    }

    companion object {
        /** Creates the consent intent the user must approve before capture. */
        fun consentIntent(context: Context): Intent {
            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            return mgr.createScreenCaptureIntent()
        }
    }
}
