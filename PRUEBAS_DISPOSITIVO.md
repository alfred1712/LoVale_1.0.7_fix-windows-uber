# Pruebas en dispositivo

JDK de Android Studio y SDK Android configurados en local.properties (no versionado).

```powershell
.\gradlew clean
.\gradlew assembleDebug testDebugUnitTest assembleDebugAndroidTest
```

Las capturas reales de pasajeros/conductores y los logs permanecen locales. Para ejecutar los tests OCR visuales, copiar las imágenes autorizadas en `app/src/androidTest/assets/` con estos nombres:

- boost-7625.jpeg, boost-9340.jpeg, priority-8174.jpeg
- uber-8511.png, uber-4916.png
- didi-4000.png, didi-4400.png, didi-center.png

Los casos visuales sin su captura se omiten mediante JUnit Assume; no se contabilizan como lectura OCR validada. Los tests unitarios de parser no requieren esas imágenes.

El teléfono debe estar conectado, desbloqueado y detenido para tests de interfaz y gestos. Las pruebas instrumentadas usan datos simulados, pueden iniciar overlays y abrir pestañas. UsigLiveTest requiere Internet y consulta la dirección de oferta autorizada; no envía GPS ni capturas. NearbyFuelLiveTest depende de la disponibilidad del proveedor y actualmente no forma parte de la suite final por la redirección insegura documentada.

Los informes VALIDACION_BETA34.md y VALIDACION_BETA35.md detallan límites pendientes de validación en calle. Un build correcto y pruebas controladas no garantizan 100% de ofertas detectadas ni confirman por sí solos un cierre de jornada real al llegar a casa.
