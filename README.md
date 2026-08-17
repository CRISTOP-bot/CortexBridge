# CortexBridge

Aplicación Android en Kotlin para mover y preparar videos cortos entre TikTok y WhatsApp.

## Funciones incluidas

- **TikTok → WhatsApp:** MP4 H.264 silencioso de hasta 6 segundos; WhatsApp normalmente lo muestra como GIF.
- **WhatsApp → TikTok:** MP4 H.264 compatible; permite conservar el audio.
- Recepción desde el menú **Compartir** de Android.
- Selector de video desde la galería y vista previa en bucle.
- Recorte de inicio y final con controles visuales.
- Calidades 480p, 720p y 1080p.
- Formatos Original, vertical 9:16 y cuadrado 1:1 con recorte centrado.
- Compresión mediante resolución de salida configurable.
- Barra de progreso real y cancelación de conversiones.
- Guardado en `Películas/CortexBridge` mediante MediaStore.
- Historial local de resultados guardados.
- Compartir mediante el selector oficial de Android.
- Tema claro/oscuro.
- Procesamiento local: los archivos no se suben a ningún servidor.
- Tests unitarios, lint, build debug y build release sin firma en GitHub Actions.

> WhatsApp normalmente usa un MP4 corto sin audio para sus GIFs, no un archivo `.gif` real. CortexBridge prioriza ese formato porque es más compatible y ligero. La importación de GIF se deja al decodificador de Android; la exportación principal sigue siendo MP4 compatible.

## Requisitos

- Android Studio reciente.
- JDK 17.
- Android SDK 35.
- Android 8.0 (API 26) o superior.
- TikTok y/o WhatsApp instalados para compartir directamente.

## Compilar localmente

Abre `CortexBridge/` en Android Studio. Desde una terminal con Gradle disponible:

```bash
gradle test lint assembleDebug assembleRelease
```

Los APK quedan en:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
```

El release es **sin firmar**. Para distribuirlo se debe configurar una keystore propia y no subirla al repositorio.

## Uso

1. Abre CortexBridge.
2. Elige `TikTok → WhatsApp` o `WhatsApp → TikTok`.
3. Selecciona un video o compártelo desde otra aplicación hacia CortexBridge.
4. Revisa la vista previa.
5. Ajusta el recorte, la calidad, el formato y el audio.
6. Pulsa **Convertir**.
7. Comparte el resultado o guárdalo en la galería.

## GitHub Actions

El workflow `.github/workflows/android.yml` ejecuta automáticamente:

- tests unitarios;
- lint;
- compilación debug;
- compilación release sin firma;
- publicación de ambos APK como artifacts.

Se ejecuta en cada push a `main`, en pull requests y manualmente desde la pestaña **Actions**.

## Próximas mejoras posibles

- Editor con desplazamiento de video por fotogramas.
- Filtros, texto y stickers.
- Codificador `.gif` real opcional.
- Firma automática mediante GitHub Environments y una keystore protegida.
- Pruebas instrumentadas en varios dispositivos.

TikTok y WhatsApp son marcas de sus respectivos propietarios. CortexBridge no es una aplicación oficial de esas plataformas.
