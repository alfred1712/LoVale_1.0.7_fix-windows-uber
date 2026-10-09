# Cierre técnico de LoVale — 9 de octubre de 2026

## Identidad y alcance

- Rama de trabajo: `codex/1.0.9`; sin merge a `master`.
- Candidata: `1.0.9-rc3`, código 54.
- Una plataforma seleccionada por vez. No se anuncia lectura simultánea.
- Historial de ofertas y costos estimados por oferta; no se presentan como ingresos cobrados ni viajes realizados.
- El APK release local usa la clave de pruebas existente para conservar la instalación. No es una firma de distribución pública.

## Correcciones de esta revisión

- `ReadingMetrics`, `ReadingHealthLog`: registro de overlays visibles/errores/cierres y escritura efectiva de historial; recuperación de contadores si falla la escritura. Los máximos de latencia se combinan como máximos, no como sumas.
- `TripAccessibilityService`: diferencia oferta procesada de oferta efectivamente persistida.
- `TripOverlayService`: no declara visible una ventana cuya creación falló. La comprobación usa el resultado de `addView`, no `isAttachedToWindow`, que aún puede ser falso al llegar `onStartCommand`. Diagnóstico por tarjeta de la central y conservación del tipo de ventana compatible con Android 7.
- `DriverTools`: historial con `LazyColumn`; no compone miles de registros al abrirlo.
- `FuelPriceControls`: editar una configuración manual conserva desactivada la actualización automática.
- `persistence.keep`: preserva constructores invocados por reflexión de Room, ML Kit/Firebase, Workers y transporte. La prueba del release anterior reprodujo un cierre por `WorkDatabase_Impl.<init>` y pérdida de registradores de ML Kit; el release corregido abrió normalmente.
- `.gitignore`: excluye adjuntos privados de la conversación.
- Tests: recuperación de métricas, privacidad/exportación real, modo manual de combustible y texto actual del historial.

## Evidencia obtenida

- 119 pruebas unitarias, cero fallos/errores.
- 21 pruebas instrumentadas correctas en Motorola Edge 40 Pro / Android 16 sobre rc2: navegación, cuatro tamaños/escalas, preferencias, polígonos, widget, arrastre/cierre, actualización de zona, tres indicadores DiDi, persistencia de casa, bloqueo HTTP, combustible manual y exportación de métricas.
- OCR ML Kit con nueve capturas locales: dos Boost, Priority y seis capturas de septiembre, incluida tarifa Uber por km y centro DiDi. Se verifican precio y distancia total, con tres tarjetas en el centro DiDi. Las imágenes privadas no se incluyen en Git.
- rc3 agrega únicamente las reglas release y versionado sobre ese conjunto validado. Arranque frío del APK optimizado corregido: 300 ms reportados por Activity Manager en una ejecución; no es un benchmark general.
- Interfaz release: inicio pausado, mínimos y plataforma conservados, preferencias y vehículo/precio existentes accesibles.
- Tras reactivar el permiso desde Ajustes, Android informa LoVale en `Bound services`, sin servicios caídos. Se instancia el lector ML Kit en el release corregido.
- Verificación visual autorizada de una tercera oferta en vivo: Uber Priority $6.388, recogida 4 min/0,7 km y viaje 27 min/5,3 km. La captura muestra LoVale con $1.065/km y $12.364/h, coincidentes con 6 km/31 min; Uber también muestra $1.065/km. El adicional $766 y el valor unitario no sustituyeron la tarifa principal. Caso textual anonimizado incorporado al corpus de regresión; captura conservada solo en `diagnostics/` ignorado por Git.
- Se recuperaron 2.359 registros; 140 desde el 6 de octubre: 112 Uber y 28 DiDi. Ninguna discrepancia aritmética entre precio, distancias/tiempos guardados y tarifas calculadas. Esto no demuestra por sí solo exactitud del OCR ni ausencia de ofertas omitidas.
- Métricas previas: Uber 198 parseos OCR completos/40 incompletos; DiDi 142/2. Incluyen relecturas. No son porcentajes de ofertas perdidas. Capturas correctas: Uber 2.922 y DiDi 230; 8 errores de captura en total.
- Lint: cero errores; avisos de versiones, presentación y patrones de Android. La referencia estática del overlay se libera en `onDestroy`; el layout flotante recibe sus parámetros de WindowManager.

## Límites del cierre

1. La fuente de combustible respondió hoy HTTP 301 desde HTTPS hacia HTTP. La app bloquea el downgrade y conserva la referencia anterior con advertencia; el ajuste manual sigue disponible. No presentar esa consulta automática como funcional hoy.
2. No hay firma de producción configurada. La firma local sirve para validación, no para publicar el producto.
3. El registro automático conserva siete archivos diarios de hasta 512 KiB cada uno, sin texto OCR, imágenes, importes ni direcciones. Un cierre abrupto puede perder el último minuto en RAM. El límite diario se registra; no se interpreta ausencia de eventos como ausencia de ofertas.
4. GPS: pruebas de lógica y persistencia; no se reprodujo hoy una jornada completa ni una llegada física a casa. Los tramos sin posición suficiente se declaran parciales.
5. Prueba en vivo del APK release rc3: Uber $4.087, recogida 0,1 km/1 min y viaje 4 km/23 min; cálculo 996,83/km y 10.217,50/h, overlay visible. Otra oferta de $4.864 con 6 km/30 min produjo 810,67/km y 9.728/h; cierre por timeout de 9 s registrado. `takeScreenshotOfWindow` entregó imágenes 1080×2400 a ML Kit. El precio por km de Uber fue excluido como importe total. Hubo relecturas parciales alternadas con completas; no se interpretan como ofertas perdidas. El usuario no confirmó visualmente los datos de origen de la primera oferta, por lo que los logs prueban el flujo, no exactitud visual absoluta.
6. La revisión contractual pendiente está documentada en `REVISION_TERMINOS_PLATAFORMAS.md`. Esta validación técnica no certifica autorización de Uber, DiDi o Cabify ni cumplimiento contractual.

## Pruebas ejecutadas

```powershell
.\gradlew clean assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug assembleRelease
```

También se ejecutó el comando limpio completo sobre rc3: `BUILD SUCCESSFUL`. No se reutiliza la instrumentación debug para certificar release: sus referencias a clases optimizadas no son compatibles; el arranque release se verificó independientemente mediante Activity Manager, interfaz real y ofertas Uber en vivo.

No declarar estabilidad pública únicamente por compilar. El flujo técnico principal pasó la prueba en vivo; mantener esta entrega como candidata de distribución hasta resolver firma de producción y la exigencia contractual del proyecto. La actualización de combustible continúa limitada a la referencia manual mientras la fuente no permita HTTPS.
