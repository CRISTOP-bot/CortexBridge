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
- Historial local persistente de resultados guardados con miniaturas cuando Android las proporciona.
- Compartir mediante el selector oficial de Android.
- Tema claro/oscuro.
- Logo vectorial de CortexBridge como icono de la app y dentro de la interfaz.
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

El APK release principal es **sin firmar**. Para probarlo en el teléfono, descarga el asset que termina en `-debug.apk`, que sí está firmado con la clave debug de Android. Para distribuirlo públicamente se debe configurar una keystore propia y no subirla al repositorio.

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
- publicación de ambos APK como artifacts;
- publicación de un APK debug firmado para instalarlo directamente en un teléfono de pruebas;
- CodeQL, Dependabot, Detekt, KtLint, revisión de dependencias, control de tamaño y compilación nocturna;
- limpieza semanal de prereleases antiguas;
- workflow manual para releases firmadas de producción.

Se ejecuta en cada push a `main`, en pull requests y manualmente desde la pestaña **Actions**.

## Release firmado de producción

El workflow `signed-release.yml` es manual y necesita estos GitHub Secrets antes de ejecutarlo:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

La keystore debe ser propia y no debe subirse al repositorio. El workflow genera una release estable con un APK firmado y un `.aab` listo para Google Play. La publicación final en Google Play requiere una cuenta de Play Console, ficha de aplicación, política de privacidad y revisión de Google.

## Workflows incluidos

| Workflow | Función |
| --- | --- |
| `android.yml` | Tests, lint y APKs debug/release sin firma |
| `release.yml` | Prerelease automática por cambio en `main` |
| `signed-release.yml` | Release estable firmada, manual |
| `codeql.yml` | Análisis de seguridad Kotlin/Java |
| `quality.yml` | Detekt, KtLint y documentación |
| `dependency-review.yml` | Revisión de dependencias en PRs |
| `apk-size.yml` | Límite de 30 MiB para el APK debug |
| `nightly.yml` | Compilación para API 26, 30 y 35 |
| `cleanup-prereleases.yml` | Conserva las últimas 10 prereleases |

## Próximas mejoras posibles

- Editor con desplazamiento de video por fotogramas.
- Filtros, texto y stickers.
- Codificador `.gif` real opcional.
- Firma automática mediante GitHub Environments y una keystore protegida.
- Pruebas instrumentadas en varios dispositivos.

TikTok y WhatsApp son marcas de sus respectivos propietarios. CortexBridge no es una aplicación oficial de esas plataformas.
