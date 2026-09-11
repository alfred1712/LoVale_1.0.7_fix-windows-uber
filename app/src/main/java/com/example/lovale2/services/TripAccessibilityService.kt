package com.example.lovale2.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.lovale2.data.settings.AppSettings
import com.example.lovale2.data.settings.RideApp
import com.example.lovale2.data.settings.SettingsRepository
import com.example.lovale2.domain.DatosViaje
import com.example.lovale2.domain.MotivoEvaluacion
import com.example.lovale2.domain.ResultadoEvaluacion
import com.example.lovale2.domain.TripEvaluator
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.Executor

/**
 * LoVale 1.0.8
 *
 * Detección híbrida:
 * - Accessibility tree como vía principal.
 * - Uber usa screenshot+OCR solo como fallback cuando el árbol no contiene una oferta válida.
 * - Android 14+: se captura exclusivamente la ventana de Uber.
 * - Android 11-13: Android solo permite screenshot de display; inmediatamente se recorta
 *   al bounds de la ventana de Uber antes de entregar la imagen a ML Kit.
 */
class TripAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "LoVale"
        private const val ALERT_CHANNEL_ID = "viajes_channel_id"
        private const val ALERT_NOTIFICATION_ID = 1001
        private const val OCR_COOLDOWN_MS = 1000L
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val mainExecutor = Executor { command -> Handler(Looper.getMainLooper()).post(command) }
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @Volatile
    private var settingsCache = AppSettings()

    @Volatile
    private var ocrInProgress = false

    private var lastOcrAt = 0L
    private var estadoPrimerPlano = ""
    private var ultimoTextoLogueado = ""
    private var ultimoOcrLogueado = ""
    private var lastUberWindowId = -1
    private var lastUberWindowBounds: Rect? = null
    private val ofertasProcesadas = LinkedHashMap<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Servicio de accesibilidad CONECTADO - LoVale 1.0.8")

        actualizarFiltroDePaquete(settingsCache.selectedApp)

        val repository = SettingsRepository(applicationContext)
        serviceScope.launch {
            repository.settingsFlow.collect { settings ->
                val appCambio = settings.selectedApp != settingsCache.selectedApp
                settingsCache = settings

                if (appCambio) {
                    ofertasProcesadas.clear()
                    estadoPrimerPlano = ""
                    ultimoTextoLogueado = ""
                    ultimoOcrLogueado = ""
                    lastUberWindowId = -1
                    lastUberWindowBounds = null
                }

                actualizarFiltroDePaquete(settings.selectedApp)
                Log.d(
                    TAG,
                    "Configuración: activo=${settings.serviceActive}, app=${settings.selectedApp?.label ?: "NINGUNA"}, " +
                        "minKm=${settings.minRateByKm}, minHora=${settings.minRateByHour}, pickupMax=${settings.maxPickupDistance}"
                )
            }
        }
    }

    private fun actualizarFiltroDePaquete(app: RideApp?) {
        val info = serviceInfo ?: return
        info.packageNames = if (app == null) emptyArray() else arrayOf(app.packageName)
        setServiceInfo(info)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            manejarEvento(event)
        } catch (t: Throwable) {
            // Nunca dejamos que un evento malformado o una ventana problemática mate el servicio.
            Log.e(TAG, "EVENT error no fatal: type=${event.eventType} pkg=${event.packageName}", t)
            ocrInProgress = false
        }
    }

    private fun manejarEvento(event: AccessibilityEvent) {
        val settings = settingsCache
        val selectedApp = settings.selectedApp ?: return
        if (!settings.serviceActive) return

        val targetPackage = selectedApp.packageName
        val eventPackage = event.packageName?.toString()?.lowercase(Locale.ROOT).orEmpty()
        if (eventPackage != targetPackage) return

        if (selectedApp == RideApp.UBER && event.windowId >= 0) {
            lastUberWindowId = event.windowId
        }

        val textos = mutableListOf<String>()
        val ventanasDelPaquete = try {
            windows?.filter { window ->
                val pkg = window.root?.packageName?.toString()?.lowercase(Locale.ROOT).orEmpty()
                pkg == targetPackage
            } ?: emptyList()
        } catch (t: Throwable) {
            Log.w(TAG, "No se pudieron enumerar ventanas de $targetPackage", t)
            emptyList()
        }

        if (ventanasDelPaquete.isNotEmpty()) {
            registrarCambioPrimerPlano(targetPackage)

            for (ventana in ventanasDelPaquete) {
                if (selectedApp == RideApp.UBER) {
                    if (ventana.id >= 0) lastUberWindowId = ventana.id
                    val bounds = Rect()
                    try {
                        ventana.getBoundsInScreen(bounds)
                        if (!bounds.isEmpty) lastUberWindowBounds = Rect(bounds)
                    } catch (_: Throwable) {
                        // El OCR sigue funcionando con fallback de display si no hay bounds.
                    }
                }

                val root = ventana.root ?: continue
                try {
                    recolectarTextos(root, textos)
                } finally {
                    @Suppress("DEPRECATION")
                    try { root.recycle() } catch (_: Throwable) {}
                }
            }
        } else {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val rootPackage = rootNode.packageName?.toString()?.lowercase(Locale.ROOT).orEmpty()
                if (rootPackage.isNotBlank() && rootPackage != targetPackage) {
                    registrarCambioPrimerPlano("")
                    @Suppress("DEPRECATION")
                    try { rootNode.recycle() } catch (_: Throwable) {}
                    return
                }

                registrarCambioPrimerPlano(targetPackage)
                try {
                    recolectarTextos(rootNode, textos)
                } finally {
                    @Suppress("DEPRECATION")
                    try { rootNode.recycle() } catch (_: Throwable) {}
                }
            }
        }

        val ofertaEncontrada = procesarTextoDePantalla(
            textos = textos,
            selectedApp = selectedApp,
            targetPackage = targetPackage,
            source = "A11Y"
        )

        if (selectedApp == RideApp.UBER && !ofertaEncontrada) {
            solicitarOcrUber(targetPackage)
        }
    }

    private fun procesarTextoDePantalla(
        textos: List<String>,
        selectedApp: RideApp,
        targetPackage: String,
        source: String
    ): Boolean {
        if (textos.isEmpty()) {
            if (selectedApp == RideApp.UBER && source == "A11Y") {
                Log.d(TAG, "UBER/A11Y sin texto útil; fallback OCR")
            }
            return false
        }

        val textoCompleto = textos.joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (textoCompleto.isBlank()) return false

        if (source == "OCR") {
            if (textoCompleto != ultimoOcrLogueado) {
                Log.d(TAG, "UBER/OCR (${textoCompleto.length} chars): ${textoCompleto.take(900)}")
                ultimoOcrLogueado = textoCompleto
            }
        } else if (textoCompleto != ultimoTextoLogueado) {
            Log.d(TAG, "$source [${selectedApp.label}] (${textoCompleto.length} chars): ${textoCompleto.take(700)}")
            ultimoTextoLogueado = textoCompleto
        }

        if (!esPantallaDeOfertas(selectedApp, textoCompleto)) return false

        val ofertas = separarOfertas(textoCompleto, selectedApp)
        if (ofertas.isEmpty()) return false

        Log.d(TAG, "${selectedApp.label}/$source: ${ofertas.size} oferta(s) candidata(s)")
        var algunaValida = false
        for (oferta in ofertas) {
            if (procesarOferta(targetPackage, selectedApp, oferta, source)) algunaValida = true
        }
        return algunaValida
    }

    private fun solicitarOcrUber(targetPackage: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.d(TAG, "UBER/OCR no disponible: Android < 11")
            return
        }

        val now = System.currentTimeMillis()
        if (ocrInProgress || now - lastOcrAt < OCR_COOLDOWN_MS) return

        lastOcrAt = now
        ocrInProgress = true

        val callback = object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                manejarScreenshotUber(screenshot, targetPackage)
            }

            override fun onFailure(errorCode: Int) {
                ocrInProgress = false
                Log.w(TAG, "UBER/OCR screenshot falló, code=$errorCode windowId=$lastUberWindowId")
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && lastUberWindowId >= 0) {
                Log.d(TAG, "UBER/OCR capturando solo ventana Uber id=$lastUberWindowId")
                takeScreenshotOfWindow(lastUberWindowId, mainExecutor, callback)
            } else {
                Log.d(TAG, "UBER/OCR capturando display; OCR se recortará a ventana Uber")
                @Suppress("DEPRECATION")
                takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)
            }
        } catch (t: Throwable) {
            // Esta llamada puede lanzar SecurityException/IllegalStateException si el servicio
            // no se reactivó tras cambiar canTakeScreenshot o el OEM bloquea la captura.
            ocrInProgress = false
            Log.e(TAG, "UBER/OCR no se pudo iniciar screenshot; reactivar Accesibilidad puede ser necesario", t)
        }
    }

    private fun manejarScreenshotUber(screenshot: ScreenshotResult, targetPackage: String) {
        val hardwareBuffer = screenshot.hardwareBuffer
        val bitmap = try {
            Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                ?.copy(Bitmap.Config.ARGB_8888, false)
        } catch (t: Throwable) {
            Log.e(TAG, "UBER/OCR error convirtiendo screenshot", t)
            null
        } finally {
            try { hardwareBuffer.close() } catch (_: Throwable) {}
        }

        if (bitmap == null) {
            ocrInProgress = false
            return
        }

        val region = try {
            prepararRegionUber(bitmap)
        } catch (t: Throwable) {
            Log.w(TAG, "UBER/OCR no se pudo recortar; usando bitmap recibido", t)
            bitmap
        }

        if (region !== bitmap) bitmap.recycle()
        procesarScreenshotUber(region, targetPackage)
    }

    /**
     * En Android 11-13 takeScreenshot captura el display. Esta función limita el OCR
     * a la ventana de Uber conocida por Accessibility y además descarta una franja mínima
     * superior/inferior para evitar barra de estado/navegación.
     */
    private fun prepararRegionUber(bitmap: Bitmap): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && lastUberWindowId >= 0) {
            // takeScreenshotOfWindow ya devolvió únicamente la ventana de Uber.
            return bitmap
        }

        val bounds = lastUberWindowBounds
        if (bounds == null || bounds.isEmpty) {
            // Fallback conservador: se evita status bar y navegación, sin depender de una
            // posición concreta de la tarjeta para soportar variantes de Uber.
            val top = (bitmap.height * 0.06f).toInt().coerceAtLeast(0)
            val bottom = (bitmap.height * 0.96f).toInt().coerceAtMost(bitmap.height)
            return Bitmap.createBitmap(bitmap, 0, top, bitmap.width, (bottom - top).coerceAtLeast(1))
        }

        val left = bounds.left.coerceIn(0, bitmap.width - 1)
        val top = bounds.top.coerceIn(0, bitmap.height - 1)
        val right = bounds.right.coerceIn(left + 1, bitmap.width)
        val bottom = bounds.bottom.coerceIn(top + 1, bitmap.height)

        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun procesarScreenshotUber(bitmap: Bitmap, targetPackage: String) {
        val image = InputImage.fromBitmap(bitmap, 0)
        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val normalized = normalizarTextoOcr(visionText.text)
                if (normalized.isBlank()) {
                    Log.d(TAG, "UBER/OCR ventana sin texto reconocido")
                } else {
                    val parsed = procesarTextoDePantalla(
                        textos = listOf(normalized),
                        selectedApp = RideApp.UBER,
                        targetPackage = targetPackage,
                        source = "OCR"
                    )
                    if (!parsed) Log.d(TAG, "UBER/OCR texto detectado pero no formó una oferta válida")
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "UBER/OCR ML Kit falló", error)
            }
            .addOnCompleteListener {
                try { bitmap.recycle() } catch (_: Throwable) {}
                ocrInProgress = false
            }
    }

    private fun normalizarTextoOcr(raw: String): String {
        var value = raw
            .replace('\u00A0', ' ')
            .replace(Regex("(?i)A\\s*R\\s*S"), "ARS")
            .replace(Regex("(?i)k\\s*m"), "km")
            .replace(Regex("(?i)m\\s*[i1l]\\s*n"), "min")
            .replace(Regex("[\\r\\n]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // OCR suele separar miles: "ARS 6 084" / "$ 6 084".
        value = value.replace(
            Regex("(?i)(ARS|\\$)\\s*([0-9]{1,3})\\s+([0-9]{3})(?![0-9])")
        ) { match -> "${match.groupValues[1]}${match.groupValues[2]}${match.groupValues[3]}" }

        return value
    }

    private fun registrarCambioPrimerPlano(packageName: String) {
        if (packageName == estadoPrimerPlano) return
        estadoPrimerPlano = packageName
        if (packageName.isBlank()) {
            Log.d(TAG, "Plataforma seleccionada fuera de primer plano; monitoreo en pausa")
        } else {
            Log.d(TAG, "${settingsCache.selectedApp?.label ?: "Plataforma"} pasó a primer plano")
        }
    }

    private fun esPantallaDeOfertas(app: RideApp, texto: String): Boolean {
        if (!contieneOferta(texto, app)) return false
        return when (app) {
            RideApp.DIDI -> texto.contains("Centro", ignoreCase = true) || texto.contains("Aceptar", ignoreCase = true)
            RideApp.UBER -> true
            RideApp.CABIFY -> texto.contains("app", ignoreCase = true) ||
                texto.contains("/km", ignoreCase = true) || contarParesMinKm(texto) >= 2
        }
    }

    private fun procesarOferta(
        packageName: String,
        app: RideApp,
        ofertaTexto: String,
        source: String
    ): Boolean {
        val minKm = settingsCache.minRateByKm.toDoubleOrNull() ?: 950.0
        val minHora = settingsCache.minRateByHour.toDoubleOrNull() ?: 0.0
        val maxPickup = settingsCache.maxPickupDistance.toDoubleOrNull() ?: 0.0

        val tripEvaluator = TripEvaluator(
            tarifaMinimaPorKm = minKm,
            tarifaMinimaPorHora = minHora
        )

        val rutas = extraerRutas(ofertaTexto)
        val pickup = rutas.firstOrNull()?.first.orEmpty()
        val destino = rutas.lastOrNull()?.second.orEmpty()
        val datos = tripEvaluator.extraerDatosDeViaje(ofertaTexto, destino, pickup)

        Log.d(
            TAG,
            "${app.label}/$source PARSE: precio=${datos.precio} pickupKm=${datos.pickupDistanceKm} " +
                "viajeKm=${datos.distanciaKm} viajeMin=${datos.duracionMin} pickup='$pickup' destino='$destino'"
        )

        if (datos.precio <= 0.0 || datos.distanciaKm <= 0.0 || datos.duracionMin <= 0.0) {
            Log.d(TAG, "${app.label}/$source oferta candidata incompleta")
            return false
        }

        val evaluacion = tripEvaluator.evaluarViaje(datos, settingsCache.excludedZones)
        val nivel = clasificarRentabilidad(
            evaluacion = evaluacion,
            datos = datos,
            minKm = minKm,
            minHora = minHora,
            maxPickup = maxPickup
        )

        Log.d(
            TAG,
            "${app.label}/$source EVAL: $%.0f | %.2f km | %.0f min | $%.0f/km | $%.0f/h | nivel=$nivel | motivo=${evaluacion.motivo}".format(
                datos.precio,
                datos.distanciaKm,
                datos.duracionMin,
                evaluacion.tarifaPorKm,
                evaluacion.tarifaPorHora
            )
        )

        val fingerprint = buildFingerprint(packageName, datos, evaluacion)
        val ahora = System.currentTimeMillis()
        val anterior = ofertasProcesadas[fingerprint]
        if (anterior != null && ahora - anterior < 4000L) {
            Log.d(TAG, "Oferta duplicada ignorada (<4s)")
            return true
        }

        ofertasProcesadas[fingerprint] = ahora
        while (ofertasProcesadas.size > 100) {
            ofertasProcesadas.remove(ofertasProcesadas.entries.first().key)
        }

        mostrarResultado(evaluacion, datos, nivel, maxPickup)
        return true
    }

    private fun clasificarRentabilidad(
        evaluacion: ResultadoEvaluacion,
        datos: DatosViaje,
        minKm: Double,
        minHora: Double,
        maxPickup: Double
    ): String {
        if (evaluacion.motivo == MotivoEvaluacion.ZONA_EXCLUIDA) return "ZONE"

        if (maxPickup > 0.0 && datos.pickupDistanceKm > 0.0 && datos.pickupDistanceKm > maxPickup) {
            return "RED"
        }

        val ratioKm = if (minKm > 0.0) evaluacion.tarifaPorKm / minKm else 1.0
        val ratioHora = if (minHora > 0.0) evaluacion.tarifaPorHora / minHora else 1.0
        val ratio = minOf(ratioKm, ratioHora)

        return when {
            ratio >= 1.0 -> "GREEN"
            ratio >= 0.85 -> "YELLOW"
            else -> "RED"
        }
    }

    private fun separarOfertas(texto: String, app: RideApp): List<String> {
        if (app == RideApp.DIDI) {
            val partes = texto.split(Regex("(?i)\\bAceptar\\b"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val candidatas = partes.filter { contieneOferta(it, app) }
            if (candidatas.isNotEmpty()) return candidatas
        }
        return if (contieneOferta(texto, app)) listOf(texto) else emptyList()
    }

    private fun contieneOferta(texto: String, app: RideApp? = null): Boolean {
        val tienePrecio = Regex("""(?:\$|ARS)\s*[0-9]""", RegexOption.IGNORE_CASE)
            .containsMatchIn(texto)
        return if (app == RideApp.UBER) {
            tienePrecio && texto.contains("min", ignoreCase = true) &&
                (texto.contains("km", ignoreCase = true) || Regex("""\b\d+\s*m\b""").containsMatchIn(texto))
        } else {
            tienePrecio && contarParesMinKm(texto) >= 2
        }
    }

    private fun contarParesMinKm(texto: String): Int {
        val pairRegex = Regex(
            """[0-9]+(?:[.,][0-9]+)?\s*min[^0-9k]{0,50}[0-9]+(?:[.,][0-9]+)?\s*(?:km|m)|[0-9]+(?:[.,][0-9]+)?\s*(?:km|m)[^0-9m]{0,50}[0-9]+(?:[.,][0-9]+)?\s*min""",
            RegexOption.IGNORE_CASE
        )
        return pairRegex.findAll(texto).count()
    }

    private fun extraerRutas(texto: String): List<Pair<String, String>> {
        val pairRegex = Regex(
            """([0-9]+(?:[.,][0-9]+)?)\s*min(?:[^0-9k]{0,18})([0-9]+(?:[.,][0-9]+)?)\s*(km|m)|([0-9]+(?:[.,][0-9]+)?)\s*(km|m)(?:[^0-9m]{0,18})([0-9]+(?:[.,][0-9]+)?)\s*min""",
            RegexOption.IGNORE_CASE
        )
        val matches = pairRegex.findAll(texto).toList()
        if (matches.isEmpty()) return emptyList()

        val resultado = mutableListOf<String>()
        for (i in matches.indices) {
            val start = matches[i].range.last + 1
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else texto.length
            val candidato = limpiarDireccion(texto.substring(start, end))
            if (looksLikeAddress(candidato)) resultado.add(candidato)
        }

        if (resultado.size >= 2) return listOf(resultado.first() to resultado.last())
        if (resultado.size == 1 && resultado.first().isNotBlank()) {
            return listOf(resultado.first() to resultado.first())
        }

        val direcciones = texto
            .split(Regex("(?i)\\b(?:A|Viaje|Express|Express Nuevo|Aceptar)\\b|\$|ARS"))
            .map { limpiarDireccion(it) }
            .filter(::looksLikeAddress)

        return if (direcciones.size >= 2) {
            listOf(direcciones.first() to direcciones.last())
        } else if (direcciones.size == 1) {
            listOf(direcciones.first() to direcciones.first())
        } else {
            emptyList()
        }
    }

    private fun limpiarDireccion(value: String): String = value
        .replace(Regex("^[\\s:·•\\-–—()]+"), "")
        .replace(Regex("(?i)^(a|viaje|destino|recogida|pickup)\\s*:?\\s*"), "")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '.', ':', '·', '•', '-', '–', '—')

    private fun looksLikeAddress(value: String): Boolean {
        val text = value.trim()
        if (text.length < 4 || text.length > 180) return false
        if (text.contains("$") || text.contains("ARS", ignoreCase = true)) return false
        if (Regex("(?i)^(Aceptar|Express|Express Nuevo|Cabify|Uber|DiDi)").containsMatchIn(text)) return false

        return text.any { it.isLetter() } && (
            text.any { it.isDigit() } ||
                Regex(
                    "palermo|belgrano|almagro|balvanera|boedo|caballito|once|retiro|constitucion|villa|san telmo|flores|recoleta|nuñez|paternal|chacarita|lanus|valentin alsina",
                    RegexOption.IGNORE_CASE
                ).containsMatchIn(text)
            )
    }

    private fun recolectarTextos(node: AccessibilityNodeInfo, destino: MutableList<String>) {
        node.text?.toString()?.let { if (it.isNotBlank()) destino.add(it) }
        node.contentDescription?.toString()?.let { if (it.isNotBlank()) destino.add(it) }

        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Throwable) { null }
            child?.let { c ->
                try {
                    recolectarTextos(c, destino)
                } finally {
                    @Suppress("DEPRECATION")
                    try { c.recycle() } catch (_: Throwable) {}
                }
            }
        }
    }

    private fun buildFingerprint(
        packageName: String,
        datos: DatosViaje,
        evaluacion: ResultadoEvaluacion
    ): String = "$packageName|${datos.precio}|${datos.distanciaKm}|${datos.duracionMin}|${datos.pickup}|${datos.destino}|${evaluacion.motivo}"

    private fun mostrarResultado(
        evaluacion: ResultadoEvaluacion,
        datos: DatosViaje,
        nivel: String,
        maxPickup: Double
    ) {
        val zoneLabel = evaluacion.zonaDetectada.orEmpty()
        val pickupFuera = maxPickup > 0.0 && datos.pickupDistanceKm > 0.0 && datos.pickupDistanceKm > maxPickup

        val (titulo, mensaje) = when (nivel) {
            "GREEN" -> "Viaje rentable" to "Cumple tus parámetros"
            "YELLOW" -> "Viaje casi rentable" to "Está entre 85% y 100% de tus mínimos"
            "ZONE" -> "Zona no deseada" to if (zoneLabel.isNotBlank()) zoneLabel else "Zona excluida"
            else -> "Viaje no rentable" to if (pickupFuera) {
                "Pickup ${"%.1f".format(datos.pickupDistanceKm)} km > máximo ${"%.1f".format(maxPickup)} km"
            } else {
                "No cumple tus parámetros"
            }
        }

        crearCanalYMostrarNotificacion(titulo, mensaje)

        val overlayIntent = Intent(this, TripOverlayService::class.java).apply {
            putExtra("EXTRA_PROFITABILITY_LEVEL", nivel)
            putExtra("EXTRA_ZONE_LABEL", zoneLabel)
        }

        try {
            if (!Settings.canDrawOverlays(this)) {
                Log.w(TAG, "Oferta detectada pero SYSTEM_ALERT_WINDOW no está concedido; se mantiene la notificación")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, overlayIntent)
            } else {
                startService(overlayIntent)
            }
            Log.d(TAG, "OVERLAY solicitado: nivel=$nivel zona='$zoneLabel'")
        } catch (t: Throwable) {
            Log.e(TAG, "No se pudo iniciar TripOverlayService", t)
        }
    }

    private fun crearCanalYMostrarNotificacion(titulo: String, descripcion: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Alertas de Viajes LoVale",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        notificationManager.notify(
            ALERT_NOTIFICATION_ID,
            NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(titulo)
                .setContentText(descripcion)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }

    override fun onInterrupt() {
        Log.d(TAG, "Servicio de accesibilidad interrumpido temporalmente")
    }

    override fun onDestroy() {
        serviceScope.cancel()
        try { textRecognizer.close() } catch (_: Throwable) {}
        super.onDestroy()
    }
}
