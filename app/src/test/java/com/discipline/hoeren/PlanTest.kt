package com.discipline.hoeren

import com.discipline.hoeren.core.Plan
import com.discipline.hoeren.core.PlanClock
import com.discipline.hoeren.core.Session
import com.discipline.hoeren.core.formatClock
import com.discipline.hoeren.core.formatHm
import com.discipline.hoeren.core.formatSigned
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanTest {

    private var nextId = 1L

    private fun s(day: Int, from: String, to: String): Session {
        fun min(t: String) = t.substringBefore(':').toInt() * 60 + t.substringAfter(':').toInt()
        return Session(nextId++, day, min(from), min(to))
    }

    // ------------------------------------------------------------------
    // Длительность сессии
    // ------------------------------------------------------------------

    @Test
    fun duration_isPlainDifference() {
        assertEquals(50, s(1, "14:30", "15:20").durationMin)
        assertEquals(120, s(1, "08:00", "10:00").durationMin)
    }

    @Test
    fun duration_crossingMidnight_countsIntoNextDay() {
        assertEquals(70, s(1, "23:30", "00:40").durationMin)
        assertEquals(1, s(1, "23:59", "00:00").durationMin)
    }

    @Test
    fun duration_zeroWhenStartEqualsEnd() {
        assertEquals(0, s(1, "12:00", "12:00").durationMin)
    }

    // ------------------------------------------------------------------
    // Норма
    // ------------------------------------------------------------------

    @Test
    fun norm_onEmptyPlan_is117Minutes() {
        // 3600 / 31 = 116.13, с округлением вверх — 117 минут = 1:57
        assertEquals(117, Plan.normForDay(emptyList(), 1))
        assertEquals("1:57", formatHm(Plan.normForDay(emptyList(), 1)))
    }

    @Test
    fun norm_doesNotMoveWithinTheSameDay() {
        // Норма считается от сделанного ДО этого дня, поэтому записи за сегодня
        // её не меняют — иначе цель бы «убегала» по мере практики.
        val before = Plan.normForDay(emptyList(), 3)
        val sessions = listOf(s(3, "09:00", "10:00"), s(3, "18:00", "19:00"))
        assertEquals(before, Plan.normForDay(sessions, 3))
    }

    @Test
    fun norm_growsAfterSkippedDays() {
        // Пять пропущенных дней подряд — норма на шестой день выросла со 117 до 139.
        val skipped = emptyList<Session>()
        assertEquals(117, Plan.normForDay(skipped, 1))
        assertEquals(139, Plan.normForDay(skipped, 6))
        assertTrue(Plan.normForDay(skipped, 6) > Plan.normForDay(skipped, 1))
    }

    @Test
    fun norm_shrinksAfterOverwork() {
        val overworked = listOf(s(1, "08:00", "14:00"))   // 6 часов в первый день
        assertTrue(Plan.normForDay(overworked, 2) < Plan.normForDay(emptyList(), 2))
    }

    @Test
    fun norm_isZeroOnceGoalIsClosed() {
        val done = (1..30).map { s(it, "00:00", "02:00") }   // 30 × 120 = 3600
        assertEquals(3600, Plan.totalDone(done))
        assertEquals(0, Plan.normForDay(done, 31))
        assertEquals(0, Plan.remainingTotal(done))
    }

    @Test
    fun norm_onLastDay_equalsWholeRemainder() {
        val sessions = listOf(s(1, "00:00", "01:00"))       // 60 минут
        assertEquals(3600 - 60, Plan.normForDay(sessions, 31))
    }

    // ------------------------------------------------------------------
    // Главное свойство: цель закрывается ровно
    // ------------------------------------------------------------------

    @Test
    fun followingTheNormEveryDay_closesExactly3600() {
        val sessions = mutableListOf<Session>()
        for (day in 1..Plan.DAYS) {
            val norm = Plan.normForDay(sessions, day)
            if (norm > 0) sessions.add(Session(nextId++, day, 0, norm))
        }
        assertEquals(Plan.GOAL_MIN, Plan.totalDone(sessions))
        assertEquals(0, Plan.remainingTotal(sessions))
    }

    @Test
    fun skippingFiveDaysThenFollowingTheNorm_stillCloses3600() {
        val sessions = mutableListOf<Session>()
        for (day in 1..Plan.DAYS) {
            if (day <= 5) continue                          // прогуляли пять дней
            val norm = Plan.normForDay(sessions, day)
            if (norm > 0) sessions.add(Session(nextId++, day, 0, norm))
        }
        assertEquals(Plan.GOAL_MIN, Plan.totalDone(sessions))
    }

    @Test
    fun editingAPastDay_recomputesLaterNorms() {
        val sessions = mutableListOf(s(1, "08:00", "09:00"))    // 60 минут
        val normDay2Before = Plan.normForDay(sessions, 2)
        sessions[0] = s(1, "08:00", "11:00")                    // исправили на 180 минут
        assertTrue(Plan.normForDay(sessions, 2) < normDay2Before)
    }

    // ------------------------------------------------------------------
    // Суммы, остатки, статусы
    // ------------------------------------------------------------------

    @Test
    fun doneOn_sumsAllSessionsOfTheDay() {
        val sessions = listOf(s(4, "09:00", "10:30"), s(4, "20:00", "20:30"), s(5, "09:00", "10:00"))
        assertEquals(120, Plan.doneOn(sessions, 4))
        assertEquals(60, Plan.doneOn(sessions, 5))
        assertEquals(120, Plan.doneBefore(sessions, 5))
    }

    @Test
    fun remainingOn_dropsToZeroWhenNormIsClosed() {
        val sessions = listOf(s(1, "08:00", "10:00"))       // 120 при норме 117
        assertEquals(0, Plan.remainingOn(sessions, 1))
    }

    @Test
    fun status_reflectsWhatHappenedOnTheDay() {
        val today = 5
        val sessions = listOf(
            s(1, "08:00", "10:00"),     // 120 >= 117 → норма закрыта
            s(3, "08:00", "08:30"),     // 30 — мало
            s(4, "06:00", "12:00"),     // 360 — сильно больше нормы
        )
        assertEquals(Plan.DayStatus.DONE, Plan.statusOf(sessions, 1, today))
        assertEquals(Plan.DayStatus.MISSED, Plan.statusOf(sessions, 2, today))
        assertEquals(Plan.DayStatus.UNDER, Plan.statusOf(sessions, 3, today))
        assertEquals(Plan.DayStatus.OVER, Plan.statusOf(sessions, 4, today))
        assertEquals(Plan.DayStatus.IN_PROGRESS, Plan.statusOf(sessions, 5, today))
        assertEquals(Plan.DayStatus.FUTURE, Plan.statusOf(sessions, 6, today))
    }

    @Test
    fun missedDays_countsOnlyFinishedEmptyDays() {
        val sessions = listOf(s(1, "08:00", "10:00"))
        assertEquals(3, Plan.missedDays(sessions, 5))   // пропущены дни 2, 3, 4
    }

    // ------------------------------------------------------------------
    // Показатели сводки
    // ------------------------------------------------------------------

    @Test
    fun paceDelta_isPositiveWhenAheadOfEvenTempo() {
        val ahead = listOf(s(1, "06:00", "12:00"))          // 360 минут за первый день
        assertTrue(Plan.paceDeltaMin(ahead, 2) > 0)
        assertTrue(Plan.paceDeltaMin(emptyList(), 6) < 0)   // пять пустых дней — отставание
    }

    @Test
    fun streak_countsConsecutiveFulfilledDays() {
        val sessions = (1..4).map { s(it, "08:00", "10:00") }   // по 120 при норме ~117
        assertEquals(4, Plan.streak(sessions, 4))
    }

    @Test
    fun streak_isBrokenByAMissedDay() {
        val sessions = listOf(s(1, "08:00", "10:00"), s(3, "08:00", "10:00"))
        assertEquals(1, Plan.streak(sessions, 3))               // только третий день
    }

    @Test
    fun streak_ignoresTodayWhileItsNormIsNotClosedYet() {
        // День 3 ещё идёт и норма не закрыта — серия за дни 1–2 не должна рваться.
        val sessions = listOf(s(1, "08:00", "10:00"), s(2, "08:00", "10:00"), s(3, "08:00", "08:10"))
        assertEquals(2, Plan.streak(sessions, 3))
    }

    @Test
    fun bestDay_findsTheLongestDay() {
        val sessions = listOf(s(1, "08:00", "09:00"), s(7, "08:00", "12:00"), s(9, "08:00", "10:00"))
        assertEquals(7 to 240, Plan.bestDay(sessions))
    }

    @Test
    fun forecastDay_isZeroWithoutProgress() {
        assertEquals(0, Plan.forecastDay(emptyList(), 5))
    }

    @Test
    fun forecastDay_predictsEarlyFinishAtAStrongTempo() {
        val sessions = (1..5).map { s(it, "06:00", "12:00") }   // по 360 минут в день
        val forecast = Plan.forecastDay(sessions, 5)
        assertTrue(forecast in 1..Plan.DAYS)
        assertTrue(forecast < Plan.DAYS)
    }

    // ------------------------------------------------------------------
    // Календарь
    // ------------------------------------------------------------------

    @Test
    fun epochDay_matchesKnownReferencePoints() {
        assertEquals(0L, PlanClock.epochDay(1970, 1, 1))
        assertEquals(1L, PlanClock.epochDay(1970, 1, 2))
        assertEquals(59L, PlanClock.epochDay(1970, 3, 1))
        // 2024 — високосный: 29 февраля существует и идёт перед 1 марта
        assertEquals(
            PlanClock.epochDay(2024, 2, 29) + 1,
            PlanClock.epochDay(2024, 3, 1),
        )
        assertEquals(31L, PlanClock.epochDay(2026, 8, 31) - PlanClock.epochDay(2026, 7, 31))
    }

    @Test
    fun resolve_placesDateInsideThePlan() {
        assertEquals(PlanClock.Phase.DURING, PlanClock.resolve(2026, 8, 1).phase)
        assertEquals(1, PlanClock.resolve(2026, 8, 1).day)
        assertEquals(17, PlanClock.resolve(2026, 8, 17).day)
        assertEquals(31, PlanClock.resolve(2026, 8, 31).day)
    }

    @Test
    fun resolve_detectsBeforeAndAfter() {
        val before = PlanClock.resolve(2026, 7, 30)
        assertEquals(PlanClock.Phase.BEFORE, before.phase)
        assertEquals(2, before.daysUntilStart)

        assertEquals(PlanClock.Phase.AFTER, PlanClock.resolve(2026, 9, 1).phase)
        assertEquals(PlanClock.Phase.BEFORE, PlanClock.resolve(2026, 1, 15).phase)
    }

    @Test
    fun weekday_firstOfAugust2026_isSaturday() {
        assertEquals(5, Plan.weekdayOf(1))      // 0 = Пн, 5 = Сб
        assertEquals(6, Plan.weekdayOf(2))      // воскресенье
        assertEquals(0, Plan.weekdayOf(3))      // понедельник
    }

    // ------------------------------------------------------------------
    // Форматирование
    // ------------------------------------------------------------------

    @Test
    fun formatting_producesExpectedStrings() {
        assertEquals("1:57", formatHm(117))
        assertEquals("0:05", formatHm(5))
        assertEquals("60:00", formatHm(3600))
        assertEquals("14:30", formatClock(14 * 60 + 30))
        assertEquals("00:00", formatClock(0))
        assertEquals("+1:20", formatSigned(80))
        assertEquals("−0:45", formatSigned(-45))
    }
}
