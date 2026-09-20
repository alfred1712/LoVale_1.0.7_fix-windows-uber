package com.example.lovale2

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lovale2.domain.TripEvaluator
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class BoostScreenshotTest {
    @Test fun mlKitReadsBothRealBoostScreenshots() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            listOf("boost-9340.jpeg" to 9340.0, "boost-7625.jpeg" to 7625.0).forEach { (file, price) ->
                val bitmap = context.openOfferFixture(file).use { BitmapFactory.decodeStream(it) }
                try {
                    val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 30, TimeUnit.SECONDS).text
                    val trip = TripEvaluator(1000.0, 20000.0).extraerDatosDeViaje(text)
                    assertEquals("Precio en $file", price, trip.precio, .001)
                    assertTrue("Lectura completa en $file", trip.completeReading)
                } finally { bitmap.recycle() }
            }
        } finally { recognizer.close() }
    }
}
