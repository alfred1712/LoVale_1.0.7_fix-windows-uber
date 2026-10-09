package com.example.lovale2.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import com.example.lovale2.diagnostics.DiagnosticRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import android.os.SystemClock
import com.example.lovale2.BuildConfig
import com.example.lovale2.domain.OfferText
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
import com.example.lovale2.domain.evaluator
import com.example.lovale2.domain.classifyOffer
import com.example.lovale2.data.settings.OfferHistory
import com.example.lovale2.data.settings.OfferRecord
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

    private val neighborhoods = com.example.lovale2.data.network.UsigNeighborhoods()
    private val zoneJobs = mutableSetOf<String>()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)
    private val mainExecutor = Executor { command -> Handler(Looper.getMainLooper()).post(command) }
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @Volatile
    private var settingsCache = AppSettings()

    @Volatile
    private var ocrInProgress = false

    private val handler = Handler(Looper.getMainLooper())
    private val summary by lazy { com.example.lovale2.data.settings.SessionSummaryStore.get(this) }
    private val offerDismissal = com.example.lovale2.domain.OfferDismissal()
    private var watchingOverlay = false
    private val checkOverlay = object : Runnable {
        override fun run() {
            watchingOverlay = false
            if (!settingsCache.serviceActive || !TripOverlayService.isOfferVisible()) return
            if (usesOcrFallback(settingsCache.selectedApp)) {
                settingsCache.selectedApp?.let { solicitarOcrPlataforma(it.packageName) }
                watchOverlay()
            }
        }
    }
    private fun watchOverlay() {
        if (!watchingOverlay && usesOcrFallback(settingsCache.selectedApp)) {
            watchingOverlay = true
            handler.postDelayed(checkOverlay, 1000L)
        }
    }
    private fun closeOffer(reason: String) {
        suppressUntil = SystemClock.elapsedRealtime() + 800L
        generation++ // Invalidate screenshots taken before dismissal.
        offerDismissal.reset()
        TripOverlayService.dismissOffer(reason)
    }
    private var dismissTargets = emptyList<Rect>()
    private var dismissTargetsAt = 0L
    private var suppressUntil = 0L
    private var centralCards: ArrayList<Intent>? = null
    private var cardAnchor: com.example.lovale2.domain.OfferCard? = null
    private val recordIds = linkedMapOf<String, String>()
    private var generation = 0
    private var destroyed = false
    private var referenceInProgress = false
    private var readId = 0L
    private var retryPending = false
    private var lastOcrAt = -OCR_COOLDOWN_MS
    private var lastTreeFinishedAt = -500L
    private var pendingEvent: AccessibilityEvent? = null
    private val readPendingEvent = Runnable {
        val event = pendingEvent
        pendingEvent = null
        if (event != null) {
            try { manejarEvento(event) }
            catch (e: Exception) { Log.e(TAG, "${settingsCache.selectedApp?.label}/A11Y lectura falló", e) }
            finally {
                lastTreeFinishedAt = SystemClock.elapsedRealtime()
                @Suppress("DEPRECATION")
                event.recycle()
            }
        }
    }
    private fun usesOcrFallback(app: RideApp?) = app == RideApp.UBER || app == RideApp.DIDI
    private fun ocrPlatformActive() = !destroyed && settingsCache.serviceActive && usesOcrFallback(settingsCache.selectedApp)
    private fun scheduleRetry() {
        if (retryPending || !ocrPlatformActive()) return
        retryPending = true
        handler.postDelayed({
            retryPending = false
            if (ocrPlatformActive()) settingsCache.selectedApp?.let { solicitarOcrPlataforma(it.packageName, false) }
        }, OCR_COOLDOWN_MS)
    }
    private var estadoPrimerPlano = ""
    private var ultimoTextoLogueado = ""
    private var ultimoOcrLogueado = ""
    private var lastTargetWindowId = -1
    private var lastUberEventAt = -10000L
    private var lastTargetWindowBounds: Rect? = null
    private val ofertasProcesadas = LinkedHashMap<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        MonitoringHealth.connected(true)
        DiagnosticRecorder.initialize(this)
        DiagnosticRecorder.event("reader_connected")
        serviceScope.launch {
            DiagnosticRecorder.state.collectLatest { diagnostic ->
                if (diagnostic.active && diagnostic.visual) {
                    while (DiagnosticRecorder.active()) {
                        captureDiagnosticReference()
                        delay(5000L)
                    }
                }
            }
        }
        if (Settings.canDrawOverlays(this)) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(ALERT_NOTIFICATION_ID)
        }
        Log.d(TAG, "Servicio de accesibilidad CONECTADO - LoVale 1.0.8")

        actualizarFiltroDePaquete(settingsCache.selectedApp)
        Log.d(TAG, "${settingsCache.selectedApp?.label}/A11Y conectado sdk=" + Build.VERSION.SDK_INT + " capabilities=" + serviceInfo?.capabilities)

        val repository = SettingsRepository(applicationContext)
        serviceScope.launch {
            repository.settingsFlow.collect { settings ->
                val appCambio = settings.selectedApp != settingsCache.selectedApp
                if (appCambio || settings.serviceActive != settingsCache.serviceActive) {
                    if (appCambio || !settings.serviceActive) DiagnosticRecorder.stop("pausa o cambio de plataforma")
                    com.example.lovale2.diagnostics.ReadingHealthLog.platform(settings.selectedApp?.label)
                    DiagnosticRecorder.event(if (settings.serviceActive) "monitor_active" else "monitor_paused")
                    MonitoringHealth.paused()
                    generation++
                    watchingOverlay = false
                    offerDismissal.reset()
                    TripOverlayService.dismissOffer("pausa o cambio de plataforma")
                    handler.removeCallbacksAndMessages(null)
                    @Suppress("DEPRECATION")
                    pendingEvent?.recycle()
                    pendingEvent = null
                    retryPending = false
                }
                settingsCache = settings

                if (appCambio) {
                    ofertasProcesadas.clear()
                    estadoPrimerPlano = ""
                    ultimoTextoLogueado = ""
                    ultimoOcrLogueado = ""
                    lastTargetWindowId = -1
                    lastTargetWindowBounds = null
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
        if (settingsCache.serviceActive && event.packageName?.toString() == settingsCache.selectedApp?.packageName) {
            MonitoringHealth.event()
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && TripOverlayService.isCenterVisible()) closeOffer("lista desplazada")
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                try {
                    val node = event.source
                    val clicked = Rect()
                    node?.getBoundsInScreen(clicked)
                    val visualClose = !clicked.isEmpty && SystemClock.elapsedRealtime() - dismissTargetsAt < 5000 &&
                        dismissTargets.any { it.contains(clicked.centerX(), clicked.centerY()) }
                    val labels = try { event.text.map { it.toString() } + listOfNotNull(node?.text?.toString(), node?.contentDescription?.toString(), node?.viewIdResourceName?.substringAfterLast('/')?.takeIf { it.contains("close", true) || it.contains("reject", true) }?.let { "cerrar" }) }
                        finally { @Suppress("DEPRECATION") node?.recycle() }
                    if (TripOverlayService.isOfferVisible() && (visualClose || labels.any { com.example.lovale2.domain.OfferDismissal.isCloseAction(it) || it.trim().lowercase() in setOf("aceptar", "me interesa") })) closeOffer("rechazo/cierre de oferta")
                } catch (e: Exception) { Log.w(TAG, "A11Y acción no legible: ${e.javaClass.simpleName}") }
            }
        }
        if (settingsCache.serviceActive &&
            event.packageName?.toString() == settingsCache.selectedApp?.packageName) {
            // Coalesce bursts BEFORE walking nodes, leaving the main queue free for OCR callbacks.
            val hadPending = pendingEvent != null
            @Suppress("DEPRECATION")
            pendingEvent?.recycle()
            @Suppress("DEPRECATION")
            pendingEvent = AccessibilityEvent.obtain(event)
            if (!hadPending) handler.postDelayed(readPendingEvent,
                (500L - (SystemClock.elapsedRealtime() - lastTreeFinishedAt)).coerceAtLeast(0L))
            return
        }
        try {
            manejarEvento(event)
        } catch (t: Throwable) {
            // Nunca dejamos que un evento malformado o una ventana problemática mate el servicio.
            Log.e(TAG, "EVENT error no fatal: type=${event.eventType} pkg=${event.packageName}", t)
        }
    }

    private fun manejarEvento(event: AccessibilityEvent) {
        val settings = settingsCache
        val selectedApp = settings.selectedApp ?: return
        if (!settings.serviceActive) return

        val targetPackage = selectedApp.packageName
        val eventPackage = event.packageName?.toString()?.lowercase(Locale.ROOT).orEmpty()
        if (BuildConfig.DEBUG) Log.d(TAG, "${settingsCache.selectedApp?.label}/A11Y EVENT type=" + event.eventType + " window=" + event.windowId + " match=" + (eventPackage == targetPackage))
        if (eventPackage != targetPackage) return

        if (usesOcrFallback(selectedApp) && event.windowId >= 0) {
            lastTargetWindowId = event.windowId
            lastUberEventAt = SystemClock.elapsedRealtime()
        }

        val textos = mutableListOf<String>()
        val treeStartedAt = SystemClock.elapsedRealtime()
        val budget = TreeBudget(treeStartedAt + 100L, enabled = true)
        val ventanasDelPaquete = try {
            windows?.filter { window ->
                val root = window.root
                val pkg = try { root?.packageName?.toString()?.lowercase(Locale.ROOT).orEmpty() }
                    finally { @Suppress("DEPRECATION") root?.recycle() }
                pkg == targetPackage
            } ?: emptyList()
        } catch (t: Throwable) {
            Log.w(TAG, "No se pudieron enumerar ventanas de $targetPackage", t)
            emptyList()
        }

        if (ventanasDelPaquete.isNotEmpty()) {
            registrarCambioPrimerPlano(targetPackage)

            for (ventana in ventanasDelPaquete) {
                if (usesOcrFallback(selectedApp)) {
                    if (ventana.id >= 0) lastTargetWindowId = ventana.id
                    val bounds = Rect()
                    try {
                        ventana.getBoundsInScreen(bounds)
                        if (!bounds.isEmpty) lastTargetWindowBounds = Rect(bounds)
                    } catch (_: Throwable) {
                        // El OCR sigue funcionando con fallback de display si no hay bounds.
                    }
                }

                val root = ventana.root ?: continue
                try {
                    recolectarTextos(root, textos, budget)
                } finally {
                    @Suppress("DEPRECATION")
                    try { root.recycle() } catch (_: Throwable) {}
                }
            }
        } else {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val rootPackage = rootNode.packageName?.toString()?.lowercase(Locale.ROOT).orEmpty()
                if (rootPackage != targetPackage) {
                    registrarCambioPrimerPlano("")
                    @Suppress("DEPRECATION")
                    try { rootNode.recycle() } catch (_: Throwable) {}
                    return
                }

                registrarCambioPrimerPlano(targetPackage)
                try {
                    recolectarTextos(rootNode, textos, budget)
                } finally {
                    @Suppress("DEPRECATION")
                    try { rootNode.recycle() } catch (_: Throwable) {}
                }
            }
        }

        Log.d(TAG, "A11Y extracción ms=" + (SystemClock.elapsedRealtime() - treeStartedAt) +
            " nodes=" + budget.nodes + " limitada=" + budget.limited)
        DiagnosticRecorder.event("a11y_tree", "ms" to (SystemClock.elapsedRealtime() - treeStartedAt),
            "nodes" to budget.nodes, "limited" to budget.limited, "texts" to textos.size)
        val ofertaEncontrada = procesarTextoDePantalla(
            textos = textos,
            selectedApp = selectedApp,
            targetPackage = targetPackage,
            source = "A11Y"
        )

        if (usesOcrFallback(selectedApp) && !ofertaEncontrada) {
            solicitarOcrPlataforma(targetPackage)
        }
    }

    private fun procesarTextoDePantalla(
        textos: List<String>,
        selectedApp: RideApp,
        targetPackage: String,
        source: String
    ): Boolean {
        if (textos.isEmpty()) {
            if (usesOcrFallback(selectedApp) && source == "A11Y") {
                Log.d(TAG, "${settingsCache.selectedApp?.label}/A11Y sin texto útil; fallback OCR")
            }
            return false
        }

        val textoCompleto = OfferText.normalize(textos.joinToString(" "))
            .replace(Regex("\\s+"), " ")
            .trim()

        if (textoCompleto.isBlank()) return false

        readId++
        DiagnosticRecorder.event("read", "readId" to readId, "source" to source, "chars" to textoCompleto.length)
        DiagnosticRecorder.text(source, textos.joinToString("\n"))
        if (com.example.lovale2.domain.ReadingWatch.candidate(textoCompleto)) com.example.lovale2.diagnostics.ReadingReport.observed(source, textos.joinToString("\n"))
        MonitoringHealth.readings.observe(com.example.lovale2.domain.ReadingWatch.candidate(textoCompleto), false, SystemClock.elapsedRealtime())

        val candidate = esPantallaDeOfertas(selectedApp, textoCompleto)
        // Uber/DiDi may expose only the waiting screen through A11Y while an offer is drawn.
        if ((source == "OCR" || !usesOcrFallback(selectedApp)) && TripOverlayService.isOfferVisible() &&
            offerDismissal.observe(textoCompleto, candidate)) closeOffer("oferta desaparecida; dos lecturas de espera")
        DiagnosticRecorder.event("detector", "readId" to readId, "candidate" to candidate)
        if (!candidate) {
            Log.d(TAG, "PARSE detector rechazó source=" + source + " chars=" + textoCompleto.length)
            // Señal conservadora de tarjeta parcial, nunca se evalúa como viaje completo.
            if ((source == "OCR" || !usesOcrFallback(selectedApp)) &&
                Regex("(?:ARS|\\$)\\s*[0-9]", RegexOption.IGNORE_CASE).containsMatchIn(textoCompleto) &&
                Regex("\\b(?:Aceptar|Viaje|recogida)\\b", RegexOption.IGNORE_CASE).containsMatchIn(textoCompleto) &&
                contarParesMinKm(textoCompleto) == 1) {
                procesarOferta(targetPackage, selectedApp, textoCompleto, source)
            }
            return false
        }

        if (source == "A11Y" && selectedApp == RideApp.DIDI && textoCompleto.contains("Centro de viajes", true) && Build.VERSION.SDK_INT >= 30) return false
        val ofertas = separarOfertas(textoCompleto, selectedApp)
        if (ofertas.isEmpty()) return false

        Log.d(TAG, "${selectedApp.label}/$source: ${ofertas.size} oferta(s) candidata(s)")
        var algunaValida = false
        for (oferta in ofertas) {
            if (procesarOferta(targetPackage, selectedApp, oferta, source)) algunaValida = true
        }
        return algunaValida
    }

    private data class TargetWindow(val id: Int, val bounds: Rect)
    private fun currentTargetWindow(allowCached: Boolean = true): TargetWindow? = try {
        windows.orEmpty().firstNotNullOfOrNull { window ->
            val root = window.root
            val pkg = try { root?.packageName?.toString() } finally {
                @Suppress("DEPRECATION")
                root?.recycle()
            }
            if (pkg == settingsCache.selectedApp?.packageName ||
                (allowCached && pkg == null && window.id == lastTargetWindowId && window.isActive &&
                    SystemClock.elapsedRealtime() - lastUberEventAt < 2500L)) {
                val bounds = Rect()
                window.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) TargetWindow(window.id, bounds) else null
            } else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "${settingsCache.selectedApp?.label}/OCR ventana no verificable", e)
        null
    }

    /** Independent reference: no detector gate and no additional OCR. Window API only. */
    private fun captureDiagnosticReference() {
        if (!DiagnosticRecorder.visualEnabled() || !settingsCache.serviceActive || Build.VERSION.SDK_INT < 34) return
        val platform = settingsCache.selectedApp?.packageName ?: return
        if (platform != DiagnosticRecorder.state.value.packageName) return
        val sessionId = DiagnosticRecorder.sessionId()
        if (ocrInProgress || referenceInProgress || !getSystemService(android.os.PowerManager::class.java).isInteractive ||
            getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) {
            DiagnosticRecorder.event("reference_skipped", "reason" to "ocupado/pantalla bloqueada")
            return
        }
        val window = currentTargetWindow(allowCached = false)
        if (window == null) {
            DiagnosticRecorder.event("reference_skipped", "reason" to "ventana no verificable")
            return
        }
        val token = generation
        val at = SystemClock.elapsedRealtime()
        referenceInProgress = true
        try {
            takeScreenshotOfWindow(window.id, mainExecutor, object : TakeScreenshotCallback {
                override fun onFailure(errorCode: Int) {
                    referenceInProgress = false
                    DiagnosticRecorder.event("reference_error", "code" to errorCode)
                }
                override fun onSuccess(screenshot: ScreenshotResult) {
                    try {
                        if (token != generation || sessionId != DiagnosticRecorder.sessionId() ||
                            !DiagnosticRecorder.visualEnabled() || currentTargetWindow(false)?.id != window.id) return
                        val hardware = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace) ?: return
                        try {
                            if (hardware.width.toLong() * hardware.height > 12_000_000L) {
                                DiagnosticRecorder.event("reference_skipped", "reason" to "imagen demasiado grande")
                                return
                            }
                            hardware.copy(Bitmap.Config.ARGB_8888, false)?.let { DiagnosticRecorder.image(it, at, platform) }
                        } finally { hardware.recycle() }
                    } catch (e: Exception) {
                        DiagnosticRecorder.event("reference_error", "type" to e.javaClass.simpleName)
                    } finally { screenshot.hardwareBuffer.close(); referenceInProgress = false }
                }
            })
        } catch (e: Exception) {
            referenceInProgress = false
            DiagnosticRecorder.event("reference_error", "type" to e.javaClass.simpleName)
        }
    }

    private fun solicitarOcrPlataforma(targetPackage: String, allowRetry: Boolean = true) {
        if (!ocrPlatformActive()) return
        if (!getSystemService(android.os.PowerManager::class.java).isInteractive ||
            getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.d(TAG, "${settingsCache.selectedApp?.label}/OCR no disponible: Android < 11")
            return
        }
        if (ocrInProgress || referenceInProgress || SystemClock.elapsedRealtime() - lastOcrAt < OCR_COOLDOWN_MS) {
            Log.d(TAG, "${settingsCache.selectedApp?.label}/OCR diferido busy=" + ocrInProgress)
            if (allowRetry) scheduleRetry()
            return
        }
        val window = currentTargetWindow()
        if (window == null) {
            if (TripOverlayService.isOfferVisible()) closeOffer("plataforma fuera de pantalla")
            Log.d(TAG, "${settingsCache.selectedApp?.label}/OCR omitido: ventana seleccionada no verificable")
            if (allowRetry) scheduleRetry()
            return
        }
        lastOcrAt = SystemClock.elapsedRealtime()
        ocrInProgress = true
        MonitoringHealth.ocrStarted()
        captureTarget(window, targetPackage, generation,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE, allowRetry)
    }

    private fun captureTarget(window: TargetWindow, targetPackage: String, token: Int,
                            windowCapture: Boolean, allowRetry: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        fun failed(code: Int, error: Throwable? = null) {
            DiagnosticRecorder.event("screenshot_error", "code" to code, "windowMode" to windowCapture)
            Log.w(TAG, "${settingsCache.selectedApp?.label}/OCR screenshot fallo windowMode=" + windowCapture + " code=" + code + " id=" + window.id, error)
            if (windowCapture && code != ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS &&
                code != ERROR_TAKE_SCREENSHOT_SECURE_WINDOW && ocrPlatformActive() &&
                token == generation && currentTargetWindow() == window) {
                Log.d(TAG, "${settingsCache.selectedApp?.label}/OCR fallback display con recorte verificado")
                captureTarget(window, targetPackage, token, false, allowRetry)
            } else {
                ocrInProgress = false
                MonitoringHealth.ocrFinished(false)
                if (allowRetry && token == generation) scheduleRetry()
            }
        }
        val callback = object : TakeScreenshotCallback {
            override fun onFailure(errorCode: Int) = failed(errorCode)
            override fun onSuccess(screenshot: ScreenshotResult) {
                var bitmap: Bitmap? = null
                try {
                    if (!ocrPlatformActive() || token != generation || currentTargetWindow() != window) {
                        Log.d(TAG, "${settingsCache.selectedApp?.label}/OCR screenshot descartado: contexto cambió")
                        ocrInProgress = false
                        MonitoringHealth.ocrFinished(true)
                        return
                    }
                    val wrapped = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                    bitmap = try { wrapped?.copy(Bitmap.Config.ARGB_8888, false) } finally { wrapped?.recycle() }
                    val original = bitmap ?: error("Bitmap nulo")
                    DiagnosticRecorder.event("screenshot_ready")
                    if (!windowCapture) {
                        val bounds = Rect(window.bounds)
                        check(bounds.intersect(0, 0, original.width, original.height)) { "Bounds fuera del display" }
                        val cropped = Bitmap.createBitmap(original, bounds.left, bounds.top, bounds.width(), bounds.height())
                        if (cropped !== original) original.recycle()
                        bitmap = cropped
                    }
                    val region = bitmap ?: error("Region nula")
                    Log.d(TAG, "${settingsCache.selectedApp?.label}/OCR screenshot OK windowMode=" + windowCapture + " image=" + region.width + "x" + region.height)
                    procesarScreenshotPlataforma(region, targetPackage, token, allowRetry)
                    bitmap = null
                } catch (e: Exception) {
                    bitmap?.recycle()
                    ocrInProgress = false
                    MonitoringHealth.ocrFinished(false)
                    Log.e(TAG, "${settingsCache.selectedApp?.label}/OCR imagen inválida", e)
                } finally { screenshot.hardwareBuffer.close() }
            }
        }
        try {
            Log.d(TAG, "${settingsCache.selectedApp?.label}/OCR screenshot REQUEST windowMode=" + windowCapture + " id=" + window.id)
            DiagnosticRecorder.event("screenshot_request", "windowMode" to windowCapture, "windowId" to window.id)
            if (windowCapture && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                takeScreenshotOfWindow(window.id, mainExecutor, callback)
            } else {
                takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)
            }
        } catch (e: Exception) { failed(-1, e) }
    }

    private fun procesarScreenshotPlataforma(bitmap: Bitmap, targetPackage: String, token: Int, allowRetry: Boolean) {
        val startedAt = SystemClock.elapsedRealtime()
        val expectedWindow = currentTargetWindow()
        serviceScope.launch {
            try {
                val reading = kotlinx.coroutines.withContext(Dispatchers.Default) { OfferOcrReader(textRecognizer).read(bitmap) }
                MonitoringHealth.ocrFinished(true)
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                DiagnosticRecorder.event("ocr_result", "ms" to elapsed, "cards" to reading.cards.size, "refined" to reading.refined)
                Log.d(TAG, "OCR focalizado ms=$elapsed cards=${reading.cards.size} refined=${reading.refined}")
                if (!ocrPlatformActive() || token != generation || currentTargetWindow() != expectedWindow || elapsed > 5000L) {
                    DiagnosticRecorder.event("stale_read")
                    return@launch
                }
                val app = settingsCache.selectedApp ?: return@launch
                dismissTargetsAt = SystemClock.elapsedRealtime()
                val margin = (resources.displayMetrics.density * 18).toInt()
                dismissTargets = reading.dismissTargets.map { line -> Rect(line.left, line.top, line.right, line.bottom).apply {
                    offset(expectedWindow?.bounds?.left ?: 0, expectedWindow?.bounds?.top ?: 0); inset(-margin, -margin)
                } }
                val parsed = if (reading.cards.isEmpty()) {
                    procesarTextoDePantalla(listOf(reading.text), app, targetPackage, "OCR")
                } else {
                    var all = true
                    val central = app == RideApp.DIDI && (reading.cards.size > 1 || reading.text.contains("Centro de viajes", true))
                    centralCards = if (central) arrayListOf() else null
                    try {
                        for (card in reading.cards) {
                            cardAnchor = if (central) card else null
                            if (!procesarTextoDePantalla(listOf(card.text), app, targetPackage, "OCR")) all = false
                        }
                        centralCards?.takeIf { it.isNotEmpty() }?.let { batch ->
                            if (Settings.canDrawOverlays(this@TripAccessibilityService)) ContextCompat.startForegroundService(this@TripAccessibilityService,
                                Intent(this@TripAccessibilityService, TripOverlayService::class.java).putParcelableArrayListExtra("EXTRA_CARDS", batch))
                        }
                    } finally { centralCards = null; cardAnchor = null }
                    all
                }
                if (!parsed && allowRetry) scheduleRetry()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                MonitoringHealth.ocrFinished(false)
                Log.e(TAG, "OCR lectura focalizada falló", e)
                DiagnosticRecorder.event("ocr_error")
                if (allowRetry) scheduleRetry()
            } finally { bitmap.recycle(); ocrInProgress = false }
        }
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
        if (SystemClock.elapsedRealtime() < suppressUntil) return false
        val options = com.example.lovale2.data.settings.DriverOptionsStore.get(this).state.value
        if (options.quietAt()) return false
        val activeSettings = options.effective(settingsCache)
        val minKm = activeSettings.minRateByKm.replace(',', '.').toDoubleOrNull() ?: 950.0
        val minHora = activeSettings.minRateByHour.replace(',', '.').toDoubleOrNull() ?: 0.0
        val maxPickup = activeSettings.maxPickupDistance.replace(',', '.').toDoubleOrNull() ?: 0.0

        val tripEvaluator = activeSettings.evaluator()

        val rutas = extraerRutas(ofertaTexto)
        val pickup = rutas.firstOrNull()?.first.orEmpty()
        val destino = rutas.lastOrNull()?.second.orEmpty()
        val parsed = tripEvaluator.extraerDatosDeViaje(ofertaTexto, destino, pickup)
        val datos = parsed.copy(
            pickup = neighborhoods.cached(pickup)?.let { "$pickup, CABA - $it" } ?: pickup,
            destino = neighborhoods.cached(destino)?.let { "$destino, CABA - $it" } ?: destino)

        Log.d(
            TAG,
            "${app.label}/$source PARSE: precio=${datos.precio} pickupKm=${datos.pickupDistanceKm} " +
                "pickupMin=${datos.pickupDurationMin} viajeKm=${datos.distanciaKm} viajeMin=${datos.duracionMin} ${datos.priceDiagnostics}"
        )

        DiagnosticRecorder.event("parse", "readId" to readId, "source" to source,
            "complete" to datos.completeReading, "fare" to datos.precio, "pickupKm" to datos.pickupDistanceKm,
            "tripKm" to datos.distanciaKm, "pickupMin" to datos.pickupDurationMin, "tripMin" to datos.duracionMin)
        if (!datos.completeReading) {
            summary.incomplete()
            Log.d(TAG, "${app.label}/$source oferta candidata incompleta")
            val now = SystemClock.elapsedRealtime()
            if ((source == "OCR" || !usesOcrFallback(app)) && now - lastIncompleteAt > 15000L && now - lastCompleteAt > 9000L) {
                lastIncompleteAt = now
                mostrarResultado(ResultadoEvaluacion(false, MotivoEvaluacion.DISTANCIA_INVALIDA,
                    Double.NaN, Double.NaN), datos, "INCOMPLETE", maxPickup, "partial-$now", activeSettings)
            }
            return false
        }

        MonitoringHealth.readings.observe(true, true, SystemClock.elapsedRealtime())
        val evaluacion = tripEvaluator.evaluarViaje(datos, activeSettings.excludedZones)
        val nivel = classifyOffer(datos, evaluacion, activeSettings)
        lastCompleteAt = SystemClock.elapsedRealtime()

        Log.d(
            TAG,
            "${app.label}/$source EVAL: precio=$%.0f | totalKm=%.3f | totalMin=%.1f | $%.0f/km | $%.0f/h | nivel=$nivel | motivo=${evaluacion.motivo}".format(
                datos.precio,
                datos.distanciaKm + datos.pickupDistanceKm,
                datos.duracionMin + datos.pickupDurationMin,
                evaluacion.tarifaPorKm,
                evaluacion.tarifaPorHora
            )
        )

        offerDismissal.reset()
        watchOverlay()
        val fingerprint = buildFingerprint(packageName, parsed)
        DiagnosticRecorder.event("evaluation", "readId" to readId, "offerId" to fingerprint,
            "rateKm" to evaluacion.tarifaPorKm, "rateHour" to evaluacion.tarifaPorHora,
            "minKm" to minKm, "minHour" to minHora, "maxPickup" to maxPickup, "level" to nivel)
        JourneyLocationService.offerDetected()
        val ahora = SystemClock.elapsedRealtime()
        val anterior = ofertasProcesadas[fingerprint]
        if (anterior != null && ahora - anterior < 30000L) {
            ofertasProcesadas[fingerprint] = ahora
            if (centralCards != null) mostrarResultado(evaluacion, datos, nivel, maxPickup, fingerprint, activeSettings)
            resolveZones(datos, fingerprint, activeSettings)
            DiagnosticRecorder.event("deduplicated", "offerId" to fingerprint)
            Log.d(TAG, "Oferta duplicada ignorada (<30s desde última lectura)")
            return true
        }

        ofertasProcesadas[fingerprint] = ahora
        while (ofertasProcesadas.size > 100) {
            ofertasProcesadas.remove(ofertasProcesadas.entries.first().key)
        }

        summary.offer()
        OfferFeedback.play(this, options)
        val recordId = java.util.UUID.randomUUID().toString()
        recordIds[fingerprint] = recordId
        while (recordIds.size > 100) recordIds.remove(recordIds.keys.first())
        DiagnosticRecorder.event("history_offer", "recordId" to recordId, "offerId" to fingerprint)
        serviceScope.launch {
            try {
                OfferHistory(this@TripAccessibilityService).add(OfferRecord(System.currentTimeMillis(), app.label,
                    datos.precio, evaluacion.tarifaPorKm, evaluacion.tarifaPorHora,
                    nivel,
                    if (maxPickup > 0 && datos.pickupDistanceKm > maxPickup) "PICKUP_EXCEDIDO" else evaluacion.motivo.name,
                    false, id = recordId, sessionId = summary.state.value.startedAt, targetKm = minKm, targetHour = minHora,
                    surge = com.example.lovale2.domain.surgeLabel(ofertaTexto), neighborhood = com.example.lovale2.domain.explicitNeighborhood(datos.destino), totalKm = datos.distanciaKm + datos.pickupDistanceKm,
                    totalMinutes = datos.duracionMin + datos.pickupDurationMin,
                    costs = tripEvaluator.estimarCostos(datos, activeSettings.vehicleCosts),
                    vehicleSnapshot = activeSettings.vehicleCosts.takeIf { it.ready }))
                DiagnosticRecorder.event("history_saved")
            } catch (e: Exception) {
                DiagnosticRecorder.event("history_error")
                Log.e(TAG, "HISTORY no se pudo guardar oferta", e)
            }
        }
        mostrarResultado(evaluacion, datos, nivel, maxPickup, fingerprint, activeSettings)
        resolveZones(datos, fingerprint, activeSettings)
        return true
    }

    private fun resolveZones(datos: DatosViaje, offerId: String, settings: AppSettings) {
        if (hasExplicitZone(datos.pickup) && hasExplicitZone(datos.destino)) return
        if (zoneJobs.size >= 8 || !zoneJobs.add(offerId)) return
        val token = generation
        val originalSettings = settingsCache
        val window = currentTargetWindow()
        serviceScope.launch {
            try {
                val stillRelevant = { generation == token && settingsCache == originalSettings && settingsCache.serviceActive }
                val pickup = if (hasExplicitZone(datos.pickup)) null else neighborhoods.resolve(datos.pickup, stillRelevant)
                val destination = if (hasExplicitZone(datos.destino)) null else neighborhoods.resolve(datos.destino, stillRelevant)
                if (pickup == null && destination == null) return@launch
                // Never revive a rejected offer, update another platform or attach stale screen results.
                if (generation != token || settingsCache != originalSettings || !settingsCache.serviceActive || currentTargetWindow() != window) return@launch
                val enriched = datos.copy(
                    pickup = pickup?.let { "${datos.pickup}, CABA - $it" } ?: datos.pickup,
                    destino = destination?.let { "${datos.destino}, CABA - $it" } ?: datos.destino)
                val result = settings.evaluator().evaluarViaje(enriched, settings.excludedZones)
                val known = hasExplicitZone(enriched.pickup) && hasExplicitZone(enriched.destino)
                val level = classifyOffer(enriched, result, settings)
                TripOverlayService.updateZone(offerId, known, result.zonaDetectada.orEmpty(), level)
                recordIds[offerId]?.let { OfferHistory(this@TripAccessibilityService).updateEvaluation(it, level, result.motivo.name, com.example.lovale2.domain.explicitNeighborhood(enriched.destino)) }
                Log.d(TAG, "ZONE/USIG resolved pickup=${pickup != null} destination=${destination != null} known=$known")
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { Log.w(TAG, "ZONE/USIG unavailable: ${e.javaClass.simpleName}") }
            finally { zoneJobs.remove(offerId) }
        }
    }

    private var lastIncompleteAt = -15000L
    private var lastCompleteAt = -9000L

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
            OfferText.isUberCandidate(texto)
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
            return listOf(resultado.first() to "")
        }

        val direcciones = texto
            .split(Regex("(?i)\\b(?:A|Viaje|Express|Express Nuevo|Aceptar)\\b|\$|ARS"))
            .map { limpiarDireccion(it) }
            .filter(::looksLikeAddress)

        return if (direcciones.size >= 2) {
            listOf(direcciones.first() to direcciones.last())
        } else if (direcciones.size == 1) {
            listOf(direcciones.first() to "")
        } else {
            emptyList()
        }
    }

    private fun limpiarDireccion(value: String): String = value
        .replace(Regex("^[\\s:·•\\-–—()]+"), "")
        .replace(Regex("(?i)^(a|viaje|destino|recogida|pickup)\\b\\s*:?\\s*"), "")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '.', ':', '·', '•', '-', '–', '—')

    private fun hasExplicitZone(address: String): Boolean =
        Regex("(?i)(?:CABA|Buenos Aires|Vicente L[oó]pez)\\s*-\\s*[A-Za-zÁÉÍÓÚáéíóúÑñ]").containsMatchIn(address)

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

    private class TreeBudget(val deadline: Long, val enabled: Boolean,
                             var nodes: Int = 0, var limited: Boolean = false)

    private fun recolectarTextos(node: AccessibilityNodeInfo, destino: MutableList<String>, budget: TreeBudget, depth: Int = 0) {
        if (budget.enabled && (budget.nodes >= 180 || depth >= 30 || SystemClock.elapsedRealtime() >= budget.deadline)) {
            budget.limited = true
            return
        }
        budget.nodes++
        node.text?.toString()?.let { if (it.isNotBlank()) destino.add(it) }
        node.contentDescription?.toString()?.let { if (it.isNotBlank()) destino.add(it) }

        for (i in 0 until node.childCount) {
            if (budget.enabled && (SystemClock.elapsedRealtime() >= budget.deadline || budget.nodes >= 180)) {
                budget.limited = true
                break
            }
            val child = try { node.getChild(i) } catch (_: Throwable) { null }
            child?.let { c ->
                try {
                    recolectarTextos(c, destino, budget, depth + 1)
                } finally {
                    @Suppress("DEPRECATION")
                    try { c.recycle() } catch (_: Throwable) {}
                }
            }
        }
    }

    private fun buildFingerprint(
        packageName: String,
        datos: DatosViaje
    ): String {
        // Distinguish simultaneous equal-price routes without putting addresses in diagnostic IDs.
        val route = java.security.MessageDigest.getInstance("SHA-256")
            .digest("${datos.pickup}|${datos.destino}".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "$packageName|${datos.precio}|${datos.distanciaKm}|${datos.duracionMin}|${datos.pickupDistanceKm}|${datos.pickupDurationMin}|$route"
    }

    private fun mostrarResultado(
        evaluacion: ResultadoEvaluacion,
        datos: DatosViaje,
        nivel: String,
        maxPickup: Double,
        offerId: String,
        activeSettings: AppSettings
    ) {
        DiagnosticRecorder.event("overlay_request", "offerId" to offerId, "level" to nivel)
        val zoneLabel = evaluacion.zonaDetectada.orEmpty()
        val pickupFuera = maxPickup > 0.0 && datos.pickupDistanceKm > 0.0 && datos.pickupDistanceKm > maxPickup

        val (titulo, mensaje) = when (nivel) {
            "INCOMPLETE" -> "Lectura incompleta" to "No se pudo leer precio, recogida y viaje completos"
            "GREEN" -> "Viaje rentable" to "Cumple tus parámetros"
            "YELLOW" -> "Viaje casi rentable" to "Está entre 85% y 100% de tus mínimos"
            "ZONE" -> "Zona no deseada" to if (zoneLabel.isNotBlank()) zoneLabel else "Zona excluida"
            else -> "Viaje no rentable" to if (pickupFuera) {
                "Pickup ${"%.1f".format(datos.pickupDistanceKm)} km > máximo ${"%.1f".format(maxPickup)} km"
            } else {
                "No cumple tus parámetros"
            }
        }

        val overlayIntent = Intent(this, TripOverlayService::class.java).apply {
            activeSettings.evaluator().estimarCostos(datos, activeSettings.vehicleCosts)?.let {
                putExtra("EXTRA_ESTIMATED_NET", it.net)
            }
            putExtra("EXTRA_OFFER_ID", offerId)
            putExtra("EXTRA_FARE", datos.precio)
            putExtra("EXTRA_PLATFORM_PACKAGE", activeSettings.selectedApp?.packageName)
            putExtra("EXTRA_RATE_KM", evaluacion.tarifaPorKm)
            putExtra("EXTRA_RATE_HOUR", evaluacion.tarifaPorHora)
            putExtra("EXTRA_MIN_KM", activeSettings.minRateByKm.replace(',', '.').toDoubleOrNull() ?: 950.0)
            putExtra("EXTRA_MIN_HOUR", activeSettings.minRateByHour.replace(',', '.').toDoubleOrNull() ?: 0.0)
            putExtra("EXTRA_PICKUP_EXCEEDED", pickupFuera)
            putExtra("EXTRA_ZONE_KNOWN", hasExplicitZone(datos.pickup) && hasExplicitZone(datos.destino))
            putExtra("EXTRA_PROFITABILITY_LEVEL", nivel)
            putExtra("EXTRA_ZONE_LABEL", zoneLabel)
        }

        if (centralCards != null) {
            val window = currentTargetWindow()
            val statusTop = if (Build.VERSION.SDK_INT >= 30) getSystemService(android.view.WindowManager::class.java)
                .currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars()).top else 0
            overlayIntent.putExtra("EXTRA_CARD_TOP", (cardAnchor?.top ?: 0) + (window?.bounds?.top ?: 0) - statusTop)
            overlayIntent.putExtra("EXTRA_CARD_RIGHT", window?.bounds?.right ?: resources.displayMetrics.widthPixels)
            centralCards?.add(overlayIntent)
            return
        }
        try {
            if (!Settings.canDrawOverlays(this)) {
                Log.w(TAG, "Oferta detectada pero SYSTEM_ALERT_WINDOW no está concedido; se mantiene la notificación")
                crearCanalYMostrarNotificacion(titulo, mensaje)
                return
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(ALERT_NOTIFICATION_ID)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, overlayIntent)
            } else {
                startService(overlayIntent)
            }
            Log.d(TAG, "OVERLAY solicitado: nivel=$nivel")
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
                .setSmallIcon(com.example.lovale2.R.drawable.ic_lovale_notification)
                .setContentTitle(titulo)
                .setContentText(descripcion)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }

    override fun onInterrupt() {
        MonitoringHealth.connected(false)
        Log.d(TAG, "Servicio de accesibilidad interrumpido temporalmente")
    }

    override fun onDestroy() {
        DiagnosticRecorder.event("reader_disconnected")
        DiagnosticRecorder.stop("servicio interrumpido")
        MonitoringHealth.connected(false)
        destroyed = true
        generation++
        handler.removeCallbacksAndMessages(null)
        @Suppress("DEPRECATION")
        pendingEvent?.recycle()
        pendingEvent = null
        serviceScope.cancel()
        try { textRecognizer.close() } catch (_: Throwable) {}
        super.onDestroy()
    }
}
