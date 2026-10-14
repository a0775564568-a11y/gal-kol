package com.example.audio

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encapsulates streaming writes of 16-bit PCM mono audio into standard RIFF WAV files.
 */
class WavWriter(
    private val outputFile: File,
    private val sampleRate: Int = 44100,
    private val channels: Short = 1,
    private val bitsPerSample: Short = 16
) {
    private var fos: FileOutputStream? = null
    private var totalAudioBytes: Long = 0

    init {
        fos = FileOutputStream(outputFile)
        // Write placeholder 44-byte WAV header
        fos?.write(ByteArray(44))
    }

    @Synchronized
    fun writeSamples(buffer: ShortArray, readSize: Int) {
        if (readSize <= 0) return
        val byteBuffer = ByteBuffer.allocate(readSize * 2)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until readSize) {
            byteBuffer.putShort(buffer[i])
        }
        val bytes = byteBuffer.array()
        fos?.write(bytes)
        totalAudioBytes += bytes.size
    }

    @Synchronized
    fun close() {
        fos?.flush()
        fos?.close()
        fos = null

        // Update RIFF and Data chunk headers with exact byte sizes
        try {
            RandomAccessFile(outputFile, "rw").use { raf ->
                raf.seek(0)
                raf.write(writeHeader(totalAudioBytes, sampleRate, channels, bitsPerSample))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        fun writeHeader(
            audioDataBytes: Long,
            sampleRate: Int,
            channels: Short,
            bitsPerSample: Short
        ): ByteArray {
            val totalDataLen = audioDataBytes + 36
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = (channels * bitsPerSample / 8).toShort()

            val header = ByteArray(44)
            val buffer = ByteBuffer.wrap(header)
            buffer.order(ByteOrder.LITTLE_ENDIAN)

            // "RIFF"
            buffer.put('R'.code.toByte())
            buffer.put('I'.code.toByte())
            buffer.put('F'.code.toByte())
            buffer.put('F'.code.toByte())
            buffer.putInt(totalDataLen.toInt())

            // "WAVE"
            buffer.put('W'.code.toByte())
            buffer.put('A'.code.toByte())
            buffer.put('V'.code.toByte())
            buffer.put('E'.code.toByte())

            // "fmt "
            buffer.put('f'.code.toByte())
            buffer.put('m'.code.toByte())
            buffer.put('t'.code.toByte())
            buffer.put(' '.code.toByte())
            buffer.putInt(16) // Subchunk1Size for PCM
            buffer.putShort(1) // AudioFormat 1 = PCM
            buffer.putShort(channels)
            buffer.putInt(sampleRate)
            buffer.putInt(byteRate)
            buffer.putShort(blockAlign)
            buffer.putShort(bitsPerSample)

            // "data"
            buffer.put('d'.code.toByte())
            buffer.put('a'.code.toByte())
            buffer.put('t'.code.toByte())
            buffer.put('a'.code.toByte())
            buffer.putInt(audioDataBytes.toInt())

            return header
        }
    }
}
