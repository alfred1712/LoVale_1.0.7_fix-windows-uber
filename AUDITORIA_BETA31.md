# Auditoría y diagnóstico — 1.0.8-beta31 (versionCode 36)

Actualización posterior: se recuperaron las mediciones, se completó el build limpio y pasaron las pruebas visuales. Ver `AUDITORIA_BETA32.md` para la entrega vigente, los resultados y sus límites. Los pendientes descritos abajo corresponden al corte de beta31.

Fecha: 14/09/2026. Rama: `feature/1.0.8-uber-hybrid-detection`. Sin merge a master.

## Resultado y alcance

Se revisaron los archivos Kotlin de producción, el manifiesto, configuración de Accessibility, red y backups, y las dependencias efectivas de Gradle. Es una revisión estática con pruebas específicas en un Motorola Edge 40 Pro / Android 16, no una certificación de seguridad ni un pentest de las dependencias o del sistema operativo.

La versión compila y está instalada para pruebas. **Todavía no se puede afirmar que su consumo durante una jornada sea bajo ni que no pierda ofertas.** Tampoco está resuelto el transporte HTTP de la fuente oficial de combustible. Estos puntos impiden dar una recomendación de lanzamiento general basada solamente en este trabajo.

## Cambios realizados

| Archivos | Cambio / motivo |
|---|---|
| `diagnostics/DiagnosticRecorder.kt`, `ui/DiagnosticPanel.kt`, `ui/VehicleCostPanel.kt` | Diagnóstico opcional en Ganancias. Registro privado, exportación ZIP elegida por el usuario y borrado. Sin servidor de envío. |
| `services/TripAccessibilityService.kt` | Etapas A11Y, captura, OCR, detector, parser, evaluación, deduplicación y solicitud del overlay. Referencia visual opcional cada 5 s mediante captura de ventana, Android 14+. Reciclado de raíces usadas al filtrar ventanas, comprobación estricta del paquete del árbol activo y presupuesto de recorrido también para Cabify. OCR suspendido con pantalla bloqueada/apagada. |
| `services/TripNotificationListenerService.kt` | Eliminado el evaluador antiguo de notificaciones que usaba tarifas fijas, coincidencias parciales de paquetes y una lista de deduplicación sin límite. Solo permanece el cierre de un overlay coincidente tras descarte explícito de su notificación, respetando app y estado activo. |
| `data/settings/SettingsRepository.kt` | El estado activo vive en memoria y no se recupera como activo tras reiniciar el proceso. Pausa/cambio de plataforma detienen el diagnóstico. |
| `services/JourneyLocationService.kt`, `services/DriverLocation.kt`, `ui/JourneyPanel.kt` | El GPS de jornada empieza con la primera oferta válida; una jornada reanudada lo inicia inmediatamente. Permisos comprobados en los puntos de solicitud. Combustible reutiliza una posición reciente y precisa si existe. |
| `data/settings/JourneyStore.kt` | No reescribir preferencias cuando el estado no cambió. |
| `services/TripOverlayService.kt` | Registro de overlay visible, cierre por toque/timeout/descarte y error al agregar la ventana. |
| `domain/TripEvaluator.kt`, `FiniteTripTest.kt` | Rechazo de entradas excesivas y números no finitos/desbordamientos; no aprobar métricas inválidas. Se preservan los cálculos con recogida + viaje. |
| `app/build.gradle.kts`, `data/network/NearbyFuelSource.kt` | Gson explícito 2.13.2 sustituye el 2.8.5 transitivo; uso de `JsonParser.parseString`. Incremento de versión. |
| `domain/ZoneEvaluator.kt` | Propagación de cancelación en el componente antiguo, actualmente sin llamadas desde el flujo de ofertas. |
| `res/xml/data_extraction_rules.xml` | Historial excluido también de transferencia entre dispositivos; casa y contador ya estaban excluidos. |

