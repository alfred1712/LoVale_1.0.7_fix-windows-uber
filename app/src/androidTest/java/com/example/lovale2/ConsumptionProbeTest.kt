package com.example.lovale2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.TrafficStats
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lovale2.diagnostics.DiagnosticRecorder
import com.example.lovale2.domain.TripEvaluator
import com.google.android.gms.tasks.Tasks
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** Reproducible microbenchmark, not an estimate of battery drain while driving.
 * Includes instrumentation overhead; images come from an asset, not Android screenshot API.
 */
class ConsumptionProbeTest {
    @Test fun compareOcrWithAndWithoutRecording() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bitmap = instrumentation.context.openOfferFixture("priority-8174.jpeg").use { BitmapFactory.decodeStream(it) }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val evaluator = TripEvaluator(950.0, 10000.0)
        val results = mutableListOf<Map<String, Any?>>()
        try {
            DiagnosticRecorder.delete()
            Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 10, TimeUnit.SECONDS)
            for (mode in listOf("idle", "ocr", "technical", "visual")) {
                if (mode == "technical" || mode == "visual") DiagnosticRecorder.start(context, "com.ubercab.driver", mode == "visual")
                val start = SystemClock.elapsedRealtime()
                val cpu = Process.getElapsedCpuTime()
                val rx = TrafficStats.getUidRxBytes(Process.myUid())
                val tx = TrafficStats.getUidTxBytes(Process.myUid())
                val times = mutableListOf<Long>()
                repeat(10) { index ->
                    val tick = SystemClock.elapsedRealtime()
                    if (mode != "idle") {
                        val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 10, TimeUnit.SECONDS).text
                        times.add(SystemClock.elapsedRealtime() - tick)
                        val parsed = evaluator.extraerDatosDeViaje(text)
                        assertTrue(parsed.completeReading)
                        assertEquals(8174.0, parsed.precio, 0.01)
                        assertEquals(10.4, parsed.pickupDistanceKm + parsed.distanciaKm, 0.01)
                        DiagnosticRecorder.event("probe_ocr", "ms" to times.last(), "fare" to parsed.precio)
                        DiagnosticRecorder.text("OCR", text)
                        if (mode == "visual" && index % 5 == 0) {
                            DiagnosticRecorder.image(bitmap.copy(Bitmap.Config.ARGB_8888, false), tick, "com.ubercab.driver")
                        }
                    }
                    Thread.sleep((1000 - (SystemClock.elapsedRealtime() - tick)).coerceAtLeast(0L))
                }
                val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
                results.add(mapOf("mode" to mode, "elapsedMs" to SystemClock.elapsedRealtime() - start,
                    "cpuMs" to Process.getElapsedCpuTime() - cpu, "pssKiB" to memory.totalPss,
                    "uidRxBytes" to DiagnosticRecorder.delta(rx, TrafficStats.getUidRxBytes(Process.myUid())),
                    "uidTxBytes" to DiagnosticRecorder.delta(tx, TrafficStats.getUidTxBytes(Process.myUid())),
                    "ocrMs" to times, "imageWidth" to bitmap.width, "imageHeight" to bitmap.height))
                DiagnosticRecorder.stop("microbenchmark")
                DiagnosticRecorder.delete()
            }
            File(context.getExternalFilesDir(null), "beta31-consumption.json").writeText(Gson().toJson(results))
        } finally {
            recognizer.close(); bitmap.recycle(); DiagnosticRecorder.delete()
        }
    }
}
