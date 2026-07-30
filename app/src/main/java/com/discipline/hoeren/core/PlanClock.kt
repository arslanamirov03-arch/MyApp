package com.discipline.hoeren.core

/**
 * Определение «какой сегодня день плана» без java.time (minSdk 24 без desugaring).
 *
 * Календарная арифметика сведена к номеру дня от условной эпохи по алгоритму
 * days_from_civil Говарда Хиннанта — чистая целочисленная формула, корректная для
 * григорианского календаря, включая високосные годы.
 */
object PlanClock {

    enum class Phase {
        /** Ещё до 1 августа — мобилизация не началась. */
        BEFORE,

        /** Идёт период плана, 1–31 августа. */
        DURING,

        /** После 31 августа — план закрыт, показываем итог. */
        AFTER,
    }

    data class PlanDate(
        val phase: Phase,
        /** День августа 1..31. Для BEFORE это 1, для AFTER — 31. */
        val day: Int,
        /** Сколько дней осталось до старта (только для BEFORE, иначе 0). */
        val daysUntilStart: Int,
    )

    /** Номер дня от 1970-01-01. Отрицательные значения допустимы. */
    fun epochDay(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400                                   // 0..399
        val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy           // 0..146096
        return era.toLong() * 146097L + doe.toLong() - 719468L
    }

    private val START = epochDay(Plan.YEAR, Plan.MONTH, 1)
    private val END = epochDay(Plan.YEAR, Plan.MONTH, Plan.DAYS)

    /** Разрешает произвольную календарную дату в положение внутри плана. */
    fun resolve(year: Int, month: Int, dayOfMonth: Int): PlanDate {
        val today = epochDay(year, month, dayOfMonth)
        return when {
            today < START -> PlanDate(Phase.BEFORE, 1, (START - today).toInt())
            today > END -> PlanDate(Phase.AFTER, Plan.DAYS, 0)
            else -> PlanDate(Phase.DURING, (today - START + 1).toInt(), 0)
        }
    }
}

// ----------------------------------------------------------------------
// Форматирование
// ----------------------------------------------------------------------

/** Минуты → «1:57». Используется для норм и длительностей. */
fun formatHm(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    return "${m / 60}:${(m % 60).toString().padStart(2, '0')}"
}

/** Минуты от полуночи → «14:30». */
fun formatClock(minuteOfDay: Int): String {
    val m = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
    return "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"
}

/** Минуты → «2 ч 05 мин» либо «45 мин». */
fun formatLong(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    val h = m / 60
    val mm = m % 60
    return if (h > 0) "$h ч ${mm.toString().padStart(2, '0')} мин" else "$mm мин"
}

/** Минуты со знаком → «+1:20» / «−0:45». Для отставания и опережения. */
fun formatSigned(minutes: Int): String {
    val sign = if (minutes < 0) "−" else "+"
    return sign + formatHm(kotlin.math.abs(minutes))
}
