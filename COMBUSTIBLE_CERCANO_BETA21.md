# Beta21: combustible por cercanía

Versión 1.0.8-beta21, versionCode 26. Rama feature/1.0.8-uber-hybrid-detection, sin merge.

La consulta automática utiliza la API oficial de Secretaría de Energía, recurso 80ac25de-a44a-4445-9215-090cf55cfda5. Se verificó su esquema y disponibilidad: precios, empresa, producto, dirección, coordenadas, horario y fecha de vigencia.

- NearbyFuelSource descarga todas las filas de la empresa y producto seleccionados (hasta 10.000; rechaza resultados incompletos). La cercanía se calcula en el teléfono, sin transmitir sus coordenadas. Selecciona por distancia geográfica dentro de 50 km, no por precio, localidad fija o fecha más reciente. Nafta premium se corresponde con Infinia/Quantium/V-Power según marca; Shell usa la denominación oficial SHELL C.A.P.S.A.
- Se toma el precio más reciente de cada horario de esa estación y el mayor de ellos. Si la estación cercana carece de precio válido dentro de 90 días, no se la sustituye silenciosamente por otra más lejana; se conserva la referencia anterior con aviso.
- DriverLocation mantiene una posición solo en memoria, de hasta 2 minutos y 100 m de precisión. FuelPriceControls obtiene una posición puntual al consultar; JourneyLocationService también la aporta cuando ya está funcionando. No se usa la casa guardada.
- FuelPriceWorker conserva la programación cada 12 h. Si no hay permiso/ubicación reciente, deja la consulta pendiente y pide actualizar desde Combustible. No inicia otro servicio GPS en segundo plano ni pide ubicación permanente.
- La UI elimina la selección fija de provincia/localidad, muestra dirección y distancia al consultar y enlaza la fuente oficial. La distancia es en línea recta, no la distancia de manejo.
- SettingsRepository conserva precio y procedencia ante errores, invalida respuestas de una selección anterior y evita que una consulta con ubicación más antigua sobrescriba una nueva. Cambiar de estación permite usar la fecha publicada por esa estación, aunque sea anterior a la de otra.

Validación: clean, assembleDebug y 66 tests unitarios aprobados. Los tests nuevos cubren distancia frente a precio/fecha, marca/producto, coordenadas inválidas, radio, vigencia, horarios, respuesta incompleta y consultas atrasadas. NearbyFuelLiveTest consulta la API desde Android con una coordenada pública de prueba, sin leer ni cambiar ubicación/configuración del conductor.

FuelSelectionTest y NavigationTest pasaron en el Motorola Android 16. NearbyFuelLiveTest pasó tras corregir la redirección de la API: el servidor oficial redirige HTTPS a HTTP. Se permite HTTP exclusivamente a datos.energia.gob.ar en network_security_config.xml; el resto de dominios conserva bloqueo de cleartext. Es transporte de datos públicos sin coordenadas ni credenciales; no tiene la garantía de integridad de HTTPS y los precios continúan siendo una referencia validada por formato/rango/fecha. La consulta con la ubicación real del conductor queda para el botón Actualizar precio y su permiso Android.

Limitaciones: la estación se elige entre registros oficiales con coordenadas y producto identificables; sus datos pueden tener errores o estar desactualizados. No se garantiza precio de surtidor en tiempo real. Una consulta programada necesita posición reciente; si el contador no está funcionando y Android no aporta ubicación, se completa al abrir Combustible y actualizar con permiso preciso.

Fuente: https://datos.energia.gob.ar/dataset/1c181390-5045-475e-94dc-410429be4b17
