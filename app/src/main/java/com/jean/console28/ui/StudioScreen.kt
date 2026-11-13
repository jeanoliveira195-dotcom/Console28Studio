package com.jean.console28.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jean.console28.audio.ConsoleSettings
import com.jean.console28.audio.RecordingInfo
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import kotlin.math.max

private val Gold = Color(0xFFD6B574)
private val Background = Color(0xFF101218)
private val Panel = Color(0xFF1C202A)
private val Pale = Color(0xFFE9E0D2)
private val Muted = Color(0xFFB8B6BC)
private val Red = Color(0xFFEF8073)

@Composable
fun StudioScreen(
    tab: Int,
    onTab: (Int) -> Unit,
    settings: ConsoleSettings,
    onSettings: (ConsoleSettings) -> Unit,
    recording: Boolean,
    recordings: List<RecordingInfo>,
    currentRecording: RecordingInfo?,
    onChooseRecording: (RecordingInfo) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPlayRecording: (RecordingInfo) -> Unit,
    onExportProcessed: (RecordingInfo) -> Unit,
    onExportOriginal: (RecordingInfo) -> Unit,
    onPickMusic: () -> Unit,
    onTogglePlay: () -> Unit,
    isPlaying: Boolean,
    isProcessedPreview: Boolean,
    title: String,
    message: String,
    positionMs: () -> Long,
    durationMs: () -> Long,
    onSeek: (Long) -> Unit,
) {
    val scheme = darkColorScheme(primary = Gold, onPrimary = Background, background = Background,
        surface = Panel, onSurface = Pale, onBackground = Pale, secondary = Gold)
    MaterialTheme(colorScheme = scheme) {
        Surface(color = Background, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("C O N S O L E  /  2 8", color = Gold, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Text("ANALOG STUDIO  ·  EDIÇÃO PESSOAL", color = Muted, fontSize = 11.sp)
                Text("Player e gravação inspirados nos consoles britânicos dos anos 1970.", color = Pale)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TabButton("REPRODUZIR", tab == 0, { onTab(0) }, Modifier.weight(1f))
                    TabButton("GRAVAR VOZ", tab == 1, { onTab(1) }, Modifier.weight(1f))
                }
                if (tab == 0) {
                    PlaybackCard(
                        title = title, isPlaying = isPlaying, onPickMusic = onPickMusic,
                        onTogglePlay = onTogglePlay, positionMs = positionMs,
                        durationMs = durationMs, onSeek = onSeek,
                        isProcessedPreview = isProcessedPreview
                    )
                } else {
                    RecorderCard(recording, currentRecording, recordings, onStartRecording,
                        onStopRecording, onChooseRecording, onPlayRecording,
                        onExportProcessed, onExportOriginal)
                }
                SettingsCard(settings, onSettings, enabled = !recording)
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF242834)),
                    shape = RoundedCornerShape(12.dp)) {
                    Text(message, color = Pale, modifier = Modifier.padding(14.dp), fontSize = 13.sp)
                }
                Text("Emulação autoral inspirada em circuitos analógicos. Não é uma Neve 8028 real nem produto afiliado à AMS Neve. Sempre faça uma gravação de teste antes de produzir conteúdo importante.",
                    color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun TabButton(text: String, selected: Boolean, click: () -> Unit, modifier: Modifier) {
    if (selected) Button(onClick = click, modifier = modifier) { Text(text, fontSize = 12.sp) }
    else OutlinedButton(onClick = click, modifier = modifier) { Text(text, fontSize = 12.sp) }
}

@Composable
private fun PlaybackCard(
    title: String,
    isPlaying: Boolean,
    onPickMusic: () -> Unit,
    onTogglePlay: () -> Unit,
    positionMs: () -> Long,
    durationMs: () -> Long,
    onSeek: (Long) -> Unit,
    isProcessedPreview: Boolean,
) {
    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            position = positionMs().toFloat()
            duration = durationMs().toFloat()
            delay(400)
        }
    }
    SectionCard {
        Text("LEITOR DE ÁUDIO", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Slider(
            value = if (duration > 0) (position / duration).coerceIn(0f, 1f) else 0f,
            onValueChange = { onSeek((it * duration).toLong()) },
            enabled = duration > 0f
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(clock(position.toLong()), color = Muted, fontSize = 12.sp)
            Text(clock(duration.toLong()), color = Muted, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPickMusic, modifier = Modifier.weight(1f)) { Text("ABRIR MP3/FLAC", fontSize = 12.sp) }
            OutlinedButton(onClick = onTogglePlay, modifier = Modifier.weight(1f)) { Text(if (isPlaying) "PAUSAR" else "TOCAR", fontSize = 12.sp) }
        }
        if (isProcessedPreview) {
            Text("O WAV selecionado já foi tratado: o efeito não será aplicado novamente.", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RecorderCard(
    recording: Boolean,
    currentRecording: RecordingInfo?,
    recordings: List<RecordingInfo>,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onChooseRecording: (RecordingInfo) -> Unit,
    onPlayRecording: (RecordingInfo) -> Unit,
    onExportProcessed: (RecordingInfo) -> Unit,
    onExportOriginal: (RecordingInfo) -> Unit,
) {
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(recording) {
        if (recording) {
            elapsed = 0
            while (true) { delay(1000); elapsed++ }
        }
    }
    SectionCard {
        Text("GRAVAÇÃO DE NARRAÇÃO", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(if (recording) "●  GRAVANDO  ${clock(elapsed * 1000L)}" else "MICROFONE PRONTO",
            color = if (recording) Red else Pale, fontWeight = FontWeight.Bold)
        Text("Captação mono · WAV PCM 16-bit · 48 kHz (ou 44,1 kHz se necessário).",
            fontSize = 12.sp, color = Muted)
        Text("Salva o áudio original e o áudio tratado separadamente. Não reproduz a voz ao vivo, evitando retorno/eco.",
            fontSize = 12.sp, color = Muted)
        if (recording) {
            Button(onClick = onStopRecording, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("PARAR E SALVAR") }
        } else {
            Button(onClick = onStartRecording, modifier = Modifier.fillMaxWidth()) { Text("INICIAR GRAVAÇÃO") }
        }
    }
    if (currentRecording != null) {
        SectionCard {
            Text("ÚLTIMA GRAVAÇÃO", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(currentRecording.processed.name, maxLines = 2, fontSize = 13.sp)
            Text("${currentRecording.sampleRate} Hz · ${bytes(currentRecording.processed)}", color = Muted, fontSize = 12.sp)
            OutlinedButton(onClick = { onPlayRecording(currentRecording) }, enabled = !recording,
                modifier = Modifier.fillMaxWidth()) { Text("OUVIR VERSÃO TRATADA") }
            Button(onClick = { onExportProcessed(currentRecording) }, enabled = !recording,
                modifier = Modifier.fillMaxWidth()) { Text("EXPORTAR WAV PARA VÍDEO") }
            OutlinedButton(onClick = { onExportOriginal(currentRecording) },
                enabled = !recording && currentRecording.original != null,
                modifier = Modifier.fillMaxWidth()) { Text("EXPORTAR WAV ORIGINAL") }
        }
    }
    if (recordings.isNotEmpty()) {
        SectionCard {
            Text("MINHAS GRAVAÇÕES", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            recordings.take(20).forEach { rec ->
                OutlinedButton(onClick = { onChooseRecording(rec) }, enabled = !recording,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(rec.processed.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                }
            }
            Text("Os arquivos ficam guardados no aplicativo até serem exportados ou os dados do app serem apagados.",
                color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingsCard(s: ConsoleSettings, update: (ConsoleSettings) -> Unit, enabled: Boolean) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("CIRCUITO ANALÓGICO", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("MOTOR DE ÁUDIO RUST · DSP NATIVO", color = Muted, fontSize = 10.sp)
            }
            Switch(checked = s.enabled, onCheckedChange = { update(s.copy(enabled = it)) }, enabled = enabled)
        }
        HorizontalDivider(color = Color(0xFF343946))
        Text("PRESETS", color = Muted, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedButton(onClick = { update(ConsoleSettings.narration()) }, enabled = enabled,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp), modifier = Modifier.weight(1f)) {
                Text("VOZ", fontSize = 11.sp)
            }
            OutlinedButton(onClick = { update(ConsoleSettings.music()) }, enabled = enabled,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp), modifier = Modifier.weight(1f)) {
                Text("MÚSICA", fontSize = 11.sp)
            }
            OutlinedButton(onClick = { update(ConsoleSettings.vintage()) }, enabled = enabled,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp), modifier = Modifier.weight(1f)) {
                Text("VINTAGE", fontSize = 11.sp)
            }
        }
        Dial("SATURAÇÃO", s.drive, enabled) { update(s.copy(drive = it)) }
        Dial("CALOR / GRAVES", s.warmth, enabled) { update(s.copy(warmth = it)) }
        Dial("PRESENÇA / VOZ", s.presence, enabled) { update(s.copy(presence = it)) }
        Dial("BRILHO", s.air, enabled) { update(s.copy(air = it)) }
        Dial("MISTURA DO EFEITO", s.mix, enabled) { update(s.copy(mix = it)) }
        Text("SAÍDA  ${String.format(Locale.US, "%+.1f dB", s.outputDb)}", color = Pale,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Slider(value = s.outputDb.coerceIn(-18f, 6f), onValueChange = { update(s.copy(outputDb = it)) },
            valueRange = -18f..6f, enabled = enabled)
        Text("Ajustes feitos durante a reprodução são ouvidos imediatamente. Ao começar uma gravação, os parâmetros são fixados para aquela tomada.",
            color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun Dial(label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Pale, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text("${(value.coerceIn(0f,1f) * 100).toInt()}%", color = Gold, fontSize = 12.sp)
    }
    Slider(value = value.coerceIn(0f, 1f), onValueChange = onChange, enabled = enabled)
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF343846)),
        colors = CardDefaults.cardColors(containerColor = Panel),
        modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp),
            content = content)
    }
}

private fun clock(milliseconds: Long): String {
    val seconds = max(0L, milliseconds) / 1000
    return "%02d:%02d".format(Locale.US, seconds / 60, seconds % 60)
}

private fun bytes(file: File): String = "%.1f MB".format(Locale.US, file.length().toDouble() / (1024 * 1024))
