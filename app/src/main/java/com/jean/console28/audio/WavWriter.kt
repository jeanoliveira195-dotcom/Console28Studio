package com.jean.console28.audio

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

/** PCM RIFF/WAVE little-endian. Grava o cabeçalho de forma atômica ao fechar. */
class WavWriter(file: File, private val rate: Int, private val channels: Int) : Closeable {
    private val stream = RandomAccessFile(file, "rw")
    private var dataBytes = 0L
    private var closed = false

    init {
        require(rate > 0 && channels in 1..2)
        stream.setLength(0)
        stream.write(ByteArray(44))
    }

    @Synchronized
    fun append(pcm: ShortArray, sampleCount: Int) {
        check(!closed)
        require(sampleCount in 0..pcm.size && sampleCount % channels == 0)
        val addition = sampleCount.toLong() * 2
        // Limite deliberado abaixo de 2 GB para manter compatibilidade com WAV RIFF clássico.
        require(dataBytes + addition <= Int.MAX_VALUE - 44L) { "Limite de WAV atingido" }
        val bytes = ByteArray(sampleCount * 2)
        for (i in 0 until sampleCount) {
            val x = pcm[i].toInt()
            bytes[i * 2] = (x and 0xff).toByte()
            bytes[i * 2 + 1] = ((x shr 8) and 0xff).toByte()
        }
        stream.write(bytes)
        dataBytes += addition
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            stream.seek(0)
            stream.writeBytes("RIFF")
            stream.writeUInt32LE(36 + dataBytes)
            stream.writeBytes("WAVE")
            stream.writeBytes("fmt ")
            stream.writeUInt32LE(16)
            stream.writeUInt16LE(1) // PCM
            stream.writeUInt16LE(channels)
            stream.writeUInt32LE(rate.toLong())
            stream.writeUInt32LE(rate.toLong() * channels * 2)
            stream.writeUInt16LE(channels * 2)
            stream.writeUInt16LE(16)
            stream.writeBytes("data")
            stream.writeUInt32LE(dataBytes)
        } finally {
            stream.close()
        }
    }
}

private fun RandomAccessFile.writeUInt16LE(x: Int) {
    write(x and 0xff); write((x shr 8) and 0xff)
}

private fun RandomAccessFile.writeUInt32LE(x: Long) {
    repeat(4) { i -> write(((x shr (8 * i)) and 0xff).toInt()) }
}
