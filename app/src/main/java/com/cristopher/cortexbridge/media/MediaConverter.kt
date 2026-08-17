package com.cristopher.cortexbridge.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Converts shared media to a broadly compatible H.264 MP4.
 *
 * WhatsApp GIFs are usually short, silent MP4 videos rather than GIF files.
 * Therefore the WhatsApp output is capped at six seconds and has its audio
 * track removed. TikTok receives a normal MP4 and keeps audio when present.
 */
object MediaConverter {
    enum class Destination { WHATSAPP, TIKTOK }

    suspend fun convert(
        context: Context,
        input: Uri,
        destination: Destination
    ): File = suspendCancellableCoroutine { continuation ->
        val outputDirectory = File(context.cacheDir, "shared").apply { mkdirs() }
        val output = File(
            outputDirectory,
            "cortexbridge_${destination.name.lowercase()}_${System.currentTimeMillis()}.mp4"
        )
        val baseItem = MediaItem.Builder()
            .setUri(input)
            .apply {
                if (destination == Destination.WHATSAPP) {
                    setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setEndPositionMs(6_000)
                            .build()
                    )
                }
            }
            .build()
        val editedItem = EditedMediaItem.Builder(baseItem)
            .setRemoveAudio(destination == Destination.WHATSAPP)
            .build()
        val request = TransformationRequest.Builder()
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .build()

        val transformer = Transformer.Builder(context)
            .setTransformationRequest(request)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(
                    composition: androidx.media3.transformer.Composition,
                    exportResult: ExportResult
                ) {
                    continuation.resume(output)
                }

                override fun onError(
                    composition: androidx.media3.transformer.Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    output.delete()
                    continuation.resumeWithException(exportException)
                }
            })
            .build()

        continuation.invokeOnCancellation {
            transformer.cancel()
            output.delete()
        }
        transformer.start(editedItem, output.absolutePath)
    }
}
