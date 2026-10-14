package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.AcousticExplanation
import com.example.dsp.FrequencyBandsEnergy
import com.example.ui.theme.AcousticAmber
import com.example.ui.theme.AcousticBlue
import com.example.ui.theme.AcousticCyan
import com.example.ui.theme.AcousticRed
import java.util.Locale

@Composable
fun ExplanationCard(
    explanation: AcousticExplanation,
    energyBands: FrequencyBandsEnergy? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("explanation_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AcousticCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "הסבר אקוסטי",
                        tint = AcousticBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = explanation.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = explanation.confidenceLevel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Threshold warning if exceeded
            if (explanation.isThresholdExceeded && explanation.thresholdDifferenceDb != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            AcousticRed.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "התראת חריגה",
                        tint = AcousticRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "התראה: העוצמה גבוהה ב־${String.format(Locale.US, "%.1f", explanation.thresholdDifferenceDb)} dB מהסף שהוגדר!",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AcousticRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Plain Hebrew Interpretation
            Text(
                text = explanation.summaryText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Reference Comparison
            ExplanationSubBlock(
                title = "השוואה לדיבור רגיל (נקודת ייחוס):",
                content = explanation.referenceComparisonText,
                accentColor = AcousticBlue
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Spectral character (Tone vs Noise)
            ExplanationSubBlock(
                title = "אופי הצליל והתדרים:",
                content = explanation.spectralCharacterText,
                accentColor = AcousticCyan
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Dynamics & Crest Factor
            ExplanationSubBlock(
                title = "דינמיקה ומקדם שיא (Crest Factor):",
                content = explanation.dynamicsText,
                accentColor = if (explanation.dynamicsText.contains("קטימה")) AcousticRed else AcousticAmber
            )

            // Exposure advisory if present
            explanation.exposureAdvisoryText?.let { advisory ->
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            AcousticAmber.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp)
                ) {
                    Text(
                        text = advisory,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                }
            }

            // Energy bands chart if provided
            energyBands?.let { bands ->
                Spacer(modifier = Modifier.height(14.dp))
                FrequencyBandsBarChart(energyBands = bands)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Limitations note
            Text(
                text = "מגבלות מדידה: ${explanation.limitationsNote}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun ExplanationSubBlock(
    title: String,
    content: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(36.dp)
                .background(accentColor, shape = RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = content,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )
        }
    }
}
