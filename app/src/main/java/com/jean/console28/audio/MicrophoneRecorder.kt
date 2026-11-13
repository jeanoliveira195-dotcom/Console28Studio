package com.jean.console28.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/** Grava simultaneamente sinal seco e processado em WAV mono PCM 16-bit. */
data class RecordingInfo(val processed: File, val original: File?, val sampleRate: Int)

class MicrophoneRecorder(private val context: Context) {
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var currentRecord: AudioRecord? = null

    val isRecording: Boolean get() = running.get()

    fun recordings(): List<RecordingInfo> {
        val dir = File(context.filesDir, "recordings")
        return dir.listFiles { file -> file.isFile && file.name.endsWith("_console.wav") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { wet ->
                val dry = File(dir, wet.name.removeSuffix("_console.wav") + "_original.wav")
                RecordingInfo(wet, dry.takeIf { it.exists() }, readWavSampleRate(wet))
            } ?: emptyList()
    }

    fun start(settings: ConsoleSettings, onComplete: (RecordingInfo?, String?) -> Unit): Boolean {
        if (!running.compareAndSet(false, true)) return false
        worker = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            var mic: AudioRecord? = null
            var wetWriter: WavWriter? = null
            var dryWriter: WavWriter? = null
            var wetFile: File? = null
            var dryFile: File? = null
            var failure: String? = null
            try {
                val pair = createRecorder()
                mic = pair.first
                currentRecord = mic
                val rate = pair.second
                val dir = File(context.filesDir, "recordings")
                check(dir.exists() || dir.mkdirs()) { "Não foi possível criar a pasta de gravações" }
                val tag = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
                wetFile = File(dir, "gravacao_${tag}_console.wav")
                dryFile = File(dir, "gravacao_${tag}_original.wav")
                wetWriter = WavWriter(wetFile, rate, 1)
                dryWriter = WavWriter(dryFile, rate, 1)
                val dsp = ConsoleDsp(rate, 1)
                val input = ShortArray(2048)
                mic.startRecording()
                check(mic.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microfone não iniciou" }
                try {
                    while (running.get()) {
                        val read = mic.read(input, 0, input.size, AudioRecord.READ_BLOCKING)
                        if (read < 0) error("Erro de captura do microfone: $read")
                        if (read == 0) continue
                        dryWriter.append(input, read)
                        val treated = input.copyOf(read)
                        dsp.process(treated, read, settings)
                        wetWriter.append(treated, read)
                    }
                } finally {
                    dsp.close()
                }
            } catch (e: Exception) {
                failure = e.message ?: e.javaClass.simpleName
            } finally {
                running.set(false)
                try { if (mic?.recordingState == AudioRecord.RECORDSTATE_RECORDING) mic.stop() } catch (_: Exception) { }
                try { mic?.release() } catch (_: Exception) { }
                currentRecord = null
                try { wetWriter?.close() } catch (e: Exception) { failure = e.message ?: "Erro ao finalizar WAV tratado" }
                try { dryWriter?.close() } catch (e: Exception) { failure = e.message ?: "Erro ao finalizar WAV original" }
                if (failure != null) {
                    wetFile?.delete(); dryFile?.delete()
                    onComplete(null, failure)
                } else if (wetFile?.exists() == true) {
                    onComplete(RecordingInfo(wetFile, dryFile, readWavSampleRate(wetFile)), null)
                } else {
                    onComplete(null, "A gravação não gerou um arquivo")
                }
            }
        }, "Console28-AudioRecord")
        worker?.start()
        return true
    }

    fun stop() {
        running.set(false)
        // read() é bloqueante por um bloco curto; a thread encerra e atualiza os cabeçalhos.
    }

    @SuppressLint("MissingPermission") // Permissão checada em MainActivity antes de iniciar.
    private fun createRecorder(): Pair<AudioRecord, Int> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val unprocessed = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val source = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
        for (rate in intArrayOf(48000, 44100)) {
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0) continue
            try {
                val audioRecord = AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build())
                    .setBufferSizeInBytes(max(min * 2, 16384))
                    .build()
                if (audioRecord.state == AudioRecord.STATE_INITIALIZED) return audioRecord to rate
                audioRecord.release()
            } catch (_: IllegalArgumentException) {
                // Alguns aparelhos recusam 48 kHz mesmo quando getMinBufferSize é válido.
            } catch (_: IllegalStateException) {
                // Tenta a próxima frequência antes de informar falha.
            }
        }
        error("Microfone não oferece captura PCM compatível")
    }

    private fun readWavSampleRate(file: File): Int = try {
        java.io.RandomAccessFile(file, "r").use { f ->
            f.seek(24)
            (0..3).sumOf { index -> f.readUnsignedByte() shl (8 * index) }
        }
    } catch (_: Exception) { 48000 }
}
