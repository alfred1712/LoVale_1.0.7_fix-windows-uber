# Neto estimado automático · beta17 (versionCode 22)

## Comportamiento
Configuración única en «Configurar mi vehículo»: vehículo, combustible, consumo de referencia, precio unitario y reserva de desgaste. No registra cargas ni solicita datos por oferta. El costo se calcula localmente sobre las distancias ya extraídas (recogida + pasajero), sin OCR adicional, GPS ni servicios nuevos.

Referencia incluida: Fiat Cronos Like 1.3 MT5 MY26, ciclo urbano 8 L/100 km. Fuente del fabricante: https://cronos.fiat.com.ar/noprecache/ficha-tecnica-cronos.pdf . No extrapolar esa homologación a otras versiones o a GNC. Para otros vehículos el consumo se configura una sola vez.

El precio se ingresa una vez y se actualiza con un deslizador. Se muestra la fecha de referencia y un aviso en el panel luego de 14 días. No hay actualización por red: no se logró verificar un precio vigente del portal oficial durante esta implementación. No se presenta un precio preestablecido ficticio como actual.

Fórmula: combustible = km totales × consumo/100 × precio unitario. Reserva de desgaste = combustible × porcentaje elegido. Neto = importe de oferta − combustible − reserva. El 20% inicial de reserva es una hipótesis editable de LoVale, no una estadística oficial ni mantenimiento medido. El neto puede ser negativo. Los filtros y colores de $/km y $/hora siguen calculándose con el precio de la oferta.

## Resumen diario
El OCR observa ofertas y no confirma aceptación ni realización. El historial permite marcar «Realizado» y deshacerlo. El resumen suma los viajes marcados en el día local de confirmación, incluso si la oferta fue registrada antes. Confirmar repetidamente conserva el mismo instante; no duplica el viaje. Conserva los costos y configuración de cada oferta, sin recalcular días anteriores al cambiar precios.

Ofertas anteriores sin costos no inventan costos cero: el resumen indica cuántas faltan y muestra gastos/neto parciales. Retención: 200 ofertas no confirmadas más confirmadas de los últimos 31 días. El borrado explícito del historial también borra el resumen. Sin direcciones ni capturas en historial.

## Archivos
- `VehicleCosts`, `TripEvaluator`: validación y estimación central de costos.
- `SettingsRepository`: configuración persistente con claves nuevas; no reactiva opciones de beta14.
- `OfferHistory`, `DailySummary`: snapshots por oferta, confirmación reversible, retención y agregación local.
- `VehicleCostPanel`, `MainScreen`, `DriverTools`, `MainViewModel`: configuración inicial, ajuste de precio y resumen.
- `TripAccessibilityService`, `TripOverlayService`, layout: incorporación del neto estimado al flujo existente y presentación opcional; desaparece cuando no hay configuración válida o lectura completa.
- Tests unitarios y de configuración en dispositivo; corregido packageName desactualizado del test de ejemplo.

## Límites
No es ganancia real en bolsillo: se utilizan importes, distancias y tiempos ofrecidos, no comprobantes ni kilómetros efectivamente recorridos. No incluye desvíos, cancelaciones, espera extra, recorridos sin pasajero fuera del pickup, peajes, impuestos, seguro u otros gastos. No se descuenta una comisión extra porque el importe de la oferta se interpreta como pago al conductor. Para uso GNC mixto el consumo de nafta de arranque no se calcula por separado.

No hay cambios de perfil por hora, merge a master ni modificación de la selección de plataforma. La validación sintética del neto no reemplaza una nueva prueba de oferta real.
