package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.audio.AudioEngine
import com.example.data.AppSettings
import com.example.data.MeasurementEntity
import com.example.domain.AcousticExplainer
import com.example.domain.AcousticExplanation
import com.example.dsp.FrequencyBandsEnergy
import com.example.dsp.SoundStability
import com.example.ui.components.FrequencyBandsBarChart
import com.example.ui.components.SpectrogramVisualizer
import com.example.ui.components.SpectrumVisualizer
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.AcousticAmber
import com.example.ui.theme.AcousticBlue
import com.example.ui.theme.AcousticCyan
import com.example.ui.theme.AcousticGreen
import com.example.ui.theme.AcousticRed
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.RobotoMonoFontFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun LiveAnalysisScreen(
    audioEngine: AudioEngine,
    settings: AppSettings,
    onSaveMeasurement: suspend (MeasurementEntity) -> Unit,
    onOpenStatistics: () -> Unit = {}
) {
    val context = LocalContext.current
    val liveState by audioEngine.uiState.collectAsState()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            audioEngine.startCapture()
        }
    }

    LaunchedEffect(hasMicPermission) {
        if (hasMicPermission) {
            audioEngine.startCapture()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioEngine.stopCapture()
        }
    }

    // Visualizer View Mode: 0 = Waveform, 1 = Spectrum FFT, 2 = Spectrogram
    var visualizerMode by remember { mutableIntStateOf(0) }

    // Waveform controls
    var timeWindowMs by remember { mutableStateOf(50f) }
    var isFullscreenWaveform by remember { mutableStateOf(false) }

    // Dialog states
    var showSaveDialog by remember { mutableStateOf(false) }
    var snapshotTitle by remember { mutableStateOf("") }
    var snapshotNotes by remember { mutableStateOf("") }
    var showExpandedExplanationDialog by remember { mutableStateOf(false) }

    // STABLE Acoustic Explanation: Update only once every 1.5s to prevent jumping
    var stableExplanation by remember {
        mutableStateOf(
            AcousticExplainer.explain(
                metrics = liveState.metrics,
                isCalibrated = settings.isCalibrated,
                userThresholdDb = settings.alertThresholdDb,
                referenceSpeechDb = settings.speechReferenceDb
            )
        )
    }

    LaunchedEffect(settings) {
        while (isActive) {
            delay(1500)
            if (!liveState.isFrozen) {
                stableExplanation = AcousticExplainer.explain(
                    metrics = liveState.metrics,
                    isCalibrated = settings.isCalibrated,
                    userThresholdDb = settings.alertThresholdDb,
                    referenceSpeechDb = settings.speechReferenceDb
                )
            }
        }
    }

    if (!hasMicPermission) {
        MicPermissionDeniedCard(onRequestPermission = {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        })
        return
    }

    // Root layout: No outer scroll to keep whole screen rock-stable
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("live_analysis_screen")
    ) {
        // --- 1. TOP COMPACT HUD BANNER (Sleek height ~62dp, leaving maximum space for wave & explanation) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Decibel Readout & Stability Pill
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val displayDb = if (settings.isCalibrated) {
                        liveState.metrics.estimatedDbSpl ?: (liveState.metrics.rmsDbfs + 94.0)
                    } else {
                        liveState.metrics.rmsDbfs
                    }
                    val unit = if (settings.isCalibrated) "dB SPL" else "dBFS"

                    val levelColor = when {
                        displayDb > -6.0 -> AcousticRed
                        displayDb > -18.0 -> AcousticAmber
                        displayDb > -45.0 -> AcousticCyan
                        else -> Color.Gray
                    }

                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(34.dp)
                            .background(levelColor, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = String.format(Locale.US, "%.1f", displayDb),
                                fontFamily = RobotoMonoFontFamily,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = levelColor
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = unit,
                                fontFamily = RobotoMonoFontFamily,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = liveState.metrics.stabilityIndex.labelHe,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Compact Telemetry Chips (Peak, Avg, Freq)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TelemetryChip(
                        label = "שיא",
                        value = "${String.format(Locale.US, "%.1f", liveState.metrics.peakDbfs)} dB",
                        color = AcousticRed
                    )
                    TelemetryChip(
                        label = "ממוצע",
                        value = "${String.format(Locale.US, "%.1f", liveState.avgDbfs)} dB",
                        color = AcousticAmber
                    )
                    TelemetryChip(
                        label = "תדר",
                        value = "${liveState.metrics.dominantFrequencyHz.toInt()} Hz",
                        color = AcousticBlue
                    )
                }

                // Action Buttons (Freeze, Reset, Auto-Save Indicator)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { audioEngine.toggleFreeze() },
                        modifier = Modifier.size(32.dp).testTag("freeze_button")
                    ) {
                        Icon(
                            imageVector = if (liveState.isFrozen) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (liveState.isFrozen) "המשך" else "הקפא",
                            tint = if (liveState.isFrozen) AcousticAmber else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { audioEngine.resetAggregations() },
                        modifier = Modifier.size(32.dp).testTag("reset_stats_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "איפוס", modifier = Modifier.size(18.dp))
                    }

                    // Auto-Save pulsing badge button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AcousticGreen.copy(alpha = 0.15f))
                            .clickable { onOpenStatistics() }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(AcousticGreen, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "שמירה אוטומטית",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = AcousticGreen
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // --- 2. CENTRAL HERO AREA 1: LIVE WAVEFORM & VISUALIZER (Takes ~50% of central screen) ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.15f)
                .testTag("waveform_card_container"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DeepNavy)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Visualizer Top Control Bar (Integrated Switcher & Zoom Controls)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Segmented Mode Switcher
                    Row(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf("〰️ צורת גל קול", "📊 ספקטרום FFT", "🌈 ספקטרוגרמה").forEachIndexed { index, title ->
                            val isSelected = visualizerMode == index
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) AcousticCyan else Color.Transparent)
                                    .clickable { visualizerMode = index }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // Zoom & Fullscreen controls (visible when on Waveform mode)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (visualizerMode == 0) {
                            IconButton(
                                onClick = { timeWindowMs = maxOf(10f, timeWindowMs / 2f) },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "הגדל", tint = AcousticCyan, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { timeWindowMs = minOf(500f, timeWindowMs * 2f) },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "הקטן", tint = AcousticCyan, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = "${timeWindowMs.toInt()}ms",
                                fontSize = 10.sp,
                                fontFamily = RobotoMonoFontFamily,
                                color = AcousticCyan
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        IconButton(
                            onClick = { isFullscreenWaveform = true },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "מסך מלא", tint = AcousticCyan, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Visualizer Canvas (Prominent & High Resolution)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    when (visualizerMode) {
                        0 -> WaveformVisualizer(
                            samples = liveState.waveformPoints,
                            timeWindowMs = timeWindowMs,
                            isFrozen = liveState.isFrozen,
                            modifier = Modifier.fillMaxSize()
                        )
                        1 -> SpectrumVisualizer(
                            spectrumDbfs = liveState.spectrumDbfs,
                            sampleRate = audioEngine.sampleRate,
                            fftSize = audioEngine.fftSize,
                            dominantFreqHz = liveState.metrics.dominantFrequencyHz,
                            useLogScale = settings.useLogFrequencyScale,
                            modifier = Modifier.fillMaxSize().padding(4.dp)
                        )
                        2 -> SpectrogramVisualizer(
                            currentSpectrumDbfs = liveState.spectrumDbfs,
                            modifier = Modifier.fillMaxSize().padding(4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // --- 3. CENTRAL HERO AREA 2: PROMINENT ACOUSTIC EXPLANATIONS CARD (Takes ~50% of central screen) ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.05f)
                .testTag("acoustic_explanation_container"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                // Header with Category Title & Confidence
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(AcousticBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = AcousticBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stableExplanation.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stableExplanation.confidenceLevel,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Button to open deep-dive dialog or manual snapshot
                    Row {
                        IconButton(
                            onClick = {
                                snapshotTitle = "מדידה ${System.currentTimeMillis() % 10000}"
                                snapshotNotes = ""
                                showSaveDialog = true
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.BookmarkBorder, contentDescription = "שמור ידנית", tint = AcousticBlue, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            onClick = { showExpandedExplanationDialog = true },
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("הסבר מורחב", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AcousticBlue)
                        }
                    }
                }

                // Live Anomaly Alert Banner (Appears immediately when scream, spike or clipping is detected)
                liveState.recentAnomaly?.let { anomaly ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AcousticRed.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .clickable { onOpenStatistics() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NotificationImportant, contentDescription = null, tint = AcousticRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "אירוע חריג: ${anomaly.eventType} (${String.format(Locale.US, "%.1f", anomaly.peakDbfs)} dBFS) - נשמר אוטומטית!",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AcousticRed
                                )
                            }
                            Text(
                                text = "לסטטיסטיקות >",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AcousticRed
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Plain-Hebrew Explanation Core Text
                Text(
                    text = stableExplanation.summaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 19.sp,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Acoustic Comparison and Pitch Details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExplanationMiniBox(
                        title = "השוואה לשיחה רגילה:",
                        text = stableExplanation.referenceComparisonText,
                        color = AcousticBlue,
                        modifier = Modifier.weight(1f)
                    )
                    ExplanationMiniBox(
                        title = "אופי התדרים:",
                        text = stableExplanation.spectralCharacterText,
                        color = AcousticCyan,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Frequency Bands Spectrum Mini Visualizer (Sub-Bass, Bass, Mid, Treble)
                FrequencyBandsBarChart(
                    energyBands = liveState.metrics.energyBands,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Fullscreen Waveform Dialog
    if (isFullscreenWaveform) {
        Dialog(
            onDismissRequest = { isFullscreenWaveform = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DeepNavy)
                    .padding(16.dp)
            ) {
                WaveformVisualizer(
                    samples = liveState.waveformPoints,
                    timeWindowMs = timeWindowMs,
                    isFrozen = liveState.isFrozen,
                    modifier = Modifier.fillMaxSize()
                )
                Button(
                    onClick = { isFullscreenWaveform = false },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text("סגור מסך מלא")
                }
            }
        }
    }

    // Expanded Deep-Dive Explanation Dialog
    if (showExpandedExplanationDialog) {
        AlertDialog(
            onDismissRequest = { showExpandedExplanationDialog = false },
            title = { Text(stableExplanation.title, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stableExplanation.summaryText,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                    Text(
                        text = "דינמיקה ומקדם שיא (Crest Factor):\n${stableExplanation.dynamicsText}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    stableExplanation.exposureAdvisoryText?.let { advisory ->
                        Text(
                            text = "הנחיות חשיפה ובטיחות:\n$advisory",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AcousticAmber
                        )
                    }
                    Text(
                        text = "מגבלות מדידה:\n${stableExplanation.limitationsNote}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showExpandedExplanationDialog = false }) {
                    Text("סגור")
                }
            }
        )
    }

    // Save Manual Snapshot Dialog
    if (showSaveDialog) {
        val bands = liveState.metrics.energyBands
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("שמירת דגימת מדידה ידנית") },
            text = {
                Column {
                    Text("הנתונים נשמרים תמיד אוטומטית ברקע! כאן תוכל להוסיף שם אישי והערה:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = snapshotTitle,
                        onValueChange = { snapshotTitle = it },
                        label = { Text("כותרת המדידה") },
                        modifier = Modifier.fillMaxWidth().testTag("snapshot_title_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = snapshotNotes,
                        onValueChange = { snapshotNotes = it },
                        label = { Text("הערות (אופציונלי)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val entity = MeasurementEntity(
                            title = snapshotTitle.ifBlank { "מדידה ידנית ${System.currentTimeMillis() % 10000}" },
                            rmsDbfs = liveState.metrics.rmsDbfs,
                            peakDbfs = liveState.metrics.peakDbfs,
                            minDbfs = liveState.minDbfs,
                            crestFactorDb = liveState.metrics.crestFactorDb,
                            dominantFrequencyHz = liveState.metrics.dominantFrequencyHz,
                            estimatedDbSpl = liveState.metrics.estimatedDbSpl,
                            leqDb = liveState.metrics.leqDb,
                            isCalibrated = settings.isCalibrated,
                            notes = snapshotNotes,
                            stabilityLabel = liveState.metrics.stabilityIndex.labelHe,
                            energyBandsCsv = "${bands.subBass},${bands.bass},${bands.lowMid},${bands.mid},${bands.highMid},${bands.treble},${bands.brilliance}"
                        )
                        CoroutineScope(Dispatchers.IO).launch {
                            onSaveMeasurement(entity)
                        }
                        showSaveDialog = false
                    },
                    modifier = Modifier.testTag("confirm_save_button")
                ) {
                    Text("שמור")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("ביטול")
                }
            }
        )
    }
}

@Composable
private fun TelemetryChip(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color.Gray.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = label, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontFamily = RobotoMonoFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun ExplanationMiniBox(
    title: String,
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {
        Column {
            Text(text = title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = text,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MicPermissionDeniedCard(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.MicOff, contentDescription = null, tint = AcousticRed, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "נדרשת הרשאת מיקרופון",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "כדי למדוד גלי קול, עוצמת דציבלים וספקטרום תדרים בזמן אמת, גל־קול זקוקה לגישה למיקרופון המכשיר. העיבוד נעשה מקומית ללא אינטרנט.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = AcousticBlue)
                ) {
                    Text("אשר הרשאת מיקרופון")
                }
            }
        }
    }
}
