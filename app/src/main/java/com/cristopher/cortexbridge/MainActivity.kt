package com.cristopher.cortexbridge

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.RangeSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.cristopher.cortexbridge.media.GallerySaver
import com.cristopher.cortexbridge.media.MediaConverter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

class MainActivity : ComponentActivity() {
    private var incomingUri by mutableStateOf<Uri?>(null)
    private var darkMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingUri = readIncomingUri(intent)
        setContent {
            CortexBridgeTheme(darkMode = darkMode) {
                CortexBridgeApp(
                    initialUri = incomingUri,
                    onToggleDarkMode = { darkMode = !darkMode }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUri = readIncomingUri(intent)
    }

    private fun readIncomingUri(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }
}

data class VideoInfo(val durationMs: Long, val width: Int, val height: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CortexBridgeApp(
    initialUri: Uri?,
    onToggleDarkMode: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var selectedUri by remember { mutableStateOf(initialUri) }
    var info by remember { mutableStateOf<VideoInfo?>(null) }
    var destination by remember { mutableStateOf(MediaConverter.Destination.WHATSAPP) }
    var quality by remember { mutableStateOf(MediaConverter.Quality.MEDIUM) }
    var aspect by remember { mutableStateOf(MediaConverter.Aspect.ORIGINAL) }
    var keepAudio by remember { mutableStateOf(false) }
    var trimStartMs by remember { mutableStateOf(0L) }
    var trimEndMs by remember { mutableStateOf(6_000L) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    var isConverting by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var conversionJob by remember { mutableStateOf<Job?>(null) }
    var history by remember { mutableStateOf(loadHistory(context)) }

    fun addHistory(uri: Uri) {
        history = listOf(uri) + history.filterNot { it == uri }
        persistHistory(context, history)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUri = uri
            outputFile = null
            savedUri = null
        }
    }
    val writePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) saveOutput(context, outputFile, destination, snackbar, scope) { uri ->
            savedUri = uri
            addHistory(uri)
        }
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null) selectedUri = initialUri
    }
    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        info = withContext(Dispatchers.IO) { readVideoInfo(context, uri) }
        trimStartMs = 0
        trimEndMs = info?.durationMs?.coerceAtLeast(1_000L) ?: 6_000L
    }
    LaunchedEffect(destination, info) {
        if (destination == MediaConverter.Destination.WHATSAPP) {
            trimEndMs = minOf(trimEndMs, trimStartMs + 6_000L)
        }
    }

    fun startConversion() {
        val source = selectedUri ?: return
        val videoInfo = info ?: VideoInfo(trimEndMs, 0, 0)
        conversionJob?.cancel()
        isConverting = true
        progress = 0
        outputFile = null
        savedUri = null
        conversionJob = scope.launch {
            try {
                outputFile = MediaConverter.convert(
                    context = context,
                    input = source,
                    options = MediaConverter.Options(
                        destination = destination,
                        startMs = trimStartMs,
                        endMs = trimEndMs,
                        durationMs = videoInfo.durationMs,
                        sourceWidth = videoInfo.width,
                        sourceHeight = videoInfo.height,
                        quality = quality,
                        aspect = aspect,
                        removeAudio = destination == MediaConverter.Destination.WHATSAPP || !keepAudio
                    ),
                    onProgress = { value -> scope.launch { progress = value } }
                )
                snackbar.showSnackbar("Conversión terminada")
            } catch (_: CancellationException) {
                snackbar.showSnackbar("Conversión cancelada")
            } catch (error: Exception) {
                snackbar.showSnackbar("No se pudo convertir: ${error.message ?: "formato no compatible"}")
            } finally {
                isConverting = false
                conversionJob = null
            }
        }
    }

    val maxSeconds = max(1f, (info?.durationMs ?: 6_000L) / 1_000f)
    val currentRange = trimStartMs / 1_000f..(trimEndMs.coerceAtLeast(trimStartMs + 1_000L) / 1_000f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(com.cristopher.cortexbridge.R.drawable.ic_cortexbridge),
                            contentDescription = "Logo de CortexBridge",
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("CortexBridge")
                    }
                },
                actions = {
                    TextButton(onClick = onToggleDarkMode) {
                        Icon(Icons.Default.DarkMode, "Tema oscuro")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Tu puente entre TikTok y WhatsApp", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Edita, convierte, guarda y comparte videos cortos. Los archivos se procesan localmente.",
                style = MaterialTheme.typography.bodyMedium
            )

            if (selectedUri != null) {
                keyVideoPreview(selectedUri!!)
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. Dirección", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = destination == MediaConverter.Destination.WHATSAPP,
                            onClick = { destination = MediaConverter.Destination.WHATSAPP; keepAudio = false; outputFile = null },
                            label = { Text("TikTok → WhatsApp") },
                            leadingIcon = { Icon(Icons.Default.ArrowForward, null, Modifier.size(16.dp)) }
                        )
                        FilterChip(
                            selected = destination == MediaConverter.Destination.TIKTOK,
                            onClick = { destination = MediaConverter.Destination.TIKTOK; outputFile = null },
                            label = { Text("WhatsApp → TikTok") },
                            leadingIcon = { Icon(Icons.Default.ArrowForward, null, Modifier.size(16.dp)) }
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("2. Video", fontWeight = FontWeight.SemiBold)
                    Text(if (selectedUri == null) "Selecciona un archivo de video." else "${info?.let { formatTime(it.durationMs) } ?: "Analizando…"} · ${info?.width ?: "?"}×${info?.height ?: "?"}")
                    OutlinedButton(onClick = { picker.launch("video/*") }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.VideoLibrary, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Elegir video o GIF")
                    }
                }
            }

            if (info != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("3. Recorte", fontWeight = FontWeight.SemiBold)
                        Text("Desde ${formatTime(trimStartMs)} hasta ${formatTime(trimEndMs)}")
                        RangeSlider(
                            value = currentRange,
                            onValueChange = { range ->
                                trimStartMs = (range.start * 1_000).toLong()
                                trimEndMs = (range.endInclusive * 1_000).toLong().coerceAtLeast(trimStartMs + 1_000L)
                            },
                            valueRange = 0f..maxSeconds,
                            steps = 0
                        )
                        if (destination == MediaConverter.Destination.WHATSAPP) {
                            Text("WhatsApp/GIF se limita a 6 segundos.", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("4. Exportación", fontWeight = FontWeight.SemiBold)
                        Text("Calidad")
                        OptionRow(MediaConverter.Quality.entries.map { it.label }, quality.label) { label ->
                            quality = MediaConverter.Quality.entries.first { it.label == label }
                        }
                        Text("Formato")
                        OptionRow(MediaConverter.Aspect.entries.map { it.label }, aspect.label) { label ->
                            aspect = MediaConverter.Aspect.entries.first { it.label == label }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = keepAudio && destination == MediaConverter.Destination.TIKTOK,
                                onCheckedChange = { keepAudio = it },
                                enabled = destination == MediaConverter.Destination.TIKTOK
                            )
                            Text("Conservar audio para TikTok")
                        }
                    }
                }
            }

            if (isConverting) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text("Convirtiendo… $progress%")
                        }
                        LinearProgressIndicator(progress = { progress / 100f }, Modifier.fillMaxWidth())
                        OutlinedButton(onClick = { conversionJob?.cancel() }, Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Cancel, null)
                            Spacer(Modifier.size(8.dp))
                            Text("Cancelar")
                        }
                    }
                }
            } else {
                Button(
                    onClick = { startConversion() },
                    enabled = selectedUri != null && info != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Convertir") }
            }

            outputFile?.let { file ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(8.dp))
                            Text("Resultado listo", fontWeight = FontWeight.SemiBold)
                        }
                        Text(file.name, style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { shareFile(context, file, destination) }, Modifier.weight(1f)) {
                                Icon(Icons.Default.Share, null)
                                Spacer(Modifier.size(4.dp))
                                Text("Compartir")
                            }
                            OutlinedButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT <= 28 && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                                        writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    } else {
                                        saveOutput(context, file, destination, snackbar, scope) { uri ->
                                            savedUri = uri
                                            addHistory(uri)
                                        }
                                    }
                                },
                                Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Save, null)
                                Spacer(Modifier.size(4.dp))
                                Text(if (savedUri == null) "Guardar" else "Guardado")
                            }
                        }
                    }
                }
            }

            if (history.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Historial", fontWeight = FontWeight.SemiBold)
                        history.take(5).forEachIndexed { index, _ -> Text("• Video guardado ${index + 1}") }
                    }
                }
            }

            Text(
                "Nota: WhatsApp normalmente usa MP4 silencioso para sus GIFs; no se genera un .gif real porque ese formato pesa más y tiene menor compatibilidad.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun keyVideoPreview(uri: Uri) {
    androidx.compose.runtime.key(uri) {
        AndroidView(
            factory = { viewContext ->
                VideoView(viewContext).apply {
                    setVideoURI(uri)
                    setOnPreparedListener { player -> player.isLooping = true; start() }
                }
            },
            modifier = Modifier.fillMaxWidth().height(220.dp)
        )
    }
}

