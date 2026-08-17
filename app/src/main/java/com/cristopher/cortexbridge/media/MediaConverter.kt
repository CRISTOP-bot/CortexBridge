package com.cristopher.cortexbridge.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.Effects
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Crop
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object MediaConverter {
    enum class Destination { WHATSAPP, TIKTOK }
    enum class Quality(val label: String, val outputHeight: Int) {
        LOW("480p", 480), MEDIUM("720p", 720), HIGH("1080p", 1080)
    }
    enum class Aspect(val label: String) { ORIGINAL("Original"), VERTICAL("9:16"), SQUARE("1:1") }

    data class Options(
        val destination: Destination,
        val startMs: Long = 0,
        val endMs: Long? = null,
        val durationMs: Long = 0,
        val sourceWidth: Int = 0,
        val sourceHeight: Int = 0,
        val quality: Quality = Quality.MEDIUM,
        val aspect: Aspect = Aspect.ORIGINAL,
        val removeAudio: Boolean = destination == Destination.WHATSAPP
    )

    suspend fun convert(
        context: Context,
        input: Uri,
        options: Options,
        onProgress: (Int) -> Unit = {}
    ): File = suspendCancellableCoroutine { continuation ->
        val outputDirectory = File(context.cacheDir, "shared").apply { mkdirs() }
        val output = File(
            outputDirectory,
            "cortexbridge_${options.destination.name.lowercase()}_${System.currentTimeMillis()}.mp4"
        )

        val safeStart = options.startMs.coerceAtLeast(0)
        val sourceEnd = if (options.durationMs > 0) options.durationMs else (options.endMs ?: 0L)
        val requestedEnd = options.endMs ?: sourceEnd
        val exportEnd = if (options.destination == Destination.WHATSAPP) {
            minOf(requestedEnd.takeIf { it > 0 } ?: (safeStart + 6_000), safeStart + 6_000)
        } else {
            requestedEnd
        }
        val clip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(safeStart)
            .apply { if (exportEnd > safeStart) setEndPositionMs(exportEnd) }
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(input)
            .setClippingConfiguration(clip)
            .build()

        val effects = buildCropEffects(options)
        val editedItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(options.removeAudio)
            .setEffects(Effects(emptyList(), effects))
            .build()
        val request = TransformationRequest.Builder()
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setResolution(options.quality.outputHeight)
            .build()

        lateinit var monitor: Job
        val transformer = Transformer.Builder(context)
            .setTransformationRequest(request)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(
                    composition: androidx.media3.transformer.Composition,
                    exportResult: ExportResult
                ) {
                    monitor.cancel()
                    onProgress(100)
                    continuation.resume(output)
                }

                override fun onError(
                    composition: androidx.media3.transformer.Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    monitor.cancel()
                    output.delete()
                    continuation.resumeWithException(exportException)
                }
            })
            .build()

        monitor = CoroutineScope(Dispatchers.Default).launch {
            val holder = ProgressHolder()
            while (isActive && continuation.isActive) {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress)
                }
                delay(250)
            }
        }
        continuation.invokeOnCancellation {
            monitor.cancel()
            transformer.cancel()
            output.delete()
        }
        transformer.start(editedItem, output.absolutePath)
    }

    private fun buildCropEffects(options: Options): List<androidx.media3.common.Effect> {
        if (options.aspect == Aspect.ORIGINAL || options.sourceWidth <= 0 || options.sourceHeight <= 0) {
            return emptyList()
        }
        val desiredRatio = when (options.aspect) {
            Aspect.VERTICAL -> 9f / 16f
            Aspect.SQUARE -> 1f
            Aspect.ORIGINAL -> return emptyList()
        }
        val inputRatio = options.sourceWidth.toFloat() / options.sourceHeight.toFloat()
        return if (inputRatio > desiredRatio) {
            val keptWidth = desiredRatio / inputRatio
            val half = keptWidth / 2f
            listOf(Crop(-half, half, -0.5f, 0.5f))
        } else {
            val keptHeight = inputRatio / desiredRatio
            val half = keptHeight / 2f
            listOf(Crop(-0.5f, 0.5f, -half, half))
        }
    }
}
