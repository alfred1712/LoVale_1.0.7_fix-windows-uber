# Diagnóstico Uber — 1.0.8-beta3 (versionCode 8)

Rama: feature/1.0.8-uber-hybrid-detection. Sin merge.

Hallazgos verificables en código anterior:
- onFailure de takeScreenshotOfWindow terminaba sin intentar display.
- El cooldown descartaba eventos sin programar una nueva captura.
- Una pareja de recogida podía aceptarse como viaje y suprimir OCR.
- Normalización solo en OCR, no compartida con parser/detector.
- Faltaban logs de bitmap válido y solicitud/respuesta ML Kit.
- Los callbacks podían alertar después de pausar/cambiar plataforma.
- Un fallo de notificación podía impedir solicitar el overlay.

No había logs de las pruebas previas: estos hallazgos no identifican por sí solos la causa histórica.
El XML ya declara canTakeScreenshot, canRetrieveWindowContent y flagRetrieveInteractiveWindows.
TripOverlayService ya inicia foreground y registra OVERLAY visible; se mantiene su presentación.

Cambios:
- TripAccessibilityService: captura alternativa recortada, contexto por solicitud,
  reintento acotado, estado serializado en Main, liberación de imágenes y diagnóstico.
- OfferText: normalización común de ARS/AR$, km, m1n/mln, espacios y separadores.
- TripEvaluator: usa normalización; descarta precios /km; tolera centavos con punto.
- UberOcrTest: cinco tests adicionales. Los tests existentes cubren Uber, Cabify y DiDi.
- app/build.gradle.kts: beta3/code8 y BuildConfig para limitar texto de diagnóstico a debug.

Prueba real pendiente: adb devices no mostró dispositivos conectados.
1. Instalar app/build/outputs/apk/debug/app-debug.apk.
2. Abrir LoVale, seleccionar Uber, verificar permisos de overlay y Accesibilidad, activar monitoreo.
3. Si capabilities no incluye 128 (captura), desactivar/reactivar Accesibilidad y revisar logs.
4. Capturar: adb logcat -v threadtime LoVale:D "*:S"
5. Esperar oferta real. Secuencia:
   UBER/A11Y EVENT -> PARSE (si incompleta) -> UBER/OCR screenshot REQUEST
   -> screenshot OK -> MLKIT REQUEST -> MLKIT OK -> PARSE -> EVAL
   -> OVERLAY solicitado -> OVERLAY visible.
6. En Android 14+ windowMode=true. Si falla y es recuperable, fallback display;
   en Android 11–13 windowMode=false con recorte a bounds verificados.
7. Probar pausa durante OCR, cambio de plataforma y regresión Cabify/DiDi.

Límites:
- Android <11 no ofrece este OCR por screenshot.
- Captura segura o sin permiso no se puede resolver desde el parser.
- Sin ventana identificable se omite OCR; un evento reciente de Uber permite identificar
  una ventana activa con root nulo por su id durante 2,5 segundos.
- El reintento es acotado; no hay captura continua en ausencia de eventos.
- Se exige recogida y recorrido para Uber: tarjetas con un único par se consideran incompletas.
- Tests de texto no prueban captura, calidad OCR ni overlay en dispositivo.
- Logs de debug pueden contener direcciones: revisar antes de compartir.

Referencias técnicas:
https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
https://developers.google.com/ml-kit/vision/text-recognition/v2/android
