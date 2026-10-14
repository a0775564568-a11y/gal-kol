package com.example.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Radix-2 Cooley-Tukey Fast Fourier Transform (FFT) in pure Kotlin.
 * Computes frequency spectrum, magnitude, and dominant frequency with parabolic interpolation.
 */
object FastFourierTransform {

    /**
     * Executes in-place Cooley-Tukey Radix-2 FFT on real and imaginary arrays.
     * Both arrays must have length equal to a power of 2.
     */
    fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        require(n > 0 && (n and (n - 1)) == 0) { "FFT size must be a power of 2, got $n" }
        require(imag.size == n) { "Imaginary array length must equal real array length" }

        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR

                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // Cooley-Tukey butterflies
        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angle = -2.0 * PI / len
            val wStepR = cos(angle)
            val wStepI = sin(angle)

            var i = 0
            while (i < n) {
                var wR = 1.0
                var wI = 0.0
                for (k in 0 until halfLen) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + halfLen] * wR - imag[i + k + halfLen] * wI
                    val vI = real[i + k + halfLen] * wI + imag[i + k + halfLen] * wR

                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + halfLen] = uR - vR
                    imag[i + k + halfLen] = uI - vI

                    val nextWR = wR * wStepR - wI * wStepI
                    val nextWI = wR * wStepI + wI * wStepR
                    wR = nextWR
                    wI = nextWI
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * Computes the magnitude spectrum in dBFS (normalized so 0 dBFS is peak full scale sine).
     * Returns an array of size N/2 representing frequencies from 0 to SampleRate / 2.
     */
    fun computeMagnitudeSpectrumDbfs(
        samples: DoubleArray,
        windowType: WindowType = WindowType.HANN
    ): FloatArray {
        val n = getNextPowerOfTwo(samples.size)
        val real = DoubleArray(n)
        val imag = DoubleArray(n)

        // Apply windowing and copy
        val windowed = WindowFunction.apply(
            if (samples.size == n) samples else samples.copyOf(n),
            windowType
        )
        System.arraycopy(windowed, 0, real, 0, minOf(samples.size, n))

        fft(real, imag)

        val halfSize = n / 2
        val spectrum = FloatArray(halfSize)
        val normFactor = 2.0 / n // Amplitude normalization for single-sided spectrum

        for (i in 0 until halfSize) {
            val mag = sqrt(real[i] * real[i] + imag[i] * imag[i]) * normFactor
            // Convert to dBFS relative to full scale 1.0 (assuming normalized samples in [-1.0, 1.0])
            val clampedMag = if (mag < 1e-6) 1e-6 else mag
            val dbfs = 20.0 * log10(clampedMag)
            spectrum[i] = maxOf(-100.0, dbfs).toFloat()
        }
        return spectrum
    }

    /**
     * Finds the dominant frequency (Hz) using parabolic interpolation across the highest peak.
     */
    fun findDominantFrequency(
        spectrumDbfs: FloatArray,
        sampleRate: Int,
        fftSize: Int
    ): Float {
        if (spectrumDbfs.isEmpty()) return 0f

        val halfSize = spectrumDbfs.size
        val binResolution = sampleRate.toFloat() / fftSize

        // Ignore DC and near-zero bins (below 25 Hz) to prevent mic rumble bias
        val startBin = maxOf(1, (25f / binResolution).toInt())
        var maxIndex = startBin
        var maxVal = Float.NEGATIVE_INFINITY

        for (i in startBin until halfSize) {
            if (spectrumDbfs[i] > maxVal) {
                maxVal = spectrumDbfs[i]
                maxIndex = i
            }
        }

        // If signal is too quiet (e.g. below -80 dBFS), no clear dominant tone
        if (maxVal < -80f) return 0f

        // Parabolic interpolation for sub-bin frequency accuracy
        var refinedIndex = maxIndex.toFloat()
        if (maxIndex in 1 until halfSize - 1) {
            val alpha = spectrumDbfs[maxIndex - 1]
            val beta = spectrumDbfs[maxIndex]
            val gamma = spectrumDbfs[maxIndex + 1]
            val denom = alpha - 2f * beta + gamma
            if (denom != 0f) {
                val p = 0.5f * (alpha - gamma) / denom
                if (p in -1f..1f) {
                    refinedIndex += p
                }
            }
        }

        return refinedIndex * binResolution
    }

    /**
     * Calculates the Spectral Centroid (the "center of gravity" of the spectrum, indicating brightness/timbre).
     */
    fun computeSpectralCentroid(
        spectrumDbfs: FloatArray,
        sampleRate: Int,
        fftSize: Int
    ): Float {
        if (spectrumDbfs.isEmpty()) return 0f
        val binResolution = sampleRate.toFloat() / fftSize
        var weightedSum = 0.0
        var totalEnergy = 0.0

        for (i in 1 until spectrumDbfs.size) {
            // Linear magnitude from dB: 10^(dB/20)
            val linearMag = Math.pow(10.0, (spectrumDbfs[i] / 20.0))
            val freq = i * binResolution
            weightedSum += freq * linearMag
            totalEnergy += linearMag
        }

        return if (totalEnergy > 1e-7) (weightedSum / totalEnergy).toFloat() else 0f
    }

    fun getNextPowerOfTwo(value: Int): Int {
        var n = 1
        while (n < value) {
            n = n shl 1
        }
        return n
    }
}
