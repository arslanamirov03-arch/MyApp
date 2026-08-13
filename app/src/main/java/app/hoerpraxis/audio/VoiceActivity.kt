package app.hoerpraxis.audio

import app.hoerpraxis.data.Word

/**
 * Snaps word starts onto real speech. A recogniser can place a word a little
 * early or late — often inside a pause — so each start is moved to the nearest
 * moment where the voice actually begins. Every word is checked on its own, so
 * long silences never shift the rest of the transcript.
 */
object VoiceActivity {

    private const val FRAME_MS = 10
    private const val LOOK_FORWARD_MS = 700
    private const val LOOK_BACK_MS = 220
    private const val MIN_GAP_MS = 30L

    fun snap(words: List<Word>, pcm: FloatArray, sampleRate: Int = AudioDecoder.WHISPER_SAMPLE_RATE): List<Word> {
        if (words.isEmpty() || pcm.isEmpty()) return words

        val frameSize = sampleRate * FRAME_MS / 1000
        val frameCount = pcm.size / frameSize
        if (frameCount < 4) return words

        val energy = FloatArray(frameCount)
        for (f in 0 until frameCount) {
            var sum = 0f
            val base = f * frameSize
            for (i in 0 until frameSize) {
                val v = pcm[base + i]
                sum += v * v
            }
            energy[f] = kotlin.math.sqrt(sum / frameSize)
        }

        // Noise floor from the quietest fifth of the recording; anything a few
        // times louder counts as speech.
        val sorted = energy.copyOf()
        sorted.sort()
        val floor = sorted[(frameCount * 0.2f).toInt().coerceIn(0, frameCount - 1)]
        val loud = sorted[(frameCount * 0.95f).toInt().coerceIn(0, frameCount - 1)]
        val threshold = maxOf(floor * 3f, loud * 0.08f, 1e-4f)

        val speech = BooleanArray(frameCount) { energy[it] > threshold }

        val forwardFrames = LOOK_FORWARD_MS / FRAME_MS
        val backFrames = LOOK_BACK_MS / FRAME_MS

        val result = ArrayList<Word>(words.size)
        var previousStart = -MIN_GAP_MS
        for (word in words) {
            val frame = (word.s / FRAME_MS).toInt()
            var onset = frame
            if (frame in 0 until frameCount) {
                if (speech[frame]) {
                    // Already on speech: rewind to where this burst started.
                    var f = frame
                    var steps = 0
                    while (f > 0 && speech[f - 1] && steps < backFrames) { f--; steps++ }
                    onset = f
                } else {
                    // Sitting in a pause: move forward to the next voice.
                    var f = frame
                    var steps = 0
                    while (f < frameCount - 1 && !speech[f] && steps < forwardFrames) { f++; steps++ }
                    if (speech[f]) onset = f
                }
            }
            var start = onset.toLong() * FRAME_MS
            if (start < previousStart + MIN_GAP_MS) start = previousStart + MIN_GAP_MS
            previousStart = start
            result.add(word.copy(s = start, e = maxOf(word.e, start)))
        }

        // A word runs until the next one begins.
        for (i in 0 until result.size - 1) {
            if (result[i + 1].s > result[i].s) {
                result[i] = result[i].copy(e = result[i + 1].s)
            }
        }
        return result
    }
}
