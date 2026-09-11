package com.example.lovale2.services

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class ScreenOcrService : Service() {

    companion object {
        private const val TAG = "ScreenOcrService"
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Servicio OCR de pantalla iniciado.")
        // Aquí se procesaría el Bitmap obtenido mediante MediaProjection
        return START_STICKY
    }

    fun procesarBitmap(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val textoExtraido = visionText.text
                if (textoExtraido.isNotBlank()) {
                    Log.d(TAG, "Texto detectado en pantalla: $textoExtraido")
                    analizarTextoConFiltros(textoExtraido)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al procesar OCR en pantalla", e)
            }
    }

    private fun analizarTextoConFiltros(texto: String) {
        // Aquí conectas con tu TripEvaluator existente para evaluar precio y distancia
        if (texto.contains("$", ignoreCase = true) && (texto.contains("km", ignoreCase = true) || texto.contains("min", ignoreCase = true))) {
            Log.d(TAG, "¡Posible oferta de viaje detectada visualmente!")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}