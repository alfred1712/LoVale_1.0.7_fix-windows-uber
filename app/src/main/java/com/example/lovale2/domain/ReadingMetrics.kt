package com.example.lovale2.domain

/** Fixed-cardinality, aggregate diagnostics. Never copies OCR text, addresses, IDs or amounts. */
class ReadingMetrics {
    private val counts = linkedMapOf<String, Long>()
    private val stages = setOf("a11y_tree", "read", "detector", "screenshot_request", "screenshot_error",
        "screenshot_ready", "ocr_result", "ocr_error", "stale_read", "parse", "evaluation", "deduplicated", "history_offer", "overlay_request",
        "history_saved", "history_error", "overlay_visible", "overlay_error", "overlay_close",
        "reader_connected", "reader_disconnected", "monitor_active", "monitor_paused")
    @Synchronized fun event(platform: String, stage: String, data: Map<String, Any?>) {
        if (stage !in stages) return
        if (data["test"] == true) return
        val app = platform.takeIf { it in setOf("Uber", "DiDi", "Cabify") } ?: "none"
        val source = data["source"].takeIf { it == "OCR" || it == "A11Y" } ?: "none"
        val key = "$app.$stage.$source"
        fun count(suffix: String, amount: Long = 1) { counts[key + suffix] = (counts[key + suffix] ?: 0) + amount }
        count(".count")
        if (stage == "parse") count(if (data["complete"] == true) ".complete" else ".incomplete")
        if (stage == "parse" && data["complete"] != true) {
            fun positive(field: String) = (data[field] as? Number)?.toDouble()?.let { it.isFinite() && it > 0 } == true
            if (!positive("fare")) count(".missing_fare")
            if (!positive("pickupKm") || !positive("pickupMin")) count(".missing_pickup")
            if (!positive("tripKm") || !positive("tripMin")) count(".missing_trip")
            if (listOf("fare", "pickupKm", "pickupMin", "tripKm", "tripMin").all(::positive)) count(".rejected_structure_or_units")
        }
        if (stage == "detector") count(if (data["candidate"] == true) ".candidate" else ".not_candidate")
        if (stage == "screenshot_request") count(if (data["windowMode"] == true) ".window" else ".display")
        if (stage == "ocr_result") {
            val cards = (data["cards"] as? Number)?.toInt() ?: 0
            count(if (cards > 0) ".with_cards" else ".no_cards")
        }
        if (stage == "screenshot_error") {
            val code = (data["code"] as? Number)?.toInt()
            count(".code_" + (code?.takeIf { it in 1..6 }?.toString() ?: "other"))
        }
        if (stage in setOf("a11y_tree", "ocr_result")) {
            val ms = (data["ms"] as? Number)?.toLong()?.takeIf { it in 0..60000 }
            if (ms != null) {
                count(".timed_count"); count(".total_ms", ms)
                counts[key + ".max_ms"] = maxOf(counts[key + ".max_ms"] ?: 0, ms)
            }
        }
    }
    @Synchronized fun drain(): Map<String, Long> = counts.toMap().also { counts.clear() }
    /** Restore an unwritten batch without losing events received during disk I/O. */
    @Synchronized fun restore(batch: Map<String, Long>) {
        batch.forEach { (key, value) ->
            counts[key] = if (key.endsWith(".max_ms")) maxOf(counts[key] ?: 0, value)
                else (counts[key] ?: 0) + value
        }
    }
}
