package com.example.dsp

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Calculates physical and digital audio metrics from raw 16-bit PCM buffers.
 * Includes guards against division by zero, NaN, and negative logarithms.
 */
object AudioMetricsCalculator {

    const val FULL_SCALE_16BIT = 32768.0
    const val CLIPPING_THRESHOLD_16BIT = 32760

    /**
     * Computes all primary acoustic metrics from a 16-bit PCM buffer.
     */
    fun calculateMetrics(
        pcmBuffer: ShortArray,
        sampleRate: Int = 44100,
        fftSize: Int = 2048,
        windowType: WindowType = WindowType.HANN,
        calibrationOffsetDb: Double? = null,
        recentDbHistory: List<Double> = emptyList(),
        leqCumulativeEnergy: Double? = null,
        leqSampleCount: Long = 0
    ): Pair<AudioMetrics, FloatArray> {
        val n = pcmBuffer.size
        if (n == 0) {
            val emptyBands = FrequencyBandsEnergy(0f, 0f, 0f, 0f, 0f, 0f, 0f)
            val fallbackMetrics = AudioMetrics(
                rms = 0.0,
                peakAmplitude = 0,
                rmsDbfs = -100.0,
                peakDbfs = -100.0,
                crestFactorDb = 0.0,
                clippingCount = 0,
                hasClipping = false,
                dominantFrequencyHz = 0f,
                spectralCentroidHz = 0f,
                estimatedDbSpl = calibrationOffsetDb?.let { -100.0 + it },
                leqDb = null,
                stabilityIndex = SoundStability.VERY_STABLE,
                energyBands = emptyBands
            )
            return Pair(fallbackMetrics, FloatArray(0))
        }

        var sumSquares = 0.0
        var peak = 0
        var clippingCount = 0

        // Normalized samples array for FFT
        val normalizedSamples = DoubleArray(min(n, fftSize))

        for (i in 0 until n) {
            val sample = pcmBuffer[i].toInt()
            val absSample = kotlin.math.abs(sample)
            if (absSample > peak) {
                peak = absSample
            }
            if (absSample >= CLIPPING_THRESHOLD_16BIT) {
                clippingCount++
            }
            sumSquares += sample.toDouble() * sample.toDouble()

            if (i < normalizedSamples.size) {
                normalizedSamples[i] = sample.toDouble() / FULL_SCALE_16BIT
            }
        }

        val rms = sqrt(sumSquares / n)

        // Calculate dBFS safely
        val rmsDbfs = if (rms > 1e-4) {
            20.0 * log10(rms / FULL_SCALE_16BIT)
        } else {
            -100.0
        }

        val peakDbfs = if (peak > 0) {
            20.0 * log10(peak.toDouble() / FULL_SCALE_16BIT)
        } else {
            -100.0
        }

        // Crest factor in dB: Peak dBFS - RMS dBFS
        val crestFactorDb = if (peakDbfs > -99.0 && rmsDbfs > -99.0) {
            max(0.0, peakDbfs - rmsDbfs)
        } else {
            0.0
        }

        // Perform FFT for spectral metrics
        val spectrum = FastFourierTransform.computeMagnitudeSpectrumDbfs(normalizedSamples, windowType)
        val dominantFreq = FastFourierTransform.findDominantFrequency(spectrum, sampleRate, normalizedSamples.size)
        val centroid = FastFourierTransform.computeSpectralCentroid(spectrum, sampleRate, normalizedSamples.size)

        // Energy distribution across 7 standard acoustic bands
        val bands = computeFrequencyBandsEnergy(spectrum, sampleRate, normalizedSamples.size)

        // Stability indicator based on recent dB values variance
        val stability = computeStability(recentDbHistory, rmsDbfs)

        // Calibrated dB SPL (if user provided calibration offset)
        val estimatedDbSpl = calibrationOffsetDb?.let { offset ->
            max(0.0, rmsDbfs + offset)
        }

        // Leq (Equivalent continuous sound level)
        val currentLeq = if (leqCumulativeEnergy != null && leqSampleCount > 0) {
            val currentLevel = estimatedDbSpl ?: rmsDbfs
            val energy = Math.pow(10.0, currentLevel / 10.0)
            val updatedEnergy = (leqCumulativeEnergy * leqSampleCount + energy) / (leqSampleCount + 1)
            10.0 * log10(max(1e-4, updatedEnergy))
        } else {
            estimatedDbSpl ?: rmsDbfs
        }

        val metrics = AudioMetrics(
            rms = rms,
            peakAmplitude = peak,
            rmsDbfs = max(-100.0, min(0.0, rmsDbfs)),
            peakDbfs = max(-100.0, min(0.0, peakDbfs)),
            crestFactorDb = crestFactorDb,
            clippingCount = clippingCount,
            hasClipping = clippingCount > 0,
            dominantFrequencyHz = dominantFreq,
            spectralCentroidHz = centroid,
            estimatedDbSpl = estimatedDbSpl,
            leqDb = currentLeq,
            stabilityIndex = stability,
            energyBands = bands
        )

        return Pair(metrics, spectrum)
    }

    /**
     * Computes relative normalized linear energy percentage in each acoustic band.
     */
    fun computeFrequencyBandsEnergy(
        spectrumDbfs: FloatArray,
        sampleRate: Int,
        fftSize: Int
    ): FrequencyBandsEnergy {
        if (spectrumDbfs.isEmpty()) return FrequencyBandsEnergy(0f, 0f, 0f, 0f, 0f, 0f, 0f)

        val binRes = sampleRate.toFloat() / fftSize
        var sumSubBass = 0.0
        var sumBass = 0.0
        var sumLowMid = 0.0
        var sumMid = 0.0
        var sumHighMid = 0.0
        var sumTreble = 0.0
        var sumBrilliance = 0.0

        for (i in 1 until spectrumDbfs.size) {
            val freq = i * binRes
            val power = Math.pow(10.0, (spectrumDbfs[i] / 10.0)) // linear power
            when {
                freq in 20.0..60.0 -> sumSubBass += power
                freq in 60.0..250.0 -> sumBass += power
                freq in 250.0..500.0 -> sumLowMid += power
                freq in 500.0..2000.0 -> sumMid += power
                freq in 2000.0..4000.0 -> sumHighMid += power
                freq in 4000.0..6000.0 -> sumTreble += power
                freq in 6000.0..20000.0 -> sumBrilliance += power
            }
        }

        val total = sumSubBass + sumBass + sumLowMid + sumMid + sumHighMid + sumTreble + sumBrilliance
        if (total <= 1e-9) {
            return FrequencyBandsEnergy(0f, 0f, 0f, 0f, 0f, 0f, 0f)
        }

        return FrequencyBandsEnergy(
            subBass = (sumSubBass / total).toFloat(),
            bass = (sumBass / total).toFloat(),
            lowMid = (sumLowMid / total).toFloat(),
            mid = (sumMid / total).toFloat(),
            highMid = (sumHighMid / total).toFloat(),
            treble = (sumTreble / total).toFloat(),
            brilliance = (sumBrilliance / total).toFloat()
        )
    }

    /**
     * Evaluates sound stability using standard deviation of recent dB values.
     */
    private fun computeStability(recentDb: List<Double>, currentDb: Double): SoundStability {
        if (recentDb.size < 4) return SoundStability.VERY_STABLE
        val values = recentDb.takeLast(15) + currentDb
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)

        return when {
            stdDev < 1.8 -> SoundStability.VERY_STABLE
            stdDev < 5.0 -> SoundStability.MODERATE_FLUCTUATION
            else -> SoundStability.HIGH_FLUCTUATION
        }
    }
}
