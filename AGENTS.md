# AGENTS.md — LoVale

## Propósito del proyecto
LoVale es una app Android escrita en Kotlin cuyo objetivo es evaluar ofertas de viaje de Uber, Cabify y DiDi y mostrar una alerta rápida de rentabilidad al conductor.

La app debe priorizar estabilidad, bajo consumo, rapidez de respuesta y mínima interferencia visual con la app de conducción.

## Repositorio y ramas
- Repositorio: `alfred1712/LoVale_1.0.7_fix-windows-uber`
- Rama estable: `master`
- No modificar `master` directamente.
- Todo desarrollo debe hacerse en una rama feature/fix separada.
- Rama de trabajo actual: `feature/1.0.8-uber-hybrid-detection`
- No hacer merge a `master` sin aprobación explícita del usuario.

## Flujo de trabajo obligatorio
Antes de modificar código:
1. Leer este archivo completo.
2. Confirmar la rama actual.
3. Revisar los archivos relacionados con el cambio.
4. Evitar reescrituras amplias si un cambio localizado resuelve el problema.

Después de modificar código:
1. Ejecutar build local.
2. Corregir todos los errores de compilación.
3. Los warnings pueden permanecer solo si no comprometen estabilidad, compatibilidad o mantenimiento.
4. Resumir archivos modificados, motivo y riesgos.
5. No hacer merge automáticamente.

Comandos de build preferidos en Windows:
```powershell
.\gradlew clean
.\gradlew assembleDebug
```

El criterio mínimo de entrega es `BUILD SUCCESSFUL`.

## Estado y arquitectura actual

### Plataformas
Los paquetes objetivo son:
- Uber: `com.ubercab.driver`
- Cabify: `com.cabify.driver`
- DiDi: `com.didiglobal.driver`

Solo debe procesarse la plataforma seleccionada por el usuario.

### Detección principal
`TripAccessibilityService` es la fuente principal de detección.

Flujo esperado:
```text
Accessibility event
  -> validar plataforma seleccionada
  -> extraer texto de ventanas/nodos
  -> detectar oferta
  -> parsear
  -> TripEvaluator
  -> clasificar rentabilidad
  -> notificación + overlay
```

### Uber: detección híbrida obligatoria
Uber puede renderizar ofertas fuera del árbol de accesibilidad tradicional.

Para Uber:
1. Intentar primero con Accessibility.
2. Si no hay una oferta completa y válida, usar OCR como fallback.
3. El OCR debe activarse automáticamente; no debe existir un botón manual para “activar OCR”.
4. No usar `MediaProjection` como flujo principal.
5. Usar las APIs de screenshot del `AccessibilityService` cuando estén disponibles.
6. En Android 14+ preferir captura de la ventana de Uber (`takeScreenshotOfWindow`) si es viable.
7. En Android 11–13, si se captura display completo, recortar a la región de la ventana de Uber antes del OCR.
8. No analizar visualmente otras apps ni zonas innecesarias de la pantalla.
9. Accessibility y OCR deben converger en el mismo parser/evaluador/deduplicador.

### OCR
- ML Kit Text Recognition es el motor OCR actual.
- Evitar OCR continuo de alta frecuencia.
- Debe existir throttle/cooldown.
- No lanzar dos OCR simultáneos.
- Normalizar variantes OCR de `ARS`, `km`, `min`, coma/punto decimal y saltos de línea.
- El OCR es fallback de Uber, no un subsistema paralelo con lógica propia de evaluación.

## Parsing y evaluación
`TripEvaluator` es la fuente de verdad para cálculo de rentabilidad.

Debe soportar formatos reales observados:

### Uber
```text
ARS6,084
A 6 min (1.9 km)
...
Viaje: 19 min (8.2 km)
...
```

### Cabify
```text
$3.686 en app
$1.215/km
8 min · 1.9 km
13 min · 3 km
```

### DiDi
```text
$8.100
(10 min 2,9 km)
...
(15 min 6,1 km)
...
Aceptar
```

También debe soportar metros, por ejemplo `493m`, convirtiendo a km.

## Reglas de rentabilidad
Los parámetros configurables principales son:
- tarifa mínima por km
- tarifa mínima por hora
- pickup máximo
- zonas excluidas

Clasificación visual:
- `GREEN`: cumple 100% o más de los mínimos configurados.
- `YELLOW`: cumple al menos 85% pero menos de 100% de los mínimos configurados.
- `RED`: queda por debajo de 85% o excede pickup máximo.
- `ZONE`: zona no deseada; debe tratarse visualmente como alerta roja y mostrar el nombre de la zona si está disponible.

Si se usan varios criterios de rentabilidad, la clasificación debe basarse en el criterio más desfavorable.

## Overlay
El overlay debe ser minimalista y no cubrir gran parte de la pantalla.

