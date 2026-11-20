// language: Kotlin | path: app/src/main/java/com/jaccksallas/aiexam/ScreenCaptureHelper.kt
// runtime: Android API 26+ | target: Android device

package com.jaccksallas.aiexam

import android.app.Activity
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
import android.util.DisplayMetrics
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class ScreenCaptureHelper(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val metrics = DisplayMetrics().also { dm ->
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(dm)
    }

    // ─── Setup dari result MediaProjection permission ─────────────────────────

    fun setup(resultCode: Int, data: Intent) {
        val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
            as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)
        startCapture()
    }

    private fun startCapture() {
        val width  = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AIExamCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, Handler(Looper.getMainLooper())
        )
    }

    // ─── Capture satu frame + OCR ────────────────────────────────────────────

    fun captureAndOcr(onResult: (String) -> Unit) {
        val image: Image? = imageReader?.acquireLatestImage()
        if (image == null) {
            onResult("")
            return
        }

        val bitmap = imageToBitmap(image)
        image.close()

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { result ->
                onResult(result.text)
            }
            .addOnFailureListener {
                onResult("")
            }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        // Crop ke ukuran asli
        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }

    fun release() {
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        recognizer.close()
    }
}
