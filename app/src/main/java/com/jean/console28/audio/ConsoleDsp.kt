package com.jean.console28.audio

import java.io.Closeable

/** Controles Android. O processamento REAL é executado pelo núcleo nativo Rust. */
data class ConsoleSettings(
    val enabled: Boolean = true,
    val drive: Float = .18f,
    val warmth: Float = .65f,
    val presence: Float = .60f,
    val air: Float = .46f,
    val mix: Float = .82f,
    val outputDb: Float = -.7f,
    val lowCutHz: Float = 65f,
) {
    companion object {
        fun narration() = ConsoleSettings()
        fun music() = ConsoleSettings(drive = .11f, warmth = .56f, presence = .50f,
            air = .50f, mix = .58f, outputDb = -.5f, lowCutHz = 30f)
        fun vintage() = ConsoleSettings(drive = .45f, warmth = .73f, presence = .54f,
            air = .41f, mix = 1f, outputDb = -2.5f, lowCutHz = 45f)
        fun neutral() = ConsoleSettings(enabled = false, drive = 0f, warmth = .5f,
            presence = .5f, air = .5f, mix = 0f, outputDb = 0f, lowCutHz = 30f)
    }
}

/** Carrega a .so única gerada a partir de Rust + pequena ponte C/JNI. */
internal object NativeConsole28 {
    init { System.loadLibrary("console28_jni") }
    external fun create(sampleRate: Int, channels: Int): Long
    external fun destroy(handle: Long)
    external fun reset(handle: Long)
    external fun setSettings(handle: Long, enabled: Boolean, parameters: FloatArray)
    external fun process(handle: Long, pcm: ShortArray, count: Int)
}

/** Uma instância por stream. Não é thread-safe: uso sequencial no thread de áudio. */
class ConsoleDsp(sampleRate: Int, channels: Int) : Closeable {
    private var handle = NativeConsole28.create(sampleRate, channels)
    private var currentSettings: ConsoleSettings? = null

    fun reset() {
        check(handle != 0L) { "DSP encerrado" }
        NativeConsole28.reset(handle)
    }

    fun process(samples: ShortArray, count: Int, settings: ConsoleSettings) {
        check(handle != 0L) { "DSP encerrado" }
        require(count in 0..samples.size)
        if (settings != currentSettings) {
            NativeConsole28.setSettings(handle, settings.enabled, floatArrayOf(
                settings.drive, settings.warmth, settings.presence,
                settings.air, settings.mix, settings.outputDb, settings.lowCutHz
            ))
            currentSettings = settings
        }
        NativeConsole28.process(handle, samples, count)
    }

    override fun close() {
        val old = handle
        handle = 0L
        if (old != 0L) NativeConsole28.destroy(old)
    }
}
