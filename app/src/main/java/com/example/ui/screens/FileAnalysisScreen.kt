package com.example.ui.screens

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioFileAnalysis
import com.example.audio.AudioFileDecoder
import com.example.ui.components.SpectrumVisualizer
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.AcousticAmber
import com.example.ui.theme.AcousticBlue
import com.example.ui.theme.AcousticCyan
import com.example.ui.theme.AcousticRed
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.RobotoMonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun FileAnalysisScreen(
    initialFile: File? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var analysis by remember { mutableStateOf<AudioFileAnalysis?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Range selection for granular analysis (in ms)
    var selectionRange by remember { mutableStateOf(0f..1000f) }
    var selectedSliceWaveform by remember { mutableStateOf(FloatArray(0)) }
    var selectedSliceSpectrum by remember { mutableStateOf(FloatArray(0)) }

    // Media playback
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackPositionMs by remember { mutableFloatStateOf(0f) }
    var activeAudioUri by remember { mutableStateOf<Uri?>(null) }

    fun loadUri(uri: Uri, name: String) {
        isLoading = true
        errorMessage = null
        activeAudioUri = uri

        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false

        coroutineScope.launch {
            try {
                val result = AudioFileDecoder.decodeAndAnalyze(context, uri, name)
                analysis = result
                selectionRange = 0f..minOf(result.durationMs.toFloat(), 1000f)

                // Decode slice
                val (sliceWave, sliceSpec) = AudioFileDecoder.analyzeSelection(
                    result,
                    startMs = selectionRange.start.toLong(),
                    endMs = selectionRange.endInclusive.toLong()
                )
                selectedSliceWaveform = sliceWave
                selectedSliceSpectrum = sliceSpec

                // Initialize playback
                withContext(Dispatchers.Main) {
                    try {
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(context, uri)
                            prepare()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = e.localizedMessage ?: "שגיאה בטעינת קובץ השמע"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(initialFile) {
        initialFile?.let {
            loadUri(Uri.fromFile(it), it.name)
        }
    }

    // Playback progress ticker
    LaunchedEffect(isPlaying) {
        while (isPlaying && mediaPlayer != null) {
            val mp = mediaPlayer ?: break
            playbackPositionMs = mp.currentPosition.toFloat()
            if (!mp.isPlaying) {
                isPlaying = false
            }
            delay(100)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "audio_file"
            loadUri(uri, name)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("file_analysis_screen")
    ) {
        // Picker Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ניתוח קובץ שמע מקומי",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "תמיכה ב־WAV, MP3, M4A, AAC, OGG ועוד",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { filePickerLauncher.launch("audio/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = AcousticBlue),
                    modifier = Modifier.testTag("pick_file_button")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("בחר קובץ")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AcousticCyan)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("מפענח ומנתח קובץ שמע PCM...", fontSize = 13.sp)
                }
            }
            return
        }

        errorMessage?.let { err ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = AcousticRed.copy(alpha = 0.1f))
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AcousticRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = err, color = AcousticRed, fontSize = 13.sp)
                }
            }
        }

        val result = analysis
        if (result == null) {
            // Empty placeholder state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.AudioFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "בחר קובץ שמע לניתוח מעבדתי מעמיק",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return
        }

        // Detailed Content
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Metadata & Overview Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = result.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "משך: ${String.format(Locale.US, "%.1f", result.durationMs / 1000f)} שנ'",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "קצב דגימה: ${result.sampleRate} Hz",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "ערוצים: ${if (result.channelCount == 1) "מונו" else "סטריאו"}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Audio Metrics Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricBox(label = "עוצמת RMS", value = "${String.format(Locale.US, "%.1f", result.rmsDbfs)} dBFS")
                            MetricBox(label = "עוצמת שיא", value = "${String.format(Locale.US, "%.1f", result.peakDbfs)} dBFS")
                            MetricBox(label = "טווח דינמי", value = "${String.format(Locale.US, "%.1f", result.dynamicRangeDb)} dB")
                        }

                        // Clipping Warning
                        if (result.clippingCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AcousticRed.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = AcousticRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "זוהתה קטימה דיגיטלית ב־${result.clippingCount} דגימות בקובץ!",
                                    color = AcousticRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Playback Bar
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val mp = mediaPlayer ?: return@IconButton
                                if (isPlaying) {
                                    mp.pause()
                                    isPlaying = false
                                } else {
                                    mp.start()
                                    isPlaying = true
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "השהה" else "נגן",
                                tint = AcousticBlue
                            )
                        }

                        Slider(
                            value = playbackPositionMs,
                            onValueChange = { newPos ->
                                playbackPositionMs = newPos
                                mediaPlayer?.seekTo(newPos.toInt())
                            },
                            valueRange = 0f..maxOf(100f, result.durationMs.toFloat()),
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f s", playbackPositionMs / 1000f),
                            fontFamily = RobotoMonoFontFamily,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Segment Selector & Zoom Analysis
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "בחירת מקטע ממוקד לניתוח ספקטרלי:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "מקטע נבחר: ${selectionRange.start.toInt()}ms עד ${selectionRange.endInclusive.toInt()}ms (משך: ${(selectionRange.endInclusive - selectionRange.start).toInt()}ms)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        RangeSlider(
                            value = selectionRange,
                            onValueChange = { newRange ->
                                selectionRange = newRange
                                coroutineScope.launch {
                                    val (w, s) = AudioFileDecoder.analyzeSelection(
                                        result,
                                        startMs = newRange.start.toLong(),
                                        endMs = newRange.endInclusive.toLong()
                                    )
                                    selectedSliceWaveform = w
                                    selectedSliceSpectrum = s
                                }
                            },
                            valueRange = 0f..maxOf(100f, result.durationMs.toFloat()),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Waveform and Spectrum for Selection
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = DeepNavy)
                        ) {
                            WaveformVisualizer(
                                samples = selectedSliceWaveform,
                                timeWindowMs = (selectionRange.endInclusive - selectionRange.start),
                                modifier = Modifier.fillMaxSize().padding(6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = DeepNavy)
                        ) {
                            SpectrumVisualizer(
                                spectrumDbfs = selectedSliceSpectrum,
                                sampleRate = result.sampleRate,
                                fftSize = 2048,
                                modifier = Modifier.fillMaxSize().padding(6.dp)
                            )
                        }
                    }
                }
            }

            // Significant Segments
            if (result.quietSegments.isNotEmpty() || result.loudSegments.isNotEmpty()) {
                item {
                    Text(
                        text = "מקטעים בולטים שזוהו בקובץ:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(result.loudSegments) { seg ->
                    SegmentRow(seg = seg, isLoud = true)
                }

                items(result.quietSegments) { seg ->
                    SegmentRow(seg = seg, isLoud = false)
                }
            }

            // Scientific Note
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "הערה: קובץ שמע דיגיטלי נמדד בערכי dBFS יחסיים לתקרה הדיגיטלית. לא ניתן להסיק מקובץ שמע בלבד מה הייתה עוצמת הרעש הפיזיקלית (dB SPL) בחדר ללא כיול ערוץ ההקלטה המקורי.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricBox(label: String, value: String) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontFamily = RobotoMonoFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SegmentRow(seg: com.example.audio.AudioSegment, isLoud: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (isLoud) AcousticAmber else AcousticCyan, RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = seg.labelHe, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            text = "${seg.startMs}ms - ${seg.endMs}ms (${String.format(Locale.US, "%.1f", seg.avgDbfs)} dBFS)",
            fontFamily = RobotoMonoFontFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
