package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dsp.SoundStability
import com.example.ui.theme.AcousticAmber
import com.example.ui.theme.AcousticCyan
import com.example.ui.theme.AcousticGreen
import com.example.ui.theme.AcousticRed
import com.example.ui.theme.RobotoMonoFontFamily
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LevelMeterGauge(
    currentDbfs: Double,
    peakDbfs: Double,
    minDbfs: Double,
    estimatedDbSpl: Double?,
    isCalibrated: Boolean,
    stability: SoundStability,
    leqDb: Double?,
    dominantFreqHz: Float,
    modifier: Modifier = Modifier
) {
    val displayValue = if (isCalibrated) estimatedDbSpl ?: (currentDbfs + 94.0) else currentDbfs
    val unit = if (isCalibrated) "dB SPL" else "dBFS"

    // Normalize gauge progress: if calibrated (30..110 dB SPL), if uncalibrated (-80..0 dBFS)
    val normProgress = if (isCalibrated) {
        ((displayValue - 30.0) / (110.0 - 30.0)).coerceIn(0.0, 1.0).toFloat()
    } else {
        ((displayValue + 80.0) / 80.0).coerceIn(0.0, 1.0).toFloat()
    }

    val animatedProgress by animateFloatAsState(
        targetValue = normProgress,
        animationSpec = tween(durationMillis = 80),
        label = "gauge_progress"
    )

    // Dynamic color based on intensity
    val gaugeColor = when {
        isCalibrated && displayValue >= 85.0 -> AcousticRed
        isCalibrated && displayValue >= 70.0 -> AcousticAmber
        !isCalibrated && displayValue >= -6.0 -> AcousticRed
        !isCalibrated && displayValue >= -18.0 -> AcousticAmber
        else -> AcousticCyan
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("level_meter_gauge"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Meter Dial Arc
        Box(
            modifier = Modifier.size(240.dp, 150.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(220.dp, 220.dp)) {
                val strokeWidth = 14.dp.toPx()
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                // Background track arc (180 to 360 degrees = bottom half omitted, top semi-circle)
                drawArc(
                    color = Color.Gray.copy(alpha = 0.2f),
                    startAngle = 150f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Active level arc
                val sweep = animatedProgress * 240f
                if (sweep > 0f) {
                    drawArc(
                        brush = Brush.horizontalGradient(
                            listOf(AcousticGreen, AcousticCyan, gaugeColor)
                        ),
                        startAngle = 150f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // Tick marks
                val radius = (size.width - strokeWidth) / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                val totalTicks = 9
                for (i in 0 until totalTicks) {
                    val angleDeg = 150f + i * (240f / (totalTicks - 1))
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val pInner = Offset(
                        (center.x + (radius - 12.dp.toPx()) * cos(angleRad)).toFloat(),
                        (center.y + (radius - 12.dp.toPx()) * sin(angleRad)).toFloat()
                    )
                    val pOuter = Offset(
                        (center.x + (radius - 4.dp.toPx()) * cos(angleRad)).toFloat(),
                        (center.y + (radius - 4.dp.toPx()) * sin(angleRad)).toFloat()
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.3f),
                        start = pInner,
                        end = pOuter,
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            // Big Central Numeric Readout
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 28.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "%.1f", displayValue),
                    fontFamily = RobotoMonoFontFamily,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = unit,
                        fontFamily = RobotoMonoFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = gaugeColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isCalibrated) AcousticGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isCalibrated) "מכויל" else "לא מכויל",
                            fontSize = 10.sp,
                            color = if (isCalibrated) AcousticGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Mini Grid of Acoustic Sub-Metrics
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MetricPill(
                label = "שיא (Peak)",
                value = String.format(Locale.US, "%.1f", peakDbfs),
                unit = "dBFS"
            )
            MetricPill(
                label = "מינימום",
                value = String.format(Locale.US, "%.1f", minDbfs),
                unit = "dBFS"
            )
            MetricPill(
                label = "Leq משוקלל",
                value = leqDb?.let { String.format(Locale.US, "%.1f", it) } ?: "-",
                unit = if (isCalibrated) "dB" else "dBFS"
            )
            MetricPill(
                label = "תדר דומיננטי",
                value = if (dominantFreqHz > 25f) "${dominantFreqHz.toInt()}" else "-",
                unit = "Hz"
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Stability Status Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(
                    color = when (stability) {
                        SoundStability.VERY_STABLE -> AcousticGreen.copy(alpha = 0.15f)
                        SoundStability.MODERATE_FLUCTUATION -> AcousticCyan.copy(alpha = 0.15f)
                        SoundStability.HIGH_FLUCTUATION -> AcousticAmber.copy(alpha = 0.15f)
                    },
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = when (stability) {
                            SoundStability.VERY_STABLE -> AcousticGreen
                            SoundStability.MODERATE_FLUCTUATION -> AcousticCyan
                            SoundStability.HIGH_FLUCTUATION -> AcousticAmber
                        },
                        shape = RoundedCornerShape(4.dp)
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${stability.labelHe}: ${stability.descriptionHe}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    unit: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontFamily = RobotoMonoFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = unit,
                fontFamily = RobotoMonoFontFamily,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