El texto completo leído ya no se escribe en Logcat de depuración: solo se conserva en el registro privado cuando se elige incluir imágenes/texto. Los logs técnicos habituales conservan información operativa y numérica; conviene seguir revisando su necesidad antes de publicar.

## Cómo usar el diagnóstico

Con el vehículo detenido:

1. Elegir la plataforma en Inicio.
2. Ganancias → Diagnóstico de prueba → Preparar registro.
3. Para poder revisar posibles ofertas omitidas, elegir **Incluir imágenes y texto**. Está desmarcado por defecto y disponible desde Android 14.
4. Activar el monitoreo desde Inicio y usar la app de viajes normalmente.
5. Al terminar, pausar o finalizar el registro y exportar el ZIP desde Ganancias.

Una prueba reemplaza la anterior. El almacenamiento está en `noBackupFilesDir/diagnostic-session`, fuera de los backups. Límite: una hora, aproximadamente 50 MiB como presupuesto total, 20.000 eventos, 32 registros pendientes y una imagen pendiente de compresión. Los descartes por saturación quedan contados. El registro vencido se elimina al comprobarlo durante inicialización/exportación después de 24 horas; no se promete borrado a una hora exacta con el proceso detenido. El ZIP exportado queda bajo control del usuario y no se borra con el registro interno.

Las capturas solo solicitan la ventana verificable de la plataforma elegida; no hay fallback a captura del display para **el diagnóstico visual**. Pueden contener mapa, direcciones y otros datos visibles de esa plataforma. No se guardan coordenadas GPS en las métricas. Compresión/escritura se ejecutan fuera del hilo principal. El diagnóstico apagado no añade un bucle periódico de captura.

La muestra visual es independiente del detector, pero puede perder ofertas que aparecen entre capturas, durante OCR ocupado o con una ventana no verificable. Permite revisar evidencia después de conducir; **no constituye un contador exacto de todas las ofertas recibidas**. Dos lecturas parecidas tampoco prueban por sí solas que el importe sea correcto. El análisis de exactitud requiere contrastar imágenes legibles con las cifras extraídas.

## Seguridad: hallazgos y límites restantes

