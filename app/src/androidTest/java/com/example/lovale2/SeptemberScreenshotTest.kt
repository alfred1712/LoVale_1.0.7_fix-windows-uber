package com.example.lovale2

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class SeptemberScreenshotTest {
    @Test fun captureRealOcrEvidence() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val times = mutableListOf<String>()
        try {
            for (asset in listOf("uber-8123-rate", "uber-8511", "uber-4916", "didi-4000", "didi-4400", "didi-center")) {
                val bitmap = instrumentation.context.openOfferFixture("$asset.png").use { BitmapFactory.decodeStream(it) }
                try {
                    val began = android.os.SystemClock.elapsedRealtime()
                    val reading = com.example.lovale2.services.OfferOcrReader(recognizer).read(bitmap)
                    times += "$asset=${android.os.SystemClock.elapsedRealtime()-began}ms; refined=${reading.refined}"
                    val expected = when(asset) { "uber-8123-rate" -> listOf(8123.0 to 10.0); "uber-8511" -> listOf(8511.0 to 18.1); "uber-4916" -> listOf(4916.0 to 6.6); "didi-4000" -> listOf(4000.0 to 7.5); "didi-4400" -> listOf(4400.0 to 4.825); else -> listOf(9200.0 to 13.2, 3700.0 to 5.5, 10300.0 to 15.1) }
                    org.junit.Assert.assertEquals(asset, expected.size, reading.cards.size)
                    reading.cards.zip(expected).forEach { (card, values) ->
                        val data = com.example.lovale2.domain.TripEvaluator(0.0, 0.0).extraerDatosDeViaje(card.text)
                        org.junit.Assert.assertTrue("$asset: ${card.text}\n$data", data.completeReading)
                        org.junit.Assert.assertEquals(asset, values.first, data.precio, .001)
                        org.junit.Assert.assertEquals(asset, values.second, data.distanciaKm + data.pickupDistanceKm, .001)
                    }
                    val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 30, TimeUnit.SECONDS)
                    File(instrumentation.targetContext.getExternalFilesDir(null), "$asset.txt").writeText(
                        result.textBlocks.flatMap { it.lines }.joinToString("\n") { "${it.boundingBox}: ${it.text}" })
                } finally { bitmap.recycle() }
            }
            File(instrumentation.targetContext.getExternalFilesDir(null), "beta33-ocr-times.txt").writeText(times.joinToString("\n"))
        } finally { recognizer.close() }
    }
}
