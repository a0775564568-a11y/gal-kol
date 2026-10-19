package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.dsp.AudioMetrics
import com.example.dsp.AudioMetricsCalculator
import com.example.dsp.FrequencyBandsEnergy
import com.example.dsp.SoundStability
import com.example.dsp.WindowType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.data.AcousticAnomalyEventEntity
import com.example.data.AppDatabase
import com.example.data.LiveSessionSummaryEntity
import com.example.data.MeasurementEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class LiveAudioState(
    val isRunning: Boolean = false,
    val hasPermission: Boolean = false,
    val metrics: AudioMetrics = defaultAudioMetrics(),
    val waveformPoints: FloatArray = FloatArray(512),
    val spectrumDbfs: FloatArray = FloatArray(256) { -100f },
    val minDbfs: Double = -100.0,
    val maxDbfs: Double = -100.0,
    val avgDbfs: Double = -100.0,
    val isRecording: Boolean = false,
    val isRecordingPaused: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val recordingFilePath: String? = null,
    val isFrozen: Boolean = false,
    val recentAnomaly: AcousticAnomalyEventEntity? = null,
    val autoSaveCount: Int = 0,
    val errorMessage: String? = null
)

fun defaultAudioMetrics() = AudioMetrics(
    rms = 0.0,
    peakAmplitude = 0,
    rmsDbfs = -100.0,
    peakDbfs = -100.0,
    crestFactorDb = 0.0,
    clippingCount = 0,
    hasClipping = false,
    dominantFrequencyHz = 0f,
    spectralCentroidHz = 0f,
    estimatedDbSpl = null,
    leqDb = null,
    stabilityIndex = SoundStability.VERY_STABLE,
    energyBands = FrequencyBandsEnergy(0f, 0f, 0f, 0f, 0f, 0f, 0f)
)

