# Beta20: kilómetros de jornada

Versión 1.0.8-beta20, versionCode 25. Rama feature/1.0.8-uber-hybrid-detection, sin merge.

## Uso

En Ganancias, abrir «Configurar casa y contador». Estando en casa, tocar «Guardar casa aquí» y conceder ubicación precisa. El punto se obtiene del GPS; no se registra una dirección inventada. La casa y los totales quedan solo en este teléfono y se excluyen de backup/transferencia.

El contador se prepara al activar LoVale, una vez configurado. Un servicio de ubicación iniciado desde la app visible espera la primera oferta completa de Accessibility/OCR. Esa oferta inicia la jornada aunque se rechace; las ofertas de prueba no la inician. Se suman desplazamientos GPS, no las distancias ofrecidas, por lo que se incluyen tramos sin pasajero y vuelta a casa.

Pausar la detección no detiene una jornada en curso. Se cierra al permanecer cinco minutos detenido dentro de 100 m de casa, después de haberse alejado más de 300 m. Existe cierre manual desde Ganancias o la notificación silenciosa del contador. El contador no se reinicia solo después del cierre: se prepara de nuevo desde la app.

Una interrupción de proceso/GPS conserva los totales y marca el resultado parcial. La reanudación es explícita; no se unen puntos atravesando una pérdida prolongada de señal. No hay reconstrucción de ruta ni modificación del cálculo de ganancia neta de las ofertas.

## Archivos y decisiones

- JourneyDistance: distancia geográfica y filtros de precisión, antigüedad, ruido, velocidad y huecos de señal; HomeArrival reconoce salida y permanencia al volver.
- JourneyStore: totales, estado y casa locales; sin historial de coordenadas.
- JourneyLocationService: foreground service de ubicación, actualizaciones solicitadas cada 10 s, inicio por señal de oferta, cierre y liberación del listener.
- JourneyPanel y VehicleCostPanel: contador, permisos y configuración dentro de Ganancias.
- MainViewModel y TripAccessibilityService: preparan el contador y señalan la primera oferta válida sin iniciar servicios GPS desde OCR en segundo plano.
- Manifest y reglas de backup: permisos y tipo de servicio; exclusión de los datos de jornada.

Se usa ubicación «mientras se utiliza la app» con servicio foreground iniciado desde una pantalla visible, conforme a las restricciones de Android: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

## Validación y límites

clean y assembleDebug correctos. 57 tests unitarios aprobados, incluyendo movimiento, ruido estacionario, GPS viejo/impreciso, salto imposible, pérdida de señal, salida de casa, permanencia, paso sin detenerse y reanudación. NavigationTest y CompactNavigationTest aprobados en el Motorola Android 16, luego de retirar la superposición de Google Lens. Beta20 instalada mediante install -r y versión verificada; app abierta y pausada. Estos tests de interfaz no validan un recorrido GPS real.

La distancia es estimada por GPS; curvas entre muestras, mala cobertura y restricciones de batería pueden subestimar el recorrido. El cierre requiere señal precisa durante la permanencia. Si no hay señal dentro de casa, usar Finalizar jornada. Pendiente una jornada real para contrastar los km con el odómetro y confirmar el cierre automático en ese teléfono.

## Evaluación de próximos cambios solicitados

Ganancias automáticas: actualmente OfferHistory suma solo completedAt confirmado manualmente. Para automatizar hay que asociar oferta aceptada, viaje en curso y comprobante de finalización por plataforma; registrar el cierre una sola vez y usar el importe final cuando pueda leerse. Un rechazo, cancelación, pantalla de búsqueda, «Fin del reto» o balance acumulado no son prueba de viaje realizado. Conservar confirmación manual cuando falte evidencia. Se necesitan capturas/textos reales de aceptación y finalización para definir y probar las reglas. El neto seguiría siendo estimado por sus costos.

Uber + DiDi en pantalla dividida: viable en principio con ventanas visibles, pero aún no habilitado. El código filtra selectedApp y mantiene una sola ventana/cooldown/contexto OCR. Un modo múltiple necesita selección explícita de apps, contexto y deduplicación por paquete/ventana, OCR alternado sobre cada región, y alertas identificadas por plataforma que no se sobrescriban. No permite leer una app completamente oculta. Referencia de las APIs: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService

En la captura de las 23:29, «Fin del reto», el balance $45.800 y los puntos de Uber no representan una oferta ni una ganancia individual completada. La captura confirma el formato visual dividido, no que ambas ventanas expongan texto o permitan screenshot en todos los dispositivos. Hace falta probar ofertas reales en ambas mitades.
