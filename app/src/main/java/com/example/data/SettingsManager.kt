package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.dsp.WindowType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val isCalibrated: Boolean = false,
    val calibrationOffsetDb: Double = 94.0,
    val speechReferenceDb: Double = 60.0,
    val alertThresholdDb: Double = -12.0, // in dBFS by default, or dB SPL if calibrated
    val alertJumpThresholdDb: Double = 10.0,
    val clippingAlertEnabled: Boolean = true,
    val fftSize: Int = 2048,
    val windowType: WindowType = WindowType.HANN,
    val isDarkMode: Boolean? = null, // null = system default
    val useLogFrequencyScale: Boolean = true
)

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("galkol_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val isCal = prefs.getBoolean("is_calibrated", false)
        val defaultThreshold = if (isCal) 75.0 else -12.0
        return AppSettings(
            isCalibrated = isCal,
            calibrationOffsetDb = prefs.getFloat("cal_offset", 94.0f).toDouble(),
            speechReferenceDb = prefs.getFloat("speech_ref", 60.0f).toDouble(),
            alertThresholdDb = prefs.getFloat("alert_thresh", defaultThreshold.toFloat()).toDouble(),
            alertJumpThresholdDb = prefs.getFloat("jump_thresh", 10.0f).toDouble(),
            clippingAlertEnabled = prefs.getBoolean("clip_alert", true),
            fftSize = prefs.getInt("fft_size", 2048),
            windowType = try {
                WindowType.valueOf(prefs.getString("window_type", WindowType.HANN.name) ?: WindowType.HANN.name)
            } catch (e: Exception) {
                WindowType.HANN
            },
            isDarkMode = if (prefs.contains("dark_mode")) prefs.getBoolean("dark_mode", false) else null,
            useLogFrequencyScale = prefs.getBoolean("log_freq", true)
        )
    }

    fun updateSettings(newSettings: AppSettings) {
        prefs.edit().apply {
            putBoolean("is_calibrated", newSettings.isCalibrated)
            putFloat("cal_offset", newSettings.calibrationOffsetDb.toFloat())
            putFloat("speech_ref", newSettings.speechReferenceDb.toFloat())
            putFloat("alert_thresh", newSettings.alertThresholdDb.toFloat())
            putFloat("jump_thresh", newSettings.alertJumpThresholdDb.toFloat())
            putBoolean("clip_alert", newSettings.clippingAlertEnabled)
            putInt("fft_size", newSettings.fftSize)
            putString("window_type", newSettings.windowType.name)
            if (newSettings.isDarkMode == null) {
                remove("dark_mode")
            } else {
                putBoolean("dark_mode", newSettings.isDarkMode)
            }
            putBoolean("log_freq", newSettings.useLogFrequencyScale)
            apply()
        }
        _settings.value = newSettings
    }
}
