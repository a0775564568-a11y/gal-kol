package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.audio.AudioEngine
import com.example.data.RecordingDao
import com.example.data.RecordingEntity
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.AcousticAmber
import com.example.ui.theme.AcousticBlue
import com.example.ui.theme.AcousticCyan
import com.example.ui.theme.AcousticRed
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.RobotoMonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordingsScreen(
    audioEngine: AudioEngine,
    recordingDao: RecordingDao,
    onAnalyzeRecording: (File) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val liveState by audioEngine.uiState.collectAsState()
    val recordings by recordingDao.getAllRecordings().collectAsState(initial = emptyList())

    // Player state
    var currentlyPlayingId by remember { mutableStateOf<Long?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Rename dialog
    var renamingEntity by remember { mutableStateOf<RecordingEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    fun playRecording(entity: RecordingEntity) {
        val file = File(entity.filePath)
        if (!file.exists()) return

        if (currentlyPlayingId == entity.id) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            currentlyPlayingId = null
            return
        }

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(entity.filePath)
            prepare()
            setOnCompletionListener {
                currentlyPlayingId = null
            }
            start()
        }
        currentlyPlayingId = entity.id
    }

    fun shareFile(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "שתף הקלטת WAV"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("recordings_screen")
    ) {
        // Active Recording Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (liveState.isRecording) AcousticRed else AcousticCyan)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (liveState.isRecording) {
                                if (liveState.isRecordingPaused) "הקלטה מושהית" else "מקליט כעת..."
                            } else {
                                "הקלטת שמע באיכות 16-bit WAV"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Timer display
                    Text(
                        text = formatDuration(liveState.recordingDurationMs),
                        fontFamily = RobotoMonoFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (liveState.isRecording) AcousticRed else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Live mini waveform preview while recording
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = DeepNavy)
                ) {
                    WaveformVisualizer(
                        samples = liveState.waveformPoints,
                        timeWindowMs = 50f,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Record / Pause / Stop controls
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!liveState.isRecording) {
                        Button(
                            onClick = {
                                audioEngine.startCapture()
                                audioEngine.startRecording()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AcousticRed),
                            shape = CircleShape,
                            modifier = Modifier.size(54.dp).testTag("start_record_button")
                        ) {
                            Icon(Icons.Default.FiberManualRecord, contentDescription = "התחל הקלטה", tint = androidx.compose.ui.graphics.Color.White)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (liveState.isRecordingPaused) audioEngine.resumeRecording() else audioEngine.pauseRecording()
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (liveState.isRecordingPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (liveState.isRecordingPaused) "המשך" else "השהה"
                            )
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        Button(
                            onClick = {
                                val result = audioEngine.stopRecording()
                                if (result != null) {
                                    val timeStr = SimpleDateFormat("HH:mm:ss dd/MM", Locale.US).format(Date())
                                    val entity = RecordingEntity(
                                        title = "הקלטה $timeStr",
                                        filePath = result.file.absolutePath,
                                        durationMs = result.durationMs,
                                        sampleRate = result.sampleRate,
                                        fileSizeBytes = result.fileSize,
                                        createdAt = System.currentTimeMillis(),
                                        peakDbfs = result.peakDbfs,
                                        rmsDbfs = result.rmsDbfs
                                    )
                                    coroutineScope.launch(Dispatchers.IO) {
                                        recordingDao.insert(entity)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AcousticAmber),
                            shape = CircleShape,
                            modifier = Modifier.size(54.dp).testTag("stop_record_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "עצור ושמור", tint = androidx.compose.ui.graphics.Color.White)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "הקלטות שמורות במכשיר (${recordings.size}):",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (recordings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "אין הקלטות שמורות עדיין. לחץ על כפתור ההקלטה למעלה כדי להתחיל.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recordings, key = { it.id }) { rec ->
                    RecordingCardItem(
                        recording = rec,
                        isPlaying = currentlyPlayingId == rec.id,
                        onPlayToggle = { playRecording(rec) },
                        onRename = {
                            renamingEntity = rec
                            renameText = rec.title
                        },
                        onDelete = {
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    File(rec.filePath).delete()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                recordingDao.delete(rec)
                            }
                        },
                        onShare = { shareFile(rec.filePath) },
                        onAnalyze = { onAnalyzeRecording(File(rec.filePath)) }
                    )
                }
            }
        }
    }

    // Rename Dialog
    renamingEntity?.let { entity ->
        AlertDialog(
            onDismissRequest = { renamingEntity = null },
            title = { Text("שינוי שם הקלטה") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("שם חדש") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            recordingDao.update(entity.copy(title = renameText))
                        }
                        renamingEntity = null
                    }
                ) {
                    Text("שמור")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingEntity = null }) {
                    Text("ביטול")
                }
            }
        )
    }
}

@Composable
private fun RecordingCardItem(
    recording: RecordingEntity,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onAnalyze: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recording.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${formatDuration(recording.durationMs)} | ${recording.fileSizeBytes / 1024} KB | ${SimpleDateFormat("dd/MM/yy HH:mm", Locale.US).format(Date(recording.createdAt))}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Play / Pause Button
                IconButton(
                    onClick = onPlayToggle,
                    modifier = Modifier
                        .size(38.dp)
                        .background(if (isPlaying) AcousticAmber else AcousticBlue, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "עצור" else "נגן",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onAnalyze, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Analytics, contentDescription = "פתח במעבדה לניתוח", tint = AcousticBlue, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "שתף קובץ", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "שנה שם", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "מחק", tint = AcousticRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = (durationMs % 1000) / 100
    return String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, millis)
}
