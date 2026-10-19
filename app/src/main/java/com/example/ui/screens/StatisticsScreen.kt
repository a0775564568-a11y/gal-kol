package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Whatshot
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.AcousticAnomalyEventEntity
import com.example.data.AnomalyDao
import com.example.data.LiveSessionSummaryEntity
import com.example.data.SessionDao
import com.example.export.ReportExporter
import com.example.ui.theme.AcousticAmber
import com.example.ui.theme.AcousticBlue
import com.example.ui.theme.AcousticCyan
import com.example.ui.theme.AcousticGreen
import com.example.ui.theme.AcousticRed
import com.example.ui.theme.RobotoMonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun StatisticsScreen(
    anomalyDao: AnomalyDao,
    sessionDao: SessionDao
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allAnomalies by anomalyDao.getAllAnomalies().collectAsState(initial = emptyList())
    val allSessions by sessionDao.getAllSessions().collectAsState(initial = emptyList())

    // Active sub-tab: 0 = יומן חריגות וצעקות, 1 = סבבי ניטור רציפים שנשמרו
    var activeSubTab by remember { mutableIntStateOf(0) }

    // Time filter: 0 = היום, 1 = 7 ימים, 2 = 30 ימים, 3 = כל הזמנים
    var selectedTimeFilter by remember { mutableIntStateOf(0) }

    // Type filter: 0 = הכל, 1 = צעקות ושיאים, 2 = אימפולסים, 3 = קטימות, 4 = שריקות/תדרים
    var selectedTypeFilter by remember { mutableIntStateOf(0) }

    // Severity filter: 0 = כל הרמות, 1 = קיצונית, 2 = חמורה, 3 = בינונית
    var selectedSeverityFilter by remember { mutableIntStateOf(0) }

    // Filter by specific hour (null = all hours, 0..23 = clicked on histogram bar)
    var selectedHourFilter by remember { mutableStateOf<Int?>(null) }

    var showClearDialog by remember { mutableStateOf(false) }

    // Filtered Anomalies computation
    val filteredAnomalies = remember(allAnomalies, selectedTimeFilter, selectedTypeFilter, selectedSeverityFilter, selectedHourFilter) {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        allAnomalies.filter { item ->
            // Time match
            val matchesTime = when (selectedTimeFilter) {
                0 -> {
                    calendar.timeInMillis = now
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    item.timestamp >= calendar.timeInMillis
                }
                1 -> item.timestamp >= (now - 7L * 24 * 60 * 60 * 1000)
                2 -> item.timestamp >= (now - 30L * 24 * 60 * 60 * 1000)
                else -> true
            }

            // Type match
            val matchesType = when (selectedTypeFilter) {
                1 -> item.eventType.contains("צעקה") || item.eventType.contains("שיא")
                2 -> item.eventType.contains("אימפולס") || item.eventType.contains("פולס") || item.eventType.contains("חדה")
                3 -> item.eventType.contains("קטימה") || item.eventType.contains("Clipping")
                4 -> item.eventType.contains("צפירה") || item.eventType.contains("שריקה") || item.eventType.contains("תדר")
                else -> true
            }

            // Severity match
            val matchesSeverity = when (selectedSeverityFilter) {
                1 -> item.severity == "קיצונית"
                2 -> item.severity == "חמורה"
                3 -> item.severity == "בינונית"
                else -> true
            }

            // Hour match
            val matchesHour = if (selectedHourFilter != null) {
                calendar.timeInMillis = item.timestamp
                calendar.get(Calendar.HOUR_OF_DAY) == selectedHourFilter
            } else {
                true
            }

            matchesTime && matchesType && matchesSeverity && matchesHour
        }
    }

    // KPI Metrics
    val totalCount = filteredAnomalies.size
    val peakAnomaly = filteredAnomalies.maxByOrNull { it.peakDbfs }
    val screamsCount = filteredAnomalies.count { it.eventType.contains("צעקה") || it.eventType.contains("שיא") }
    val clippingCount = filteredAnomalies.count { it.eventType.contains("קטימה") || it.eventType.contains("Clipping") }
    val impulsesCount = filteredAnomalies.count { it.eventType.contains("אימפולס") || it.eventType.contains("פולס") }
    val avgExcessDb = if (filteredAnomalies.isNotEmpty()) {
        filteredAnomalies.map { it.excessDb }.average()
    } else 0.0

    // Hourly Histogram (0..23)
    val hourlyBuckets = remember(filteredAnomalies) {
        val buckets = IntArray(24)
        val cal = Calendar.getInstance()
        for (item in filteredAnomalies) {
            cal.timeInMillis = item.timestamp
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            if (hour in 0..23) buckets[hour]++
        }
        buckets
    }
    val maxHourlyCount = hourlyBuckets.maxOrNull()?.coerceAtLeast(1) ?: 1
    val peakHour = hourlyBuckets.indices.maxByOrNull { hourlyBuckets[it] } ?: 0

    // Severity breakdown counts
    val extremeCount = filteredAnomalies.count { it.severity == "קיצונית" }
    val severeCount = filteredAnomalies.count { it.severity == "חמורה" }
    val moderateCount = filteredAnomalies.count { it.severity == "בינונית" }
    val mildCount = filteredAnomalies.count { it.severity == "קלה" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("statistics_screen")
    ) {
        // --- 1. HEADER ROW WITH AUTO-SAVE BADGE & ACTIONS ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AcousticBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = AcousticBlue, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "מרכז סטטיסטיקות שמע",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(AcousticGreen, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "שמירה אוטומטית פעילה תמיד",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AcousticGreen
                            )
                        }
                    }
                }

                // Header Action Buttons (Share CSV & Clear)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (filteredAnomalies.isNotEmpty()) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val csvFile = ReportExporter.exportAnomaliesToCsv(context, filteredAnomalies)
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", csvFile)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "שתף דוח סטטיסטיקות גל־קול"))
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp).testTag("export_csv_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "שתף דוח CSV", tint = AcousticBlue, modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.size(32.dp).testTag("clear_stats_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "איפוס נתונים", tint = AcousticRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Sub Tabs: [יומן חריגות וצעקות] vs [סבבי ניטור רציפים]
        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
        ) {
            Tab(
                selected = activeSubTab == 0,
                onClick = { activeSubTab = 0 },
                text = { Text("יומן חריגות וצעקות (${allAnomalies.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = activeSubTab == 1,
                onClick = { activeSubTab = 1 },
                text = { Text("סבבי ניטור אוטומטיים (${allSessions.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (activeSubTab == 1) {
            // --- SESSIONS SUB TAB ---
            SessionsSubView(sessions = allSessions)
        } else {
            // --- ANOMALIES & STATISTICS SUB TAB ---
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Time Range Filter Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("היום", "7 ימים", "30 ימים", "כל הזמנים").forEachIndexed { idx, label ->
                            FilterChip(
                                selected = selectedTimeFilter == idx,
                                onClick = {
                                    selectedTimeFilter = idx
                                    selectedHourFilter = null
                                },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Type & Severity Filter Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("כל הסוגים", "צעקות ושיאים", "אימפולסים", "קטימות", "תדרים צורמים").forEachIndexed { idx, label ->
                            FilterChip(
                                selected = selectedTypeFilter == idx,
                                onClick = { selectedTypeFilter = idx },
                                label = { Text(label, fontSize = 10.sp) }
                            )
                        }
                    }
                }

                // Active Hour Filter Indicator (if clicked on histogram)
                if (selectedHourFilter != null) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AcousticBlue.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "סינון לפי שעה: ${String.format(Locale.US, "%02d:00 - %02d:00", selectedHourFilter, selectedHourFilter!! + 1)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AcousticBlue
                            )
                            TextButton(
                                onClick = { selectedHourFilter = null },
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("הצג את כל השעות", fontSize = 10.sp, color = AcousticBlue)
                            }
                        }
                    }
                }

                // --- 2. KPI SUMMARY GRID (6 Metric Cards) ---
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatCard(
                                title = "סך חריגות ואירועים",
                                value = "$totalCount",
                                sub = "נשמר אוטומטית",
                                icon = Icons.Default.NotificationImportant,
                                accentColor = AcousticAmber,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "צעקות / שיאי קול",
                                value = "$screamsCount",
                                sub = "עוצמה קולית גבוהה",
                                icon = Icons.Default.Whatshot,
                                accentColor = AcousticRed,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "קטימות ורוויה",
                                value = "$clippingCount",
                                sub = "עיוות דיגיטלי",
                                icon = Icons.Default.Warning,
                                accentColor = Color(0xFFF97316),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatCard(
                                title = "שיא עוצמה שנמדד",
                                value = peakAnomaly?.let { String.format(Locale.US, "%.1f", it.peakDbfs) } ?: "- -",
                                sub = "dBFS",
                                icon = Icons.Default.Equalizer,
                                accentColor = AcousticRed,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "חריגה ממוצעת",
                                value = "+${String.format(Locale.US, "%.1f", avgExcessDb)}",
                                sub = "dB מעל הממוצע",
                                icon = Icons.Default.TrendingUp,
                                accentColor = AcousticBlue,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "שעה עמוסה ביותר",
                                value = String.format(Locale.US, "%02d:00", peakHour),
                                sub = "${hourlyBuckets[peakHour]} אירועים",
                                icon = Icons.Default.Schedule,
                                accentColor = AcousticCyan,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // --- 3. 24-HOUR INTERACTIVE TIMELINE HISTOGRAM ---
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = AcousticBlue, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "התפלגות אירועים חריגים לפי שעות ביממה (לחץ לסינון):",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (selectedHourFilter != null) {
                                    Text(
                                        text = "${selectedHourFilter}:00 נבחר",
                                        fontSize = 10.sp,
                                        color = AcousticCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 24 Hour Bars (Interactive - tap to filter!)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(85.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                for (hour in 0..23) {
                                    val count = hourlyBuckets[hour]
                                    val barFraction = (count.toFloat() / maxHourlyCount).coerceIn(0.06f, 1f)
                                    val isHourSelected = selectedHourFilter == hour

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                selectedHourFilter = if (selectedHourFilter == hour) null else hour
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(if (isHourSelected) 11.dp else 8.dp)
                                                .fillMaxHeight(barFraction)
                                                .background(
                                                    color = when {
                                                        isHourSelected -> AcousticCyan
                                                        count > 0 -> AcousticRed
                                                        else -> Color.Gray.copy(alpha = 0.18f)
                                                    },
                                                    shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                                                )
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        if (hour % 3 == 0) {
                                            Text(
                                                text = "$hour",
                                                fontFamily = RobotoMonoFontFamily,
                                                fontSize = 8.sp,
                                                color = if (isHourSelected) AcousticCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- 4. SEVERITY BREAKDOWN BAR ---
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "התפלגות רמות חומרה:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            // Stacked Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                            ) {
                                if (totalCount == 0) {
                                    Box(modifier = Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.2f)))
                                } else {
                                    if (extremeCount > 0) Box(modifier = Modifier.weight(extremeCount.toFloat()).fillMaxHeight().background(AcousticRed))
                                    if (severeCount > 0) Box(modifier = Modifier.weight(severeCount.toFloat()).fillMaxHeight().background(Color(0xFFF97316)))
                                    if (moderateCount > 0) Box(modifier = Modifier.weight(moderateCount.toFloat()).fillMaxHeight().background(AcousticAmber))
                                    if (mildCount > 0) Box(modifier = Modifier.weight(mildCount.toFloat()).fillMaxHeight().background(AcousticCyan))
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Severity Legend
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                SeverityLegendItem(label = "קיצונית", count = extremeCount, color = AcousticRed)
                                SeverityLegendItem(label = "חמורה", count = severeCount, color = Color(0xFFF97316))
                                SeverityLegendItem(label = "בינונית", count = moderateCount, color = AcousticAmber)
                                SeverityLegendItem(label = "קלה", count = mildCount, color = AcousticCyan)
                            }
                        }
                    }
                }

                // --- 5. DETAILED ANOMALY EVENT LOG ---
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "אירועים וגלי קול חריגים שנלכדו (${filteredAnomalies.size}):",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (filteredAnomalies.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AcousticGreen, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "לא זוהו גלי קול חריגים או צעקות בטווח שנבחר.\nהאפליקציה דוגמת את השמע ברקע ושומרת כל חריגה מיד באופן אוטומטי!",
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(filteredAnomalies, key = { it.id }) { anomaly ->
                        DetailedAnomalyCardItem(
                            anomaly = anomaly,
                            onDelete = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    anomalyDao.deleteById(anomaly.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Reset Confirmation Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("איפוס נתוני סטטיסטיקה") },
            text = { Text("האם אתה בטוח שברצונך למחוק את כל יומן האירועים החריגים וסבבי הניטור השמורים?") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            anomalyDao.deleteAll()
                            sessionDao.deleteAll()
                        }
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AcousticRed)
                ) {
                    Text("מחק הכל")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("ביטול")
                }
            }
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    sub: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontFamily = RobotoMonoFontFamily,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(text = sub, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun SeverityLegendItem(
    label: String,
    count: Int,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = "$label: $count", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetailedAnomalyCardItem(
    anomaly: AcousticAnomalyEventEntity,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)
    val timeStr = dateFormat.format(Date(anomaly.timestamp))

    val severityColor = when (anomaly.severity) {
        "קיצונית" -> AcousticRed
        "חמורה" -> Color(0xFFF97316)
        "בינונית" -> AcousticAmber
        else -> AcousticCyan
    }

    val eventIcon = when {
        anomaly.eventType.contains("צעקה") -> Icons.Default.Whatshot
        anomaly.eventType.contains("קטימה") -> Icons.Default.Warning
        anomaly.eventType.contains("אימפולס") || anomaly.eventType.contains("פולס") -> Icons.Default.Equalizer
        else -> Icons.Default.NotificationImportant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Top Row: Type, Severity badge, and Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(severityColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(eventIcon, contentDescription = null, tint = severityColor, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = anomaly.eventType,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(severityColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = anomaly.severity,
                            color = severityColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Clear, contentDescription = "מחק", tint = Color.Gray, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Exact Timestamp Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = timeStr,
                    fontFamily = RobotoMonoFontFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Telemetry Pills Row (Peak dBFS, Excess dB, Dominant Freq)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "שיא: ${String.format(Locale.US, "%.1f", anomaly.peakDbfs)} dBFS",
                    fontFamily = RobotoMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AcousticRed
                )
                Text(
                    text = "חריגה: +${String.format(Locale.US, "%.1f", anomaly.excessDb)} dB",
                    fontFamily = RobotoMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AcousticAmber
                )
                Text(
                    text = "תדר: ${anomaly.dominantFrequencyHz.toInt()} Hz",
                    fontFamily = RobotoMonoFontFamily,
                    fontSize = 11.sp,
                    color = AcousticBlue
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Hebrew Verbal Explanation
            Text(
                text = anomaly.descriptionHe,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun SessionsSubView(sessions: List<LiveSessionSummaryEntity>) {
    if (sessions.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "עדיין לא נרשמו סבבי ניטור.\nהסבבים נשמרים ברקע אוטומטית בכל 10 שניות בזמן פעילות המיקרופון.",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "סבבי ניטור שנשמרו ברקע באופן אוטומטי (${sessions.size}):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            items(sessions, key = { it.id }) { session ->
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "סבב #${session.id} - ${dateFormat.format(Date(session.timestamp))}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${session.durationSeconds} שניות",
                                fontSize = 10.sp,
                                color = AcousticBlue
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "ממוצע: ${String.format(Locale.US, "%.1f", session.avgDbfs)} dB",
                                fontFamily = RobotoMonoFontFamily,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "שיא: ${String.format(Locale.US, "%.1f", session.peakDbfs)} dB",
                                fontFamily = RobotoMonoFontFamily,
                                fontSize = 10.sp,
                                color = AcousticRed
                            )
                            Text(
                                text = "אירועים חריגים: ${session.anomaliesCount}",
                                fontFamily = RobotoMonoFontFamily,
                                fontSize = 10.sp,
                                color = if (session.anomaliesCount > 0) AcousticAmber else AcousticGreen
                            )
                        }
                    }
                }
            }
        }
    }
}
