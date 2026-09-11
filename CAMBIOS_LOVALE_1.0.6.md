# LoVale 1.0.6

## Objetivo
Reducir el monitoreo global de accesibilidad y trabajar únicamente sobre la plataforma elegida por el usuario.

## Cambios principales
- Selección persistente de plataforma: Uber, Cabify o DiDi.
- El AccessibilityService cambia dinámicamente `packageNames` al paquete seleccionado.
- Paquetes utilizados en esta versión:
  - Uber: `com.ubercab.driver`
  - Cabify: `com.cabify.driver`
  - DiDi: `com.didiglobal.driver`
- Doble validación antes de leer una oferta:
  1. `AccessibilityEvent.packageName` debe ser el paquete seleccionado.
  2. `rootInActiveWindow.packageName` debe corresponder a la plataforma seleccionada.
- Si la plataforma elegida deja de estar en primer plano, LoVale deja de procesar y no registra RAW de otras aplicaciones.
- Se agregaron heurísticas de pantalla de ofertas para cada plataforma.
- Se mantiene el soporte de múltiples tarjetas de DiDi usando `Aceptar` como separador y fallback por pares min/km.
- Se mantienen evaluación por $/km, $/hora, pickup máximo y zonas excluidas.
- Se mantiene notificación y ventana flotante.
- Logs reducidos para facilitar diagnóstico.

## Prueba recomendada
1. Instalar 1.0.6.
2. Abrir LoVale y seleccionar una sola plataforma.
3. Verificar Accesibilidad y permiso de ventana flotante.
4. Activar LoVale.
5. Abrir la plataforma seleccionada y esperar una oferta.
6. Cambiar a otra aplicación y comprobar que LoVale no procesa eventos.
7. Para diagnóstico, filtrar Logcat por `LoVale`.
