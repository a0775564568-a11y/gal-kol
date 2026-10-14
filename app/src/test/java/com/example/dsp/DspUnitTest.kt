package com.example.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class DspUnitTest {

    @Test
    fun testSilentSignal() {
        val silentBuffer = ShortArray(2048) { 0 }
        val (metrics, spectrum) = AudioMetricsCalculator.calculateMetrics(
            pcmBuffer = silentBuffer,
            sampleRate = 44100,
            fftSize = 2048
        )

        assertEquals(0, metrics.peakAmplitude)
        assertEquals(0.0, metrics.rms, 1e-6)
        assertEquals(-100.0, metrics.rmsDbfs, 1e-3)
        assertEquals(-100.0, metrics.peakDbfs, 1e-3)
        assertFalse(metrics.hasClipping)
        assertEquals(0, metrics.clippingCount)
        assertEquals(0f, metrics.dominantFrequencyHz, 1e-3f)
    }

    @Test
    fun testSineWaveKnownFrequency() {
        // Generate a 1000 Hz pure sine wave at 44.1 kHz sample rate
        val sampleRate = 44100
        val targetFreq = 1000.0
        val size = 2048
        val buffer = ShortArray(size)

        for (i in 0 until size) {
            val sample = 20000.0 * sin(2.0 * PI * targetFreq * i / sampleRate)
            buffer[i] = sample.toInt().toShort()
        }

        val (metrics, _) = AudioMetricsCalculator.calculateMetrics(
            pcmBuffer = buffer,
            sampleRate = sampleRate,
            fftSize = size,
            windowType = WindowType.HANN
        )

        // FFT bin resolution is 44100 / 2048 ~= 21.5 Hz. With parabolic interpolation it should be within 10 Hz!
        assertTrue(
            "Detected freq ${metrics.dominantFrequencyHz} Hz should be close to $targetFreq Hz",
            abs(metrics.dominantFrequencyHz - targetFreq) < 15.0
        )
    }

    @Test
    fun testKnownAmplitudeRmsAndPeak() {
        // Sine wave with amplitude A = 16384 (exactly -6.02 dBFS peak)
        val amplitude = 16384.0
        val size = 4410
        val buffer = ShortArray(size)

        for (i in 0 until size) {
            val sample = amplitude * sin(2.0 * PI * 440.0 * i / 44100.0)
            buffer[i] = sample.toInt().toShort()
        }

        val (metrics, _) = AudioMetricsCalculator.calculateMetrics(
            pcmBuffer = buffer,
            sampleRate = 44100,
            fftSize = 2048
        )

        // Theoretical RMS for sine wave is A / sqrt(2) = 16384 / 1.4142 = 11585.2
        val expectedRms = amplitude / sqrt(2.0)
        assertTrue("Expected RMS ~$expectedRms, got ${metrics.rms}", abs(metrics.rms - expectedRms) < 200.0)

        // Peak dBFS should be approx -6.02 dBFS
        assertTrue("Peak dBFS should be ~ -6.0 dBFS, got ${metrics.peakDbfs}", abs(metrics.peakDbfs - (-6.02)) < 0.2)
        // RMS dBFS should be approx -9.03 dBFS
        assertTrue("RMS dBFS should be ~ -9.0 dBFS, got ${metrics.rmsDbfs}", abs(metrics.rmsDbfs - (-9.03)) < 0.3)
    }

    @Test
    fun testSignalWithClipping() {
        val size = 1000
        val buffer = ShortArray(size)

        // Insert clipped samples
        for (i in 0 until 50) {
            buffer[i] = 32767.toShort()
        }
        for (i in 50 until 100) {
            buffer[i] = (-32768).toShort()
        }

        val (metrics, _) = AudioMetricsCalculator.calculateMetrics(
            pcmBuffer = buffer,
            sampleRate = 44100,
            fftSize = 1024
        )

        assertTrue(metrics.hasClipping)
        assertEquals(100, metrics.clippingCount)
        assertEquals(32768, metrics.peakAmplitude)
        assertEquals(0.0, metrics.peakDbfs, 0.05)
    }

    @Test
    fun testStereoToMonoDownmix() {
        val stereoInterleaved = shortArrayOf(
            1000, 2000,   // sample 0 (mono = 1500)
            -500, 500,    // sample 1 (mono = 0)
            10000, 10000  // sample 2 (mono = 10000)
        )
        val monoLength = stereoInterleaved.size / 2
        val mono = ShortArray(monoLength)

        for (i in 0 until monoLength) {
            val left = stereoInterleaved[i * 2].toInt()
            val right = stereoInterleaved[i * 2 + 1].toInt()
            mono[i] = ((left + right) / 2).toShort()
        }

        assertEquals(1500.toShort(), mono[0])
        assertEquals(0.toShort(), mono[1])
        assertEquals(10000.toShort(), mono[2])
    }

    @Test
    fun testShortAndLongAudioBuffers() {
        // Very short buffer (64 samples)
        val shortBuffer = ShortArray(64) { 5000 }
        val (shortMetrics, _) = AudioMetricsCalculator.calculateMetrics(shortBuffer, sampleRate = 44100, fftSize = 64)
        assertEquals(5000, shortMetrics.peakAmplitude)
        assertFalse(shortMetrics.hasClipping)

        // Long buffer (8192 samples)
        val longBuffer = ShortArray(8192) { (sin(it.toDouble()) * 10000).toInt().toShort() }
        val (longMetrics, _) = AudioMetricsCalculator.calculateMetrics(longBuffer, sampleRate = 44100, fftSize = 2048)
        assertTrue(longMetrics.peakAmplitude > 9000)
    }

    @Test
    fun testHannWindowingFunction() {
        val testData = DoubleArray(100) { 1.0 }
        val windowed = WindowFunction.apply(testData, WindowType.HANN)

        // Endpoints of Hann window should be 0.0
        assertEquals(0.0, windowed[0], 1e-4)
        assertEquals(0.0, windowed[99], 1e-4)
        // Center of Hann window should be 1.0
        assertEquals(1.0, windowed[49], 0.05)
    }
}
