# LoVale 1.0.1 - Correcciones

## Problemas corregidos

1. **MissingForegroundServiceTypeException en TripOverlayService**
   - El servicio de overlay ahora declara `android:foregroundServiceType="specialUse"`.
   - Se agregó `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`.
   - `startForeground()` pasa explícitamente `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` en Android 14+ (API 34+).
   - En Android anteriores se usa la firma de dos argumentos.

2. **Arranque robusto del overlay**
   - TripAccessibilityService inicia TripOverlayService mediante `startForegroundService()` en Android O+.
   - El overlay valida `SYSTEM_ALERT_WINDOW` antes de intentar agregar la ventana.
   - Errores del overlay se capturan para evitar que el proceso de LoVale se caiga.

3. **Falsas detecciones fuera de Uber/Cabify/DiDi**
   - El AccessibilityService filtra por paquete ANTES de leer `rootInActiveWindow`.
   - Se usa una lista de paquetes soportados en lugar de detectar cualquier texto que contenga `$`, `km` o `min` en otras aplicaciones.
   - El Logcat ahora registra explícitamente el paquete cuando se detecta un viaje.

4. **Ciclo de vida del AccessibilityService**
   - `onInterrupt()` ya no cancela permanentemente el CoroutineScope.
   - El scope se cancela en `onDestroy()`.

5. **LoValeForegroundService**
   - `specialUse` solo se pasa a `startForeground()` en API 34+.
   - Fallos al iniciar el FGS se manejan sin derribar el proceso.

6. **Versión**
   - versionCode: 2
   - versionName: 1.0.1

## Nota de validación

El entorno de ejecución utilizado para preparar este ZIP no tiene acceso a Internet y el Gradle Wrapper necesita descargar Gradle 9.5.0 desde `services.gradle.org`, por lo que no fue posible ejecutar `assembleDebug` aquí. El proyecto sí fue inspeccionado y el AndroidManifest/XML fue validado sintácticamente.

Al abrirlo en Android Studio, ejecutar **Sync Project with Gradle Files** y después **Build > Make Project** antes de instalar.
