# Autocompletado beta13 / code18

ForbiddenZonesScreen reemplaza Geocoder por catálogo local, búsqueda desde 3 letras,
sin tildes y por inicio del nombre o palabra. Lista desplazable de hasta 20 sugerencias,
filtra zonas ya agregadas. Si no hay coincidencias permite ingreso manual.
ZoneAutocomplete contiene búsqueda comprobable en tests.
TripEvaluator elimina la etiqueta entre paréntesis antes de normalizar la zona:
antes Retiro (CABA) terminaba comparándose como retiro caba.

Catálogo: 1040 etiquetas, generado el 2026-09-11 de respuestas completas de Georef:
https://apis.datos.gob.ar/georef/api/localidades?provincia=06&max=5000&campos=nombre,departamento.nombre
https://apis.datos.gob.ar/georef/api/localidades?provincia=02&max=500&campos=nombre,departamento.nombre
Documentación: https://www.argentina.gob.ar/georef
895 registros PBA y 49 CABA, más nombres de partidos y alias Once.
Se normalizaron las etiquetas Núñez, La Boca y La Paternal.
Fuentes originales: georef-localidades-source.json y georef-caba-source.json.

No usa Google ni envía consultas desde el teléfono. Catálogo incluido en assets;
actualizaciones requieren regenerarlo. Cobertura CABA/PBA, no exhaustiva para barrios
informales. Homónimos se distinguen visualmente por partido, pero la evaluación actual
compara el nombre de zona en texto y no polígonos geográficos.

Tests de prefijos, tildes, mínimos, duplicados y zonas con etiquetas correctos.
clean y assembleDebug BUILD SUCCESSFUL. Sin merge.
