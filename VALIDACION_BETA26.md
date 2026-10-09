# Beta26 — jornada, paleta y precisión de ofertas

- MainScreen/MainViewModel: activar monitoreo prepara el contador si hay casa guardada, solicita ubicación precisa cuando falta y muestra el estado si no arranca. Conserva una jornada en curso; los kilómetros empiezan con la primera oferta válida. No exige entrar a Ganancias cada día.
- Color.kt/colors.xml: vuelve al fondo oscuro y acento cian originales, preservando la interfaz simplificada y texto de zona de 15 sp.
- TripEvaluator: rechaza metros decimales ambiguos (posible pérdida de k), más de dos tramos y asociaciones atravesando direcciones. Tolera A4 min. Excluye el neto del overlay de los candidatos de tarifa.
- TripAccessibilityService: logs con distancia y duración totales usadas en la división, número de tramos y unidad ambigua.
- Tests: captura real Priority 8174 y capturas Boost pasan ML Kit en el dispositivo (2 tests). Regresiones unitarias de las tres plataformas, metros, decimales, captura contaminada por el overlay y ofertas ambiguas.

La captura de Priority contiene $8174, recogida 1.5 km/4 min y viaje 8.9 km/21 min: total 10.4 km/25 min, $785.9615 por km y $19617.6 por hora. Los $5417/km observados son compatibles con leer 8.9 km como 8.9 m; sin logs del instante no se puede confirmar esa transcripción histórica. El OCR de la imagen adjunta reconoce km correctamente en la prueba actual. El neto anterior tampoco era fiable, al depender de esa distancia.

Pendiente: probar activación y recorrido GPS en una jornada real y observar nuevas ofertas en vivo. Ante unidades ambiguas se solicita otra lectura; puede mostrarse Lectura incompleta en lugar de rentabilidad. No se corrigen unidades inventando distancias ni se recalculan registros históricos sin evidencia.

Trabajo en feature/1.0.8-uber-hybrid-detection, sin merge a master. Versión 1.0.8-beta26, versionCode 31.
