package com.lexis.words.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Reviews open exactly at 06:00 local time — everything due for "today" arrives
 * as one queue that morning, matching the design's "Открылось в 6:00" copy.
 * We model that with an "effective day" that rolls over at 6am rather than
 * midnight: before 6am, we're still living in yesterday's review-day.
 */
object SpacedRepetition {

    fun effectiveDay(now: LocalDateTime = LocalDateTime.now()): Long {
        val day = if (now.hour < DUE_HOUR) now.toLocalDate().minusDays(1) else now.toLocalDate()
        return day.toEpochDay()
    }

    fun isDueNow(word: WordEntity, now: LocalDateTime = LocalDateTime.now()): Boolean {
        if (word.mastered || word.stage < 0) return false
        return word.dueEpochDay <= effectiveDay(now)
    }

    fun isNew(word: WordEntity): Boolean = !word.mastered && word.stage < 0

    fun statusOf(word: WordEntity, now: LocalDateTime = LocalDateTime.now()): WordStatus = when {
        word.mastered -> WordStatus.MASTERED
        word.stage < 0 -> WordStatus.NEW
        else -> WordStatus.REPEAT
    }

    /** Applies a study answer and returns the updated word. */
    fun answer(word: WordEntity, correct: Boolean, now: LocalDateTime = LocalDateTime.now()): WordEntity {
        val today = LocalDate.now(ZoneId.systemDefault())
        return if (correct) {
            val nextStage = (word.stage + 1).coerceAtMost(REPEAT_INTERVALS_DAYS.lastIndex)
            val justFinishedLastStage = word.stage == REPEAT_INTERVALS_DAYS.lastIndex
            if (justFinishedLastStage) {
                word.copy(
                    mastered = true,
                    stage = REPEAT_INTERVALS_DAYS.lastIndex,
                    lastReviewedAt = System.currentTimeMillis(),
                    correctCount = word.correctCount + 1,
                )
            } else {
                val days = REPEAT_INTERVALS_DAYS[nextStage]
                word.copy(
                    stage = nextStage,
                    dueEpochDay = today.plusDays(days.toLong()).toEpochDay(),
                    lastReviewedAt = System.currentTimeMillis(),
                    correctCount = word.correctCount + 1,
                )
            }
        } else {
            word.copy(
                stage = 0,
                mastered = false,
                dueEpochDay = today.plusDays(REPEAT_INTERVALS_DAYS[0].toLong()).toEpochDay(),
                lastReviewedAt = System.currentTimeMillis(),
                wrongCount = word.wrongCount + 1,
            )
        }
    }

    fun intervalLabel(stage: Int): String {
        val d = REPEAT_INTERVALS_DAYS.getOrElse(stage) { REPEAT_INTERVALS_DAYS.last() }
        return "$d ${dayWord(d)}"
    }

    private fun dayWord(n: Int): String = when {
        n % 10 == 1 && n % 100 != 11 -> "день"
        n % 10 in 2..4 && (n % 100 !in 12..14) -> "дня"
        else -> "дней"
    }
}
