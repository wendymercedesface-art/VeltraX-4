# Pruebas — VeltraX 4.1

## Correcciones aplicadas respecto a 4.0 / 2.0

| Área | Problema anterior | Solución 4.1 |
|------|-------------------|--------------|
| PlaybackService | Sin audio focus ni noisy handling | AudioAttributes + handleAudioFocus + becomingNoisy + wake mode |
| Biblioteca | Incluía tonos/notificaciones | Filtro IS_MUSIC / duración + tamaño mínimo |
| Títulos | Solo TITLE de MediaStore | Fallback a DISPLAY_NAME |
| Notificaciones | No se pedía POST_NOTIFICATIONS | Solicitud en MainActivity (API 33+) |
| MediaController | Fallos silenciosos | Try/catch + estado `playerReady` |
| Anterior | Siempre pista previa | Si >3 s → reinicia pista actual |
| PlayerSheet | Botón favorito duplicado | Un solo botón en la barra superior |
| Disco | Rotación definida pero no aplicada | `Modifier.rotate` cuando está reproduciendo |
| Playlists | No se podían borrar | Botón eliminar en lista de playlists |
| Descargas | No se podían quitar de la cola | Botón eliminar + `removeDownload` |
| SharedPreferences | JSON malformado podía crashear | Try/catch en lectura de favoritos/playlists/descargas |
| Activity | Posibles recreaciones | `singleTop` + configChanges |
| Red | Cleartext implícito | `network_security_config.xml` explícito |

## Pendiente de validación en dispositivo real

- Compilación APK / MSI (usar GitHub Actions o Android Studio).
- Reproducción en background con pantalla apagada.
- Widget de pantalla de inicio.
- Descargas con y sin soporte `Range`.
- Bibliotecas grandes (>2000 pistas).

La personalización visual (colores, monograma, degradados, tipografía) se mantiene idéntica a la versión 4.0.
