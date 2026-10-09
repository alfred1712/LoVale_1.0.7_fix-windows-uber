package com.example.lovale2

import com.example.lovale2.domain.ReadingMetrics
import org.junit.Assert.*
import org.junit.Test

class ReadingMetricsTest {
    @Test fun failedWriteRestoresCountsAndKeepsMaximumLatency() {
        val metrics = ReadingMetrics()
        metrics.event("Uber", "ocr_result", mapOf("ms" to 400))
        val pending = metrics.drain()
        metrics.event("Uber", "ocr_result", mapOf("ms" to 100))
        metrics.restore(pending)
        val result = metrics.drain()
        assertEquals(2L, result["Uber.ocr_result.none.count"])
        assertEquals(500L, result["Uber.ocr_result.none.total_ms"])
        assertEquals(400L, result["Uber.ocr_result.none.max_ms"])
    }
    @Test fun distinguishesDisplayAndPersistenceFromRequestsAndExcludesTests() {
        val metrics = ReadingMetrics()
        metrics.event("DiDi", "overlay_request", emptyMap())
        metrics.event("DiDi", "overlay_visible", mapOf("test" to true))
        metrics.event("DiDi", "overlay_error", emptyMap())
        metrics.event("DiDi", "history_offer", emptyMap())
        metrics.event("DiDi", "history_error", emptyMap())
        val result = metrics.drain()
        assertFalse(result.keys.any { "overlay_visible" in it || "history_saved" in it })
        assertEquals(1L, result["DiDi.overlay_error.none.count"])
        assertEquals(1L, result["DiDi.history_error.none.count"])
    }
    @Test fun discardsSensitiveAndUnboundedFields() {
        val metrics = ReadingMetrics()
        metrics.event("Uber", "parse", mapOf("source" to "OCR", "complete" to false, "text" to "Private address", "fare" to 1234))
        metrics.event("Private address", "read_text", mapOf("text" to "secret"))
        val result = metrics.drain()
        assertEquals(mapOf("Uber.parse.OCR.count" to 1L, "Uber.parse.OCR.incomplete" to 1L,
            "Uber.parse.OCR.missing_pickup" to 1L, "Uber.parse.OCR.missing_trip" to 1L), result)
        assertTrue(metrics.drain().isEmpty())
    }
    @Test fun aggregatesLatencyAndDoesNotCountRetriesAsOffers() {
        val metrics = ReadingMetrics()
        repeat(3) { metrics.event("DiDi", "parse", mapOf("complete" to false)) }
        metrics.event("DiDi", "history_offer", emptyMap())
        metrics.event("DiDi", "ocr_result", mapOf("ms" to 100, "cards" to 2))
        metrics.event("DiDi", "ocr_result", mapOf("ms" to 400, "cards" to 0))
        val result = metrics.drain()
        assertEquals(3L, result["DiDi.parse.none.incomplete"])
        assertEquals(1L, result["DiDi.history_offer.none.count"])
        assertEquals(500L, result["DiDi.ocr_result.none.total_ms"])
        assertEquals(400L, result["DiDi.ocr_result.none.max_ms"])
    }
}
