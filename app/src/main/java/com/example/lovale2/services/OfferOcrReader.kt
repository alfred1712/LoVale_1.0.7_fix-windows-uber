package com.example.lovale2.services

import android.graphics.Bitmap
import com.example.lovale2.domain.*
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer

/** Runs on a worker, one request at a time. Preserves native pixels until a retry needs enlargement. */
class OfferOcrReader(private val recognizer: TextRecognizer) {
    data class Reading(val text: String, val cards: List<OfferCard>, val refined: Int, val dismissTargets: List<OfferLine>)
    private fun lines(bitmap: Bitmap): List<OfferLine> =
        Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).textBlocks.flatMap { it.lines }
            .mapNotNull { line -> line.boundingBox?.let { OfferLine(line.text, it.left, it.top, it.right, it.bottom) } }
            .sortedWith(compareBy<OfferLine> { it.top }.thenBy { it.left })
    fun read(bitmap: Bitmap): Reading {
        val first = lines(bitmap)
        var refined = 0
        val evaluator = TripEvaluator(0.0, 0.0)
        val cards = OfferLayout.cards(first).take(8).map { card ->
            if (evaluator.extraerDatosDeViaje(card.text).completeReading) card else {
                val top = (card.top - 24).coerceAtLeast(0)
                val bottom = (card.bottom + 32).coerceAtMost(bitmap.height)
                val crop = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, bottom - top)
                val scale = minOf(2.0, kotlin.math.sqrt(8_000_000.0 / (crop.width.toDouble() * crop.height))).coerceAtLeast(1.0)
                val enlarged = Bitmap.createScaledBitmap(crop, (crop.width * scale).toInt(), (crop.height * scale).toInt(), true)
                try {
                    refined++
                    val retry = lines(enlarged).map { it.copy(left = (it.left / scale).toInt(), right = (it.right / scale).toInt(),
                        top = top + (it.top / scale).toInt(), bottom = top + (it.bottom / scale).toInt()) }
                    val next = OfferCard(retry, card.action)
                    if (evaluator.extraerDatosDeViaje(next.text).completeReading) next else card
                } finally {
                    if (enlarged !== crop) enlarged.recycle()
                    if (crop !== bitmap) crop.recycle()
                }
            }
        }
        return Reading(first.joinToString("\n") { it.text }, cards, refined, first.filter { it.text.trim().lowercase() in setOf("x", "×", "✕", "x rechazo permitido", "rechazo permitido") })
    }
}
