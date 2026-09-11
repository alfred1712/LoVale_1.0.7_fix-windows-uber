# LoVale 1.0.2

## Correcciones
- Se corrigió el parser de importes para formatos argentinos como `$ 12.345,50`.
- Ya no se toma ciegamente el primer `km` o `min` del árbol de accesibilidad cuando hay varios valores.
- Si existen varios valores de distancia/tiempo, el primer par se trata como posible pickup y el último como recorrido del viaje.
- Se incorporaron `pickup`, `pickupDistanceKm` y las zonas detectadas de pickup/destino al modelo del viaje.
- La evaluación de zonas no deseadas ahora comprueba tanto pickup como destino.
- La ventana flotante muestra precio, precio/km, precio/hora, distancia, duración, pickup, destino y advertencia de zona solo cuando corresponde.
- Se agregaron logs de diagnóstico con los valores parseados y las zonas detectadas.
- Se incrementó la versión a 1.0.2 (versionCode 3).

## Hallazgo de la prueba del 31/08/2026
Los logs de Cabify mostraron ofertas con 1.2 km / 4 min, 1.9 km / 8 min y 1.3 km / 7 min. El código anterior utilizaba esos primeros valores para calcular tarifa/km y tarifa/hora.

Esto es potencialmente incorrecto si Cabify está mostrando esos valores como distancia/tiempo hasta la recogida. La versión 1.0.2 separa explícitamente `pickupDistanceKm` cuando aparecen varios valores, y deja trazabilidad en Logcat para comprobar cómo presenta Cabify la oferta real.

## Limitación deliberada
No se inventa una distancia total del viaje. Si la pantalla de Cabify solo expone distancia/tiempo de pickup y no expone distancia/tiempo total del recorrido, LoVale no debe presentar esos valores como si fueran del viaje. La siguiente prueba debe confirmar qué valores contiene realmente el árbol de accesibilidad de la oferta.

## Build
No fue posible ejecutar Gradle en este entorno porque el wrapper requiere descargar Gradle 9.5.0 desde services.gradle.org y el entorno de ejecución no tiene conectividad externa. Se compiló de forma aislada el archivo de dominio `TripEvaluator.kt` con `kotlinc` y pasó la comprobación sintáctica.
