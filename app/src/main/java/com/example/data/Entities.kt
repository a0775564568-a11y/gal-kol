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
