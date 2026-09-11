package com.example.lovale2.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class TripAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "LoVale"
        private const val ALERT_CHANNEL_ID = "viajes_channel_id"
        private const val ALERT_NOTIFICATION_ID = 1001
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    @Volatile
    private var settingsCache = AppSettings()
    private var estadoPrimerPlano: String = ""
    private val ofertasProcesadas = LinkedHashMap<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Servicio de accesibilidad CONECTADO")

        actualizarFiltroDePaquete(settingsCache.selectedApp)

        val repository = SettingsRepository(applicationContext)
        serviceScope.launch {
            repository.settingsFlow.collect { settings ->
                val appCambio = settings.selectedApp != settingsCache.selectedApp
                settingsCache = settings

                if (appCambio) {
                    ofertasProcesadas.clear()
                    estadoPrimerPlano = ""
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

    /** Cambia el packageNames del AccessibilityService sin pedir permisos adicionales. */
    private fun actualizarFiltroDePaquete(app: RideApp?) {
        val info = serviceInfo ?: return
        info.packageNames = if (app == null) emptyArray() else arrayOf(app.packageName)
        setServiceInfo(info)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val settings = settingsCache
        val selectedApp = settings.selectedApp ?: return
        if (!settings.serviceActive) return

        val targetPackage = selectedApp.packageName
        val eventPackage = event.packageName?.toString()?.lowercase(Locale.ROOT).orEmpty()

        if (eventPackage != targetPackage) return

        // No usamos solo rootInActiveWindow: algunas apps (Uber en particular)
        // dibujan la tarjeta de oferta entrante como una ventana superpuesta
        // separada de la ventana "activa" (la de navegación/mapa de fondo).
        // Recorremos TODAS las ventanas visibles del paquete objetivo y
        // combinamos su texto para no perdernos ese popup.
        val ventanasDelPaquete = try {
            windows?.filter { w ->
                val pkg = w.root?.packageName?.toString()?.lowercase(Locale.ROOT).orEmpty()
                pkg == targetPackage
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (ventanasDelPaquete.isEmpty()) {
            // Fallback al comportamiento anterior si `windows` no devolvió nada
            // (puede pasar en algunos dispositivos/versiones de Android).
            val rootNode = rootInActiveWindow ?: return
            val rootPackage = rootNode.packageName?.toString()?.lowercase(Locale.ROOT).orEmpty()
            if (rootPackage.isNotBlank() && rootPackage != targetPackage) {
                registrarCambioPrimerPlano("")
                rootNode.recycle()
                return
            }
            registrarCambioPrimerPlano(targetPackage)
            val textos = mutableListOf<String>()
            try {
                recolectarTextos(rootNode, textos)
            } finally {
                rootNode.recycle()
            }
            procesarTextoDePantalla(textos, selectedApp, targetPackage)
            return
        }

        registrarCambioPrimerPlano(targetPackage)

        val textos = mutableListOf<String>()
        for (ventana in ventanasDelPaquete) {
            val root = ventana.root ?: continue
            try {
                recolectarTextos(root, textos)
            } finally {
                root.recycle()
            }
        }

        procesarTextoDePantalla(textos, selectedApp, targetPackage)
    }

    private var ultimoTextoLogueado = ""

    private fun procesarTextoDePantalla(textos: List<String>, selectedApp: RideApp, targetPackage: String) {
        if (textos.isEmpty()) return

        val textoCompleto = textos.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        if (textoCompleto.isBlank()) return

        if (textoCompleto != ultimoTextoLogueado) {
            Log.d(TAG, "RAW [${selectedApp.label}] (${textoCompleto.length} chars): ${textoCompleto.take(600)}")
            ultimoTextoLogueado = textoCompleto
        }

        if (!esPantallaDeOfertas(selectedApp, textoCompleto)) return

        Log.d(TAG, "Pantalla de ofertas detectada: ${selectedApp.label} | texto=${textoCompleto.take(500)}")

        val ofertas = separarOfertas(textoCompleto, selectedApp)
        if (ofertas.isEmpty()) return
        Log.d(TAG, "${selectedApp.label}: ${ofertas.size} tarjeta(s) de oferta detectada(s)")

        for (oferta in ofertas) {
            procesarOferta(targetPackage, selectedApp, oferta)
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
            RideApp.UBER -> true // Flexibilizado para Uber para evitar bloqueos por palabras clave rígidas
            RideApp.CABIFY -> texto.contains("app", ignoreCase = true) || texto.contains("/km", ignoreCase = true) || contarParesMinKm(texto) >= 2
        }
    }

    private fun procesarOferta(packageName: String, app: RideApp, ofertaTexto: String) {
        val tripEvaluator = TripEvaluator(
            tarifaMinimaPorKm = settingsCache.minRateByKm.toDoubleOrNull() ?: 950.0,
            tarifaMinimaPorHora = settingsCache.minRateByHour.toDoubleOrNull() ?: 0.0
        )

        val rutas = extraerRutas(ofertaTexto)
        val pickup = rutas.firstOrNull()?.first.orEmpty()
        val destino = rutas.lastOrNull()?.second.orEmpty()

        val datos = tripEvaluator.extraerDatosDeViaje(ofertaTexto, destino, pickup)
        Log.d(TAG, "Parse ${app.label}: precio=${datos.precio} pickupKm=${datos.pickupDistanceKm} viajeKm=${datos.distanciaKm} viajeMin=${datos.duracionMin} pickup='$pickup' destino='$destino'")

        if (datos.precio <= 0.0 || datos.distanciaKm <= 0.0 || datos.duracionMin <= 0.0) {
            Log.d(TAG, "Oferta ignorada: faltan precio/distancia/duración")
            return
        }

        val maxPickup = settingsCache.maxPickupDistance.toDoubleOrNull() ?: 0.0
        if (maxPickup > 0.0 && datos.pickupDistanceKm > maxPickup) {
            Log.d(TAG, "Oferta ignorada por pickup: ${datos.pickupDistanceKm} km > máximo ${maxPickup} km")
            return
        }

        val evaluacion = tripEvaluator.evaluarViaje(datos, settingsCache.excludedZones)
        Log.d(
            TAG,
            "Evaluación ${app.label}: $%.0f | %.2f km | %.0f min | $%.0f/km | $%.0f/h | aprobado=${evaluacion.aprobado} | motivo=${evaluacion.motivo}".format(
                datos.precio, datos.distanciaKm, datos.duracionMin, evaluacion.tarifaPorKm, evaluacion.tarifaPorHora
            )
        )

        val fingerprint = buildFingerprint(packageName, datos, evaluacion)
        val ahora = System.currentTimeMillis()
        val anterior = ofertasProcesadas[fingerprint]
        if (anterior != null && ahora - anterior < 4000L) {
            Log.d(TAG, "Oferta duplicada ignorada (<4s)")
            return
        }
        ofertasProcesadas[fingerprint] = ahora
        while (ofertasProcesadas.size > 100) ofertasProcesadas.remove(ofertasProcesadas.entries.first().key)

        mostrarResultado(evaluacion, datos)
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
        val tienePrecio = Regex("""(?:\$|ARS)\s*[0-9]""", RegexOption.IGNORE_CASE).containsMatchIn(texto)
        return if (app == RideApp.UBER) {
            tienePrecio && (texto.contains("min", ignoreCase = true) || texto.contains("km", ignoreCase = true) || texto.contains("m", ignoreCase = true))
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

    /** Devuelve (dirección, dirección) para pickup y destino. */
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
        if (resultado.size == 1 && resultado.first().isNotBlank()) return listOf(resultado.first() to resultado.first())

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
                        Regex("palermo|belgrano|almagro|balvanera|boedo|caballito|once|retiro|constitucion|villa|san telmo|flores|recoleta|nuñez|paternal|chacarita|lanus|valentin alsina", RegexOption.IGNORE_CASE).containsMatchIn(text)
                )
    }

    private fun recolectarTextos(node: AccessibilityNodeInfo, destino: MutableList<String>) {
        node.text?.toString()?.let { if (it.isNotBlank()) destino.add(it) }
        node.contentDescription?.toString()?.let { if (it.isNotBlank()) destino.add(it) }
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null }
            child?.let { c ->
                try { recolectarTextos(c, destino) } finally { c.recycle() }
            }
        }
    }

    private fun buildFingerprint(packageName: String, datos: DatosViaje, evaluacion: ResultadoEvaluacion): String =
        "$packageName|${datos.precio}|${datos.distanciaKm}|${datos.duracionMin}|${datos.pickup}|${datos.destino}|${evaluacion.motivo}"

    private fun mostrarResultado(evaluacion: ResultadoEvaluacion, datos: DatosViaje) {
        val (titulo, mensaje) = when (evaluacion.motivo) {
            MotivoEvaluacion.APROBADO -> "¡Viaje Rentable!" to "Tarifa: $%.0f/km · $%.0f/h".format(evaluacion.tarifaPorKm, evaluacion.tarifaPorHora)
            MotivoEvaluacion.ZONA_EXCLUIDA -> "Zona no deseada" to "Zona: ${evaluacion.zonaDetectada}"
            MotivoEvaluacion.TARIFA_KM_BAJA -> "Tarifa baja" to "$%.0f/km está debajo de tu mínimo".format(evaluacion.tarifaPorKm)
            MotivoEvaluacion.TARIFA_HORA_BAJA -> "Tarifa por hora baja" to "$%.0f/h está debajo de tu mínimo".format(evaluacion.tarifaPorHora)
            MotivoEvaluacion.DISTANCIA_INVALIDA -> "Datos no válidos" to "No se pudo leer el recorrido"
        }

        crearCanalYMostrarNotificacion(titulo, mensaje)

        val overlayIntent = Intent(this, TripOverlayService::class.java).apply {
            putExtra("EXTRA_PRICE", datos.precio)
            putExtra("EXTRA_RATE_KM", evaluacion.tarifaPorKm)
            putExtra("EXTRA_RATE_HOUR", evaluacion.tarifaPorHora)
            putExtra("EXTRA_DISTANCE_KM", datos.distanciaKm)
            putExtra("EXTRA_DURATION_MIN", datos.duracionMin)
            putExtra("EXTRA_PICKUP_DISTANCE_KM", datos.pickupDistanceKm)
            putExtra("EXTRA_PICKUP", datos.pickup)
            putExtra("EXTRA_DESTINATION", datos.destino)
            putExtra("EXTRA_ZONE_PICKUP", evaluacion.zonaPickupDetectada ?: "")
            putExtra("EXTRA_ZONE_DESTINATION", evaluacion.zonaDestinoDetectada ?: "")
            putExtra("EXTRA_IS_PROFITABLE", evaluacion.aprobado)
            putExtra("EXTRA_MOTIVO", evaluacion.motivo.name)
        }

        try {
            if (!Settings.canDrawOverlays(this)) {
                Log.w(TAG, "Oferta detectada pero SYSTEM_ALERT_WINDOW no está concedido; se mantiene la notificación")
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(this, overlayIntent)
            else startService(overlayIntent)
            Log.d(TAG, "TripOverlayService solicitado correctamente")
        } catch (t: Throwable) {
            Log.e(TAG, "No se pudo iniciar TripOverlayService", t)
        }
    }

    private fun crearCanalYMostrarNotificacion(titulo: String, descripcion: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(ALERT_CHANNEL_ID, "Alertas de Viajes LoVale", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        notificationManager.notify(
            ALERT_NOTIFICATION_ID,
            NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(titulo)
                .setContentText(descripcion)
                .setStyle(NotificationCompat.BigTextStyle().bigText(descripcion))
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
        super.onDestroy()
    }
}