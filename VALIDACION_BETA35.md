# Beta35 (versionCode 40): barrios por USIG

Autorización explícita del usuario para enviar direcciones de ofertas a USIG. No se envían GPS, capturas, importes ni textos completos de ofertas. No se consulta la dirección guardada de casa.

## Cambios

- `data/network/UsigNeighborhoods.kt`: consulta HTTPS a host fijo ws.usig.buenosaires.gob.ar/datos_utiles, sin redirecciones, timeout 1,8 s y respuesta máxima 16 KiB. Solo calle/altura de direcciones con municipio CABA explícito; rechaza cruces y formatos ambiguos. Exige barrio y comuna CABA válidos. Caché en memoria: 128 entradas, éxitos 24 h, fallos 60 s, una solicitud simultánea.
- `TripAccessibilityService.kt`: cálculo inmediato y resolución posterior de recogida/destino sin barrio; máximo ocho trabajos pendientes. Verifica sesión, configuración y ventana antes de aplicar resultados. Identifica rutas por hash para no confundir ofertas con iguales tarifas y distancias. No incluye direcciones en los IDs de diagnóstico. Las consultas en espera no comienzan después de pausar/cambiar sesión.
- `TripOverlayService.kt`: actualización de zona únicamente para la oferta visible correspondiente, incluyendo tarjetas del Centro DiDi; no reinicia el plazo de cierre. Una respuesta tardía no reabre overlays cerrados.
- `OfferHistory.kt`: actualiza clasificación de la oferta sin duplicarla ni alterar importes, kilómetros, aceptación o ganancias.
- `ForbiddenZonesScreen.kt`: explica proveedor y datos enviados. `app/build.gradle.kts`: versión incrementada.

## Validación

- clean y assembleDebug: BUILD SUCCESSFUL; testDebugUnitTest y assembleDebugAndroidTest correctos.
- 96 tests unitarios. Cuatro nuevos cubren minimización de datos, dirección ambigua/fuera de CABA, respuesta inválida y cambio de zona sin alterar tarifas.
- Dispositivo Android 16: UsigLiveTest confirmó Bulnes 2625 como Palermo y caché; ZoneOverlayTest confirmó actualización solo de la oferta correcta y no reapertura tras cierre. OverlayInteractionTest (dos casos) y SeptemberScreenshotTest correctos: cinco pruebas instrumentadas.
- Fuente técnica: https://usig.buenosaires.gob.ar/apis/datos_utiles/datos_utiles.json

## Límites

No garantiza resolver toda dirección OCR. Sin Internet, fuera de CABA, sin altura o con respuesta ambigua sigue Zona sin verificar. Un solo extremo excluido basta para advertir; para indicar zona deseada ambos extremos deben estar identificados. La caché se pierde al terminar el proceso. Falta observar esta consulta durante una nueva oferta real en calle; la validación de red y overlay se realizó mediante pruebas controladas.

Sin merge a master. Se mantiene la limitación de fuente de combustible descrita en VALIDACION_BETA34.md.
