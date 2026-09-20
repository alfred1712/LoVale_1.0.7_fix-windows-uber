# Beta18 (versionCode 23): precios automáticos y Boost+ incluido

## Combustible
- Fuente integrada y probada por red: https://combustibles.ar/ . Publica registros de origen oficial por estación, producto, localidad y vigencia. La consulta directa devolvió fechas más recientes que la copia del buscador; el parser usa la respuesta real de red.
- Menús YPF, AXION, SHELL y PUMA. Súper, premium con nombre comercial (Infinia, Quantium, V-Power), GNC y diésel. Provincia y localidad explícitas, sin GPS. GNC $/m³; líquidos $/L.
- La referencia es una estación: fecha más reciente entre las filas válidas de la página consultada del producto y localidad; desempate por nombre/dirección. Para horarios diferentes usa el mayor precio publicado de esa estación. No es promedio de toda la marca ni garantiza el precio de otra estación. No recorre todas las páginas del sitio.
- `FuelPriceSource` valida marca, categoría y localidad en celdas/enlaces; precios positivos, fecha válida no futura y antigüedad máxima 90 días. No busca importes sueltos en HTML. Timeout 20 s, respuesta máxima 1 MiB, HTTPS, sin redirecciones. Si cambia el formato, falla sin reemplazar el precio.
- `FuelPriceWorker`: trabajo único periódico cada 12 horas con conexión. Una consulta inmediata al guardar selección o tocar Consultar ahora. Sin OCR/FGS nuevos, sin notificaciones. Android puede demorar trabajos por Doze/batería/red; no se promete puntualidad exacta dos veces por día.
- `SettingsRepository` aplica el resultado solo si sigue activa la misma selección/revisión, conserva precio ante fallo o fuente más antigua y separa fecha de consulta de vigencia. Cambiar selección invalida el precio anterior; cambiar tipo de combustible desactiva neto hasta revisar consumo. Ajustar precio manualmente desactiva actualización automática. No modifica snapshots de viajes pasados.
- Más de 14 días: aviso de antigüedad. Se muestra fuente y estación. Consultar hoy no significa que el dato haya sido publicado hoy.

Otras fuentes revisadas: catálogo de Secretaría de Energía/Datos Argentina (rutas consultadas devolvieron 404/error), Surtidores.com.ar (precios en publicaciones mensuales gráficas), sitios de marcas (productos y localizadores, sin endpoint público de precios verificado). No se integraron como fallback para no mezclar metodologías o fechas.

## Uber Boost+
Las capturas muestran «Boost+ de ARS… incluido»: componente ya incluido en la tarifa, no se suma ni se resta otra vez.
- Tarifa 9340, pickup 0.6 km/4 min + viaje 6.4 km/27 min: 1334.29/km y 18077.42/h. El overlay anterior 339/km y 4593/h correspondía a usar solo 2373.
- Tarifa 7625, pickup 1 km/6 min + viaje 6.4 km/18 min: 1030.41/km y 19062.50/h.
- Parser descarta Boost+, variantes espaciadas y componentes marcados «incluido». Conserva protección Turbo/promos y tarifa unitaria. Tests con textos y ML Kit sobre ambas imágenes originales; imágenes solo en assets de androidTest, no en APK principal.

## Cambios y validación
`FuelPrices`, `FuelPriceSource`, `FuelPriceWorker`, `FuelPriceControls`, `SettingsRepository`, `MainViewModel`, `VehicleCostPanel`, manifest, dependencias y versión. `TripEvaluator` y tests Boost/OCR/precios/selectores.
48 tests unitarios aprobados; 6 pruebas de dispositivo aprobadas, incluyendo OCR de las dos capturas, consulta HTTP real y selección AXION/Quantium. Falta repetir una oferta real nueva en uso para confirmar toda la cadena Accessibility/captura/overlay tras esta entrega.
Rama feature/1.0.8-uber-hybrid-detection. Sin merge a master.
