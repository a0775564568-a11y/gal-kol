package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.RobotoMonoFontFamily
import java.util.LinkedList

@Composable
fun SpectrogramVisualizer(
    currentSpectrumDbfs: FloatArray,
    maxHistorySlices: Int = 80,
    modifier: Modifier = Modifier
) {
    // Rolling queue of spectrum slices
    val history = remember { LinkedList<FloatArray>() }
    var triggerRecompose by remember { mutableStateOf(0L) }

    LaunchedEffect(currentSpectrumDbfs) {
        if (currentSpectrumDbfs.isNotEmpty()) {
            if (history.size >= maxHistorySlices) {
                history.removeFirst()
            }
            history.addLast(currentSpectrumDbfs.copyOf())
            triggerRecompose++
        }
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("spectrogram_canvas")
        ) {
            val width = size.width
            val height = size.height

            if (history.isEmpty()) return@Canvas

            val sliceWidth = width / maxHistorySlices.toFloat()
            val totalSlices = history.size
            val numBins = history.first().size
            val binHeight = height / numBins.toFloat()

            for (col in 0 until totalSlices) {
                val slice = history[col]
                val x = (maxHistorySlices - totalSlices + col) * sliceWidth

                for (bin in 0 until numBins) {
                    // Invert Y so low frequencies are at bottom, high frequencies at top
                    val y = height - (bin + 1) * binHeight
                    val db = slice[bin]
                    val color = dbToHeatmapColor(db)

                    drawRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(sliceWidth + 0.5f, binHeight + 0.5f)
                    )
                }
            }

            // Grid lines
            val gridColor = Color.White.copy(alpha = 0.12f)
            drawLine(gridColor, Offset(0f, height * 0.25f), Offset(width, height * 0.25f), 1.dp.toPx())
            drawLine(gridColor, Offset(0f, height * 0.5f), Offset(width, height * 0.5f), 1.dp.toPx())
            drawLine(gridColor, Offset(0f, height * 0.75f), Offset(width, height * 0.75f), 1.dp.toPx())
        }

        // Frequency axis tags
        Text(
            text = "20 kHz",
            color = Color.White.copy(alpha = 0.6f),
            fontFamily = RobotoMonoFontFamily,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
        )
        Text(
            text = "10 kHz",
            color = Color.White.copy(alpha = 0.6f),
            fontFamily = RobotoMonoFontFamily,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.CenterStart).padding(4.dp)
        )
        Text(
            text = "0 Hz",
            color = Color.White.copy(alpha = 0.6f),
            fontFamily = RobotoMonoFontFamily,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
        )
    }
}

/**
 * Maps -100 dBFS to 0 dBFS into an acoustic spectrogram heat color palette:
 * Navy Black -> Dark Blue -> Teal -> Bright Cyan -> Amber -> Orange -> Fiery Red.
 */
private fun dbToHeatmapColor(db: Float): Color {
    val norm = ((db + 100f) / 100f).coerceIn(0f, 1f)
    return when {
        norm < 0.2f -> {
            val t = norm / 0.2f
            Color(red = 0.04f * t, green = 0.08f * t, blue = 0.18f * t)
        }
        norm < 0.45f -> {
            val t = (norm - 0.2f) / 0.25f
            Color(red = 0.04f + 0.02f * t, green = 0.08f + 0.5f * t, blue = 0.18f + 0.65f * t)
        }
        norm < 0.7f -> {
            val t = (norm - 0.45f) / 0.25f
            Color(red = 0.06f + 0.9f * t, green = 0.58f + 0.35f * t, blue = 0.83f - 0.7f * t)
        }
        norm < 0.9f -> {
            val t = (norm - 0.7f) / 0.2f
            Color(red = 0.96f, green = 0.93f - 0.5f * t, blue = 0.13f - 0.1f * t)
        }
        else -> {
            val t = (norm - 0.9f) / 0.1f
            Color(red = 0.96f + 0.04f * t, green = 0.43f - 0.35f * t, blue = 0.03f)
        }
    }
}
