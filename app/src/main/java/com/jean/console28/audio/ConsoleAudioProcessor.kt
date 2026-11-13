package com.jean.console28.audio

import android.media.AudioFormat
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference

/** Adaptador PCM para o fluxo do Media3. */
class ConsoleAudioProcessor(
    private val parameters: AtomicReference<ConsoleSettings>
) : BaseAudioProcessor() {
    private var dsp: ConsoleDsp? = null
    private var channels = 2
    private var carry = ByteArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != AudioFormat.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channels = inputAudioFormat.channelCount
        dsp?.close()
        dsp = ConsoleDsp(inputAudioFormat.sampleRate, channels)
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        dsp?.reset()
        carry = ByteArray(0)
    }

    override fun onReset() {
        dsp?.close()
        dsp = null
        carry = ByteArray(0)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesPerFrame = 2 * channels
        val available = carry.size + inputBuffer.remaining()
        val newBytes = ByteArray(available)
        carry.copyInto(newBytes)
        inputBuffer.get(newBytes, carry.size, inputBuffer.remaining())
        val processBytes = (available / bytesPerFrame) * bytesPerFrame
        carry = newBytes.copyOfRange(processBytes, available)
        if (processBytes == 0) return
        val pcm = ShortArray(processBytes / 2)
        ByteBuffer.wrap(newBytes, 0, processBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
        dsp?.process(pcm, pcm.size, parameters.get())
        val output = replaceOutputBuffer(processBytes).order(ByteOrder.LITTLE_ENDIAN)
        output.asShortBuffer().put(pcm)
        output.position(processBytes)
        output.flip()
    }
}
