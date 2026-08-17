# CortexBridge

Aplicación Android en Kotlin para convertir videos cortos entre TikTok y WhatsApp.

## Qué hace este MVP

- Selecciona un video desde la galería o recibe uno desde el menú **Compartir** de Android.
- **TikTok → WhatsApp:** genera un MP4 H.264 silencioso de hasta 6 segundos.
  WhatsApp suele representar estos MP4 sin audio como GIF.
- **WhatsApp → TikTok:** genera un MP4 H.264 compatible y conserva el audio si el archivo original lo tiene.
- Comparte el resultado usando el selector oficial de Android.
- Procesa los archivos localmente; no requiere servidor ni cuenta propia.

> WhatsApp no suele usar un archivo `.gif` real para sus GIFs: normalmente utiliza un MP4 corto sin audio. Por eso CortexBridge exporta ese formato, que es el más práctico para compartirlo como GIF.

## Requisitos

- Android Studio reciente.
- JDK 17.
- Android SDK 35.
- Un dispositivo Android 8.0 (API 26) o superior.
- TikTok y/o WhatsApp instalados para compartir directamente.

## Compilar

Abre `CortexBridge/` en Android Studio y ejecuta la configuración Gradle.

Desde una terminal con Gradle disponible:

```bash
gradle assembleDebug
```

El APK de debug queda en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Uso

1. Abre CortexBridge.
2. Elige `TikTok → WhatsApp` o `WhatsApp → TikTok`.
3. Pulsa **Elegir video** o comparte un video desde otra app hacia CortexBridge.
4. Pulsa **Convertir**.
5. Pulsa **Compartir en WhatsApp** o **Compartir en TikTok** y confirma el destino en el selector de Android.

## Estado y siguientes pasos

Este es un MVP funcional. Antes de publicarlo conviene agregar:

- recorte visual con vista previa;
- selección de duración y relación de aspecto;
- control de tamaño/calidad;
- manejo de permisos y errores por códec con mensajes más detallados;
- pruebas en distintas versiones de Android y modelos de teléfono;
- icono, capturas y firma de release.

TikTok y WhatsApp son marcas de sus respectivos propietarios. CortexBridge no es una aplicación oficial de esas plataformas.
