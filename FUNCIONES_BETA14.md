# LoVale 1.0.8-beta14 · versionCode 19

Rama feature/1.0.8-uber-hybrid-detection. Sin merge.

## Implementación
- `SettingsRepository`: costos y perfil nocturno persistentes; cambio de plataforma pausa en la misma transacción. El perfil se resuelve al procesar cada oferta, usando horario local del dispositivo. El día conserva los filtros existentes; de noche se sustituyen mínimos/pickup y se suman zonas adicionales. Desactivado inicialmente.
- `OperatingCosts`, `TripEvaluator`, `OfferAnalysis`: costos por combustible, consumo, mantenimiento por km y otros por viaje. Distancia y tiempo incluyen recogida + pasajero. La base bruta sigue predeterminada; el usuario puede elegir evaluar en neto. El importe se considera pago al conductor, sin restar otra comisión implícita. Es una estimación según costos cargados.
- `OfferHistory`: últimas 200 ofertas deduplicadas, sin direcciones ni OCR, guardadas con métricas y configuración aplicada. Historial excluido del backup en nube. Incluye borrado con confirmación. Las ofertas no representan viajes aceptados ni ganancias realizadas.
- `DriverTools`, `MainViewModel`, `MainScreen`: controles de costos/perfiles, historial y prueba editable, usando el mismo parser/evaluador/clasificador. Pruebas marcadas PRUEBA, no guardadas ni activan el monitoreo. Destino opcional para probar exclusión.
- `TripAccessibilityService`, `TripOverlayService`: lectura incompleta no muestra tarifas calculadas como fiables. Aviso conservador ante señales de tarjeta parcial; OCR sigue como fallback. Throttle de avisos 15 s y protección mientras se muestra una oferta completa. Una sola dirección ya no se duplica para afirmar que se conocen ambos extremos.
- `OfficialZones`, `ForbiddenZonesScreen`: cinco barrios de CABA con mayor suma de robos registrados (tipo Robo, año 2025), agregables voluntariamente. Retirada la lista anterior sin criterio estadístico. Pantallas desplazables.

## Datos oficiales
Fuente: GCBA, Dirección General Estadística Criminal y Mapa del Delito, dataset Delitos, licencia CC BY.
https://data.buenosaires.gob.ar/dataset/delitos

Recurso 2025 CSV:
https://data.buenosaires.gob.ar/dataset/delitos/resource/66c0f0f6-e682-403d-abbe-47227d1966fe/download

Consultado el 11/09/2026. Reproducción: `./verify-official-zones.ps1` sobre el CSV descargado.
Filtro anio=2025 y tipo=Robo; suma de cantidad por barrio, orden descendente. Incluye subtipos Robo total y Robo automotor. No suma hurtos ni amenazas. Palermo 3879, Flores 3128, Balvanera 2923, Caballito 2381, Recoleta 2184.
Son cantidades absolutas registradas, sin ajuste por población/superficie/circulación. No constituyen un ranking oficial de peligrosidad, ni riesgo por viaje. No se extrapolan a PBA. El snapshot es estático y requiere actualización explícita con nueva publicación.

## Validación y prueba pendiente
33 tests unitarios aprobados: costos, neto negativo, horarios, perfil desactivado, lectura incompleta, umbral85 y formatos conocidos de las tres plataformas.
Validar en dispositivo los nuevos controles y al menos una oferta real después de instalar: activar LoVale, recibir oferta, revisar colores y entrada única en historial. Probar costos y modo nocturno antes de conducir. La prueba sintética valida parser/evaluador/overlay; no sustituye la prueba real de Accessibility/screenshot/OCR.

## Límites
Las zonas se detectan en el texto de direcciones, sin geocodificación de trayectos. La ausencia de coincidencia no certifica seguridad. Una captura sin ninguna señal reconocible no permite afirmar que existe una oferta parcial. Historial limitado a nuevas ofertas posteriores a la instalación.
