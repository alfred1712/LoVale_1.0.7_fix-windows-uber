# Revisión preliminar de términos — 20/09/2026

## Resultado

No se puede certificar que LoVale 1.0.9-beta1 cumpla los contratos de Uber, DiDi y Cabify. La extracción automática mediante Accessibility y capturas/OCR presenta riesgo contractual; el permiso Android del conductor no equivale a autorización de la plataforma. Esta revisión técnica/documental no sustituye un dictamen jurídico argentino ni una autorización de cada plataforma.

No se modificó el APK ni se deshabilitaron funciones durante esta revisión. No considerar la revisión como aprobación para publicar o continuar pruebas reales con cuentas de conductor bajo una exigencia de ausencia de infracciones.

## Fuentes oficiales y alcance

- Uber Argentina, términos generales, apartado Restricciones: limita programas que extraen, indexan o analizan datos de sus servicios. Es evidencia de riesgo, no sustituto del contrato específico de conductor: https://www.uber.com/ar/es/legal/general-terms-of-use/
- Uber indica que los conductores activos pueden consultar sus documentos aceptados en partners.uber.com: https://www.uber.com/ar/es/legal/
- Cabify Argentina, II.6.c.vi–viii y II.10: restricciones sobre datos, copia de contenidos, medios de obtención y usos que requieren autorización. Aplican expresamente a conductores: https://cabify.com/ar/legal/terminos-y-condiciones
- DiDi Argentina, términos generales públicos, propiedad intelectual: restricciones sobre extracción, incorporación en otros productos y reutilización comercial sin consentimiento escrito: https://web.didiglobal.com/ar/legal/terminos-y-condiciones/
- Portal oficial DiDi enlaza los términos específicos de conductor: https://web.didiglobal.com/ar/legal/ → https://privacycenter.didiglobal.com/AR/privacy-notice/702e187c377b8a59aaf7ad942a0b41c3?id=127 . El lector web no recuperó el contenido de ese documento dinámico; su revisión queda pendiente. No se sustituyó por condiciones de otro país.

Fecha de consulta, no garantía de vigencia futura. Deben cotejarse versión, país y contratos efectivamente aceptados por el conductor.

## Contraste con el código

| Función | Evidencia | Evaluación preliminar |
| --- | --- | --- |
| Lectura automática y OCR | TripAccessibilityService: eventos, nodos, takeScreenshotOfWindow/takeScreenshot, ML Kit | Riesgo central de extracción automatizada no autorizada; que sea local no lo elimina. |
| Historial, comparación y CSV | OfferHistory / OfferStatistics | Reutilización y persistencia de datos obtenidos de las plataformas; requiere aclarar derechos de uso. |
| Consulta de barrio | UsigNeighborhoods | Envía calle y altura a USIG; revisar tanto permiso contractual como tratamiento de datos de terceros. |
| Reportar lectura | ReadingReport | Puede guardar/exportar direcciones con acción del conductor. Consentimiento del conductor no resuelve por sí solo derechos sobre datos de pasajeros. |
| Iconos de plataformas | RideAppIcon usa PackageManager.getApplicationIcon | Revisar uso de marcas y evitar cualquier apariencia de patrocinio/autorización. |
| Interacción con viajes | Sin coincidencias performAction/dispatchGesture/ACTION_CLICK en fuentes principales | No se encontraron mecanismos de aceptación/rechazo automático. Esto reduce interferencia, pero no autoriza la lectura. |
| Red | Fuentes principales apuntan a combustible y USIG; no se encontraron endpoints privados de las plataformas en la búsqueda | No se identificó acceso a API privada en esta revisión acotada. No equivale a auditoría exhaustiva. |

## Condiciones para avanzar con exigencia de cumplimiento

1. Obtener los contratos vigentes aceptados por los conductores de las tres plataformas, sin compartir contraseñas ni credenciales.
2. Obtener revisión jurídica y, cuando corresponda, autorización escrita que cubra expresamente Accessibility/OCR, overlay, persistencia, estadísticas, exportaciones, consultas de direcciones y uso de marcas. No asumir que una API pública autoriza todos esos usos.
3. Mientras no exista base suficiente, mantener pausado el monitoreo y no distribuir la integración como autorizada. Si se continúa desarrollo, usar datos sintéticos o material para el que existan derechos suficientes.
4. Alternativa de menor dependencia contractual: versión sin lectura de otras apps, con datos introducidos por el conductor o importaciones expresamente permitidas; verificar por separado las condiciones de cada fuente.

No se enviaron consultas a las plataformas ni se compartió código/datos con terceros durante esta revisión. No se cambió código ejecutable; no corresponde un nuevo build.
