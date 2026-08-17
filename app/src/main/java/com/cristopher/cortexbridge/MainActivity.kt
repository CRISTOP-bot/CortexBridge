package com.cristopher.cortexbridge

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.cristopher.cortexbridge.media.MediaConverter
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private var incomingUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingUri = readIncomingUri(intent)
        setContent {
            CortexBridgeTheme {
                CortexBridgeApp(initialUri = incomingUri)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CortexBridgeApp(initialUri: Uri?) {
    var selectedUri by remember { mutableStateOf(initialUri) }
    var destination by remember { mutableStateOf(MediaConverter.Destination.WHATSAPP) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var isConverting by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUri = uri
            outputFile = null
        }
    }

    fun convert() {
        val source = selectedUri ?: return
        isConverting = true
        outputFile = null
        scope.launch {
            try {
                outputFile = MediaConverter.convert(
                    context = context,
                    input = source,
                    destination = destination
                )
            } catch (error: Exception) {
                snackbar.showSnackbar("No se pudo convertir el video: ${error.message ?: "formato no compatible"}")
            } finally {
                isConverting = false
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("CortexBridge") }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Lleva tus videos cortos de una app a otra",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "CortexBridge convierte el archivo a MP4 H.264. Para WhatsApp crea un video silencioso de hasta 6 segundos, que WhatsApp muestra como GIF.",
                style = MaterialTheme.typography.bodyMedium
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("1. Elige la dirección", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = destination == MediaConverter.Destination.WHATSAPP,
                            onClick = { destination = MediaConverter.Destination.WHATSAPP; outputFile = null },
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
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("2. Selecciona el video", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (selectedUri == null) "Todavía no has elegido un archivo." else "Archivo listo para convertir.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = { picker.launch("video/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Elegir video")
                    }
                }
            }

            Button(
                onClick = { convert() },
                enabled = selectedUri != null && !isConverting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isConverting) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text("Convirtiendo…")
                } else {
                    Text("Convertir")
                }
            }

            outputFile?.let { file ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(8.dp))
                            Text("Conversión lista", fontWeight = FontWeight.SemiBold)
                        }
                        Text("Archivo: ${file.name}", style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = { shareFile(context, file, destination) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, null)
                            Spacer(Modifier.size(8.dp))
                            Text("Compartir en ${if (destination == MediaConverter.Destination.WHATSAPP) "WhatsApp" else "TikTok"}")
                        }
                    }
                }
            }

            Text(
                text = "Privacidad: el archivo se procesa localmente y la copia temporal se guarda en la caché de tu teléfono.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun shareFile(
    context: android.content.Context,
    file: File,
    destination: MediaConverter.Destination
) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("CortexBridge", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(
            shareIntent,
            "Compartir en ${if (destination == MediaConverter.Destination.WHATSAPP) "WhatsApp" else "TikTok"}"
        )
    )
}

@Composable
private fun CortexBridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
