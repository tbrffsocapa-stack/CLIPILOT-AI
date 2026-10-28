package com.tbr.clipilot

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private val Navy = Color(0xFF0A1020)
private val Panel = Color(0xFF131D32)
private val Blue = Color(0xFF7695FF)
private val TextMain = Color(0xFFF2F5FF)
private val TextMuted = Color(0xFF9BA9C5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ClipilotApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipilotApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("clipilot_settings", 0) }
    var apiKey by remember { mutableStateOf(prefs.getString("gemini_key", "") ?: "") }
    var showSettings by remember { mutableStateOf(false) }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var videoName by remember { mutableStateOf("") }
    var durationMs by remember { mutableLongStateOf(0L) }
    var command by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf<EditPlan?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Escolhe um vídeo e descreve a edição que queres.") }
    val scope = rememberCoroutineScope()

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            videoUri = uri
            outputFile = null
            plan = null
            videoName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "Vídeo selecionado"
            scope.launch {
                durationMs = readDuration(context, uri)
                status = if (durationMs > 0) "Vídeo carregado · ${formatTime(durationMs)}" else "Não consegui ler a duração deste vídeo."
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Blue, background = Navy, surface = Panel, onSurface = TextMain)) {
        Scaffold(containerColor = Navy, topBar = {
            TopAppBar(
                title = { Column { Text("CLIPILOT", fontWeight = FontWeight.Black, letterSpacing = 2.sp); Text("AI VIDEO EDITOR", color = TextMuted, fontSize = 10.sp, letterSpacing = 2.sp) } },
                actions = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, contentDescription = "Configurações", tint = TextMain) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy)
            )
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Movie, contentDescription = null, tint = Blue)
                            Text("ARQUIVO DE VÍDEO", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                        Text(if (videoUri == null) "Nenhum vídeo selecionado" else videoName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
                        if (durationMs > 0) Text("Duração: ${formatTime(durationMs)}", color = TextMuted, fontSize = 13.sp)
                        Button(onClick = { videoPicker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                            Text("Selecionar vídeo")
                        }
                    }
                }

                Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Blue)
                            Text("DESCREVE A EDIÇÃO", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                        OutlinedTextField(
                            value = command, onValueChange = { command = it; plan = null }, modifier = Modifier.fillMaxWidth(), minLines = 4,
                            placeholder = { Text("Ex.: corta os primeiros 5 segundos, deixa em preto e branco e remove o áudio…", color = TextMuted) },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Blue, unfocusedBorderColor = Color(0xFF34415B), focusedTextColor = TextMain, unfocusedTextColor = TextMain),
                            shape = RoundedCornerShape(14.dp)
                        )
                        Text("Comandos disponíveis nesta versão: cortar início/fim, silenciar áudio e preto e branco.", color = TextMuted, fontSize = 12.sp)
                        Button(
                            enabled = !busy && videoUri != null && command.isNotBlank() && durationMs > 0,
                            onClick = {
                                val uri = videoUri ?: return@Button
                                busy = true; plan = null; outputFile = null; status = "A IA está interpretando o comando…"
                                scope.launch {
                                    try {
                                        val newPlan = GeminiClient(apiKey.trim()).createPlan(command, durationMs)
                                        plan = newPlan
                                        status = "Plano pronto. Confere o resumo antes de exportar."
                                    } catch (e: Exception) { status = e.message ?: "Erro ao interpretar o comando." }
                                    finally { busy = false }
                                }
                            }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)
                        ) { if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Interpretar com IA", fontWeight = FontWeight.Bold) }
                    }
                }

                if (plan != null) {
                    val p = plan!!
                    Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("PLANO DE EDIÇÃO", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text(p.summary, color = TextMain, fontWeight = FontWeight.SemiBold)
                            Text("Trecho: ${formatTime(p.startMs)} → ${formatTime(p.endMs ?: durationMs)}", color = TextMuted, fontSize = 13.sp)
                            Text("Áudio: ${if (p.muteAudio) "removido" else "mantido"} · Preto e branco: ${if (p.grayscale) "sim" else "não"}", color = TextMuted, fontSize = 13.sp)
                            Button(enabled = !busy, onClick = {
                                val uri = videoUri ?: return@Button
                                busy = true; status = "Exportando no dispositivo…"
                                scope.launch {
                                    try {
                                        outputFile = VideoEditor(context).export(uri, p)
                                        status = "Exportação concluída."
                                    } catch (e: Exception) { status = e.message ?: "Falha na exportação." }
                                    finally { busy = false }
                                }
                            }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                                if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Aplicar edição e exportar", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                outputFile?.let { file ->
                    Surface(color = Color(0xFF102D2A), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("VÍDEO EXPORTADO", color = Color(0xFF8FE4CB), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(file.absolutePath, color = TextMain, fontSize = 12.sp)
                            Text("O arquivo está salvo na pasta privada de mídia do Clipilot. A próxima etapa pode adicionar compartilhamento e galeria de projetos.", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }

                Surface(color = Color(0xFF10192B), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(status, modifier = Modifier.padding(14.dp), color = TextMuted, fontSize = 13.sp)
                }
                Text("A chave é usada para chamar a API Gemini. As requisições enviam o comando e a duração, não o arquivo de vídeo.", color = TextMuted, fontSize = 11.sp)
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Configurações da IA") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Chave da API Gemini", color = TextMuted, fontSize = 13.sp)
                    OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("AIza…") }, singleLine = true)
                    Text("Cria uma chave no Google AI Studio. Não compartilhe o APK com tua chave salva.", color = TextMuted, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { prefs.edit().putString("gemini_key", apiKey.trim()).apply(); showSettings = false }) { Text("Salvar") } },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Cancelar") } }
        )
    }
}

private suspend fun readDuration(context: android.content.Context, uri: Uri): Long = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: Exception) { 0L } finally { try { retriever.release() } catch (_: Exception) { } }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
}
