package com.jean.console28.audio

import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A visualização monitora o PCM tratado; não altera nem envia dados de áudio. */
data class SpectrumFrame(
    val bands: FloatArray,
    val left: Float,
    val right: Float,
    val sequence: Long
)

/**
 * Espectro leve de 16 bandas com análise Goertzel em blocos de 1024 frames.
 * Chamado exclusivamente pelo thread de áudio. O UI só lê snapshots imutáveis.
 * A análise fornece intensidade aproximada por bandas, não um analisador calibrado.
 */
class AudioSpectrumMonitor {
    private val hz = doubleArrayOf(
        47.0, 70.0, 100.0, 140.0, 200.0, 300.0, 450.0, 650.0,
        950.0, 1400.0, 2100.0, 3150.0, 4700.0, 6800.0, 9700.0, 14000.0
    )
    private val n = 1024
    private val samples = FloatArray(n)
    private val window = DoubleArray(n) { i -> 0.5 - 0.5 * cos(2.0 * PI * i / (n - 1)) }
    private val coeffs = DoubleArray(hz.size)
    private val smoothed = FloatArray(hz.size)
    private val latest = AtomicReference(SpectrumFrame(FloatArray(hz.size), 0f, 0f, 0L))
    private var rate = 48_000
    private var channels = 2
    private var cursor = 0
    private var countFrames = 0
    private var sinceUpdate = 0
    private var leftEnergy = 0.0
    private var rightEnergy = 0.0
    private var measurementCount = 0
    private var sequence = 0L

    init { configure(48_000, 2) }

    fun configure(sampleRate: Int, channelCount: Int) {
        require(sampleRate in 8_000..192_000 && channelCount in 1..8)
        rate = sampleRate
        channels = channelCount
        hz.forEachIndexed { i, freq ->
            val safeHz = freq.coerceAtMost(rate * 0.45)
            coeffs[i] = 2.0 * cos(2.0 * PI * safeHz / rate)
        }
        reset()
    }

    fun reset() {
        samples.fill(0f)
        smoothed.fill(0f)
        cursor = 0
        countFrames = 0
        sinceUpdate = 0
        leftEnergy = 0.0
        rightEnergy = 0.0
        measurementCount = 0
        sequence += 1
        latest.set(SpectrumFrame(FloatArray(hz.size), 0f, 0f, sequence))
    }

    fun snapshot(): SpectrumFrame = latest.get()

    fun process(pcm: ShortArray, count: Int) {
        if (count <= 0 || count > pcm.size || count % channels != 0) return
        var i = 0
        while (i < count) {
            val left = pcm[i].toDouble() / 32768.0
            val right = if (channels > 1) pcm[i + 1].toDouble() / 32768.0 else left
            var mono = 0.0
            for (ch in 0 until channels) mono += pcm[i + ch].toDouble() / 32768.0
            mono /= channels
            samples[cursor] = mono.toFloat()
            cursor = (cursor + 1) % n
            if (countFrames < n) countFrames++
            leftEnergy += left * left
            rightEnergy += right * right
            measurementCount++
            sinceUpdate++
            if (countFrames == n && sinceUpdate >= 2048) publishFrame()
            i += channels
        }
    }

    private fun publishFrame() {
        val output = FloatArray(hz.size)
        for (band in hz.indices) {
            // Sinal de alta frequência acima de Nyquist não possui significado.
            if (hz[band] >= rate * 0.48) {
                output[band] = 0f
                smoothed[band] = 0f
                continue
            }
            val coefficient = coeffs[band]
            var q1 = 0.0
            var q2 = 0.0
            for (j in 0 until n) {
                val sample = samples[(cursor + j) % n].toDouble() * window[j]
                val q = sample + coefficient * q1 - q2
                q2 = q1
                q1 = q
            }
            val power = (q1*q1 + q2*q2 - coefficient*q1*q2).coerceAtLeast(0.0)
            val amplitude = 4.0 * sqrt(power) / n
            val normalized = (amplitude / 0.09).coerceAtLeast(0.0).pow(0.55).coerceIn(0.0, 1.0).toFloat()
            val alpha = if (normalized > smoothed[band]) 0.78f else 0.31f
            smoothed[band] += (normalized - smoothed[band]) * alpha
            output[band] = smoothed[band]
        }
        val count = measurementCount.coerceAtLeast(1)
        val left = sqrt(leftEnergy / count).toFloat().coerceIn(0f,1f)
        val right = sqrt(rightEnergy / count).toFloat().coerceIn(0f,1f)
        sequence++
        latest.set(SpectrumFrame(output, left, right, sequence))
        sinceUpdate = 0
        leftEnergy = 0.0
        rightEnergy = 0.0
        measurementCount = 0
    }
}
