package com.discipline.hoeren.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.discipline.hoeren.core.OpenSession
import com.discipline.hoeren.core.Session
import org.json.JSONArray
import org.json.JSONObject

/**
 * Хранение журнала практики.
 *
 * Данных мало (максимум несколько сотен записей за месяц), поэтому вместо базы
 * используется JSON-строка в SharedPreferences: никаких Room, KSP и миграций.
 * Состояние выставлено наружу как Compose-состояние, так что экраны
 * перерисовываются сами при любой правке.
 */
class PracticeStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var sessions by mutableStateOf(loadSessions())
        private set

    /** Начатая, но не завершённая практика. null — сейчас практики нет. */
    var openSession by mutableStateOf(loadOpen())
        private set

    private var nextId: Long = (sessions.maxOfOrNull { it.id } ?: 0L) + 1L

    // ------------------------------------------------------------------
    // Журнал сессий
    // ------------------------------------------------------------------

    fun addSession(day: Int, startMin: Int, endMin: Int) {
        sessions = (sessions + Session(nextId++, day, startMin, endMin)).sortedWith(ORDER)
        persistSessions()
    }

    fun updateSession(id: Long, startMin: Int, endMin: Int) {
        sessions = sessions
            .map { if (it.id == id) it.copy(startMin = startMin, endMin = endMin) else it }
            .sortedWith(ORDER)
        persistSessions()
    }

    fun deleteSession(id: Long) {
        sessions = sessions.filterNot { it.id == id }
        persistSessions()
    }

    fun sessionsOf(day: Int): List<Session> = sessions.filter { it.day == day }

    // ------------------------------------------------------------------
    // Активная практика
    // ------------------------------------------------------------------

    fun startPractice(day: Int, startMin: Int) {
        openSession = OpenSession(day, startMin)
        persistOpen()
    }

    /** Завершает активную практику и записывает её в журнал. */
    fun finishPractice(endMin: Int) {
        val open = openSession ?: return
        openSession = null
        persistOpen()
        addSession(open.day, open.startMin, endMin)
    }

    /** Отменяет активную практику, ничего не записывая. */
    fun cancelPractice() {
        openSession = null
        persistOpen()
    }

    /** Правит время начала уже идущей практики. */
    fun adjustPracticeStart(startMin: Int) {
        val open = openSession ?: return
        openSession = open.copy(startMin = startMin)
        persistOpen()
    }

    // ------------------------------------------------------------------
    // Сериализация
    // ------------------------------------------------------------------

    private fun persistSessions() {
        val array = JSONArray()
        for (s in sessions) {
            array.put(
                JSONObject()
                    .put(KEY_ID, s.id)
                    .put(KEY_DAY, s.day)
                    .put(KEY_START, s.startMin)
                    .put(KEY_END, s.endMin)
            )
        }
        prefs.edit().putString(KEY_SESSIONS, array.toString()).apply()
    }

    private fun loadSessions(): List<Session> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Session(
                    id = o.getLong(KEY_ID),
                    day = o.getInt(KEY_DAY),
                    startMin = o.getInt(KEY_START),
                    endMin = o.getInt(KEY_END),
                )
            }.sortedWith(ORDER)
        }.getOrDefault(emptyList())
    }

    private fun persistOpen() {
        val open = openSession
        val editor = prefs.edit()
        if (open == null) {
            editor.remove(KEY_OPEN)
        } else {
            editor.putString(
                KEY_OPEN,
                JSONObject().put(KEY_DAY, open.day).put(KEY_START, open.startMin).toString(),
            )
        }
        editor.apply()
    }

    private fun loadOpen(): OpenSession? {
        val raw = prefs.getString(KEY_OPEN, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            OpenSession(o.getInt(KEY_DAY), o.getInt(KEY_START))
        }.getOrNull()
    }

    private companion object {
        const val PREFS = "disziplin_hoeren"
        const val KEY_SESSIONS = "sessions"
        const val KEY_OPEN = "open_session"
        const val KEY_ID = "id"
        const val KEY_DAY = "day"
        const val KEY_START = "start"
        const val KEY_END = "end"

        val ORDER: Comparator<Session> = compareBy({ it.day }, { it.startMin }, { it.id })
    }
}
