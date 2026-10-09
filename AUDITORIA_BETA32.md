# Beta32 — estado de monitoreo, jornada y confirmación en prueba

Entrega: `1.0.8-beta32`, versionCode `37`. Rama `feature/1.0.8-uber-hybrid-detection`. Sin merge a master.

## Qué quedó implementado

- **Estado de lectura:** Inicio y la notificación persistente distinguen espera, recepción de actividad, permiso de lectura perdido, permiso de overlay perdido y fallos reiterados/bloqueo del OCR. Se comprueba cada 5 segundos mientras el monitoreo está activo, sin lanzar capturas adicionales. Una pantalla sin ofertas no se interpreta como un fallo. El servicio de monitoreo dejó de reiniciarse automáticamente como activo tras ser eliminado.
- **Resumen automático:** Ganancias conserva las ofertas leídas, lecturas incompletas y viajes confirmados de la última sesión, además del neto estimado. La sesión empieza al activar LoVale y termina al pausarlo o cerrar la jornada. Los km GPS siguen mostrándose por separado: no se sustituyen por la suma de distancias de ofertas. Una sesión interrumpida no inventa una hora de cierre o actividad durante el tiempo sin proceso.
- **Confirmación automática experimental:** disponible en Ganancias → Detalles → “Confirmación automática · en prueba”. Desactivada inicialmente. Solo confirma una oferta guardada cuando observó una aceptación inequívoca, viaje en curso, acción de finalizar y dos lecturas del total final coincidente. Conserva importe/costos estimados de la oferta, no calcula una liquidación bancaria. El historial indica la confirmación automática y permite deshacerla.
- **Diagnóstico:** sigue siendo opcional y exportable. El diálogo se desplaza en pantallas pequeñas, cada sesión tiene un identificador propio, los números inválidos no rompen su JSON y se vinculan los IDs del historial con las evaluaciones.

## Límites de la confirmación automática

Se implementó un seguimiento conservador en memoria de una sola plataforma seleccionada. No se suman ofertas por desaparecer, por un timeout del overlay ni por volver al mapa. Las cancelaciones reconocidas se descartan; los viajes aceptados sin evidencia final quedan para revisar. La confirmación de un mismo registro es idempotente y no cambia registros ya confirmados.

La aceptación debe corresponder a una única oferta reciente (30 segundos). El seguimiento vence tras seis horas; el final debe aparecer dentro de los dos minutos posteriores a la acción de finalizar. Las dos lecturas deben separarse al menos 750 ms y coincidir en un importe identificado como total, importe del viaje o tarifa final. Un importe diferente queda para revisar, incluso si la diferencia es pequeña. No se reemplaza la tarifa con un bono, saldo diario o propina.

**Estas señales aún necesitan validación con finales reales de cada app.** Los tests usan secuencias explícitas y no prueban que todas las versiones de Uber, DiDi o Cabify expongan esos textos/clics a Accessibility. Si el botón se dibuja sin un evento legible, faltan estados intermedios, hay ofertas simultáneas ambiguas o Android interrumpe el proceso, la app puede no confirmar automáticamente. El usuario no debe confiar todavía en esa opción como contabilidad completa de su jornada.

## Consumo recuperado de la prueba anterior

Motorola Edge 40 Pro, Android 16. Imagen de referencia de 720 × 1600. Diez segundos por bloque, diez lecturas a una por segundo en los bloques OCR. El modelo se calentó antes de empezar. Resultados recuperados del teléfono en `diagnostics/beta31-consumption.json`:

| Bloque | CPU del proceso / tiempo transcurrido | PSS al terminar | Mediana OCR | RX / TX del UID |
|---|---:|---:|---:|---:|
| Reposo después de cargar el modelo | 1,45% | 238,0 MiB | — | 0 / 0 bytes |
| OCR | 46,66% | 232,4 MiB | 242,5 ms | 0 / 0 bytes |
| OCR + registro técnico | 49,21% | 225,2 MiB | 224,5 ms | 0 / 0 bytes |
| OCR + registro visual | 42,65% | 225,5 MiB | 184,5 ms | 0 / 0 bytes |

Los tiempos OCR individuales fueron de 152 a 287 ms y se validaron $8.174 y 10,4 km en las treinta lecturas. CPU/tiempo es consumo equivalente de un núcleo, **no porcentaje de batería ni porcentaje de todos los núcleos del teléfono**. Incluye la instrumentación. El orden, calentamiento y duración breve impiden concluir que el modo visual consume menos que el normal. La prueba usó una imagen guardada y no incluye la API de screenshot, navegación de terceros ni recorrido GPS.

