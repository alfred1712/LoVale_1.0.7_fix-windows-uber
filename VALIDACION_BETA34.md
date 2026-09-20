# Validación 1.0.8-beta34 (39) — 19/09/2026

Rama feature/1.0.8-uber-hybrid-detection. Sin merge a master. El árbol incluye trabajo previo; este documento describe los cambios de esta entrega.

## Casa y revisión de seguridad

- JourneyPanel, MainViewModel, JourneyStore y JourneyDistance: campo Dirección de casa persistente, editable sin cambiar coordenadas ni kilómetros. Es una referencia escrita por el conductor, no geocodificación automática. Longitud limitada a 160 caracteres, sin caracteres de control; coordenadas finitas y dentro de rango. La dirección y el punto GPS quedan excluidos de backup y transferencia.
- AndroidManifest y network_security_config: todas las conexiones sin cifrar bloqueadas. NearbyFuelSource exige HTTPS y rechaza redirecciones. FuelPriceWorker conserva el valor anterior y explica el fallo.
- Limitación observada: el endpoint oficial de combustible devuelve una redirección a HTTP. La actualización automática no podrá completarse mientras persista; queda ajuste manual. No se envían coordenadas del conductor en la consulta de combustible.
- Retirados TripNotificationListenerService, su declaración y acceso a notificaciones ajenas, el acceso auxiliar desde MainScreen y su callback de overlay.
- Eliminados GoogleMapsService, GeocodingResponse, ZoneEvaluator, FuelPriceSource antiguo, pruebas del proveedor eliminado, layout activity_main, iconos Android genéricos y helpers sin referencias. Eliminadas dependencias Retrofit/converter. Se conserva Gson para persistencia y Jsoup para precios.
- Release usa reducción R8 de código/recursos y reglas de conservación para modelos persistidos. No se elimina el OCR offline ni arquitecturas compatibles.
- Revisados componentes exportados del manifest fusionado de producción: launcher; Accessibility protegido por BIND_ACCESSIBILITY_SERVICE; componentes de bibliotecas protegidos por permisos del sistema. Sin actividades de preview de pruebas en release. Servicios propios de ubicación/overlay/monitoreo no exportados. PendingIntent inmutables.
- Permisos conservados: overlay, Internet/estado de red, ubicación aproximada/precisa, servicios foreground y notificaciones propias. WorkManager aporta WAKE_LOCK y RECEIVE_BOOT_COMPLETED para trabajo programado. Sin cámara, micrófono, contactos, almacenamiento amplio ni ubicación en segundo plano.
- Diagnóstico local optativo con límites de duración/espacio; no se habilita exportación automática. La revisión estática y las pruebas no equivalen a una auditoría de penetración ni garantizan ausencia total de vulnerabilidades.

## Correcciones de la prueba en calle y adaptación

- OfferText/TripEvaluator/OfferLayout/OfferOcrReader/TripAccessibilityService: orden espacial de líneas, selección del importe principal, normalización ARS8,5ll y Al min, separación de tarjetas y segunda lectura focalizada ante distancias incompatibles. No se inventa un decimal cuando la imagen no confirma el dato.
- Capturas reales: 8511 / 18,1 km = 470,22/km; 4916 / 6,6 = 744,85/km; 4000 / 7,5 = 533,33/km; 4400 / 4,825 = 911,92/km. Recogida y viaje incluidos. Centro DiDi: tres tarjetas completas separadas.
- TripOverlayService/OfferDismissal: arrastre con posición persistente, cierre por rechazo detectado, protección contra respuestas OCR tardías y badges del Centro fuera del botón Aceptar, sin interceptar toques.
- JourneyLocationService/HomeArrival: continuidad entre plataformas durante pausa de cinco minutos; llegada admite precisión interior moderada y exige evidencia reciente. No se presume llegada sin GPS.
- CompletedTripTracker/OfferHistory/EarningsScreen: asociación de aceptación a tarjeta y finalización por recibo explícito con controles de coherencia. Aceptar no equivale a cobrar. Falta validar finales reales de viaje de cada proveedor y llegada a casa en una jornada completa.
- LoValeNavigation/IconLabel/VehicleCostPanel: navegación lateral en ancho suficiente, contenido acotado y campos adaptables. Pruebas en cuatro tamaños/escalas de fuente.

## Validación

- gradlew clean: BUILD SUCCESSFUL.
- assembleDebug, testDebugUnitTest, assembleDebugAndroidTest, assembleRelease y lintDebug: BUILD SUCCESSFUL.
- 92 tests unitarios, cero fallos.
- Pruebas instrumentadas en Motorola Android 16: seis tests finales de casa/seguridad, navegación/evaluación, OCR de capturas y overlay; todos correctos. Antes, suite de 17 tests de diagnóstico, capturas, navegación y overlay correcta.
- Lint: sin errores; advertencias de dependencias, estilo y APIs. La referencia de servicio del overlay se libera en onDestroy; el tipo de ventana principal está condicionado por SDK. No se afirma que lint sustituya revisión de seguridad.
- APK debug 64.922.038 bytes; release optimizado 44.829.677 bytes. Son variantes distintas, no una comparación antes/después equivalente.
- La instrumentación debug no completó el smoke test sobre el APK release optimizado; se detuvo el intento y se restauró debug para pruebas. Release compilado, pero no validado integralmente en ejecución; no publicar todavía. La firma release local de verificación usa la clave debug solo con localReleaseTest=true.
- git diff --check sin errores de whitespace.

## Pendiente

Geocodificación de barrios sin nombre explícito: USIG ofrece una API viable para CABA, pero no está integrada. Una consulta de una dirección concreta fue rechazada por revisión automática de aprobación por transmisión externa no autorizada. No se repitió. Requiere consentimiento explícito para enviar direcciones a USIG; no debe confundirse con el campo local de casa.

Quedan pruebas reales de rechazo en ambas apps, finalización/ganancias y llegada a casa. Las capturas y tests pasan, pero no permiten prometer 100% de detección en toda combinación de Android/interfaz/GPS.
