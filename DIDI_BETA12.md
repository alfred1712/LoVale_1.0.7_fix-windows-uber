# Beta12 — DiDi OCR y colores independientes

VersionCode 17. Rama feature/1.0.8-uber-hybrid-detection. Sin merge.

Evidencia: log guardado de 13:05–13:06 muestra DiDi seleccionado y activo,
eventos recibidos y recorridos de 19–61 nodos, casi siempre sin texto.
El flujo anterior no invocaba OCR para DiDi y terminaba sin evaluar.
No hay evidencia de una oferta de DiDi completa en ese registro.

Cambios:
- TripAccessibilityService reutiliza el pipeline de screenshot/OCR para DiDi seleccionado,
  con throttle, una operación simultánea, validación de ventana y generación de sesión.
  Accesibilidad sigue siendo primera opción. Cabify conserva su vía previa.
  Logs ahora identifican plataforma. Se pasan mínimos y pickup excedido al overlay.
- TripOverlayService: fondo oscuro, colores independientes por tarifa, zona/pickup
  excluidos en rojo; duración 9 s. Rates usan pickup + viaje.
- RateLevel y RateLevelTest: umbrales 100% verde, 85% amarillo, inferior rojo;
  mínimo desactivado o dato ausente neutro.
- app/build.gradle.kts: beta12/code17.

Build y tests correctos; clean y assembleDebug BUILD SUCCESSFUL.
Pendiente prueba real DiDi, además de comprobar el aspecto en dispositivo.
El OCR focalizado para DiDi se agrega por el pedido explícito de reparar su detección,
actualizando el alcance original limitado a Uber. No MediaProjection ni botón manual.
