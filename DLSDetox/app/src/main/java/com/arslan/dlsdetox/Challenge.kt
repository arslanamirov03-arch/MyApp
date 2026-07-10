package com.arslan.dlsdetox

import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Core challenge model: 30 consecutive days without installing / playing
 * Dream League Soccer 2026, starting 11 July 2026.
 *
 * Progress is stored as the set of day indices (0..29) the user has marked
 * as "clean". The user may tick any day up to and including today, so a
 * forgotten day can be filled in retroactively.
 */
object Challenge {

    const val TOTAL_DAYS = 30
    val START: LocalDate = LocalDate.of(2026, 7, 11)

    private const val PREFS = "dls_detox_prefs"
    private const val KEY_DONE = "completed_days"

    private val dayMonthFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd.MM", Locale("ru"))

    fun load(ctx: Context): MutableSet<Int> {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = sp.getStringSet(KEY_DONE, emptySet()) ?: emptySet()
        return raw.mapNotNull { it.toIntOrNull() }
            .filter { it in 0 until TOTAL_DAYS }
            .toMutableSet()
    }

    fun save(ctx: Context, days: Set<Int>) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putStringSet(KEY_DONE, days.map { it.toString() }.toSet()).apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** Days elapsed since the start date. -1 means "starts tomorrow", etc. */
    fun todayIndex(): Int =
        ChronoUnit.DAYS.between(START, LocalDate.now()).toInt()

    fun dateFor(index: Int): LocalDate = START.plusDays(index.toLong())

    fun shortLabel(index: Int): String = dateFor(index).format(dayMonthFmt)

    /** A day can be ticked once it has started (index <= today) and is within range. */
    fun isTickable(index: Int): Boolean {
        val t = todayIndex()
        return index in 0 until TOTAL_DAYS && index <= t
    }

    /** Longest run of consecutive clean days anywhere in the 30 — drives the visual tier. */
    fun bestStreak(days: Set<Int>): Int {
        var best = 0
        var cur = 0
        for (i in 0 until TOTAL_DAYS) {
            if (days.contains(i)) {
                cur++
                if (cur > best) best = cur
            } else {
                cur = 0
            }
        }
        return best
    }

    /** Consecutive clean days ending at (and including) today, going backwards. */
    fun currentStreak(days: Set<Int>): Int {
        val t = todayIndex()
        if (t < 0) return 0
        var i = t.coerceAtMost(TOTAL_DAYS - 1)
        var c = 0
        while (i >= 0 && days.contains(i)) {
            c++
            i--
        }
        return c
    }
}
