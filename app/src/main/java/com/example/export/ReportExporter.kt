package com.example.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.MeasurementEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    fun exportToCsv(context: Context, measurements: List<MeasurementEntity>): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(exportDir, "galkol_measurements_$timeStamp.csv")

        file.printWriter().use { out ->
            out.println("ID,Title,Timestamp,Date,DurationSeconds,RmsDbfs,PeakDbfs,MinDbfs,CrestFactorDb,DominantFreqHz,EstimatedDbSpl,LeqDb,IsCalibrated,Stability,Notes")
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            for (m in measurements) {
                out.println(
                    "${m.id},\"${m.title}\",${m.timestamp},\"${dateFormat.format(Date(m.timestamp))}\"," +
                            "${String.format(Locale.US, "%.2f", m.durationSeconds)}," +
                            "${String.format(Locale.US, "%.2f", m.rmsDbfs)}," +
                            "${String.format(Locale.US, "%.2f", m.peakDbfs)}," +
                            "${String.format(Locale.US, "%.2f", m.minDbfs)}," +
                            "${String.format(Locale.US, "%.2f", m.crestFactorDb)}," +
                            "${String.format(Locale.US, "%.1f", m.dominantFrequencyHz)}," +
                            "${m.estimatedDbSpl?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A"}," +
                            "${m.leqDb?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A"}," +
                            "${m.isCalibrated},\"${m.stabilityLabel}\",\"${m.notes}\""
                )
            }
        }
        return file
    }

    fun exportToJson(context: Context, measurements: List<MeasurementEntity>): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(exportDir, "galkol_measurements_$timeStamp.json")

        file.printWriter().use { out ->
            out.println("[")
            measurements.forEachIndexed { index, m ->
                val comma = if (index < measurements.size - 1) "," else ""
                out.println("""  {
    "id": ${m.id},
    "title": "${m.title}",
    "timestamp": ${m.timestamp},
    "durationSeconds": ${m.durationSeconds},
    "rmsDbfs": ${m.rmsDbfs},
    "peakDbfs": ${m.peakDbfs},
    "minDbfs": ${m.minDbfs},
    "crestFactorDb": ${m.crestFactorDb},
    "dominantFrequencyHz": ${m.dominantFrequencyHz},
    "estimatedDbSpl": ${m.estimatedDbSpl ?: "null"},
    "leqDb": ${m.leqDb ?: "null"},
    "isCalibrated": ${m.isCalibrated},
    "stabilityLabel": "${m.stabilityLabel}",
    "notes": "${m.notes}"
  }$comma""")
            }
            out.println("]")
        }
        return file
    }

    fun exportToPdf(context: Context, measurement: MeasurementEntity): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(exportDir, "report_${measurement.id}_$timeStamp.pdf")

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Header Background
        val headerPaint = Paint().apply {
            color = Color.parseColor("#0F172A") // Deep slate
        }
        canvas.drawRect(0f, 0f, 595f, 100f, headerPaint)

        // Accent strip
        val accentPaint = Paint().apply {
            color = Color.parseColor("#06B6D4") // Cyan
        }
        canvas.drawRect(0f, 100f, 595f, 106f, accentPaint)

        // Title Paint
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 22f
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("גל־קול | מעבדת שמע מתקדמת", 560f, 48f, titlePaint)

        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 12f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("דוח ניתוח אקוסטי ומדידת עוצמה | שומעים. רואים. מבינים.", 560f, 75f, subtitlePaint)

        // Content
        val textPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 13f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val labelPaint = Paint().apply {
            color = Color.parseColor("#64748B")
            textSize = 11f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        var y = 145f

        // Document Details Box
        val boxPaint = Paint().apply {
            color = Color.parseColor("#F1F5F9")
        }
        canvas.drawRoundRect(35f, y, 560f, y + 70f, 8f, 8f, boxPaint)

        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date(measurement.timestamp))
        canvas.drawText("כותרת המדידה: ${measurement.title}", 540f, y + 25f, textPaint)
        canvas.drawText("תאריך ושעה: $dateStr", 540f, y + 48f, labelPaint)
        canvas.drawText("משך מדידה: ${String.format(Locale.US, "%.1f", measurement.durationSeconds)} שניות", 260f, y + 48f, labelPaint)

        y += 100f

        // Metrics Table Header
        canvas.drawText("ערכי מדידה עיקריים:", 560f, y, titlePaint.apply {
            color = Color.parseColor("#0F172A")
            textSize = 16f
        })
        y += 25f

        val tableData = listOf(
            "עוצמת RMS ממוצעת" to "${String.format(Locale.US, "%.2f", measurement.rmsDbfs)} dBFS",
            "עוצמת שיא (Peak)" to "${String.format(Locale.US, "%.2f", measurement.peakDbfs)} dBFS",
            "עוצמת מינימום שנמדדה" to "${String.format(Locale.US, "%.2f", measurement.minDbfs)} dBFS",
            "מקדם שיא (Crest Factor)" to "${String.format(Locale.US, "%.2f", measurement.crestFactorDb)} dB",
            "תדר דומיננטי זוהה" to "${measurement.dominantFrequencyHz.toInt()} Hz",
            "עוצמת רעש משוקללת (Leq)" to (measurement.leqDb?.let { "${String.format(Locale.US, "%.1f", it)} dB" } ?: "ללא נתון"),
            "הערכת dB SPL מכויל" to (measurement.estimatedDbSpl?.let { "${String.format(Locale.US, "%.1f", it)} dB SPL" } ?: "לא מכויל (מדידת dBFS)"),
            "יציבות שמע" to (measurement.stabilityLabel.ifEmpty { "יציב" })
        )

        for ((idx, row) in tableData.withIndex()) {
            val bgRow = Paint().apply {
                color = if (idx % 2 == 0) Color.parseColor("#FFFFFF") else Color.parseColor("#F8FAFC")
            }
            canvas.drawRect(35f, y, 560f, y + 26f, bgRow)

            canvas.drawText(row.first, 540f, y + 18f, textPaint)
            val valPaint = Paint().apply {
                color = Color.parseColor("#0284C7")
                textSize = 13f
                isAntiAlias = true
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.LEFT
            }
            canvas.drawText(row.second, 55f, y + 18f, valPaint)
            y += 26f
        }

        y += 30f

        // Notes section
        if (measurement.notes.isNotEmpty()) {
            canvas.drawText("הערות משתמש:", 560f, y, titlePaint.apply {
                color = Color.parseColor("#0F172A")
                textSize = 14f
            })
            y += 20f
            canvas.drawText(measurement.notes, 560f, y, textPaint)
            y += 30f
        }

        // Disclaimer Box
        val disclaimerBox = Paint().apply {
            color = Color.parseColor("#FEF3C7") // warm amber
        }
        canvas.drawRoundRect(35f, y, 560f, y + 65f, 8f, 8f, disclaimerBox)

        val discPaint = Paint().apply {
            color = Color.parseColor("#92400E")
            textSize = 10f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("הערה מדעית ומגבלות מדידה:", 540f, y + 20f, discPaint.apply { typeface = Typeface.DEFAULT_BOLD })
        canvas.drawText("מדידה זו מבוצעת באמצעות מיקרופון מכשיר נייד ועיבוד אותות ספרתי (DSP).", 540f, y + 36f, discPaint.apply { typeface = Typeface.DEFAULT })
        canvas.drawText("ללא כיול מעבדתי מוסמך (Class 1 SLM), תוצאות אלו משמשות למטרות השוואה והערכה בלבד ולא כמדד בטיחות רשמי.", 540f, y + 50f, discPaint)

        pdfDocument.finishPage(page)
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()

        return file
    }
}
