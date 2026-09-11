# LoVale 1.0.5 — ajuste de detección Uber / DiDi / Cabify

## Problema observado
Las capturas reales muestran que las aplicaciones no presentan una única estructura:

- **DiDi / Centro de viajes:** puede mostrar varias tarjetas de viaje simultáneamente. Cada tarjeta tiene un importe y dos pares `min/km`: recogida y recorrido.
- **Uber:** muestra `ARS6,084`, luego `A 6 min (1.9 km)` y `Viaje: 19 min (8.2 km)`.
- **Cabify:** muestra `$3.686`, un indicador `$1.215/km` y luego `8 min · 1.9 km` / `13 min · 3 km`.

La versión anterior concatenaba todo el árbol de accesibilidad y luego intentaba tomar el primer/último valor. Eso no funciona cuando DiDi tiene más de una tarjeta y tampoco reconocía correctamente el prefijo `ARS` de Uber.

## Cambios

### 1. Detección de paquetes
Ahora se acepta cualquier paquete cuyo nombre contenga `uber`, `cabify` o `didi`, en vez de depender de IDs demasiado específicos.

### 2. Separación de tarjetas
Se intenta separar cada oferta por el texto/botón `Aceptar`.
Esto permite procesar individualmente las ofertas del **Centro de viajes de DiDi**.

Si la UI no expone `Aceptar` al árbol de accesibilidad, existe un fallback basado en los pares `min/km`, evitando separar por `$` porque Cabify puede mostrar más de un importe dentro de la misma tarjeta.

### 3. Parser de importes
Ahora reconoce:
- `$8.100` → 8100
- `$3.686` → 3686
- `$12.345,50` → 12345,50
- `ARS6,084` → 6084

### 4. Parser de recorrido
Para una tarjeta con dos pares `min/km`:
- primer par = recogida/pickup
- último par = recorrido del pasajero

Ejemplo DiDi:
`10 min 2,9 km` → pickup
`15 min 6,1 km` → viaje

Ejemplo Uber:
`6 min 1,9 km` → pickup
`19 min 8,2 km` → viaje

Ejemplo Cabify:
`8 min 1,9 km` → pickup
`13 min 3 km` → viaje

### 5. Pickup máximo
La configuración `Pickup máx.` ahora se aplica realmente. Si la distancia de recogida supera el máximo configurado, la oferta se ignora y queda registrada en Logcat.

### 6. Accesibilidad
Se agregaron eventos `typeViewScrolled` y `typeViewTextChanged`, además de `flagIncludeNotImportantViews` y `flagReportViewIds`, para mejorar la lectura de interfaces dinámicas como el Centro de viajes.

### 7. Ventana flotante
La app ahora comprueba `SYSTEM_ALERT_WINDOW` antes de intentar crear el overlay.
Si falta el permiso, conserva la notificación y registra claramente el motivo.
Además, la pantalla principal muestra un botón para habilitar el permiso de ventana flotante.

### 8. Antiduplicado
Una misma oferta no vuelve a generar una alerta durante 4 segundos si la app emite múltiples eventos idénticos.

## Prueba rápida recomendada
1. Instalar la nueva APK.
2. Abrir LoVale.
3. Confirmar que el servicio de accesibilidad esté habilitado.
4. Confirmar que el estado diga **Activo y Monitoreando**.
5. Confirmar que no aparezca **Falta permiso para la ventana flotante**. Si aparece, habilitarlo.
6. Abrir DiDi y entrar a **Centro de viajes**.
7. Con dos ofertas visibles, esperar unos segundos.
8. Revisar Logcat filtrando por `LoVale`.

En el caso de la captura de DiDi, la primera oferta debería interpretarse como:
- Precio: $8.100
- Pickup: 2,9 km / 10 min
- Viaje: 6,1 km / 15 min
- Tarifa viaje: aproximadamente $1.328/km
- Tarifa hora: $32.400/h

La segunda:
- Precio: $5.600
- Pickup: 2,7 km / 9 min
- Viaje: 5,8 km / 14 min
- Tarifa viaje: aproximadamente $966/km
- Tarifa hora: $24.000/h

La captura de Uber:
- Precio: $6.084
- Pickup: 1,9 km / 6 min
- Viaje: 8,2 km / 19 min
- Tarifa viaje: aproximadamente $742/km
- Tarifa hora: aproximadamente $19.213/h

La captura de Cabify:
- Precio: $3.686
- Pickup: 1,9 km / 8 min
- Viaje: 3,0 km / 13 min
- Tarifa viaje: aproximadamente $1.229/km
- Tarifa hora: aproximadamente $17.012/h
