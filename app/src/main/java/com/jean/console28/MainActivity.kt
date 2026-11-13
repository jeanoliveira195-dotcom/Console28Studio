package com.jean.console28

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.jean.console28.audio.ConsoleSettings
import com.jean.console28.audio.MicrophoneRecorder
import com.jean.console28.audio.RecordingInfo
import com.jean.console28.audio.StudioPlayer
import com.jean.console28.ui.StudioScreen
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@androidx.annotation.OptIn(UnstableApi::class)
class MainActivity : ComponentActivity() {
    private var settings by mutableStateOf(ConsoleSettings.narration())
    private val settingsRef = AtomicReference(settings)
    private lateinit var audio: StudioPlayer
    private lateinit var recorder: MicrophoneRecorder

    private var tab by mutableStateOf(0)
    private var recording by mutableStateOf(false)
    private var isPlaying by mutableStateOf(false)
    private var title by mutableStateOf("Nenhuma música selecionada")
    private var message by mutableStateOf("Tudo é processado localmente, sem internet.")
    private var recordings by mutableStateOf<List<RecordingInfo>>(emptyList())
    private var currentRecording by mutableStateOf<RecordingInfo?>(null)
    private var exportFile: File? = null

    private val chooseAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) { /* Permissão temporária é suficiente nesta sessão. */ }
        audio.open(uri)
        title = uri.lastPathSegment?.substringAfterLast('/') ?: "Música importada"
        message = "Música carregada. Ative ou desative o efeito para comparar."
        updateDsp()
        tab = 0
    }

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) beginRecording() else message = "Permissão do microfone negada. A gravação não foi iniciada."
    }

    private val saveWav = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
        val source = exportFile
        exportFile = null
        if (uri == null || source == null) return@registerForActivityResult
        Thread {
            try {
                check(source.exists()) { "Arquivo de origem não encontrado" }
                contentResolver.openOutputStream(uri, "w")?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output, 64 * 1024) }
                } ?: error("Não foi possível criar o arquivo de destino")
                runOnUiThread { message = "WAV exportado. Importe-o no editor de vídeo." }
            } catch (e: Exception) {
                runOnUiThread { message = "Falha ao exportar: ${e.message}" }
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recorder = MicrophoneRecorder(applicationContext)
        audio = StudioPlayer(applicationContext, settingsRef)
        recordings = recorder.recordings()
        audio.player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlayerError(error: PlaybackException) {
                message = "Erro de reprodução: ${error.message ?: "formato não suportado"}"
            }
        })
        setContent {
            StudioScreen(
                tab = tab,
                onTab = { if (!recording) tab = it },
                settings = settings,
                onSettings = { s -> settings = s; updateDsp() },
                recording = recording,
                recordings = recordings,
                currentRecording = currentRecording,
                onChooseRecording = { currentRecording = it },
                onStartRecording = ::askToRecord,
                onStopRecording = { recorder.stop(); message = "Finalizando os dois arquivos WAV..." },
                onPlayRecording = ::playRecording,
                onExportProcessed = { r -> exportWav(r.processed) },
                onExportOriginal = { r -> r.original?.let(::exportWav) },
                onPickMusic = { chooseAudio.launch(arrayOf("audio/*")) },
                onTogglePlay = { if (audio.player.isPlaying) audio.player.pause() else audio.player.play() },
                isPlaying = isPlaying,
                isProcessedPreview = audio.isProcessedPreview(),
                title = title,
                message = message,
                positionMs = { audio.player.currentPosition.coerceAtLeast(0) },
                durationMs = { audio.player.duration.coerceAtLeast(0) },
                onSeek = { ms -> audio.player.seekTo(ms) },
            )
        }
    }

    private fun updateDsp() {
        settingsRef.set(if (audio.isProcessedPreview()) settings.copy(enabled = false) else settings)
    }

    private fun askToRecord() {
        if (recording) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            beginRecording()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun beginRecording() {
        if (recording) return
        audio.player.pause() // Evita capturar música tocando nos alto-falantes.
        val frozenSettings = settings
        val began = recorder.start(frozenSettings) { result, error ->
            runOnUiThread {
                recording = false
                recordings = recorder.recordings()
                currentRecording = result ?: recordings.firstOrNull()
                message = if (error != null) "Falha ao gravar: $error"
                    else "Gravação concluída. WAV tratado e original disponíveis para exportação."
            }
        }
        if (began) {
            recording = true
            tab = 1
            message = "Gravando... controles congelados até a finalização."
        }
    }

    private fun playRecording(r: RecordingInfo) {
        audio.open(Uri.fromFile(r.processed), alreadyProcessed = true)
        updateDsp() // Não processar duas vezes o WAV que já contém o efeito.
        title = r.processed.name
        currentRecording = r
        message = "Prévia do WAV tratado — sem processamento duplicado."
        audio.player.play()
    }

    private fun exportWav(source: File) {
        exportFile = source
        saveWav.launch(source.name)
    }

    override fun onStop() {
        super.onStop()
        if (recorder.isRecording) recorder.stop() // MVP: não usa serviço de gravação em segundo plano.
    }

    override fun onDestroy() {
        recorder.stop()
        audio.release()
        super.onDestroy()
    }
}
