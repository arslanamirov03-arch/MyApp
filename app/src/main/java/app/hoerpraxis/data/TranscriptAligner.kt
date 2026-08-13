package app.hoerpraxis.data

/**
 * Turns plain text into timed words. Used when the user already has a
 * transcript: the text is authoritative, the timings come either from the
 * audio duration (rough) or from a recognition pass (accurate).
 */
object TranscriptAligner {

    fun splitWords(text: String): List<String> =
        text.split(Regex("\\s+")).filter { it.isNotBlank() }

    private fun normalize(word: String): String =
        word.lowercase()
            .replace("ß", "ss")
            .filter { it.isLetterOrDigit() }

    /**
     * Spreads words across the recording proportionally to their length, so a
     * pasted transcript is usable immediately. Timings are approximate until
     * [align] refines them against recognised audio.
     */
    fun fromPastedText(text: String, durationMs: Long): List<Word> {
        val words = splitWords(text)
        if (words.isEmpty()) return emptyList()
        val total = words.sumOf { it.length + 1 }.toDouble()
        val duration = if (durationMs > 0) durationMs else words.size * 400L

        var acc = 0.0
        return words.map { word ->
            val start = (acc / total * duration).toLong()
            acc += word.length + 1
            val end = (acc / total * duration).toLong()
            Word(w = word, s = start, e = end)
        }
    }

    /**
     * Maps the user's own words onto timings measured from the audio. Words
     * that match a recognised word take its timing; the rest are spread evenly
     * between their matched neighbours, so tap-to-play stays close everywhere.
     */
    fun align(userText: String, recognized: List<Word>, durationMs: Long): List<Word> {
        val words = splitWords(userText)
        if (words.isEmpty()) return emptyList()
        if (recognized.isEmpty()) return fromPastedText(userText, durationMs)

        val a = words.map { normalize(it) }
        val b = recognized.map { normalize(it.w) }

        // Guard against quadratic blow-up on very long transcripts.
        val matches: Map<Int, Int> = if (a.size.toLong() * b.size <= 6_000_000L) {
            longestCommonSubsequence(a, b)
        } else {
            emptyMap()
        }

        val starts = arrayOfNulls<Long>(words.size)
        val ends = arrayOfNulls<Long>(words.size)
        for ((i, j) in matches) {
            starts[i] = recognized[j].s
            ends[i] = recognized[j].e
        }

        // Anchor both edges so interpolation has something to work with.
        if (starts.first() == null) { starts[0] = recognized.first().s; ends[0] = null }
        if (starts.last() == null) {
            starts[words.size - 1] = recognized.last().s
            ends[words.size - 1] = recognized.last().e
        }

        val result = ArrayList<Word>(words.size)
        var i = 0
        while (i < words.size) {
            if (starts[i] != null) {
                result.add(Word(words[i], starts[i]!!, ends[i] ?: starts[i]!!))
                i++
                continue
            }
            // Gap of unmatched words between two anchors: spread them evenly.
            val gapStart = i
            var gapEnd = i
            while (gapEnd < words.size && starts[gapEnd] == null) gapEnd++
            val from = result.lastOrNull()?.e ?: 0L
            val to = if (gapEnd < words.size) starts[gapEnd]!! else maxOf(from, durationMs)
            val count = gapEnd - gapStart
            val step = ((to - from).coerceAtLeast(0L)) / count
            for (k in 0 until count) {
                val s = from + step * k
                result.add(Word(words[gapStart + k], s, s + step))
            }
            i = gapEnd
        }
        return result
    }

    /** Returns a map of indices in [a] to the matching index in [b]. */
    private fun longestCommonSubsequence(a: List<String>, b: List<String>): Map<Int, Int> {
        val n = a.size
        val m = b.size
        // dp[i][j] = LCS length of a[i..] and b[j..], stored row by row.
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (a[i] == b[j] && a[i].isNotEmpty()) {
                    dp[i + 1][j + 1] + 1
                } else {
                    maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }
        val result = HashMap<Int, Int>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] && a[i].isNotEmpty() -> { result[i] = j; i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> i++
                else -> j++
            }
        }
        return result
    }
}
