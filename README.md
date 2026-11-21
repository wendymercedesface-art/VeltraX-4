# VeltraX Nativo 4.2 — Android + Windows

Aplicación de música **local**. Android usa **Kotlin + Jetpack Compose + Material 3, MediaStore, Media3 MediaSessionService, WorkManager**. Windows usa **Compose Desktop + JavaFX Media**. Comparten el motor HTTP de descargas segmentadas en el módulo `:core`.

## Qué mejora la versión 4.2

- Audio focus real y manejo de “auriculares desconectados” (no se corta de forma brusca).
- Wake lock local para reproducción estable en background.
- Biblioteca más limpia: filtra tonos/notificaciones y usa `DISPLAY_NAME` como respaldo de título.
- Permiso de notificaciones en Android 13+ (necesario para descargas y media).
- MediaController más resistente a fallos y reconexión.
- “Anterior” inteligente: si llevas >3 s escuchando, vuelve al inicio de la pista.
- Eliminar playlists y descargas desde la UI.
- Disco del reproductor ampliado **gira** mientras reproduce.
- Validaciones extra en SharedPreferences y descargas (menos crashes por datos corruptos).
- Network security config explícito y `singleTop` en la Activity.
- Nuevo estilo **Aurora** y paletas Violeta, Atardecer y Esmeralda.
- Modo claro/oscuro, listas compactas y control de animación del reproductor.
- Preferencias visuales guardadas localmente entre sesiones.

## Funciones

- Índice real de canciones locales (MediaStore / carpetas Windows).
- Controles reproducir / pausa, anterior / siguiente, progreso, playlists y favoritos.
- Android: servicio Media3, notificación multimedia y widget de inicio.
- Descargas HTTP/HTTPS de archivos de audio **directos** (hasta 3 segmentos con `Range`).
- Interfaz sin canciones de demostración falsas; la apariencia Android se puede personalizar desde Ajustes.
- Interfaz Android personalizable: cuatro paletas, modo claro/oscuro, densidad y animaciones.

## Limitaciones

- No extrae audio de YouTube, Spotify ni proveedores con DRM, ni evade restricciones de acceso.
- Solo descarga URLs HTTP/HTTPS directas que entreguen un archivo de audio real y que tengas permiso para guardar.
- MP3 y WAV tienen la mayor compatibilidad; M4A/AAC funcionan en la mayoría de dispositivos.

## Compilar Android

1. Abre el directorio raíz en Android Studio con JDK 17. SDK Platform 35.
2. Gradle: Kotlin 2.2.20, AGP 8.11.1, Gradle 8.14.3.
3. Tarea `:androidApp:assembleDebug` → APK en `androidApp/build/outputs/apk/debug/`.
4. Dispositivo Android 10+. Concede audio (y notificaciones si se piden).

### Desde el teléfono (sin PC)

1. Sube **el contenido** de esta carpeta a un repositorio GitHub (raíz = `settings.gradle.kts`).
2. Actions → **VeltraX Android APK** → Run workflow.
3. Descarga el artefacto APK.

## Compilar Windows

JDK 17 + IntelliJ / Android Studio. `:desktopApp:run` o `:desktopApp:packageMsi`. También hay workflow `windows-msi.yml`.

## Notas

Este ZIP no incluye APK/MSI precompilados. Usa Android Studio o los workflows de GitHub Actions.
