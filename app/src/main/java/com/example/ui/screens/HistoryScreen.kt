package com.example.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.MeasurementDao
import com.example.data.MeasurementEntity
import com.example.domain.ComparisonEngine
import com.example.domain.ComparisonResult
import com.example.export.ReportExporter
import com.example.ui.theme.AcousticAmber
import com.example.ui.theme.AcousticBlue
import com.example.ui.theme.AcousticCyan
import com.example.ui.theme.AcousticRed
import com.example.ui.theme.RobotoMonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    measurementDao: MeasurementDao
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allMeasurements by measurementDao.getAllMeasurements().collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<Long>() }

    // Dialog states
    var comparisonResult by remember { mutableStateOf<ComparisonResult?>(null) }
    var renamingMeasurement by remember { mutableStateOf<MeasurementEntity?>(null) }
    var renameTitle by remember { mutableStateOf("") }

    val filtered = remember(allMeasurements, searchQuery) {
        if (searchQuery.isBlank()) allMeasurements
        else allMeasurements.filter { it.title.contains(searchQuery, ignoreCase = true) || it.notes.contains(searchQuery, ignoreCase = true) }
    }

    fun shareFile(file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "שתף קובץ דוח"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("history_screen")
    ) {
        // Search & Export Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("חיפוש בהיסטוריית מדידות") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        Button(
                            onClick = {
                                if (allMeasurements.isNotEmpty()) {
                                    val csvFile = ReportExporter.exportToCsv(context, allMeasurements)
                                    shareFile(csvFile, "text/csv")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AcousticBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ייצוא CSV", fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Button(
                            onClick = {
                                if (allMeasurements.isNotEmpty()) {
                                    val jsonFile = ReportExporter.exportToJson(context, allMeasurements)
                                    shareFile(jsonFile, "application/json")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("ייצוא JSON", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    // Compare button (active if exactly 2 items selected)
                    if (selectedIds.size == 2) {
                        Button(
                            onClick = {
                                val itemA = allMeasurements.find { it.id == selectedIds[0] }
                                val itemB = allMeasurements.find { it.id == selectedIds[1] }
                                if (itemA != null && itemB != null) {
                                    comparisonResult = ComparisonEngine.compare(itemA, itemB)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AcousticCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("compare_selected_button")
                        ) {
                            Icon(Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("השווה (2)")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (allMeasurements.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = "אין מדידות שמורות עדיין. שמור מדידות ממסך הניתוח החי.",
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
                items(filtered, key = { it.id }) { item ->
                    MeasurementHistoryItem(
                        item = item,
                        isSelected = selectedIds.contains(item.id),
                        onToggleSelect = {
                            if (selectedIds.contains(item.id)) selectedIds.remove(item.id)
                            else if (selectedIds.size < 2) selectedIds.add(item.id)
                        },
                        onExportPdf = {
                            val pdfFile = ReportExporter.exportToPdf(context, item)
                            shareFile(pdfFile, "application/pdf")
                        },
                        onRename = {
                            renamingMeasurement = item
                            renameTitle = item.title
                        },
                        onDelete = {
                            coroutineScope.launch(Dispatchers.IO) {
                                measurementDao.delete(item)
                            }
                        }
                    )
                }
            }
        }
    }

    // Rename Dialog
    renamingMeasurement?.let { item ->
        AlertDialog(
            onDismissRequest = { renamingMeasurement = null },
            title = { Text("ערוך כותרת מדידה") },
            text = {
                OutlinedTextField(
                    value = renameTitle,
                    onValueChange = { renameTitle = it },
                    label = { Text("כותרת חדשה") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            measurementDao.update(item.copy(title = renameTitle))
                        }
                        renamingMeasurement = null
                    }
                ) {
                    Text("שמור")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingMeasurement = null }) {
                    Text("ביטול")
                }
            }
        )
    }

    // Comparison Result Dialog
    comparisonResult?.let { res ->
        ComparisonResultDialog(
            result = res,
            onDismiss = { comparisonResult = null }
        )
    }
}

@Composable
private fun MeasurementHistoryItem(
    item: MeasurementEntity,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onExportPdf: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) AcousticCyan.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(item.timestamp))} | ${item.stabilityLabel}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text(
                        text = "RMS: ${String.format(Locale.US, "%.1f", item.rmsDbfs)} dBFS",
                        fontFamily = RobotoMonoFontFamily,
                        fontSize = 11.sp,
                        color = AcousticBlue
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "שיא: ${String.format(Locale.US, "%.1f", item.peakDbfs)} dBFS",
                        fontFamily = RobotoMonoFontFamily,
                        fontSize = 11.sp,
                        color = AcousticAmber
                    )
                    item.estimatedDbSpl?.let { spl ->
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${String.format(Locale.US, "%.1f", spl)} dB SPL",
                            fontFamily = RobotoMonoFontFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Actions
            IconButton(onClick = onExportPdf, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = "דוח PDF", tint = AcousticRed, modifier = Modifier.size(18.dp))
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

@Composable
private fun ComparisonResultDialog(
    result: ComparisonResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("תוצאות השוואת שתי מדידות", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${result.titleA}   לעומת   ${result.titleB}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AcousticBlue
                )
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(text = result.summaryTextHe, fontSize = 12.sp, lineHeight = 17.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Difference metrics
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CompMetric(label = "הפרש RMS", value = "${String.format(Locale.US, "%+.1f", result.rmsDiffDb)} dB")
                    CompMetric(label = "הפרש שיא", value = "${String.format(Locale.US, "%+.1f", result.peakDiffDb)} dB")
                    CompMetric(label = "הפרש Crest", value = "${String.format(Locale.US, "%+.1f", result.crestDiffDb)} dB")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(text = result.dynamicsComparisonHe, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = result.frequencyComparisonHe, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("סגור")
            }
        }
    )
}

@Composable
private fun CompMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontFamily = RobotoMonoFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
