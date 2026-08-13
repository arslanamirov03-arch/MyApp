package app.hoerpraxis.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder

/**
 * Decodes the audio track of any media file Android understands (mp3, m4a,
 * mp4, mkv, ogg, flac, wav …) into 16 kHz mono float PCM for whisper.
 * Resampling happens on the fly so memory stays proportional to the output.
 */
object AudioDecoder {

    const val WHISPER_SAMPLE_RATE = 16000

    fun decodeTo16kMono(path: String, onProgress: (Int) -> Unit = {}): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(path)
        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                trackIndex = i; format = f; break
            }
        }
        require(trackIndex >= 0 && format != null) { "В файле нет аудиодорожки" }
        extractor.selectTrack(trackIndex)

        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION))
            format.getLong(MediaFormat.KEY_DURATION) else -1L

        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()

        val out = FloatArrayBuilder()
        val resampler = Resampler(out)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            if (durationUs > 0) {
                                onProgress(((extractor.sampleTime * 100) / durationUs).toInt().coerceIn(0, 100))
                            }
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex >= 0 -> {
                        val outputFormat = codec.outputFormat
                        val sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        val pcmEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING))
                            outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        else AudioFormat.ENCODING_PCM_16BIT

                        val buffer = codec.getOutputBuffer(outIndex)!!
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.order(ByteOrder.LITTLE_ENDIAN)

                        if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                            val fb = buffer.asFloatBuffer()
                            val mono = FloatArray(fb.remaining() / channels)
                            val frame = FloatArray(channels)
                            for (j in mono.indices) {
                                fb.get(frame)
                                var sum = 0f; for (c in 0 until channels) sum += frame[c]
                                mono[j] = sum / channels
                            }
                            resampler.feed(mono, sampleRate)
                        } else {
                            val sb = buffer.asShortBuffer()
                            val mono = FloatArray(sb.remaining() / channels)
                            for (j in mono.indices) {
                                var sum = 0f
                                for (c in 0 until channels) sum += sb.get() / 32768f
                                mono[j] = sum / channels
                            }
                            resampler.feed(mono, sampleRate)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone -> {
                        // keep draining until EOS flag arrives
                    }
                }
            }
        } finally {
            codec.stop(); codec.release(); extractor.release()
        }
        return out.toArray()
    }

    /** Linear-interpolation resampler that keeps its phase across buffers. */
    private class Resampler(private val out: FloatArrayBuilder) {
        private var last = 0f
        private var hasLast = false
        private var pos = 0.0

        fun feed(input: FloatArray, inputRate: Int) {
            if (input.isEmpty()) return
            if (inputRate == WHISPER_SAMPLE_RATE) { out.addAll(input); return }
            val step = inputRate.toDouble() / WHISPER_SAMPLE_RATE
            if (!hasLast) { last = input[0]; hasLast = true }
            // virtual timeline: index -1 is `last`, 0..n-1 is `input`
            while (pos < input.size) {
                val i = pos.toInt()
                val frac = (pos - i).toFloat()
                val a = if (pos < 0.0 || i == 0 && pos < 0) last else input[maxOf(i, 0)]
                val b = if (i + 1 < input.size) input[i + 1] else input[input.size - 1]
                out.add(a + (b - a) * frac)
                pos += step
            }
            pos -= input.size
            last = input[input.size - 1]
        }
    }

    private class FloatArrayBuilder {
        private val chunks = ArrayList<FloatArray>()
        private var current = FloatArray(1 shl 16)
        private var index = 0
        private var total = 0

        fun add(v: Float) {
            if (index == current.size) { chunks.add(current); current = FloatArray(1 shl 16); index = 0 }
            current[index++] = v; total++
        }

        fun addAll(values: FloatArray) { for (v in values) add(v) }

        fun toArray(): FloatArray {
            val result = FloatArray(total)
            var offset = 0
            for (c in chunks) { c.copyInto(result, offset); offset += c.size }
            current.copyInto(result, offset, 0, index)
            return result
        }
    }
}
