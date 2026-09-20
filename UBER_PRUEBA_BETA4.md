# Prueba real y corrección beta4 — 2026-09-11

Dispositivo: Motorola Edge 40 Pro, SDK 36. Accesibilidad capabilities=129 y overlay allow.

Evidencia beta3 observada por ADB:
- 12:11:11.278 screenshot OK windowMode=true image=1080x2400.
- 12:11:11.278 MLKIT REQUEST.
- Durante el intervalo: ráfagas de A11Y sin texto y OCR diferido busy=true.
- 12:11:34.669 MLKIT OK blocks=15 chars=197 (23,391 s después).
- El texto correspondía a la pantalla anterior, “Estás desconectado”.
- No se observaron EVAL ni OVERLAY en el fragmento disponible.
- El buffer de logcat rota: no se dispone de un registro completo de toda la sesión.

Conclusión: captura y entrada a ML Kit funcionan. Se observó una demora que impidió renovar
la imagen mientras llegaban eventos. La causa exacta de esa demora (inicio del motor,
trabajo de OCR o cola del hilo principal) requiere los nuevos tiempos instrumentados.

Cambios beta4/code9:
- TripAccessibilityService agrupa eventos Uber antes de leer el árbol y espera 500 ms
  después de finalizar una lectura antes de iniciar la siguiente.
- Recorrido Uber acotado a 100 ms entre consultas, 180 nodos, profundidad 30.
  Una consulta IPC individual puede exceder ese presupuesto.
- Log de duración y límite de lectura; tiempos de finalización ML Kit en executor separado
  y llegada del callback a Main.
- Resultado OCR con más de 5 s se descarta y programa nueva captura.
- No inicia dos OCR en paralelo ni libera una imagen aún usada por ML Kit.
- Cabify y DiDi conservan su recorrido previo sin estos límites.
- app/build.gradle.kts incrementa versión a 1.0.8-beta4, versionCode 9.

Validación: clean y assembleDebug BUILD SUCCESSFUL; tests unitarios sin errores.
Pendiente repetir oferta real en beta4, confirmar tiempos y alerta.
Riesgo: el recorrido acotado puede ser parcial y depender más del OCR.
No se modificó parser/evaluador en esta corrección. No se hizo merge.
