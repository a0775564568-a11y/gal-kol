package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AcousticCyan
import com.example.ui.theme.AcousticPurple
import com.example.ui.theme.RobotoMonoFontFamily
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun SpectrumVisualizer(
    spectrumDbfs: FloatArray,
    sampleRate: Int = 44100,
    fftSize: Int = 2048,
    dominantFreqHz: Float = 0f,
    useLogScale: Boolean = true,
    modifier: Modifier = Modifier
) {
    var inspectedPoint by remember { mutableStateOf<Pair<Float, Float>?>(null) } // freqHz to dBFS

    val minFreq = 20f
    val maxFreq = (sampleRate / 2).toFloat()
    val minDb = -100f
    val maxDb = 0f

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("spectrum_canvas")
                .pointerInput(spectrumDbfs, useLogScale) {
                    detectTapGestures { tapOffset ->
                        val w = size.width
                        val h = size.height
                        val relX = (tapOffset.x / w).coerceIn(0f, 1f)
                        val freq = if (useLogScale) {
                            minFreq * (maxFreq / minFreq).pow(relX)
                        } else {
                            minFreq + relX * (maxFreq - minFreq)
                        }
                        val binRes = sampleRate.toFloat() / fftSize
                        val bin = (freq / binRes).toInt().coerceIn(0, spectrumDbfs.size - 1)
                        val db = spectrumDbfs[bin]
                        inspectedPoint = Pair(freq, db)
                    }
                }
        ) {
            val width = size.width
            val height = size.height

            // Background dB grid lines (-20, -40, -60, -80 dBFS)
            val gridColor = Color.White.copy(alpha = 0.08f)
            val dbs = listOf(-20f, -40f, -60f, -80f)
            for (db in dbs) {
                val y = height * (1f - (db - minDb) / (maxDb - minDb))
                drawLine(gridColor, Offset(0f, y), Offset(width, y), strokeWidth = 1.dp.toPx())
            }

            // Frequency vertical grid lines
            val freqMarkers = if (useLogScale) {
                listOf(100f, 250f, 500f, 1000f, 2500f, 5000f, 10000f, 20000f)
            } else {
                listOf(2000f, 5000f, 10000f, 15000f, 20000f)
            }

            for (f in freqMarkers) {
                if (f <= maxFreq) {
                    val relX = if (useLogScale) {
                        log10(f / minFreq) / log10(maxFreq / minFreq)
                    } else {
                        (f - minFreq) / (maxFreq - minFreq)
                    }
                    val x = relX * width
                    drawLine(gridColor, Offset(x, 0f), Offset(x, height), strokeWidth = 1.dp.toPx())
                }
            }

            if (spectrumDbfs.isEmpty()) return@Canvas

            val binRes = sampleRate.toFloat() / fftSize
            val path = Path()
            val fillPath = Path()

            var isFirst = true
            for (i in 1 until spectrumDbfs.size) {
                val freq = i * binRes
                if (freq < minFreq) continue
                if (freq > maxFreq) break

                val relX = if (useLogScale) {
                    (log10(freq / minFreq) / log10(maxFreq / minFreq)).coerceIn(0f, 1f)
                } else {
                    ((freq - minFreq) / (maxFreq - minFreq)).coerceIn(0f, 1f)
                }

                val db = spectrumDbfs[i].coerceIn(minDb, maxDb)
                val relY = (1f - (db - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
                val x = relX * width
                val y = relY * height

                if (isFirst) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, height)
                    fillPath.lineTo(x, y)
                    isFirst = false
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }

            fillPath.lineTo(width, height)
            fillPath.close()

            // Draw filled gradient
            val fillBrush = Brush.verticalGradient(
                colors = listOf(
                    AcousticCyan.copy(alpha = 0.35f),
                    AcousticPurple.copy(alpha = 0.15f),
                    Color.Transparent
                )
            )
            drawPath(fillPath, fillBrush, style = Fill)

            // Draw spectrum outline
            val lineBrush = Brush.horizontalGradient(
                colors = listOf(AcousticCyan, Color(0xFF38BDF8), AcousticPurple)
            )
            drawPath(path, lineBrush, style = Stroke(width = 2.dp.toPx()))

            // Draw dominant frequency peak marker
            if (dominantFreqHz >= minFreq) {
                val domRelX = if (useLogScale) {
                    (log10(dominantFreqHz / minFreq) / log10(maxFreq / minFreq)).coerceIn(0f, 1f)
                } else {
                    ((dominantFreqHz - minFreq) / (maxFreq - minFreq)).coerceIn(0f, 1f)
                }
                val domX = domRelX * width
                drawLine(
                    color = Color.Yellow.copy(alpha = 0.8f),
                    start = Offset(domX, 0f),
                    end = Offset(domX, height),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // Draw inspected point
            inspectedPoint?.let { (f, db) ->
                val pRelX = if (useLogScale) {
                    (log10(f / minFreq) / log10(maxFreq / minFreq)).coerceIn(0f, 1f)
                } else {
                    ((f - minFreq) / (maxFreq - minFreq)).coerceIn(0f, 1f)
                }
                val pRelY = (1f - (db.coerceIn(minDb, maxDb) - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
                val px = pRelX * width
                val py = pRelY * height

                drawCircle(Color.White, radius = 5.dp.toPx(), center = Offset(px, py))
                drawCircle(AcousticCyan, radius = 3.dp.toPx(), center = Offset(px, py))
            }
        }

        // Inspected Point Callout
        inspectedPoint?.let { (f, db) ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.75f), shape = MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "תדר: %.0f Hz | %.1f dBFS", f, db),
                    color = Color.White,
                    fontFamily = RobotoMonoFontFamily,
                    fontSize = 11.sp
                )
            }
        }

        // Dominant frequency badge
        if (dominantFreqHz > 30f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color(0xFF854D0E).copy(alpha = 0.85f), shape = MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "שיא: %.0f Hz", dominantFreqHz),
                    color = Color.Yellow,
                    fontFamily = RobotoMonoFontFamily,
                    fontSize = 10.sp
                )
            }
        }

        // dB axis labels
        Text(
            text = "0 dB",
            color = Color.White.copy(alpha = 0.4f),
            fontFamily = RobotoMonoFontFamily,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 2.dp)
        )
        Text(
            text = "-100 dB",
            color = Color.White.copy(alpha = 0.4f),
            fontFamily = RobotoMonoFontFamily,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 2.dp)
        )
    }
}