Otra instantánea de beta32, después de abrir la actividad tras los tests, registró PSS residente de 113.910 KiB y Swap PSS de 87.879 KiB. No es una medición de pico ni una comparación controlada con la tabla anterior. Evidencia: `diagnostics/beta32-meminfo.txt`.

**Conclusión sobre consumo:** se confirmó que el procesamiento OCR usa CPU apreciable bajo carga de una lectura por segundo. No hay evidencia suficiente para estimar batería por hora durante una jornada ni para afirmar que el consumo móvil diario sea cero. El diagnóstico exporta CPU, memoria y bytes del UID para una prueba real. El muestreo visual añade trabajo y continúa siendo opcional.

## Seguridad pendiente

Permanece el riesgo de integridad del precio de combustible por la conexión HTTP de la fuente oficial. La comprobación del 14/09/2026 mostró que su endpoint HTTPS redirige a HTTP. El dominio está limitado por la configuración de seguridad de red y no recibe la ubicación del conductor, pero eso no protege el contenido recibido frente a alteraciones. No se presenta como resuelto.

ML Kit procesa imágenes/texto en el teléfono, aunque puede transmitir métricas de uso/rendimiento; el resultado de cero bytes en una prueba corta no elimina esa posibilidad. Fuente: [privacidad de ML Kit](https://developers.google.com/ml-kit/terms). Las diferencias de bytes del UID abarcan interfaces, no distinguen exclusivamente datos móviles: [TrafficStats](https://developer.android.com/reference/android/net/TrafficStats).

Los nuevos contadores de sesión están excluidos de backup y transferencia. El registro sigue en almacenamiento privado excluido de backup y se comparte solo por exportación manual. La auditoría estática y los tests no constituyen una certificación de seguridad ni una revisión exhaustiva de todas las dependencias.

## Archivos y validación

Los cambios de esta entrega se concentran en `MonitoringHealth`, `LoValeForegroundService`, `CompletedTripTracker`, `TripAccessibilityService`, `OfferHistory`, `SessionSummaryStore`, `JourneyLocationService`, `SettingsRepository`, `DiagnosticRecorder`, las pantallas Inicio/Ganancias/Historial/Diagnóstico y las reglas de backup. Las pruebas nuevas cubren espera frente a fallo real, finalización inequívoca, cancelaciones, ambigüedad, tarifa final distinta, vencimientos, persistencia compatible e idempotencia.

- `gradlew clean`: **BUILD SUCCESSFUL**.
- `gradlew assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug`, desde el árbol limpio: **BUILD SUCCESSFUL**, 85 tareas ejecutadas.
- **90 tests unitarios**, sin fallos.
- **11 tests de dispositivo**, correctos: seis de diagnóstico, Priority, Boost y tres de navegación/diálogo/pantalla compacta.
- Lint: **0 errores, 56 advertencias**. Principalmente versiones disponibles, KTX, textos y recursos antiguos; no se ocultaron mediante un baseline.
- La prueba visual falló inicialmente porque Android había apagado/bloqueado la pantalla; al acceder a la actividad desbloqueada pasó. Las pruebas de navegación mantienen encendida su ventana mientras se ejecutan, sin cambiar el tiempo de bloqueo configurado del teléfono.
- APK limpio beta32 instalado con `install -r`, conservando los datos. `git diff --check` sin errores de whitespace.

## Próxima prueba en calle

Antes de conducir, elegir la app en Inicio y preparar un diagnóstico en Ganancias. Para revisar omisiones, habilitar imágenes/texto de la ventana seleccionada; el muestreo puede omitir ofertas breves y no representa un censo completo. Activar LoVale y usar la plataforma normalmente. Al finalizar, exportar el ZIP con el vehículo detenido. No hace falta contar errores u ofertas manualmente mientras se conduce.

Para validar la confirmación automática, interesa conservar la secuencia completa de una aceptación, el viaje en curso y su pantalla final; también una cancelación para comprobar que no se sume. El registro visual dura como máximo una hora y puede detenerse antes por límites de eventos/almacenamiento. No se han simulado viajes reales ni se ha activado la confirmación experimental en nombre del usuario.
