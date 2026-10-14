package com.example.dsp

/**
 * Data models for acoustic and DSP calculations.
 */
data class AudioMetrics(
    val rms: Double,
    val peakAmplitude: Int,
    val rmsDbfs: Double,
    val peakDbfs: Double,
    val crestFactorDb: Double,
    val clippingCount: Int,
    val hasClipping: Boolean,
    val dominantFrequencyHz: Float,
    val spectralCentroidHz: Float,
    val estimatedDbSpl: Double?, // null if uncalibrated
    val leqDb: Double?,
    val stabilityIndex: SoundStability,
    val energyBands: FrequencyBandsEnergy
)

enum class SoundStability(val labelHe: String, val descriptionHe: String) {
    VERY_STABLE("יציב מאוד", "עוצמת הצליל קבועה ואחידה לאורך זמן"),
    MODERATE_FLUCTUATION("תנודתי קל", "שינויים מתונים בעוצמה, אופייני לדיבור או רחשי סביבה"),
    HIGH_FLUCTUATION("תנודתי מאוד", "שינויים מהירים וחדים, צליל מקוטע או פולסיבי")
}

data class FrequencyBandsEnergy(
    val subBass: Float,     // 20 - 60 Hz
    val bass: Float,        // 60 - 250 Hz
    val lowMid: Float,      // 250 - 500 Hz
    val mid: Float,         // 500 - 2000 Hz
    val highMid: Float,     // 2000 - 4000 Hz
    val treble: Float,      // 4000 - 6000 Hz
    val brilliance: Float   // 6000 - 20000 Hz
) {
    fun toList(): List<Pair<String, Float>> = listOf(
        "סאב-באס (20-60Hz)" to subBass,
        "באס (60-250Hz)" to bass,
        "מיד נמוך (250-500Hz)" to lowMid,
        "מיד (0.5-2kHz)" to mid,
        "מיד גבוה (2-4kHz)" to highMid,
        "טרבל (4-6kHz)" to treble,
        "אוויר/ברק (6-20kHz)" to brilliance
    )
}

enum class WindowType(val labelHe: String) {
    HANN("האן (Hann)"),
    HAMMING("האמינג (Hamming)"),
    BLACKMAN("בלקמן (Blackman)"),
    RECTANGULAR("מלבני (Rectangular)")
}
