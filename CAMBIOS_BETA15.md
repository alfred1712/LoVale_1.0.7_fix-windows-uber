# LoVale 1.0.8-beta15 (versionCode 20)

- Eliminados costos, ganancia neta y perfiles día/noche de `DriverTools`, `SettingsRepository`, `MainViewModel`, `OfferAnalysis`, `TripEvaluator` y del flujo Accessibility/overlay. Las preferencias antiguas ya no se leen ni afectan la evaluación. Se conservan los filtros principales, historial y modo de prueba.
- El cálculo usa el precio completo dividido por distancia/tiempo de recogida + viaje. Continúa el umbral del 85% por criterio.
- `OfferHistory` conserva registros anteriores; los que fueron calculados con costos se identifican como registros de beta14 para evitar confundirlos con tarifas actuales.
- `RideAppIcon`, `MainScreen` y el manifiesto usan los iconos reales de las apps instaladas mediante PackageManager y visibilidad limitada a los tres paquetes. Conservan nombre accesible, estado seleccionado y pausa al cambiar plataforma. Si una app no está instalada se muestra su nombre como respaldo.
- Tests del evaluador actualizados al cálculo sin costos. No se modifica master ni se hace merge.

La apariencia de los iconos depende de la versión instalada de cada app. Pendiente comprobación visual en dispositivo; ADB no detectó teléfono conectado durante esta entrega.
