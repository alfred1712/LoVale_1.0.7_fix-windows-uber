# Beta16 · versionCode 21

Reporte del conductor: Uber mostraba un adicional Turbo y LoVale lo tomaba como precio de viaje.

`TripEvaluator.kt`: excluye importes asociados directamente a Turbo, promo/promoción, bono, bonificación, adicional, extra o propina, además de los adicionales con + y tarifas unitarias. Distingue etiquetas previas del sufijo «de Turbo» perteneciente al importe anterior. No suma el adicional al precio mostrado. Si solo se reconoce un adicional, devuelve precio cero y lectura incompleta.

`TripAccessibilityService.kt`: logs PARSE incluyen cantidad de importes, descartados y candidatos, sin texto sensible adicional.

`UberOcrTest.kt`: casos sintéticos del reporte con orden variable, promo mayor que el precio, solo adicional y mención Turbo sin otro importe. 34 tests aprobados, incluyendo formatos de Uber, Cabify y DiDi.

La captura exacta del incidente no está disponible. Este ajuste cubre etiquetas textuales reconocidas; si OCR pierde la etiqueta y el signo + o cambia el orden, aún puede haber ambigüedad. Es necesaria una nueva oferta real Turbo para confirmar el caso del dispositivo. Revisar precio elegido en PARSE y las métricas EVAL.

Se conservan los cambios de beta15: sin costos/perfiles y selector con iconos instalados. Sin merge a master.
