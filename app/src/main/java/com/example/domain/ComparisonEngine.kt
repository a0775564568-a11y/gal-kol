package com.example.domain

import com.example.data.MeasurementEntity
import java.util.Locale
import kotlin.math.abs

data class ComparisonResult(
    val titleA: String,
    val titleB: String,
    val rmsDiffDb: Double,    // A - B
    val peakDiffDb: Double,   // A - B
    val crestDiffDb: Double,  // A - B
    val dominantFreqDiffHz: Float,
    val louderSignalName: String,
    val summaryTextHe: String,
    val dynamicsComparisonHe: String,
    val frequencyComparisonHe: String,
    val energyBandsComparison: List<BandComparison>
)

data class BandComparison(
    val bandName: String,
    val energyA: Float,
    val energyB: Float,
    val diff: Float
)

object ComparisonEngine {

    fun compare(a: MeasurementEntity, b: MeasurementEntity): ComparisonResult {
        val rmsDiff = a.rmsDbfs - b.rmsDbfs
        val peakDiff = a.peakDbfs - b.peakDbfs
        val crestDiff = a.crestFactorDb - b.crestFactorDb
        val freqDiff = a.dominantFrequencyHz - b.dominantFrequencyHz

        val louderSignal = if (abs(rmsDiff) < 0.5) {
            "שתי המדידות בעוצמה דיגיטלית שקולה כמעט לחלוטין"
        } else if (rmsDiff > 0) {
            "${a.title} חזקה יותר"
        } else {
            "${b.title} חזקה יותר"
        }

        val summary = buildString {
            if (abs(rmsDiff) < 0.5) {
                append("שני האותות נמדדו בעוצמת RMS כמעט זהה (הפרש זניח של ${String.format(Locale.US, "%.1f", abs(rmsDiff))} dB).")
            } else if (rmsDiff > 0) {
                append("האות '${a.title}' חזק בכ־${String.format(Locale.US, "%.1f", rmsDiff)} dBFS (RMS) בהשוואה ל־'${b.title}'. ")
                if (rmsDiff >= 6.0) {
                    append("הפרש זה מייצג הכפלה כפולה של מתח האות הדיגיטלי.")
                }
            } else {
                append("האות '${b.title}' חזק בכ־${String.format(Locale.US, "%.1f", abs(rmsDiff))} dBFS (RMS) בהשוואה ל־'${a.title}'. ")
                if (abs(rmsDiff) >= 6.0) {
                    append("הפרש זה מייצג הכפלה כפולה של מתח האות הדיגיטלי.")
                }
            }
        }

        val dynamics = buildString {
            if (abs(crestDiff) < 1.0) {
                append("לשני האותות מאפייני דינמיקה דומים מאוד (יחס Crest Factor דומה).")
            } else if (crestDiff > 0) {
                append("'${a.title}' דינמי יותר ובעל פולסים חדים יותר (Crest Factor גבוה ב־${String.format(Locale.US, "%.1f", crestDiff)} dB).")
            } else {
                append("'${b.title}' דינמי יותר ובעל פולסים חדים יותר (Crest Factor גבוה ב־${String.format(Locale.US, "%.1f", abs(crestDiff))} dB).")
            }
        }

        val freqComp = buildString {
            if (a.dominantFrequencyHz > 40f && b.dominantFrequencyHz > 40f) {
                val fA = a.dominantFrequencyHz.toInt()
                val fB = b.dominantFrequencyHz.toInt()
                if (abs(fA - fB) < 10) {
                    append("שני האותות חולקים תדר דומיננטי זהה של כ־$fA Hz.")
                } else {
                    append("תדר דומיננטי: ב־'${a.title}' נמדד $fA Hz, בעוד שב־'${b.title}' נמדד $fB Hz (הפרש של ${abs(fA - fB)} Hz).")
                }
            } else {
                append("לא זוהה הבדל מובהק בתדרים בודדים; ניתוח אנרגיה רחב-סרט.")
            }
        }

        // Parse energy bands if available
        val bandsA = parseBands(a.energyBandsCsv)
        val bandsB = parseBands(b.energyBandsCsv)
        val bandNames = listOf(
            "סאב-באס (20-60Hz)",
            "באס (60-250Hz)",
            "מיד נמוך (250-500Hz)",
            "מיד (0.5-2kHz)",
            "מיד גבוה (2-4kHz)",
            "טרבל (4-6kHz)",
            "אוויר/ברק (6-20kHz)"
        )

        val comparisons = mutableListOf<BandComparison>()
        for (i in bandNames.indices) {
            val valA = bandsA.getOrElse(i) { 0f }
            val valB = bandsB.getOrElse(i) { 0f }
            comparisons.add(
                BandComparison(
                    bandName = bandNames[i],
                    energyA = valA,
                    energyB = valB,
                    diff = valA - valB
                )
            )
        }

        return ComparisonResult(
            titleA = a.title,
            titleB = b.title,
            rmsDiffDb = rmsDiff,
            peakDiffDb = peakDiff,
            crestDiffDb = crestDiff,
            dominantFreqDiffHz = freqDiff,
            louderSignalName = louderSignal,
            summaryTextHe = summary,
            dynamicsComparisonHe = dynamics,
            frequencyComparisonHe = freqComp,
            energyBandsComparison = comparisons
        )
    }

    private fun parseBands(csv: String): List<Float> {
        if (csv.isBlank()) return emptyList()
        return csv.split(",").mapNotNull { it.trim().toFloatOrNull() }
    }
}
