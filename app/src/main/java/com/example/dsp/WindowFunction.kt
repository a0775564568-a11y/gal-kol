package com.example.dsp

import kotlin.math.PI
import kotlin.math.cos

/**
 * Mathematical window functions for spectral analysis.
 */
object WindowFunction {

    fun apply(data: DoubleArray, type: WindowType = WindowType.HANN): DoubleArray {
        val n = data.size
        if (n <= 1 || type == WindowType.RECTANGULAR) return data.copyOf()

        val windowed = DoubleArray(n)
        val factor = 2.0 * PI / (n - 1)

        when (type) {
            WindowType.HANN -> {
                for (i in 0 until n) {
                    val w = 0.5 * (1.0 - cos(factor * i))
                    windowed[i] = data[i] * w
                }
            }
            WindowType.HAMMING -> {
                for (i in 0 until n) {
                    val w = 0.54 - 0.46 * cos(factor * i)
                    windowed[i] = data[i] * w
                }
            }
            WindowType.BLACKMAN -> {
                val factor2 = 4.0 * PI / (n - 1)
                for (i in 0 until n) {
                    val w = 0.42 - 0.5 * cos(factor * i) + 0.08 * cos(factor2 * i)
                    windowed[i] = data[i] * w
                }
            }
            WindowType.RECTANGULAR -> {
                System.arraycopy(data, 0, windowed, 0, n)
            }
        }
        return windowed
    }

    fun getWindowFactors(size: Int, type: WindowType = WindowType.HANN): DoubleArray {
        val factors = DoubleArray(size)
        if (size <= 1 || type == WindowType.RECTANGULAR) {
            factors.fill(1.0)
            return factors
        }
        val factor = 2.0 * PI / (size - 1)
        when (type) {
            WindowType.HANN -> {
                for (i in 0 until size) {
                    factors[i] = 0.5 * (1.0 - cos(factor * i))
                }
            }
            WindowType.HAMMING -> {
                for (i in 0 until size) {
                    factors[i] = 0.54 - 0.46 * cos(factor * i)
                }
            }
            WindowType.BLACKMAN -> {
                val factor2 = 4.0 * PI / (size - 1)
                for (i in 0 until size) {
                    factors[i] = 0.42 - 0.5 * cos(factor * i) + 0.08 * cos(factor2 * i)
                }
            }
            WindowType.RECTANGULAR -> {
                factors.fill(1.0)
            }
        }
        return factors
    }
}
