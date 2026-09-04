package com.arslan.hoerzeit.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Цель: 60 часов слуха. Минимум каждый день: 30 минут. */
object Goal {
    const val TOTAL_MS: Long = 60L * 60L * 60L * 1000L      // 60 часов
    const val DAILY_MS: Long = 30L * 60L * 1000L            // 30 минут

    /** Дольше этого одна сессия почти наверняка означает забытую кнопку «Стоп». */
    const val LONG_SESSION_MS: Long = 6L * 60L * 60L * 1000L
}

/** Одна сессия практики: от «Старт» до «Стоп» (или добавленная вручную). */
data class Session(
    val id: Long,
    val start: Long,
    val end: Long
) {
    val durationMs: Long get() = (end - start).coerceAtLeast(0L)

    val date: LocalDate
        get() = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate()

    val startTime: LocalDateTime
        get() = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDateTime()

    val endTime: LocalDateTime
        get() = Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault()).toLocalDateTime()
}

/** Снимок прогресса — всё, что показывается на экране. */
data class Progress(
    val totalMs: Long,
    val todayMs: Long,
    val streakDays: Int,
    val activeDays: Int
) {
    val fraction: Float get() = (totalMs.toFloat() / Goal.TOTAL_MS).coerceIn(0f, 1f)
    val percent: Float get() = fraction * 100f
    val leftMs: Long get() = (Goal.TOTAL_MS - totalMs).coerceAtLeast(0L)
    val todayFraction: Float get() = (todayMs.toFloat() / Goal.DAILY_MS).coerceIn(0f, 1f)
    val dailyDone: Boolean get() = todayMs >= Goal.DAILY_MS
    val goalReached: Boolean get() = totalMs >= Goal.TOTAL_MS
}
