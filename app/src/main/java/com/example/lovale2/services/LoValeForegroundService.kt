package com.example.lovale2.services

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import java.nio.ByteBuffer

class LoValeForegroundService : Service() {

    companion object {
        private const val TAG = "LoValeForegroundService"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Iniciando servicio de monitoreo LoVale...")

        if (!ScreenCaptureHolder.isCapturing || ScreenCaptureHolder.resultData == null) {
            Log.e(TAG, "No hay credenciales de MediaProjection válidas.")
            stopSelf()
            return START_NOT_STICKY
        }

        iniciarCapturaPantalla()
        iniciarBucleDeEscaneo()

        return START_STICKY
    }

    private fun iniciarCapturaPantalla() {
        try {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(
                ScreenCaptureHolder.resultCode,
                ScreenCaptureHolder.resultData!!
            )

            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
            isRunning = true
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar VirtualDisplay", e)
        }
    }

    private fun iniciarBucleDeEscaneo() {
        serviceScope.launch {
            while (isRunning) {
                delay(2500) // Escanea la pantalla cada 2.5 segundos
                capturarYAnalizarPantalla()
            }
        }
    }

    private fun capturarYAnalizarPantalla() {
        val image = imageReader?.acquireLatestImage() ?: return
        try {
            val planes = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            // Procesar con ML Kit OCR
            procesarConMlKit(bitmap)

        } catch (e: Exception) {
            Log.e(TAG, "Error al procesar frame de pantalla", e)
            image.close()
        }
    }

    private fun procesarConMlKit(bitmap: Bitmap) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val textoCompleto = visionText.text
                if (textoCompleto.contains("$", ignoreCase = true) &&
                    (textoCompleto.contains("km", ignoreCase = true) || textoCompleto.contains("min", ignoreCase = true))) {
                    Log.d(TAG, "¡Viaje detectado en pantalla!\nContenido: $textoCompleto")
                    // Aquí puedes disparar tu lógica de alertas o notificaciones si pasa tus filtros
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Fallo en reconocimiento OCR", e)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        virtualDisplay?.release()
        mediaProjection?.stop()
        ScreenCaptureHolder.isCapturing = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}