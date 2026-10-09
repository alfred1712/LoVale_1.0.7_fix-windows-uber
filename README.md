# LoVale 1.0.9

Aplicación Android para evaluar ofertas de Uber, DiDi y Cabify y mostrar tarifas por kilómetro y hora en una ventana flotante. Los cálculos incluyen recogida más viaje.

Primera entrega estable para pruebas privadas, código de versión 55. No es una publicación en Play Store ni una integración oficialmente autorizada por las plataformas.

## Funciones

- Lectura por Accesibilidad con OCR localizado como respaldo donde Android lo permite.
- Una plataforma seleccionada por vez; no acepta ni rechaza viajes automáticamente.
- Indicadores de rentabilidad por km/h, recogida máxima y zonas excluidas.
- Overlay compacto, arrastrable y temporal; tarjetas independientes en el centro de viajes DiDi.
- Historial de ofertas, estadísticas y exportación. No equivale a viajes realizados o ganancias cobradas.
- Costos estimados por oferta, contador GPS de jornada y llegada a casa.
- Preferencias, respaldo de filtros y acceso a Movida Ya mediante el navegador del dispositivo.
- Diagnóstico local acotado, sin subir registros automáticamente.

## Pruebas privadas

Instalar el APK entregado por el responsable del proyecto. Habilitar Accesibilidad y ventana flotante para LoVale; ubicación precisa es opcional para jornada GPS y combustible cercano. Seleccionar una plataforma y activar el monitoreo. La aplicación inicia pausada. Configurar y revisar resultados con el vehículo detenido.

Android mínimo 7 (API 24). El OCR mediante captura del servicio requiere Android 11 o posterior; las pruebas físicas de esta entrega se realizaron en Android 16. No se garantiza la lectura de interfaces nuevas o de todos los dispositivos.

## Limitaciones conocidas

- La fuente oficial de combustible redirige HTTPS a HTTP al cierre de esta entrega. Se bloquea esa conexión y se conserva la referencia anterior; actualizar el precio manualmente para los costos estimados.
- Los kilómetros GPS pueden ser parciales si se interrumpe la ubicación.
- La imagen del vehículo es ilustrativa y puede diferir en año o versión.
- El cumplimiento de los contratos de cada plataforma requiere revisión específica; no se certifica con las pruebas técnicas.

## Compilar y validar

Con JDK compatible, Android SDK y Gradle Wrapper:

```powershell
.\gradlew clean
.\gradlew assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug assembleRelease
```

El release se optimiza con R8 y queda sin firmar por defecto. La firma de producción no está incluida. No guardar claves, capturas privadas ni diagnósticos en Git. Las pruebas de imágenes reales requieren los assets locales excluidos del repositorio; el corpus textual anonimizado sí está incluido.

Ver [informe de validación](VALIDACION_1_0_9_RC3.md) y [notas de entrega privada](ENTREGA_1_0_9_PRIVADA.md).
