package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dsp.FrequencyBandsEnergy
import com.example.ui.theme.AcousticBlue
import com.example.ui.theme.AcousticCyan
import com.example.ui.theme.RobotoMonoFontFamily

@Composable
fun FrequencyBandsBarChart(
    energyBands: FrequencyBandsEnergy,
    modifier: Modifier = Modifier
) {
    val bands = energyBands.toList()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = "התפלגות אנרגיה אקוסטית בתחומי תדרים:",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Vertical bars container
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            bands.forEach { (label, energyFraction) ->
                BandColumn(label = label, fraction = energyFraction)
            }
        }
    }
}

@Composable
private fun BandColumn(
    label: String,
    fraction: Float
) {
    val animatedHeight by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 100),
        label = "band_anim"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(42.dp)
    ) {
        // Percentage text
        Text(
            text = "${(fraction * 100).toInt()}%",
            fontFamily = RobotoMonoFontFamily,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))

        // Bar container
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(80.dp)
                .background(Color.Gray.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(animatedHeight)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(AcousticCyan, AcousticBlue)
                        ),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        // Short label
        val shortLabel = when {
            label.contains("סאב") -> "סאב"
            label.contains("באס") -> "באס"
            label.contains("נמוך") -> "L-Mid"
            label.contains("גבוה") -> "H-Mid"
            label.contains("מיד") -> "מיד"
            label.contains("טרבל") -> "טרבל"
            else -> "אוויר"
        }

        Text(
            text = shortLabel,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
