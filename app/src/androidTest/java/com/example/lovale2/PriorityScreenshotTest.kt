package com.example.lovale2

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lovale2.domain.TripEvaluator
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class PriorityScreenshotTest {
    @Test fun realPriorityOfferHasCorrectTotalDistance() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap = context.openOfferFixture("priority-8174.jpeg").use { BitmapFactory.decodeStream(it) }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 30, TimeUnit.SECONDS).text
            val evaluator = TripEvaluator(1000.0, 20000.0)
            val data = evaluator.extraerDatosDeViaje(text)
            Log.d("LoVale", "TEST Priority: $data")
            assertTrue("OCR: $text", data.completeReading)
            assertEquals(8174.0, data.precio, .001)
            assertEquals(1.5, data.pickupDistanceKm, .001)
            assertEquals(8.9, data.distanciaKm, .001)
            val result = evaluator.evaluarViaje(data, emptyList())
            assertEquals(8174.0 / 10.4, result.tarifaPorKm, .001)
            assertEquals(19617.6, result.tarifaPorHora, .001)
        } finally { recognizer.close(); bitmap.recycle() }
    }
}
