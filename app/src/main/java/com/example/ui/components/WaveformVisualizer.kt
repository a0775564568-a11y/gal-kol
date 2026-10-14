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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AcousticAmber
import com.example.ui.theme.AcousticCyan
import com.example.ui.theme.AcousticRed
import com.example.ui.theme.RobotoMonoFontFamily
import java.util.Locale

@Composable
fun WaveformVisualizer(
    samples: FloatArray,
    timeWindowMs: Float = 50f,
    isFrozen: Boolean = false,
    modifier: Modifier = Modifier
) {
    var selectedPoint by remember { mutableStateOf<Pair<Float, Float>?>(null) } // timeMs to amplitude

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("waveform_canvas")
                .pointerInput(samples) {
                    detectTapGestures { tapOffset ->
                        val w = size.width
                        val h = size.height
                        val relativeX = tapOffset.x / w
                        val sampleIdx = (relativeX * (samples.size - 1)).toInt().coerceIn(0, samples.size - 1)
                        val amp = samples[sampleIdx]
                        val timeAtPoint = relativeX * timeWindowMs
                        selectedPoint = Pair(timeAtPoint, amp)
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f

            // Background grid lines (amplitude: +1.0, +0.5, 0.0, -0.5, -1.0)
            val gridColor = Color.White.copy(alpha = 0.08f)
            val centerLineColor = Color.White.copy(alpha = 0.2f)

            // Horizontal grid
            drawLine(gridColor, Offset(0f, height * 0.1f), Offset(width, height * 0.1f), strokeWidth = 1.dp.toPx())
            drawLine(gridColor, Offset(0f, height * 0.3f), Offset(width, height * 0.3f), strokeWidth = 1.dp.toPx())
            drawLine(centerLineColor, Offset(0f, centerY), Offset(width, centerY), strokeWidth = 1.5.dp.toPx())
            drawLine(gridColor, Offset(0f, height * 0.7f), Offset(width, height * 0.7f), strokeWidth = 1.dp.toPx())
            drawLine(gridColor, Offset(0f, height * 0.9f), Offset(width, height * 0.9f), strokeWidth = 1.dp.toPx())

            // Vertical time grid (5 divisions)
            for (step in 1..4) {
                val x = width * (step / 5f)
                drawLine(gridColor, Offset(x, 0f), Offset(x, height), strokeWidth = 1.dp.toPx())
            }

            if (samples.isEmpty()) return@Canvas

            // Waveform path
            val path = Path()
            val pointsCount = samples.size
            val dx = width / (pointsCount - 1).toFloat()

            // Dynamic color gradient based on signal intensity
            var maxAmp = 0f
            for (s in samples) {
                val absS = kotlin.math.abs(s)
                if (absS > maxAmp) maxAmp = absS
            }

            val waveGradient = Brush.horizontalGradient(
                colors = when {
                    maxAmp > 0.98f -> listOf(AcousticRed, AcousticAmber, AcousticRed)
                    maxAmp > 0.75f -> listOf(AcousticAmber, AcousticCyan, AcousticAmber)
                    else -> listOf(AcousticCyan, Color(0xFF38BDF8), AcousticCyan)
                }
            )

            for (i in 0 until pointsCount) {
                val x = i * dx
                val y = centerY - (samples[i] * (height * 0.44f))
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                brush = waveGradient,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw selected point inspection marker
            selectedPoint?.let { (time, amp) ->
                val ptX = (time / timeWindowMs) * width
                val ptY = centerY - (amp * (height * 0.44f))
                drawCircle(
                    color = Color.White,
                    radius = 5.dp.toPx(),
                    center = Offset(ptX, ptY)
                )
                drawCircle(
                    color = AcousticCyan,
                    radius = 3.dp.toPx(),
                    center = Offset(ptX, ptY)
                )
                // Inspection crosshairs
                drawLine(
                    color = Color.White.copy(alpha = 0.4f),
                    start = Offset(ptX, 0f),
                    end = Offset(ptX, height),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        // Overlay Inspection Box when tapped
        selectedPoint?.let { (time, amp) ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.75f), shape = MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "זמן: %.1f ms | משרעת: %.3f", time, amp),
                    color = Color.White,
                    fontFamily = RobotoMonoFontFamily,
                    fontSize = 11.sp
                )
            }
        }

        // Freeze label indicator
        if (isFrozen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(AcousticAmber.copy(alpha = 0.9f), shape = MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "מוקפא (Freeze)",
                    color = Color.Black,
                    fontFamily = RobotoMonoFontFamily,
                    fontSize = 10.sp
                )
            }
        }

        // Axis labels
        Text(
            text = "+1.0",
            color = Color.White.copy(alpha = 0.4f),
            fontFamily = RobotoMonoFontFamily,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 2.dp)
        )
        Text(
            text = "-1.0",
            color = Color.White.copy(alpha = 0.4f),
            fontFamily = RobotoMonoFontFamily,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 2.dp)
        )
        Text(
            text = "${timeWindowMs.toInt()} ms",
            color = Color.White.copy(alpha = 0.4f),
            fontFamily = RobotoMonoFontFamily,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 2.dp)
        )
    }
}
