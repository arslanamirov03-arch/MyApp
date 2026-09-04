package com.arslan.hoerzeit.ui

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

private val RU = Locale("ru")

private val MONTHS_GEN = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря"
)

private val WEEKDAYS = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

/** 1 → «день», 2 → «дня», 5 → «дней». */
fun plural(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}

/** 3_900_000 → «1 ч 05 мин». Всегда округляет вниз до минуты. */
fun formatHm(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "$hours ч ${"%02d".format(RU, minutes)} мин"
        else -> "$minutes мин"
    }
}

/** Компактно, для мелких подписей: «1:05» или «34 мин». */
fun formatShort(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours:${"%02d".format(RU, minutes)}" else "$minutes мин"
}

/** Живой секундомер: «00:12:45». */
fun formatClock(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    return "%02d:%02d:%02d".format(
        RU,
        totalSeconds / 3600,
        (totalSeconds % 3600) / 60,
        totalSeconds % 60
    )
}

fun formatTime(time: LocalDateTime): String = "%02d:%02d".format(RU, time.hour, time.minute)

fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(RU, hour, minute)

/** «Сегодня», «Вчера» или «чт, 4 сентября». */
fun formatDay(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Сегодня"
    today.minusDays(1) -> "Вчера"
    else -> "${WEEKDAYS[date.dayOfWeek.value - 1]}, ${date.dayOfMonth} ${MONTHS_GEN[date.monthValue - 1]}"
}

fun formatDateFull(date: LocalDate): String =
    "${date.dayOfMonth} ${MONTHS_GEN[date.monthValue - 1]} ${date.year}"
