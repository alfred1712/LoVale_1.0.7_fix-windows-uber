package com.example.lovale2.domain

class OfferDismissal {
    private var idleReads = 0
    fun reset() { idleReads = 0 }
    fun observe(text: String, candidate: Boolean): Boolean {
        if (candidate) { reset(); return false }
        val idle = Regex("(?i)est[aá]s conectado|est[aá]s desconectado|buscando (?:viajes|solicitudes)|\\bbuscando\\b|llegaste a la recogida|iniciar viaje")
            .containsMatchIn(text.trim())
        idleReads = if (idle) idleReads + 1 else 0
        return idleReads >= 2
    }
    companion object {
        fun isCloseAction(label: String) = label.trim().lowercase() in
            setOf("x", "×", "✕", "rechazo permitido", "cerrar", "cerrar oferta", "cerrar solicitud", "rechazar", "rechazar viaje", "descartar", "close", "decline")
    }
}