Debe mostrar principalmente:
- `RENTABLE` en verde
- `CASI RENTABLE` en amarillo
- `NO RENTABLE` en rojo
- `ZONA NO DESEADA` en rojo

Para zona no deseada, mostrar además el nombre de la zona si está disponible.

No mostrar por defecto en el overlay:
- precio
- tarifa por km
- tarifa por hora
- distancia
- duración
- pickup completo
- destino completo

Esos datos pueden seguir usándose internamente para evaluación y logs.

La ventana debe ser compacta, no `MATCH_PARENT`, y no bloquear interacción innecesariamente con la app de conducción.

## Estado de la app
- LoVale debe iniciar siempre en estado `Pausado` al abrir la app.
- El estado `Activo` no debe sobrevivir al cierre/reinicio de la app como si siguiera monitoreando.
- La plataforma seleccionada sí puede persistirse.
- Cambiar de plataforma debe pausar el monitoreo antes de aplicar la nueva selección.

## Foreground services
`LoValeForegroundService` debe ser liviano y no debe contener OCR ni MediaProjection.

Su función es mantener el estado de monitoreo cuando corresponda y cumplir correctamente con requisitos de foreground service.

Todo servicio iniciado con `startForegroundService()` debe llamar a `startForeground()` dentro del tiempo exigido por Android.

## Logging de diagnóstico
Usar `Log.d/w/e` con tag principal `LoVale` para el flujo crítico.

Para Uber mantener logs distinguibles, por ejemplo:
- `UBER/A11Y`
- `UBER/OCR`
- `PARSE`
- `EVAL`
- `OVERLAY`

Ante un fallo, los logs deben permitir identificar si ocurrió en:
1. recepción del evento
2. selección/filtro de paquete
3. extracción Accessibility
4. screenshot
5. OCR
6. parsing
7. evaluación
8. overlay

No loguear datos sensibles innecesarios en builds de producción.

## Estabilidad y manejo de errores
- No permitir que una excepción de screenshot, ventana, nodo, OCR u overlay cierre la app.
- Proteger APIs sensibles a versión de Android con checks de SDK.
- Liberar `HardwareBuffer`, `Bitmap`, recognizers y otros recursos cuando corresponda.
- Evitar fugas de memoria.
- Evitar loops agresivos o procesamiento continuo sin throttle.
- No usar `!!` salvo que la invariancia esté garantizada y documentada.

## Compatibilidad
- `minSdk = 24`
- OCR por screenshot de Accessibility solo puede usarse donde la API de Android lo permita.
- Mantener fallback funcional para Android anteriores cuando sea posible.
- No romper Cabify ni DiDi al corregir Uber.

## Versionado
Cada entrega debe incrementar correctamente:
- `versionCode`: entero siempre creciente.
- `versionName`: versión visible al usuario.

No reutilizar un `versionCode` ya publicado/probado.

Convención sugerida durante pruebas:
- `1.0.8-beta1`
- `1.0.8-beta2`
- etc.

Cuando se considere estable:
- `1.0.8`

## Tests y validación
Antes de dar por terminado un cambio:
- Compilar con Gradle.
- Si se toca parser/evaluador, agregar o actualizar tests unitarios.
- Validar al menos los formatos reales de Uber, Cabify y DiDi conocidos.
- Si el cambio afecta Uber OCR, dejar logs suficientes para prueba en dispositivo real.

El test en dispositivo real sigue siendo obligatorio para confirmar detección de ofertas de Uber.

## Qué no hacer
- No volver a introducir botón manual de OCR.
- No volver a introducir `MediaProjection` salvo que exista una razón técnica fuerte y aprobada explícitamente.
- No hacer OCR de toda la pantalla de forma continua si puede limitarse a Uber.
- No modificar `master` directamente.
- No hacer merge sin aprobación.
- No eliminar compatibilidad con Cabify o DiDi para resolver Uber.
- No ampliar el overlay con información que el usuario pidió ocultar.

## Objetivo actual de la rama 1.0.8
Conseguir que Uber funcione de forma confiable usando detección híbrida Accessibility + OCR focalizado en Uber, manteniendo Cabify y DiDi estables y mostrando un overlay minimalista de rentabilidad.

## Criterios de aceptación para 1.0.8
La versión puede considerarse candidata a merge cuando:
1. Compila sin errores.
2. LoVale inicia pausado.
3. No existe botón manual de OCR.
4. Cabify sigue detectando y evaluando ofertas.
5. DiDi sigue detectando y evaluando ofertas.
6. Uber genera al menos una alerta correcta en prueba real.
7. Si Accessibility no ve la oferta de Uber, el OCR fallback se ejecuta y queda visible en logs.
8. Overlay verde/amarillo/rojo funciona según los parámetros configurados.
9. Zona excluida genera alerta roja específica.
10. No hay cierres inesperados durante una sesión normal de prueba.
