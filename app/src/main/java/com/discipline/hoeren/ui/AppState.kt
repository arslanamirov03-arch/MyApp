package com.discipline.hoeren.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.discipline.hoeren.core.PlanClock
import com.discipline.hoeren.data.PracticeStore
import java.util.Calendar

enum class Screen { TODAY, PLAN, STATS }

/**
 * Состояние приложения: журнал практики плюс текущие дата и время.
 *
 * Приложение само определяет, какой сегодня день и который час, — пользователю
 * достаточно открыть его и нажать СТАРТ.
 */
class AppState(context: Context) {

    val store = PracticeStore(context.applicationContext)

    var year by mutableStateOf(0); private set
    var month by mutableStateOf(0); private set
    var dayOfMonth by mutableStateOf(0); private set

    /** Текущее время как минуты от полуночи. */
    var nowMinute by mutableStateOf(0); private set

    var screen by mutableStateOf(Screen.TODAY)

    /** День августа, открытый в редакторе. null — редактор закрыт. */
    var editorDay by mutableStateOf<Int?>(null)

    init {
        refreshNow()
    }

    /** Перечитывает системные дату и время. Вызывается при открытии и по таймеру. */
    fun refreshNow() {
        val c = Calendar.getInstance()
        year = c.get(Calendar.YEAR)
        month = c.get(Calendar.MONTH) + 1
        dayOfMonth = c.get(Calendar.DAY_OF_MONTH)
        nowMinute = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    /**
     * Подменяет «сейчас» фиксированной датой. Используется экранными тестами,
     * чтобы рендерить любой день плана независимо от системных часов.
     */
    fun overrideNow(year: Int, month: Int, dayOfMonth: Int, minuteOfDay: Int) {
        this.year = year
        this.month = month
        this.dayOfMonth = dayOfMonth
        this.nowMinute = minuteOfDay
    }

    val planDate: PlanClock.PlanDate
        get() = PlanClock.resolve(year, month, dayOfMonth)

    /** Текущий день плана 1..31. */
    val today: Int get() = planDate.day

    val phase: PlanClock.Phase get() = planDate.phase

    /** День недели сегодняшней календарной даты: 0 = понедельник. */
    val weekdayToday: Int
        get() {
            val c = Calendar.getInstance()
            c.set(year, month - 1, dayOfMonth)
            return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
        }
}

private val MONTHS_GENITIVE = arrayOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)

private val WEEKDAYS = arrayOf(
    "понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье",
)

private val WEEKDAYS_SHORT = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/** «2 августа 2026». Названия месяцев зашиты, чтобы не зависеть от локали устройства. */
fun formatDate(day: Int, month: Int, year: Int): String =
    "$day ${MONTHS_GENITIVE[(month - 1).coerceIn(0, 11)]} $year"

fun weekdayName(index: Int): String = WEEKDAYS[((index % 7) + 7) % 7]

fun weekdayShort(index: Int): String = WEEKDAYS_SHORT[((index % 7) + 7) % 7]

/** «дня», «дней», «день» — согласование числительного. */
fun pluralDays(n: Int): String {
    val a = n % 100
    if (a in 11..14) return "дней"
    return when (a % 10) {
        1 -> "день"
        2, 3, 4 -> "дня"
        else -> "дней"
    }
}
