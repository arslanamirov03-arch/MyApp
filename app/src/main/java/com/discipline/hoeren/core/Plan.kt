package com.discipline.hoeren.core

/**
 * Вся арифметика плана. Чистые функции без зависимостей от Android — покрыты
 * юнит-тестами в PlanTest.
 *
 * Цель зашита намертво: 60 часов активной слуховой практики с 1 по 31 августа 2026.
 */
object Plan {

    const val GOAL_MIN = 60 * 60      // 3600 минут = 60 часов
    const val DAYS = 31               // 1..31 августа
    const val YEAR = 2026
    const val MONTH = 8               // август, 1-based

    /** 1 августа 2026 — суббота. Индексация недели: 0 = понедельник, 6 = воскресенье. */
    private const val FIRST_DAY_WEEKDAY = 5

    /** День недели дня плана: 0 = Пн … 6 = Вс. */
    fun weekdayOf(day: Int): Int = (FIRST_DAY_WEEKDAY + day - 1) % 7

    // ------------------------------------------------------------------
    // Суммы
    // ------------------------------------------------------------------

    fun doneOn(sessions: List<Session>, day: Int): Int =
        sessions.filter { it.day == day }.sumOf { it.durationMin }

    fun doneBefore(sessions: List<Session>, day: Int): Int =
        sessions.filter { it.day < day }.sumOf { it.durationMin }

    fun totalDone(sessions: List<Session>): Int = sessions.sumOf { it.durationMin }

    fun daysRemainingFrom(day: Int): Int = (DAYS - day + 1).coerceAtLeast(1)

    // ------------------------------------------------------------------
    // Норма
    // ------------------------------------------------------------------

    /**
     * Норма на день в минутах: весь невыполненный остаток цели делится поровну на
     * дни, начиная с этого.
     *
     *     норма(d) = ceil( max(0, 3600 - сделано_до(d)) / (31 - d + 1) )
     *
     * Норма считается от того, что сделано **до** этого дня, поэтому в течение дня
     * она не «убегает» при записи сессий. Пропущенный день автоматически поднимает
     * норму следующих дней, переработка — снижает. Округление вверх гарантирует,
     * что при выполнении нормы каждый день цель в 3600 минут будет закрыта.
     */
    fun normForDay(sessions: List<Session>, day: Int): Int {
        val d = day.coerceIn(1, DAYS)
        val left = (GOAL_MIN - doneBefore(sessions, d)).coerceAtLeast(0)
        if (left == 0) return 0
        val daysLeft = daysRemainingFrom(d)
        return (left + daysLeft - 1) / daysLeft   // деление с округлением вверх
    }

    /** Сколько ещё нужно сделать в этот день, чтобы закрыть его норму. */
    fun remainingOn(sessions: List<Session>, day: Int): Int =
        (normForDay(sessions, day) - doneOn(sessions, day)).coerceAtLeast(0)

    /** Остаток до цели в 60 часов. */
    fun remainingTotal(sessions: List<Session>): Int =
        (GOAL_MIN - totalDone(sessions)).coerceAtLeast(0)

    // ------------------------------------------------------------------
    // Статусы
    // ------------------------------------------------------------------

    enum class DayStatus {
        /** День ещё не наступил. */
        FUTURE,

        /** Сегодня, норма пока не закрыта. */
        IN_PROGRESS,

        /** Норма закрыта. */
        DONE,

        /** Норма перевыполнена (125% и больше). */
        OVER,

        /** День прошёл, что-то сделано, но нормы не хватило. */
        UNDER,

        /** День прошёл, не сделано ничего. */
        MISSED,
    }

    fun statusOf(sessions: List<Session>, day: Int, today: Int): DayStatus {
        val done = doneOn(sessions, day)
        val norm = normForDay(sessions, day)
        return when {
            done > 0 && norm == 0 -> DayStatus.OVER
            norm > 0 && done >= norm * 5 / 4 -> DayStatus.OVER
            norm > 0 && done >= norm -> DayStatus.DONE
            norm == 0 -> DayStatus.DONE
            day > today -> DayStatus.FUTURE
            day == today -> DayStatus.IN_PROGRESS
            done == 0 -> DayStatus.MISSED
            else -> DayStatus.UNDER
        }
    }

    // ------------------------------------------------------------------
    // Показатели для сводки
    // ------------------------------------------------------------------

    /**
     * Опережение (плюс) или отставание (минус) от ровного темпа на конец
     * предыдущего дня. Ровный темп — 3600/31 минут в день.
     */
    fun paceDeltaMin(sessions: List<Session>, today: Int): Int {
        val elapsed = (today - 1).coerceIn(0, DAYS)
        val expected = GOAL_MIN.toLong() * elapsed / DAYS
        return (totalDone(sessions) - expected).toInt()
    }

    /**
     * Серия ударных дней подряд: сколько последних завершившихся дней подряд
     * норма была закрыта. Сегодняшний день учитывается, только если норма уже
     * выполнена, — иначе он ещё не окончен и серию не прерывает.
     */
    fun streak(sessions: List<Session>, today: Int): Int {
        var count = 0
        var day = today.coerceIn(0, DAYS)
        if (day in 1..DAYS && doneOn(sessions, day) < normForDay(sessions, day)) day--
        while (day >= 1) {
            val norm = normForDay(sessions, day)
            if (doneOn(sessions, day) >= norm && (norm > 0 || doneOn(sessions, day) > 0)) {
                count++
                day--
            } else {
                break
            }
        }
        return count
    }

    /** Лучший день: (номер дня, минуты). Ноль, если ничего не сделано. */
    fun bestDay(sessions: List<Session>): Pair<Int, Int> {
        var bestDay = 0
        var bestMin = 0
        for (d in 1..DAYS) {
            val v = doneOn(sessions, d)
            if (v > bestMin) {
                bestMin = v
                bestDay = d
            }
        }
        return bestDay to bestMin
    }

    /** Средний темп за уже прошедшие дни (включая сегодняшний), минут в день. */
    fun averagePerDay(sessions: List<Session>, today: Int): Int {
        val elapsed = today.coerceIn(1, DAYS)
        return totalDone(sessions) / elapsed
    }

    /**
     * Прогноз: на каком дне августа цель будет закрыта при текущем среднем темпе.
     * Ноль — если темп нулевой или цель не будет достигнута до 31 августа.
     */
    fun forecastDay(sessions: List<Session>, today: Int): Int {
        val avg = averagePerDay(sessions, today)
        if (avg <= 0) return 0
        val left = remainingTotal(sessions)
        if (left == 0) return today.coerceIn(1, DAYS)
        val needDays = (left + avg - 1) / avg
        val day = today + needDays
        return if (day <= DAYS) day else 0
    }

    /** Количество прогулов среди завершившихся дней. */
    fun missedDays(sessions: List<Session>, today: Int): Int =
        (1 until today.coerceIn(1, DAYS + 1)).count { doneOn(sessions, it) == 0 }
}