- Los servicios de overlay, monitoreo y ubicación no están exportados. Accessibility y el listener de notificaciones requieren los permisos de enlace reservados al sistema. No se encontraron claves API incorporadas en el código revisado ni un WebView con ejecución de JavaScript.
- La consulta de combustible sigue usando HTTP en el dominio oficial permitido explícitamente; el resto del tráfico en claro está bloqueado por configuración. El 14/09/2026 se comprobó un `301` desde HTTPS hacia HTTP en `datos.energia.gob.ar/api/3/action/datastore_search`. No se envían coordenadas ni credenciales, pero un intermediario podría alterar precios: **riesgo de integridad pendiente**. No se eliminó la actualización automática sin disponer de una alternativa equivalente y verificada.
- El OCR de ML Kit procesa imágenes en el teléfono, pero el SDK puede enviar métricas de rendimiento/uso y consultar servidores. No es correcto afirmar que toda la app genera cero tráfico al reconocer texto. Fuente: [ML Kit: privacidad y tratamiento de datos](https://developers.google.com/ml-kit/terms).
- Se inspeccionó el árbol de dependencias y se actualizó Gson. No se efectuó un análisis exhaustivo de vulnerabilidades de todos los componentes transitivos. La existencia de versiones más recientes no prueba una vulnerabilidad ni justifica actualizar todas las librerías sin regresión.
- Una instalación debug facilita inspección por herramientas de desarrollo autorizadas. No debe confundirse esta APK de pruebas con una distribución de producción firmada y revisada.
- El parser es heurístico y el neto depende de supuestos de combustible/desgaste. Lectura completa no equivale a validación independiente del precio ni a confirmación de viaje realizado.

## Consumo: lo que puede y no puede concluirse

| Recurso | Evidencia / presupuesto |
|---|---|
| OCR | Un reconocimiento simultáneo; cooldown de 1 s. Se conserva para no introducir retrasos adicionales en las ofertas. Puede seguir siendo el principal costo de CPU si la app genera eventos frecuentes. |
| Accessibility | Eventos agrupados antes de recorrer árboles; límite de 180 nodos, profundidad 30 y 100 ms de recorrido por lectura. |
| GPS | Solicitud cada 10 s durante la jornada; sin solicitudes continuas mientras espera la primera oferta. Se detiene al finalizar. El tiempo hasta el primer fix puede dejar sin contar un tramo inicial; no se inventa distancia. |
| Combustible | Trabajo programado cada 12 h, condicionado por conexión y posición reciente; también hay consultas manuales. Cada respuesta tiene límite de cuerpo de 4 MiB. Dos respuestas al límite representarían 8 MiB/día de cuerpo, pero **no es una medición de datos móviles ni un límite diario de tráfico**, porque existen consultas manuales, cabeceras y SDK. |
| Diagnóstico visual | Como máximo un intento cada 5 s durante una hora, sin OCR adicional. Agrega captura, copia de bitmap, JPEG y escritura; por eso es opcional. |
| Red medida | El ZIP incluye diferencias de bytes recibidos/enviados por el UID, sumando interfaces. Un contador no disponible se trata como desconocido, no cero. Fuente: [TrafficStats](https://developer.android.com/reference/android/net/TrafficStats). |
| CPU/memoria medidas | El ZIP incluye tiempo de CPU del proceso, duración y PSS al finalizar. No representan porcentaje de batería por hora. |
| Batería | No se obtuvo una medición atribuible a LoVale durante una jornada. Brillo, señal, navegación, temperatura y carga afectan la batería del teléfono. Se requiere una sesión controlada o perfil energético compatible. Fuente: [Power Profiler](https://developer.android.com/studio/profile/power-profiler). |

Se ejecutó en el teléfono un microbenchmark de 40 s: 10 s de reposo y tres bloques de 10 lecturas a una por segundo, sin registro / registro técnico / registro visual. Se comprobó en cada lectura la oferta de $8.174 y sus 10,4 km totales. **No incluye el costo de la API de screenshot ni un recorrido GPS real**; usa la imagen de prueba y tiene sobrecarga de instrumentación. Terminó correctamente. Los resultados numéricos quedaron en el teléfono, `Android/data/com.example.lovale2/files/beta31-consumption.json`, pendientes de recuperar; no se presentan cifras sin haberlas leído.

## Validación realizada y pendiente

- Builds locales después de los cambios: `assembleDebug`, `assembleDebugAndroidTest`, `BUILD SUCCESSFUL`.
- **79 tests unitarios**, cero fallos y cero errores, incluyendo Uber, Cabify, DiDi, costos y GPS.
- `lintDebug`: `BUILD SUCCESSFUL`, **0 errores y 55 advertencias**. Predominan versiones disponibles, textos no extraídos a recursos, recursos antiguos sin uso y sugerencias KTX. La referencia estática del overlay se limpia en `onDestroy`; aún conviene medir ciclos repetidos para descartar retención anómala. La inflación sin raíz usa parámetros explícitos de WindowManager.
- Teléfono: **8 tests correctos** de privacidad/límites/exportación/pausa y OCR de las capturas Priority y Boost; además, **1 microbenchmark correcto**.
- APK beta31 y APK de tests instaladas con `install -r`, conservando datos de usuario.
- Pendientes: recuperar las métricas, comprobar la navegación/dialogo visual, ejecutar `gradlew clean` seguido de `gradlew assembleDebug` como cierre final y realizar una sesión real de ofertas con el diagnóstico preparado por el usuario.

La revisión automática de permisos bloqueó el comando que recuperaba métricas e iniciaba las pruebas de navegación por agotamiento del límite de uso de Codex. Es un bloqueo de las herramientas de esta sesión; no un fallo de compilación ni de las pruebas ya terminadas. No se intentó sortearlo.
