# Beta5 — prueba real y overlay temporal

Beta4 confirmó flujo real Uber: screenshot 12:18:12.316, OCR 223 ms,
precio 8415, pickup 0.8 km, recorrido 6.9 km/33 min; OVERLAY visible RED
12:18:12.575. Con mínimos 1000/km y 20000/h, 15300/h justifica RED.
La primera captura había confundido +ARS410 del mapa con el precio.

Cambios beta5 / versionCode 10:
- TripEvaluator excluye importes precedidos por +, correspondientes a adicionales.
- UberOcrTest agrega dos pruebas de regresión del precio y rendimiento horario.
- TripOverlayService cierra a los 4 segundos; mismo ID no reinicia temporizador.
- TripAccessibilityService deduplica con datos numéricos y motivo, sin direcciones OCR
  inestables; suprime repetidos durante 30 segundos desde la última lectura.
- app/build.gradle.kts incrementa versión.

Validación: 20 tests sin errores, clean y assembleDebug BUILD SUCCESSFUL.
Instalación beta5 en Motorola por ADB. Pendiente confirmar cierre en próxima oferta.

Límites: cierre por tiempo, no sincronizado exactamente con desaparición de tarjeta.
Dos ofertas distintas con iguales importes, distancias, duración y motivo en menos de
30 segundos pueden considerarse duplicadas. Un adicional que OCR lea sin + aún requiere
mejor aislamiento espacial de tarjeta. No merge a master.
