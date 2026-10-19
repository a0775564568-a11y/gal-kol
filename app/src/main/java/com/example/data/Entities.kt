package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Double = 0.0,
    val rmsDbfs: Double,
    val peakDbfs: Double,
    val minDbfs: Double,
    val crestFactorDb: Double,
    val dominantFrequencyHz: Float,
    val estimatedDbSpl: Double?,
    val leqDb: Double?,
    val isCalibrated: Boolean,
    val notes: String = "",
    val stabilityLabel: String = "",
    val energyBandsCsv: String = "" // "subBass,bass,lowMid,mid,highMid,treble,brilliance"
)

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val durationMs: Long,
    val sampleRate: Int = 44100,
    val fileSizeBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val peakDbfs: Double = -100.0,
    val rmsDbfs: Double = -100.0
)

@Entity(tableName = "acoustic_anomalies")
data class AcousticAnomalyEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String, // "צעקה / שיא עוצמה", "חריגת סף ממושכת", "קטימה דיגיטלית", "צפירה / שריקה"
    val peakDbfs: Double,
    val rmsDbfs: Double,
    val estimatedDbSpl: Double?,
    val excessDb: Double, // בכמה דציבלים חרג
    val severity: String, // "קיצונית", "חמורה", "בינונית", "קלה"
    val dominantFrequencyHz: Float,
    val durationMs: Long,
    val descriptionHe: String
)

@Entity(tableName = "live_sessions_log")
data class LiveSessionSummaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Long,
    val avgDbfs: Double,
    val peakDbfs: Double,
    val minDbfs: Double,
    val leqDb: Double,
    val anomaliesCount: Int,
    val dominantFrequencyHz: Float
)
