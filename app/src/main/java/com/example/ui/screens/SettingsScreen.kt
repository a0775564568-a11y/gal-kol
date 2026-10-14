package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.dsp.WindowType
import com.example.ui.theme.AcousticAmber
import com.example.ui.theme.AcousticBlue
import com.example.ui.theme.AcousticCyan
import com.example.ui.theme.RobotoMonoFontFamily
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section 1: Microphone Calibration & Acoustic Reference
        SettingsSectionCard(
            title = "כיול מיקרופון ועוצמת ייחוס",
            icon = Icons.Default.Tune,
            iconTint = AcousticBlue
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "הפעל כיול dB SPL", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        text = "מאפשר אמידת ערכי dB SPL פיזיקליים ביחס לדיבור רגיל. כאשר כבוי, התצוגה היא ב־dBFS בלבד.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.isCalibrated,
                    onCheckedChange = { onSettingsChanged(settings.copy(isCalibrated = it)) },
                    modifier = Modifier.testTag("calibration_switch")
                )
            }

            if (settings.isCalibrated) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "היסט כיול (Offset): ${String.format(Locale.US, "%.1f", settings.calibrationOffsetDb)} dB SPL",
                    fontFamily = RobotoMonoFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AcousticBlue
                )
                Slider(
                    value = settings.calibrationOffsetDb.toFloat(),
                    onValueChange = { onSettingsChanged(settings.copy(calibrationOffsetDb = it.toDouble())) },
                    valueRange = 70f..120f,
                    modifier = Modifier.fillMaxWidth().testTag("calibration_slider")
                )
                Text(
                    text = "מוגדר כברירת מחדל ל־94 dB SPL (שווה ערך ל־1 Pa תקני). כוונן בהתאם למד רעש ייחוס חיצוני.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "נקודת ייחוס לדיבור רגיל: ${settings.speechReferenceDb.toInt()} dB SPL",
                fontFamily = RobotoMonoFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = settings.speechReferenceDb.toFloat(),
                onValueChange = { onSettingsChanged(settings.copy(speechReferenceDb = it.toDouble())) },
                valueRange = 50f..75f,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "נקודת ההשוואה לדיבור רגיל (ברירת מחדל: 60 dB SPL במרחק מטר).",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Section 2: Alerts and Thresholds
        SettingsSectionCard(
            title = "התראות וספי עוצמה",
            icon = Icons.Default.NotificationsActive,
            iconTint = AcousticAmber
        ) {
            val unit = if (settings.isCalibrated) "dB SPL" else "dBFS"
            val range = if (settings.isCalibrated) 50f..100f else -60f..0f

            Text(
                text = "סף התראת עוצמה: ${String.format(Locale.US, "%.1f", settings.alertThresholdDb)} $unit",
                fontFamily = RobotoMonoFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = settings.alertThresholdDb.toFloat().coerceIn(range.start, range.endInclusive),
                onValueChange = { onSettingsChanged(settings.copy(alertThresholdDb = it.toDouble())) },
                valueRange = range,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "התראה על קטימה (Clipping)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        text = "מציג אזהרה מיידית כאשר גל הקול דוחס את המשרעת המקסימלית של המיקרופון.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.clippingAlertEnabled,
                    onCheckedChange = { onSettingsChanged(settings.copy(clippingAlertEnabled = it)) }
                )
            }
        }

        // Section 3: DSP & FFT Parameters
        SettingsSectionCard(
            title = "הגדרות עיבוד אותות (DSP & FFT)",
            icon = Icons.Default.GraphicEq,
            iconTint = AcousticCyan
        ) {
            Text(text = "גודל חלון FFT:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1024, 2048, 4096).forEach { size ->
                    FilterChip(
                        selected = settings.fftSize == size,
                        onClick = { onSettingsChanged(settings.copy(fftSize = size)) },
                        label = { Text("$size דגימות", fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(text = "פונקציית חלון (Windowing):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WindowType.values().forEach { w ->
                    FilterChip(
                        selected = settings.windowType == w,
                        onClick = { onSettingsChanged(settings.copy(windowType = w)) },
                        label = { Text(w.labelHe.substringBefore(" "), fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "סקאלת תדרים לוגריתמית", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(text = "מותאמת לשמיעה האנושית (העדפת תדרי שמע נמוכים ובינוניים).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = settings.useLogFrequencyScale,
                    onCheckedChange = { onSettingsChanged(settings.copy(useLogFrequencyScale = it)) }
                )
            }
        }

        // Section 4: Appearance & Theme
        SettingsSectionCard(
            title = "מראה ועיצוב",
            icon = Icons.Default.Palette,
            iconTint = MaterialTheme.colorScheme.primary
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "מצב כהה (Dark Theme)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(text = "מעבר ידני בין ערכת נושא בהירה לכהה.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = settings.isDarkMode ?: false,
                    onCheckedChange = { onSettingsChanged(settings.copy(isDarkMode = it)) }
                )
            }
        }

        // Section 5: About the App
        SettingsSectionCard(
            title = "אודות גל־קול",
            icon = Icons.Default.Info,
            iconTint = AcousticBlue
        ) {
            Text(
                text = "גל־קול | מעבדת שמע מתקדמת",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "שומעים. רואים. מבינים.",
                style = MaterialTheme.typography.bodyMedium,
                color = AcousticBlue,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "גרסה 1.0.0 | אפליקציית ניתוח אקוסטי ועיבוד אותות ספרתי (DSP) מקומי מלא, ללא שרתים, ללא רשת, וללא פרסומות.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
