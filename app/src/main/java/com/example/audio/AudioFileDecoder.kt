package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.dsp.AudioMetricsCalculator
import com.example.dsp.FastFourierTransform
import com.example.dsp.WindowType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class AudioFileAnalysis(
    val fileName: String,
    val durationMs: Long,
    val sampleRate: Int,
    val channelCount: Int,
    val mimeType: String,
    val peakDbfs: Double,
    val rmsDbfs: Double,
    val dynamicRangeDb: Double,
    val clippingCount: Int,
    val waveformEnvelope: FloatArray, // Downsampled min/max envelope points
    val pcmSamples: ShortArray,        // Mono mixed-down PCM samples for detailed analysis
    val quietSegments: List<AudioSegment>,
    val loudSegments: List<AudioSegment>,
    val clippingSegments: List<AudioSegment>
)

data class AudioSegment(
    val startMs: Long,
    val endMs: Long,
    val labelHe: String,
    val avgDbfs: Double
)

object AudioFileDecoder {

    /**
     * Decodes any supported audio file (WAV, MP3, M4A, AAC, etc.) from Uri to 16-bit PCM mono ShortArray,
     * extracting acoustic metadata and segment statistics.
     */
    suspend fun decodeAndAnalyze(
        context: Context,
        uri: Uri,
        fileName: String = "audio_file"
    ): AudioFileAnalysis = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            throw IllegalArgumentException("לא ניתן לפתוח את קובץ השמע: ${e.localizedMessage}")
        }

        var trackIndex = -1
        var audioFormat: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                trackIndex = i
                audioFormat = format
                break
            }
        }

        if (trackIndex < 0 || audioFormat == null) {
            extractor.release()
            throw IllegalArgumentException("הקובץ אינו מכיל ערוץ שמע תקין.")
        }

        extractor.selectTrack(trackIndex)
        val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: "audio/unknown"
        val sampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } else 44100
        val channels = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } else 1
        val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
            audioFormat.getLong(MediaFormat.KEY_DURATION)
        } else 0L
        val durationMs = durationUs / 1000

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(audioFormat, null, null, 0)
        codec.start()

        val outputStream = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false
        val timeoutUs = 5000L

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inputBufIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inputBufIndex >= 0) {
                    val inputBuf = codec.getInputBuffer(inputBufIndex)
                    if (inputBuf != null) {
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            codec.queueInputBuffer(inputBufIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
            }

            val res = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (res >= 0) {
                val outputBuf = codec.getOutputBuffer(res)
                if (outputBuf != null && bufferInfo.size > 0) {
                    outputBuf.position(bufferInfo.offset)
                    outputBuf.limit(bufferInfo.offset + bufferInfo.size)
                    val chunk = ByteArray(bufferInfo.size)
                    outputBuf.get(chunk)
                    outputStream.write(chunk)
                }
                codec.releaseOutputBuffer(res, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        val rawPcmBytes = outputStream.toByteArray()
        val shortBuffer = ByteBuffer.wrap(rawPcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val totalShorts = shortBuffer.remaining()

        // Mix down multi-channel audio to mono
        val monoShorts: ShortArray
        if (channels > 1) {
            val monoLength = totalShorts / channels
            monoShorts = ShortArray(monoLength)
            var srcIdx = 0
            for (i in 0 until monoLength) {
                var sum = 0
                for (c in 0 until channels) {
                    sum += shortBuffer.get(srcIdx++)
                }
                monoShorts[i] = (sum / channels).coerceIn(-32768, 32767).toShort()
            }
        } else {
            monoShorts = ShortArray(totalShorts)
            shortBuffer.get(monoShorts)
        }

        // Analyze full waveform and segments
        return@withContext computeAnalysis(
            fileName = fileName,
            durationMs = if (durationMs > 0) durationMs else (monoShorts.size * 1000L / sampleRate),
            sampleRate = sampleRate,
            channelCount = channels,
            mimeType = mime,
            monoPcm = monoShorts
        )
    }

    private fun computeAnalysis(
        fileName: String,
        durationMs: Long,
        sampleRate: Int,
        channelCount: Int,
        mimeType: String,
        monoPcm: ShortArray
    ): AudioFileAnalysis {
        val totalSamples = monoPcm.size
        if (totalSamples == 0) {
            return AudioFileAnalysis(
                fileName = fileName,
                durationMs = 0L,
                sampleRate = sampleRate,
                channelCount = channelCount,
                mimeType = mimeType,
                peakDbfs = -100.0,
                rmsDbfs = -100.0,
                dynamicRangeDb = 0.0,
                clippingCount = 0,
                waveformEnvelope = FloatArray(200),
                pcmSamples = ShortArray(0),
                quietSegments = emptyList(),
                loudSegments = emptyList(),
                clippingSegments = emptyList()
            )
        }

        var sumSquares = 0.0
        var globalPeak = 0
        var clippingCount = 0

        for (i in 0 until totalSamples) {
            val sample = monoPcm[i].toInt()
            val abs = kotlin.math.abs(sample)
            if (abs > globalPeak) globalPeak = abs
            if (abs >= AudioMetricsCalculator.CLIPPING_THRESHOLD_16BIT) clippingCount++
            sumSquares += sample.toDouble() * sample.toDouble()
        }

        val rms = sqrt(sumSquares / totalSamples)
        val rmsDbfs = if (rms > 1e-4) 20.0 * log10(rms / 32768.0) else -100.0
        val peakDbfs = if (globalPeak > 0) 20.0 * log10(globalPeak / 32768.0) else -100.0

        // Downsampled waveform envelope (e.g. 500 points for display)
        val envelopePoints = 500
        val envelope = FloatArray(envelopePoints)
        val windowSize = max(1, totalSamples / envelopePoints)

        for (p in 0 until envelopePoints) {
            val start = p * windowSize
            val end = min(totalSamples, start + windowSize)
            var maxAmp = 0
            for (i in start until end) {
                val abs = kotlin.math.abs(monoPcm[i].toInt())
                if (abs > maxAmp) maxAmp = abs
            }
            envelope[p] = (maxAmp / 32768.0f).coerceIn(0f, 1f)
        }

        // Segment analysis (e.g. 500ms chunk blocks)
        val chunkDurationSamples = sampleRate / 2 // 500ms
        val totalChunks = totalSamples / chunkDurationSamples
        val chunkDbList = mutableListOf<Pair<Long, Double>>() // startMs to dBFS
        var minRmsChunk = 0.0
        var maxRmsChunk = -100.0

        val quietSegs = mutableListOf<AudioSegment>()
        val loudSegs = mutableListOf<AudioSegment>()
        val clipSegs = mutableListOf<AudioSegment>()

        for (c in 0 until totalChunks) {
            val startSample = c * chunkDurationSamples
            val endSample = min(totalSamples, startSample + chunkDurationSamples)
            var chunkSumSq = 0.0
            var chunkClip = 0
            for (i in startSample until endSample) {
                val s = monoPcm[i].toInt()
                chunkSumSq += s.toDouble() * s.toDouble()
                if (kotlin.math.abs(s) >= AudioMetricsCalculator.CLIPPING_THRESHOLD_16BIT) chunkClip++
            }
            val chunkRms = sqrt(chunkSumSq / (endSample - startSample))
            val chunkDb = if (chunkRms > 1e-4) 20.0 * log10(chunkRms / 32768.0) else -100.0
            val startMs = (startSample.toLong() * 1000) / sampleRate
            val endMs = (endSample.toLong() * 1000) / sampleRate

            chunkDbList.add(startMs to chunkDb)
            if (chunkDb > -99.0) {
                if (minRmsChunk == 0.0 || chunkDb < minRmsChunk) minRmsChunk = chunkDb
                if (chunkDb > maxRmsChunk) maxRmsChunk = chunkDb
            }

            if (chunkClip > 0) {
                clipSegs.add(AudioSegment(startMs, endMs, "קטימה דיגיטלית ($chunkClip דגימות)", chunkDb))
            } else if (chunkDb > -12.0) {
                loudSegs.add(AudioSegment(startMs, endMs, "מקטע חזק במיוחד", chunkDb))
            } else if (chunkDb < -45.0) {
                quietSegs.add(AudioSegment(startMs, endMs, "מקטע שקט", chunkDb))
            }
        }

        val dynamicRangeDb = if (maxRmsChunk > -99.0 && minRmsChunk < 0.0) {
            max(0.0, maxRmsChunk - minRmsChunk)
        } else {
            max(0.0, peakDbfs - rmsDbfs)
        }

        return AudioFileAnalysis(
            fileName = fileName,
            durationMs = durationMs,
            sampleRate = sampleRate,
            channelCount = channelCount,
            mimeType = mimeType,
            peakDbfs = peakDbfs,
            rmsDbfs = rmsDbfs,
            dynamicRangeDb = dynamicRangeDb,
            clippingCount = clippingCount,
            waveformEnvelope = envelope,
            pcmSamples = monoPcm,
            quietSegments = quietSegs.take(10),
            loudSegments = loudSegs.take(10),
            clippingSegments = clipSegs.take(10)
        )
    }

    /**
     * Extracts a sub-range (from startMs to endMs) of the decoded PCM buffer,
     * and performs FFT and detailed metrics computation on that exact segment.
     */
    fun analyzeSelection(
        analysis: AudioFileAnalysis,
        startMs: Long,
        endMs: Long,
        fftSize: Int = 2048,
        windowType: WindowType = WindowType.HANN
    ): Pair<FloatArray, FloatArray> {
        val startSample = ((startMs * analysis.sampleRate) / 1000).toInt().coerceIn(0, analysis.pcmSamples.size)
        val endSample = ((endMs * analysis.sampleRate) / 1000).toInt().coerceIn(startSample, analysis.pcmSamples.size)
        val sliceSize = endSample - startSample

        if (sliceSize <= 0) {
            return Pair(FloatArray(0), FloatArray(0))
        }

        val slice = ShortArray(sliceSize)
        System.arraycopy(analysis.pcmSamples, startSample, slice, 0, sliceSize)

        // Waveform preview for selection
        val previewPoints = 256
        val preview = FloatArray(previewPoints)
        val step = max(1, sliceSize / previewPoints)
        for (i in 0 until previewPoints) {
            val idx = min(i * step, sliceSize - 1)
            preview[i] = (slice[idx] / 32768.0f).coerceIn(-1.0f, 1.0f)
        }

        // FFT on up to fftSize samples
        val fftSamples = min(sliceSize, fftSize)
        val normSamples = DoubleArray(fftSamples)
        for (i in 0 until fftSamples) {
            normSamples[i] = slice[i] / 32768.0
        }
        val spectrum = FastFourierTransform.computeMagnitudeSpectrumDbfs(normSamples, windowType)

        return Pair(preview, spectrum)
    }
}
