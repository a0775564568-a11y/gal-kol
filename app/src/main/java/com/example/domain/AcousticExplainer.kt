package com.example.domain

import com.example.dsp.AudioMetrics
import com.example.dsp.SoundStability
import java.util.Locale

/**
 * Generates scientific, clear, and friendly Hebrew acoustic explanations.
 * Strictly adheres to acoustic principles and distinguishes between dBFS and calibrated dB SPL.
 */
data class AcousticExplanation(
    val title: String,
    val summaryText: String,
    val referenceComparisonText: String,
    val spectralCharacterText: String,
    val dynamicsText: String,
    val exposureAdvisoryText: String?,
    val isThresholdExceeded: Boolean,
    val thresholdDifferenceDb: Double?,
    val confidenceLevel: String,
    val limitationsNote: String
)

object AcousticExplainer {

    const val SPEECH_REFERENCE_DB_SPL = 60.0

    fun explain(
        metrics: AudioMetrics,
        isCalibrated: Boolean,
        userThresholdDb: Double,
        referenceSpeechDb: Double = SPEECH_REFERENCE_DB_SPL
    ): AcousticExplanation {
        val currentLevel = if (isCalibrated) metrics.estimatedDbSpl ?: (metrics.rmsDbfs + 100.0) else metrics.rmsDbfs
        val unit = if (isCalibrated) "dB SPL" else "dBFS"

        // Threshold check
        val thresholdExceeded = currentLevel > userThresholdDb
        val diffFromThreshold = if (thresholdExceeded) currentLevel - userThresholdDb else null

        // Summary text in Hebrew
        val summary = if (isCalibrated) {
            val spl = metrics.estimatedDbSpl ?: 0.0
            when {
                spl < 40.0 -> "סביבה שקטה במיוחד, כגון חדר שינה שקט בלילה או ספרייה ריקה."
                spl < 50.0 -> "רמת שמע נמוכה, המאפיינת לחישה, שקט בייתי או רחש מזגן עדין."
                spl in 50.0..65.0 -> "תחום עוצמה טיפוסי לשיחה אנושית רגילה במרחק של כמטר אחד."
                spl in 65.0..75.0 -> "עוצמה בינונית-גבוהה, אופיינית לדיבור מוגבר, משרד פעיל או טלוויזיה בעוצמה רגילה."
                spl in 75.0..85.0 -> "עוצמה גבוהה משמעותית, כגון רעש תנועה עירונית, רחוב סואן או מסעדה הומה."
                else -> "עוצמה חזקה מאוד! בסביבה תעשייתית, כלי עבודה או מוזיקה חזקה."
            }
        } else {
            when {
                metrics.rmsDbfs < -60.0 -> "האות הדיגיטלי קרוב לרצפת הרעש של המיקרופון (שקט כמעט מוחלט)."
                metrics.rmsDbfs < -35.0 -> "קליטת אות דיגיטלי ברמה מתונה עם מרווח ביטחון (Headroom) רחב."
                metrics.rmsDbfs < -18.0 -> "אות ברמה תקינה ואידיאלית לעיבוד שמע ללא עיוותים."
                metrics.rmsDbfs < -3.0 -> "אות בעוצמה דיגיטלית גבוהה מאוד, מתקרב לסף הרוויה."
                else -> "זהירות: האות קרוב מאוד לתקרה הדיגיטלית (0 dBFS) ועלול לגרום לעיוות."
            }
        }

        // Reference comparison
        val refComparison = if (isCalibrated) {
            val spl = metrics.estimatedDbSpl ?: 0.0
            val diff = spl - referenceSpeechDb
            val diffStr = String.format(Locale.US, "%.1f", kotlin.math.abs(diff))
            when {
                kotlin.math.abs(diff) < 3.0 ->
                    "העוצמה הנמדדת (${String.format(Locale.US, "%.1f", spl)} dB SPL) קרובה מאוד לנקודת הייחוס של דיבור רגיל ($referenceSpeechDb dB SPL)."
                diff > 0 ->
                    "הצליל חזק בכ־$diffStr דציבלים מנקודת הייחוס של דיבור רגיל ($referenceSpeechDb dB SPL). מדובר בהפרש מורגש בעוצמה הפיזיקלית, אך אין פירוש הדבר שהצליל מסוכן בהכרח."
                else ->
                    "הצליל חלש בכ־$diffStr דציבלים מנקודת הייחוס של דיבור רגיל ($referenceSpeechDb dB SPL)."
            }
        } else {
            "המדידה מוצגת בערכי dBFS דיגיטליים יחסיים (0 dBFS = המשרעת המקסימלית שהחומרה מסוגלת לדגום). ללא כיול מכשיר, לא ניתן להמיר ערך זה ל־dB SPL פיזיקלי תקני."
        }

        // Spectral Character (Tone vs Noise)
        val spectralChar = if (metrics.dominantFrequencyHz > 40f && metrics.crestFactorDb > 10.0) {
            val freqRounded = metrics.dominantFrequencyHz.toInt()
            val noteName = getApproximateMusicalNote(metrics.dominantFrequencyHz)
            "זוהה רכיב טונאלי דומיננטי ומובהק בתדר $freqRounded Hz ($noteName). הצליל בעל גוון מוזיקלי או שריקה ברורה."
        } else if (metrics.spectralCentroidHz > 3500f) {
            "הספקטרום מאופיין באנרגיה גבוהה בתדרים הגבוהים (צליל בהיר, רשרוש, חיכוך או לחישה)."
        } else if (metrics.spectralCentroidHz in 400f..2500f) {
            "עיקר האנרגיה מרוכזת בתחום הדיבור האנושי (תדרי ביניים 500-2000 Hz)."
        } else {
            "אנרגיה ספקטרלית מפוזרת ברוחב פס רחב (רעש רציף או רחש רקע)."
        }

        // Dynamics & Crest Factor
        val dynamics = when {
            metrics.hasClipping ->
                "זוהתה קטימה דיגיטלית (Clipping) ב־${metrics.clippingCount} דגימות! האות חרג מיכולת הדגימה של המיקרופון וישנו עיוות במשרעת."
            metrics.crestFactorDb > 15.0 ->
                "מקדם שיא (Crest Factor) גבוה של ${String.format(Locale.US, "%.1f", metrics.crestFactorDb)} dB מעיד על צליל דינמי ביותר בעל פולסים חדים (כגון מחיאת כף או הקשה)."
            metrics.crestFactorDb < 6.0 && metrics.rmsDbfs > -50.0 ->
                "מקדם שיא נמוך (${String.format(Locale.US, "%.1f", metrics.crestFactorDb)} dB) מצביע על צליל דחוס ורציף (כגון מנוע, מאוורר או צפירה)."
            else ->
                "דינמיקה טבעית עם יחס שיא לממוצע של ${String.format(Locale.US, "%.1f", metrics.crestFactorDb)} dB. ${metrics.stabilityIndex.descriptionHe}."
        }

        // Exposure advisory (only when calibrated or very high)
        val advisory = if (isCalibrated) {
            val spl = metrics.estimatedDbSpl ?: 0.0
            when {
                spl >= 85.0 ->
                    "זהירות: עוצמת הרעש עולה על 85 dB SPL. חשיפה ממושכת (מעל 8 שעות ב-85dB, או מעל שעתיים ב-90dB) עלולה לפגוע בשמיעה לפי המלצות הבריאות הבינלאומיות (NIOSH/OSHA)."
                spl >= 75.0 ->
                    "עוצמה מוגברת: חשיפה רגילה אינה מסוכנת, אך בסביבת עבודה או מנוחה מומלץ לוודא שהרעש אינו רציף ומפריע לריכוז."
                else -> null
            }
        } else {
            if (metrics.hasClipping) "מומלץ להרחיק את המיקרופון ממקור הקול כדי למנוע עיוות דיגיטלי." else null
        }

        val confidence = if (isCalibrated) "כיול משתמש פעיל (דיוק יחסי משוער)" else "מדידה דיגיטלית ישירה (dBFS מלא)"
        val limitations = if (isCalibrated) {
            "מיקרופון סמארטפון אינו מכשיר מדידה מעבדתי (Class 1). ערכי ה־dB SPL מבוססים על כיול יחסי בלבד."
        } else {
            "מערכת השמע דוגמת את האות הדיגיטלי (dBFS). להערכת עוצמת קול אקוסטית בחדר בצע כיול במסך ההגדרות."
        }

        return AcousticExplanation(
            title = "מה אנחנו שומעים עכשיו?",
            summaryText = summary,
            referenceComparisonText = refComparison,
            spectralCharacterText = spectralChar,
            dynamicsText = dynamics,
            exposureAdvisoryText = advisory,
            isThresholdExceeded = thresholdExceeded,
            thresholdDifferenceDb = diffFromThreshold,
            confidenceLevel = confidence,
            limitationsNote = limitations
        )
    }

    private fun getApproximateMusicalNote(frequencyHz: Float): String {
        if (frequencyHz < 20f || frequencyHz > 8000f) return ""
        val notes = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val midiNote = kotlin.math.round(69 + 12 * kotlin.math.log2(frequencyHz / 440.0)).toInt()
        val noteIndex = (midiNote % 12 + 12) % 12
        val octave = (midiNote / 12) - 1
        return "${notes[noteIndex]}$octave"
    }
}