@Composable
private fun OptionRow(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = selected == option, onClick = { onSelected(option) }, label = { Text(option) })
        }
    }
}

private fun readVideoInfo(context: android.content.Context, uri: Uri): VideoInfo {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        VideoInfo(
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 6_000L,
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        )
    } finally {
        retriever.release()
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun saveOutput(
    context: android.content.Context,
    file: File?,
    destination: MediaConverter.Destination,
    snackbar: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    onSaved: (Uri) -> Unit
) {
    if (file == null) return
    scope.launch(Dispatchers.IO) {
        val name = "cortexbridge_${destination.name.lowercase()}_${System.currentTimeMillis()}.mp4"
        val uri = GallerySaver.saveVideo(context, file, name)
        withContext(Dispatchers.Main) {
            if (uri != null) {
                onSaved(uri)
                snackbar.showSnackbar("Guardado en la galería")
            } else {
                snackbar.showSnackbar("No se pudo guardar el video")
            }
        }
    }
}

private fun shareFile(context: android.content.Context, file: File, destination: MediaConverter.Destination) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("CortexBridge", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Compartir en ${if (destination == MediaConverter.Destination.WHATSAPP) "WhatsApp" else "TikTok"}"))
}

private fun loadHistory(context: android.content.Context): List<Uri> =
    context.getSharedPreferences("cortexbridge", 0)
        .getStringSet("history", emptySet())
        ?.map(Uri::parse)
        .orEmpty()

private fun persistHistory(context: android.content.Context, history: List<Uri>) {
    context.getSharedPreferences("cortexbridge", 0)
        .edit()
        .putStringSet("history", history.take(20).map(Uri::toString).toSet())
        .apply()
}

@Composable
private fun CortexBridgeTheme(darkMode: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkMode) darkColorScheme() else lightColorScheme(),
        content = content
    )
}