class AudioEngine(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var captureJob: Job? = null
    private var recordingTimerJob: Job? = null

    private var audioRecord: AudioRecord? = null
    private val isCapturing = AtomicBoolean(false)
    private var isFrozen = false

    // Configuration
    var sampleRate: Int = 44100
    var fftSize: Int = 2048
    var windowType: WindowType = WindowType.HANN
    var calibrationOffsetDb: Double? = null // e.g. 100.0 dB

    // State flow
    private val _uiState = MutableStateFlow(LiveAudioState())
    val uiState: StateFlow<LiveAudioState> = _uiState.asStateFlow()

    // History & Aggregation
    private val recentDbfsList = mutableListOf<Double>()
    private var minDbfsObserved = 0.0
    private var maxDbfsObserved = -100.0
    private var sumDbfsObserved = 0.0
    private var countObserved = 0L
    private var leqCumulativeEnergy: Double? = null
    private var leqCount: Long = 0
    private var lastUiUpdateTime = 0L

    // Automatic Logging & Anomaly Detector
    private val database = AppDatabase.getInstance(context)
    private var lastAnomalyLogTime = 0L
    private var lastAutoSaveTime = 0L
    private var lastRmsDbfs = -100.0
    private var sessionStartTime = 0L
    private var sessionAnomaliesCount = 0

    // Recording management
    private var activeWavWriter: WavWriter? = null
    private var activeRecordingFile: File? = null
    private var recordingStartTime = 0L
    private var accumulatedDurationMs = 0L
    private var isRecordingPaused = false
    private var recordingPeakDbfs = -100.0
    private var recordingRmsDbfs = -100.0

    @SuppressLint("MissingPermission")
    @Synchronized
    fun startCapture() {
        if (isCapturing.get()) return

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize, fftSize * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "אתחול המיקרופון נכשל. ודא שההרשאה מאושרת ושהמיקרופון אינו תפוס."
                )
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            isCapturing.set(true)
            sessionStartTime = System.currentTimeMillis()
            lastAutoSaveTime = sessionStartTime
            sessionAnomaliesCount = 0
            _uiState.value = _uiState.value.copy(isRunning = true, errorMessage = null)

            captureJob = scope.launch {
                runCaptureLoop()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.value = _uiState.value.copy(
                errorMessage = "שגיאה בפתיחת המיקרופון: ${e.localizedMessage ?: "שגיאה לא ידועה"}"
            )
        }
    }

    private suspend fun runCaptureLoop() {
        val audioBuffer = ShortArray(fftSize)

        while (isCapturing.get() && scope.isActive) {
            val record = audioRecord ?: break
            val readCount = record.read(audioBuffer, 0, fftSize)

            if (readCount > 0) {
                // If recording is active and not paused, stream PCM to the open WAV file
                if (activeWavWriter != null && !isRecordingPaused) {
                    activeWavWriter?.writeSamples(audioBuffer, readCount)
                }

                // If UI is frozen, do not update waveform and spectrum
                if (!isFrozen) {
                    val (metrics, spectrum) = AudioMetricsCalculator.calculateMetrics(
                        pcmBuffer = audioBuffer,
                        sampleRate = sampleRate,
                        fftSize = fftSize,
                        windowType = windowType,
                        calibrationOffsetDb = calibrationOffsetDb,
                        recentDbHistory = recentDbfsList,
                        leqCumulativeEnergy = leqCumulativeEnergy,
                        leqSampleCount = leqCount
                    )

                    // Track recording stats
                    if (activeWavWriter != null && !isRecordingPaused) {
                        recordingPeakDbfs = maxOf(recordingPeakDbfs, metrics.peakDbfs)
                        if (recordingRmsDbfs == -100.0) recordingRmsDbfs = metrics.rmsDbfs
                        else recordingRmsDbfs = (recordingRmsDbfs * 0.95) + (metrics.rmsDbfs * 0.05)
                    }

                    // Update rolling history & aggregation
                    if (metrics.rmsDbfs > -99.0) {
                        recentDbfsList.add(metrics.rmsDbfs)
                        if (recentDbfsList.size > 30) recentDbfsList.removeAt(0)

                        if (countObserved == 0L) {
                            minDbfsObserved = metrics.rmsDbfs
                            maxDbfsObserved = metrics.rmsDbfs
                            sumDbfsObserved = metrics.rmsDbfs
                            countObserved = 1
                        } else {
                            minDbfsObserved = minOf(minDbfsObserved, metrics.rmsDbfs)
                            maxDbfsObserved = maxOf(maxDbfsObserved, metrics.rmsDbfs)
                            sumDbfsObserved += metrics.rmsDbfs
                            countObserved++
                        }

                        // Leq accumulation
                        val curLevel = metrics.estimatedDbSpl ?: metrics.rmsDbfs
                        val energy = Math.pow(10.0, curLevel / 10.0)
                        leqCumulativeEnergy = if (leqCumulativeEnergy == null) energy else (leqCumulativeEnergy!! * leqCount + energy) / (leqCount + 1)
                        leqCount++
                    }

                    // Downsample waveform for UI canvas (512 points)
                    val points = FloatArray(512)
                    val step = maxOf(1, readCount / 512)
                    for (i in 0 until 512) {
                        val sampleIdx = minOf(i * step, readCount - 1)
                        points[i] = (audioBuffer[sampleIdx].toFloat() / 32768.0f).coerceIn(-1.0f, 1.0f)
                    }

                    // Throttle UI updates to ~30 FPS to avoid layout thrashing
                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdateTime >= 33L) {
                        lastUiUpdateTime = now
                        _uiState.value = _uiState.value.copy(
                            metrics = metrics,
                            waveformPoints = points,
                            spectrumDbfs = spectrum,
                            minDbfs = minDbfsObserved,
                            maxDbfs = maxDbfsObserved,
                            avgDbfs = if (countObserved > 0) sumDbfsObserved / countObserved else -100.0,
                            isFrozen = false
                        )
                    }

                    // Real-Time Anomaly Detection (Screams, Spikes, Clipping, Sirens)
                    val jump = if (lastRmsDbfs > -95.0) metrics.rmsDbfs - lastRmsDbfs else 0.0
                    val isScreamOrSpike = metrics.peakDbfs > -8.5 || (jump > 9.5 && metrics.peakDbfs > -22.0)
                    val isClipping = metrics.hasClipping
                    val isSiren = metrics.dominantFrequencyHz in 800f..4000f && metrics.crestFactorDb > 14.0 && metrics.rmsDbfs > -22.0

                    if ((isScreamOrSpike || isClipping || isSiren) && (now - lastAnomalyLogTime >= 1200L)) {
                        lastAnomalyLogTime = now
                        val eventType = when {
                            isClipping -> "קטימה דיגיטלית (Clipping)"
                            metrics.peakDbfs > -4.0 -> "צעקה חזקה / שיא עוצמה קיצוני"
                            metrics.peakDbfs > -8.5 -> "צעקה / שיא עוצמה פתאומי"
                            jump > 9.5 -> "קפיצת עוצמה חדה (אימפולס)"
                            isSiren -> "צפירה / שריקה חריגה"
                            else -> "חריגת סף אקוסטית"
                        }
                        val severity = when {
                            metrics.peakDbfs > -3.0 || isClipping -> "קיצונית"
                            metrics.peakDbfs > -9.0 -> "חמורה"
                            metrics.peakDbfs > -18.0 -> "בינונית"
                            else -> "קלה"
                        }
                        val excess = maxOf(0.0, metrics.peakDbfs - (-20.0))
                        val anomaly = AcousticAnomalyEventEntity(
                            timestamp = now,
                            eventType = eventType,
                            peakDbfs = metrics.peakDbfs,
                            rmsDbfs = metrics.rmsDbfs,
                            estimatedDbSpl = metrics.estimatedDbSpl,
                            excessDb = excess,
                            severity = severity,
                            dominantFrequencyHz = metrics.dominantFrequencyHz,
                            durationMs = 650L,
                            descriptionHe = "$eventType בעוצמת שיא של ${String.format(Locale.US, "%.1f", metrics.peakDbfs)} dBFS"
                        )
                        scope.launch(Dispatchers.IO) {
                            database.anomalyDao().insert(anomaly)
                        }
                        sessionAnomaliesCount++
                        _uiState.value = _uiState.value.copy(recentAnomaly = anomaly)
                        scope.launch {
                            delay(4500)
                            if (_uiState.value.recentAnomaly?.id == anomaly.id || _uiState.value.recentAnomaly?.timestamp == anomaly.timestamp) {
                                _uiState.value = _uiState.value.copy(recentAnomaly = null)
                            }
                        }
                    }
                    lastRmsDbfs = metrics.rmsDbfs

                    // Automatic Background Session Saving every 10 seconds!
                    if (now - lastAutoSaveTime >= 10000L && countObserved > 0) {
                        lastAutoSaveTime = now
                        val avg = sumDbfsObserved / countObserved
                        val dur = (now - sessionStartTime) / 1000
                        val bands = metrics.energyBands
                        scope.launch(Dispatchers.IO) {
                            database.sessionDao().insert(
                                LiveSessionSummaryEntity(
                                    timestamp = now,
                                    durationSeconds = dur,
                                    avgDbfs = avg,
                                    peakDbfs = maxDbfsObserved,
                                    minDbfs = minDbfsObserved,
                                    leqDb = leqCumulativeEnergy?.let { 10.0 * kotlin.math.log10(it) } ?: avg,
                                    anomaliesCount = sessionAnomaliesCount,
                                    dominantFrequencyHz = metrics.dominantFrequencyHz
                                )
                            )
                            val timeStr = SimpleDateFormat("HH:mm:ss dd/MM", Locale.US).format(Date(now))
                            database.measurementDao().insert(
                                MeasurementEntity(
                                    title = "ניתוח חי אוטומטי $timeStr",
                                    timestamp = now,
                                    durationSeconds = dur.toDouble(),
                                    rmsDbfs = metrics.rmsDbfs,
                                    peakDbfs = metrics.peakDbfs,
                                    minDbfs = minDbfsObserved,
                                    crestFactorDb = metrics.crestFactorDb,
                                    dominantFrequencyHz = metrics.dominantFrequencyHz,
                                    estimatedDbSpl = metrics.estimatedDbSpl,
                                    leqDb = metrics.leqDb,
                                    isCalibrated = calibrationOffsetDb != null,
                                    notes = "נשמר אוטומטית ברקע ($sessionAnomaliesCount חריגות)",
                                    stabilityLabel = metrics.stabilityIndex.labelHe,
                                    energyBandsCsv = "${bands.subBass},${bands.bass},${bands.lowMid},${bands.mid},${bands.highMid},${bands.treble},${bands.brilliance}"
                                )
                            )
                            _uiState.value = _uiState.value.copy(
                                autoSaveCount = _uiState.value.autoSaveCount + 1
                            )
                        }
                    }
                }
            } else {
                delay(10)
            }
        }
    }

    @Synchronized
    fun stopCapture() {
        isCapturing.set(false)
        captureJob?.cancel()
        captureJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
        _uiState.value = _uiState.value.copy(isRunning = false)
    }

    fun toggleFreeze() {
        isFrozen = !isFrozen
        _uiState.value = _uiState.value.copy(isFrozen = isFrozen)
    }

    fun resetAggregations() {
        recentDbfsList.clear()
        minDbfsObserved = 0.0
        maxDbfsObserved = -100.0
        sumDbfsObserved = 0.0
        countObserved = 0L
        leqCumulativeEnergy = null
        leqCount = 0
        _uiState.value = _uiState.value.copy(
            minDbfs = -100.0,
            maxDbfs = -100.0,
            avgDbfs = -100.0
        )
    }

    // --- Recording Methods ---

    @Synchronized
    fun startRecording(customTitle: String? = null): File {
        val recordingsDir = File(context.filesDir, "recordings").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = customTitle?.let { "${it}_$timeStamp.wav" } ?: "recording_$timeStamp.wav"
        val file = File(recordingsDir, fileName)

        activeRecordingFile = file
        activeWavWriter = WavWriter(file, sampleRate = sampleRate)
        recordingStartTime = System.currentTimeMillis()
        accumulatedDurationMs = 0L
        isRecordingPaused = false
        recordingPeakDbfs = -100.0
        recordingRmsDbfs = -100.0

        _uiState.value = _uiState.value.copy(
            isRecording = true,
            isRecordingPaused = false,
            recordingDurationMs = 0L,
            recordingFilePath = file.absolutePath
        )

        recordingTimerJob = scope.launch {
            while (isCapturing.get() && activeWavWriter != null) {
                delay(100)
                if (!isRecordingPaused) {
                    val currentDuration = accumulatedDurationMs + (System.currentTimeMillis() - recordingStartTime)
                    _uiState.value = _uiState.value.copy(recordingDurationMs = currentDuration)
                }
            }
        }

        return file
    }

    @Synchronized
    fun pauseRecording() {
        if (activeWavWriter != null && !isRecordingPaused) {
            accumulatedDurationMs += System.currentTimeMillis() - recordingStartTime
            isRecordingPaused = true
            _uiState.value = _uiState.value.copy(isRecordingPaused = true)
        }
    }

    @Synchronized
    fun resumeRecording() {
        if (activeWavWriter != null && isRecordingPaused) {
            recordingStartTime = System.currentTimeMillis()
            isRecordingPaused = false
            _uiState.value = _uiState.value.copy(isRecordingPaused = false)
        }
    }

    @Synchronized
    fun stopRecording(): RecordingResult? {
        recordingTimerJob?.cancel()
        recordingTimerJob = null

        val writer = activeWavWriter ?: return null
        val file = activeRecordingFile ?: return null

        if (!isRecordingPaused) {
            accumulatedDurationMs += System.currentTimeMillis() - recordingStartTime
        }

        writer.close()
        activeWavWriter = null

        val duration = accumulatedDurationMs
        val result = RecordingResult(
            file = file,
            durationMs = duration,
            peakDbfs = recordingPeakDbfs,
            rmsDbfs = recordingRmsDbfs,
            sampleRate = sampleRate,
            fileSize = file.length()
        )

        _uiState.value = _uiState.value.copy(
            isRecording = false,
            isRecordingPaused = false,
            recordingDurationMs = 0L,
            recordingFilePath = null
        )

        activeRecordingFile = null
        return result
    }
}

data class RecordingResult(
    val file: File,
    val durationMs: Long,
    val peakDbfs: Double,
    val rmsDbfs: Double,
    val sampleRate: Int,
    val fileSize: Long
)
