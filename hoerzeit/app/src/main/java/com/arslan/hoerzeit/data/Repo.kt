package com.arslan.hoerzeit.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Всё хранится в SharedPreferences и переживает перезапуск приложения.
 *
 * Идущая сессия хранится как метка времени старта, а не как счётчик, —
 * поэтому время идёт правильно, даже если приложение свернули или система его выгрузила.
 */
class Repo private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _sessions = MutableStateFlow(readSessions())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _activeStart = MutableStateFlow(prefs.getLong(KEY_ACTIVE, 0L).takeIf { it > 0L })
    val activeStart: StateFlow<Long?> = _activeStart.asStateFlow()

    // --- сессия по кнопке ---------------------------------------------------

    fun start(now: Long = System.currentTimeMillis()) {
        if (_activeStart.value != null) return
        prefs.edit().putLong(KEY_ACTIVE, now).apply()
        _activeStart.value = now
    }

    /** Заканчивает идущую сессию. Возвращает её, если она была длиннее минуты минимума. */
    fun stop(now: Long = System.currentTimeMillis()): Session? {
        val started = _activeStart.value ?: return null
        prefs.edit().remove(KEY_ACTIVE).apply()
        _activeStart.value = null
        if (now - started < MIN_SESSION_MS) return null
        return add(started, now)
    }

    fun cancelActive() {
        prefs.edit().remove(KEY_ACTIVE).apply()
        _activeStart.value = null
    }

    // --- ручное добавление и правка -----------------------------------------

    fun add(start: Long, end: Long): Session {
        val session = Session(id = System.nanoTime(), start = start, end = end)
        _sessions.value = (_sessions.value + session).sortedByDescending { it.start }
        writeSessions()
        return session
    }

    fun remove(id: Long) {
        _sessions.value = _sessions.value.filterNot { it.id == id }
        writeSessions()
    }

    // --- подсчёты -----------------------------------------------------------

    fun progress(list: List<Session> = _sessions.value, today: LocalDate = LocalDate.now()): Progress {
        val byDay = list.groupBy { it.date }
        return Progress(
            totalMs = list.sumOf { it.durationMs },
            todayMs = byDay[today]?.sumOf { it.durationMs } ?: 0L,
            streakDays = streak(byDay, today),
            activeDays = byDay.size
        )
    }

    /** Сколько дней подряд закрыта дневная цель. Сегодняшний день не рвёт серию, пока он не закончен. */
    private fun streak(byDay: Map<LocalDate, List<Session>>, today: LocalDate): Int {
        val done = { day: LocalDate -> (byDay[day]?.sumOf { it.durationMs } ?: 0L) >= Goal.DAILY_MS }
        var day = if (done(today)) today else today.minusDays(1)
        var count = 0
        while (done(day)) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    // --- хранение -----------------------------------------------------------

    private fun readSessions(): List<Session> = runCatching {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(Session(o.getLong("i"), o.getLong("s"), o.getLong("e")))
            }
        }.sortedByDescending { it.start }
    }.getOrDefault(emptyList())

    private fun writeSessions() {
        val array = JSONArray()
        _sessions.value.forEach { s ->
            array.put(JSONObject().put("i", s.id).put("s", s.start).put("e", s.end))
        }
        prefs.edit().putString(KEY_SESSIONS, array.toString()).apply()
    }

    companion object {
        private const val FILE = "hoerzeit"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_ACTIVE = "active_start"

        /** Меньше 10 секунд — это случайное нажатие, не сессия. */
        private const val MIN_SESSION_MS = 10_000L

        @Volatile
        private var instance: Repo? = null

        fun get(context: Context): Repo =
            instance ?: synchronized(this) {
                instance ?: Repo(context).also { instance = it }
            }
    }
}
