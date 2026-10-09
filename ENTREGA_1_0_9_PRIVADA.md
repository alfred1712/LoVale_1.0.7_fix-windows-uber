# LoVale 1.0.9 — entrega estable de uso privado

Fecha: 9 de octubre de 2026. Alcance confirmado por el usuario: su teléfono y el de un colega para pruebas privadas. Posteriormente autorizó merge a master y publicación del código en GitHub; el APK se entrega localmente, sin adjuntarlo como descarga pública.

## Versión

- `versionName`: `1.0.9`; `versionCode`: `55`.
- Rama de desarrollo: `codex/1.0.9`. Merge a `master` y versión del código en GitHub autorizados por el usuario.
- APK optimizado con R8; firma local existente para actualizar conservando datos.
- Respecto de rc3: solo versión y tres referencias de texto a la pestaña Jornada, anteriormente llamada Ganancias.
- No utilizar esta firma de pruebas para una publicación comercial. La firma definitiva y la revisión contractual continúan pendientes para distribución.

## Alcance validado

119 tests unitarios y 21 tests instrumentados en el dispositivo sobre la candidata previa. Compilación debug/release, lint sin errores. Pruebas reales del APK optimizado con Uber, incluida comparación visual autorizada de una oferta Priority: $6.388 / (0,7 + 5,3 km) = $1.064,67/km; $6.388 × 60 / (4 + 27 min) = $12.363,87/h. El overlay muestra $1.065/km y $12.364/h; el precio unitario coincide con Uber. El adicional de Priority no reemplaza la tarifa principal.

La evidencia detallada y los límites de cada prueba están en `VALIDACION_1_0_9_RC3.md`. Esta entrega no promete lectura perfecta de todas las interfaces futuras ni convierte ofertas en viajes completados.

Build limpio final de código 55: `clean assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug assembleRelease`, resultado `BUILD SUCCESSFUL`. APK final instalado mediante actualización: Android confirma `versionName=1.0.9`, `versionCode=55`, y la actividad arranca correctamente. Tamaño del APK firmado local: 45.293.029 bytes; firma v2/v3 verificada. La copia para pruebas privadas está en `release-artifacts/LoVale-1.0.9-private.apk`, excluida de Git, junto con su SHA-256.

## Cambios de cierre

- Reparado el arranque de release y la inicialización de ML Kit/WorkManager mediante reglas para constructores utilizados por reflexión.
- Diagnóstico automático de lectura, persistencia y presentación del overlay; recuperación del lote de contadores ante error de escritura.
- Historial con composición diferida para jornadas extensas.
- Conservación del modo manual al editar combustible.
- Pruebas de regresión con el formato Uber recibido hoy, anonimizado.
- Capturas, métricas privadas y archivos adjuntos excluidos de Git.

## Limitaciones conocidas

- Combustible automático: fuente oficial redirige HTTPS a HTTP. LoVale bloquea la conexión y conserva el precio anterior, mostrando advertencia. Ajustar manualmente el precio para que el neto estimado tenga una referencia actual. Las tarifas brutas por km/h no dependen del precio del combustible.
- GPS depende de permisos, precisión y continuidad de ubicación; se informa total parcial cuando hay interrupciones. No se repitió una jornada física completa hoy.
- Solo se monitorea la plataforma seleccionada. Cambiar de plataforma pausa el monitoreo.
- Historial y estadísticas representan ofertas analizadas; el neto de cada oferta es una estimación, no ganancia cobrada.
- Registros automáticos locales: siete archivos diarios acotados, sin imágenes ni direcciones; un cierre abrupto puede perder el último minuto de contadores.

## Instalación y conservación

Actualizar con `adb install -r`, sin desinstalar ni borrar datos. Abrir LoVale y comprobar el estado del lector; Android puede requerir reactivar Accesibilidad después de una actualización o un cierre del servicio. El inicio queda pausado por diseño. Conservar la clave local para futuras actualizaciones privadas, sin agregarla a Git.
