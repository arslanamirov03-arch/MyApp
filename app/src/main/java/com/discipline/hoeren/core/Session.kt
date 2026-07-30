package com.discipline.hoeren.core

/** Минут в сутках. */
const val MINUTES_PER_DAY = 1440

/**
 * Одна сессия слуховой практики.
 *
 * Дата хранится как номер дня августа 2026 (1..31), время — как число минут от
 * полуночи (0..1439). Целочисленная модель выбрана намеренно: она не требует
 * java.time и работает на minSdk 24 без core library desugaring.
 */
data class Session(
    val id: Long,
    val day: Int,
    val startMin: Int,
    val endMin: Int,
) {
    /**
     * Длительность в минутах. Если конец меньше начала, считаем, что практика
     * перешла через полночь (23:30 → 00:40 = 70 минут), и относим её к дню начала.
     */
    val durationMin: Int
        get() = if (endMin >= startMin) endMin - startMin else endMin + MINUTES_PER_DAY - startMin
}

/**
 * Начатая, но ещё не завершённая практика. Хранится отдельно от списка сессий и
 * переживает закрытие приложения, поэтому можно нажать СТАРТ, выйти и вернуться.
 */
data class OpenSession(
    val day: Int,
    val startMin: Int,
)
