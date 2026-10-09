# Interfaz beta19

Versión 1.0.8-beta19, código 24. Rama feature/1.0.8-uber-hybrid-detection; sin merge.

- MainActivity: área segura de Android y teclado, fondo oscuro y retorno desde zonas.
- LoValeNavigation: Inicio, Combustible y Ganancias, navegación inferior fija; conserva el desplazamiento al cambiar de pestaña.
- MainScreen: filtros más legibles, avisos cortos y acceso a la oferta de prueba.
- VehicleCostPanel: pantallas separadas de combustible y ganancias. Precio y neto destacados; resumen diario de viajes confirmados; aclaraciones desplegables.
- FuelPriceControls: empresa, producto, actualización y antigüedad visibles; fuente y estación dentro de Más información.
- DriverTools: historial en Ganancias y prueba en Inicio.
- Theme: paleta oscura consistente y contraste de controles.

Validación: clean y assembleDebug correctos; 48 tests unitarios aprobados. Las 8 pruebas de dispositivo pasaron entre las ejecuciones de la suite y la repetición aislada de NavigationTest. Esta última prepara el permiso de notificaciones antes de abrir MainActivity. La primera ejecución visual quedó bloqueada por el teléfono en reposo; luego se corrigió la preparación del permiso en el test.

NavigationTest verifica pestañas, contenido separado y límites frente a los insets reales; genera capturas en los archivos externos de la app. CompactNavigationTest verifica desplazamiento y navegación con 320 x 420 dp y fuente al 150 %. Revisadas las capturas del Motorola Android 16.

La revisión visual cubre el dispositivo conectado y el escenario compacto de prueba. Falta confirmar visualmente el Samsung de las capturas del usuario. El neto sigue siendo estimado y requiere marcar los viajes realizados; no se modificó el cálculo ni la detección de ofertas.
