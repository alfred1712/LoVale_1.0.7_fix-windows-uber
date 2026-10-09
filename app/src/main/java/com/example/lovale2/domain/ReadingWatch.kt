package com.example.lovale2.domain

/** Idle minutes are not evidence of missed offers. Require repeated candidate failures. */
class ReadingWatch {
    companion object {
        fun candidate(text: String) = Regex("(?i)(?:ARS|\\$)\\s*\\d").containsMatchIn(text) &&
            Regex("(?i)aceptar|me interesa|viaje disponible").containsMatchIn(text)
    }
    private var first = -1L
    private var last = -1L
    private var failures = 0
    fun observe(candidate: Boolean, complete: Boolean, now: Long) {
        if (complete) { reset(); return }
        if (!candidate) return
        if (first < 0 || now - last > 180_000 || now < last) { first = now; failures = 0 }
        last = now; failures++
    }
    fun warning(now: Long) = first >= 0 && failures >= 3 && now - first >= 120_000 && now - last in 0..30_000
    fun reset() { first = -1; last = -1; failures = 0 }
}
