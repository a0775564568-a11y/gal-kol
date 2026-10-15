package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import com.example.ui.components.ExplanationCard
import com.example.ui.components.LevelMeterGauge
import com.example.ui.components.SpectrogramVisualizer
import com.example.ui.components.SpectrumVisualizer
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.AcousticAmber
import com.example.ui.theme.AcousticBlue
import com.example.ui.theme.AcousticCyan
import com.example.ui.theme.DeepNavy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun LiveAnalysisScreen(
    audioEngine: AudioEngine,
    settings: AppSettings,
    onSaveMeasurement: suspend (MeasurementEntity) -> Unit
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

    // Tabs: 0 = Waveform, 1 = Spectrum, 2 = Spectrogram, 3 = Explanation & Bands
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("צורת גל", "ספקטרום FFT", "ספקטרוגרמה", "הסבר ומדדים")

    // Waveform controls
    var timeWindowMs by remember { mutableStateOf(50f) }
    var isFullscreenWaveform by remember { mutableStateOf(false) }

    // Save snapshot dialog state
    var showSaveDialog by remember { mutableStateOf(false) }
    var snapshotTitle by remember { mutableStateOf("") }
    var snapshotNotes by remember { mutableStateOf("") }

    // STABLE Acoustic Explanation: Update only once every 1.5 seconds to prevent jumping!
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

    // Fixed root Column with NO verticalScroll to prevent bouncing!
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
            .testTag("live_analysis_screen")
    ) {
        // Top Hero Meter (Completely fixed dimensions)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                LevelMeterGauge(
                    currentDbfs = liveState.metrics.rmsDbfs,
                    peakDbfs = liveState.metrics.peakDbfs,
                    minDbfs = liveState.minDbfs,
                    estimatedDbSpl = liveState.metrics.estimatedDbSpl,
                    isCalibrated = settings.isCalibrated,
                    stability = liveState.metrics.stabilityIndex,
                    leqDb = liveState.metrics.leqDb,
                    dominantFreqHz = liveState.metrics.dominantFrequencyHz
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Toolbar Actions (Freeze, Reset Stats, Save Snapshot)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { audioEngine.toggleFreeze() },
                        modifier = Modifier.testTag("freeze_button")
                    ) {
                        Icon(
                            imageVector = if (liveState.isFrozen) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (liveState.isFrozen) "המשך" else "הקפא",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (liveState.isFrozen) "המשך" else "הקפא", fontSize = 12.sp)
                    }

                    IconButton(
                        onClick = { audioEngine.resetAggregations() },
                        modifier = Modifier.testTag("reset_stats_button").size(36.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "איפוס מדידות", modifier = Modifier.size(20.dp))
                    }

                    Button(
                        onClick = {
                            snapshotTitle = "מדידה ${System.currentTimeMillis() % 10000}"
                            snapshotNotes = ""
                            showSaveDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AcousticBlue),
                        modifier = Modifier.testTag("save_snapshot_button")
                    ) {
                        Icon(Icons.Default.BookmarkBorder, contentDescription = "שמור", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("שמור מדידה", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Visualizer Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 12.sp, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.testTag("tab_$index")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tab Content Section
        if (selectedTab == 3) {
            // Dedicated Explanations Tab (Scrollable inside its own bounds)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                ExplanationCard(
                    explanation = stableExplanation,
                    energyBands = liveState.metrics.energyBands
                )
            }
        } else {
            // Visualizer Container Card (Fills remaining height smoothly without scrolling)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DeepNavy)
            ) {
                when (selectedTab) {
                    0 -> {
                        // Waveform view with controls
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { timeWindowMs = maxOf(10f, timeWindowMs / 2f) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ZoomIn, contentDescription = "הגדל", tint = AcousticCyan, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { timeWindowMs = minOf(500f, timeWindowMs * 2f) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ZoomOut, contentDescription = "הקטן", tint = AcousticCyan, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${timeWindowMs.toInt()}ms",
                                        fontSize = 11.sp,
                                        color = AcousticCyan
                                    )
                                }

                                IconButton(
                                    onClick = { isFullscreenWaveform = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Fullscreen, contentDescription = "מסך מלא", tint = AcousticCyan, modifier = Modifier.size(18.dp))
                                }
                            }

                            WaveformVisualizer(
                                samples = liveState.waveformPoints,
                                timeWindowMs = timeWindowMs,
                                isFrozen = liveState.isFrozen,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        }
                    }
                    1 -> {
                        SpectrumVisualizer(
                            spectrumDbfs = liveState.spectrumDbfs,
                            sampleRate = audioEngine.sampleRate,
                            fftSize = audioEngine.fftSize,
                            dominantFreqHz = liveState.metrics.dominantFrequencyHz,
                            useLogScale = settings.useLogFrequencyScale,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                        )
                    }
                    2 -> {
                        SpectrogramVisualizer(
                            currentSpectrumDbfs = liveState.spectrumDbfs,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Fixed-Height Stable Status Bar (One line, click to open full details)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clickable { selectedTab = 3 },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = AcousticBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stableExplanation.summaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "להסבר מלא",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AcousticBlue
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = null,
                            tint = AcousticBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
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

    // Save Snapshot Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("שמירת מדידה נוכחית") },
            text = {
                Column {
                    Text("הזן שם ותיאור לשמירת ערכי המדידה בהיסטוריה:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = snapshotTitle,
                        onValueChange = { snapshotTitle = it },
                        label = { Text("כותרת המדידה") },
                        modifier = Modifier.fillMaxWidth().testTag("snapshot_title_field")
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
                        val bands = liveState.metrics.energyBands
                        val entity = MeasurementEntity(
                            title = snapshotTitle.ifBlank { "מדידה שמורה" },
                            timestamp = System.currentTimeMillis(),
                            durationSeconds = 1.0,
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
private fun MicPermissionDeniedCard(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.MicOff,
                    contentDescription = null,
                    tint = AcousticAmber,
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "נדרשת הרשאת מיקרופון",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "כדי לנתח גלי קול בזמן אמת, למדוד עוצמות ולהציג ספקטרום תדרים, אפליקציית גל־קול זקוקה לגישה למיקרופון. כל הנתונים מעובדים ישירות במכשירך בלבד וללא שליחה לרשת.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = AcousticBlue)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("אשר הרשאת מיקרופון")
                }
            }
        }
    }
}
