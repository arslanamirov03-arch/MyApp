package com.arslan.dlsdetox

/**
 * Visual tiers. The longer the streak of clean days, the richer and more
 * unusual the look becomes: colours deepen, orbs multiply, stars appear
 * and — at the very end — fireworks celebrate the win.
 */
data class Palette(
    val minStreak: Int,
    val name: String,
    val message: String,
    val bg: IntArray,      // 2 or 3 gradient stops (top -> bottom)
    val accent: Int,
    val accent2: Int,
    val orbCount: Int,
    val stars: Boolean,
    val sparks: Boolean,
    val fireworks: Boolean,
    val glow: Float
)

object Palettes {

    val list: List<Palette> = listOf(
        Palette(
            0, "Начало", "Каждый путь начинается с первого дня.",
            intArrayOf(0xFF14161C.toInt(), 0xFF1E2230.toInt()),
            0xFF7C8798.toInt(), 0xFFAAB4C4.toInt(),
            0, false, false, false, 0.00f
        ),
        Palette(
            1, "Первый шаг", "Первый день позади — ты уже сильнее вчерашнего себя.",
            intArrayOf(0xFF0B1F2A.toInt(), 0xFF123141.toInt()),
            0xFF38BDF8.toInt(), 0xFF7DD3FC.toInt(),
            7, false, false, false, 0.22f
        ),
        Palette(
            3, "Разгон", "Три дня подряд. Привычка просыпается.",
            intArrayOf(0xFF08312E.toInt(), 0xFF0C4A46.toInt()),
            0xFF2DD4BF.toInt(), 0xFF5EEAD4.toInt(),
            11, false, false, false, 0.32f
        ),
        Palette(
            5, "Уверенность", "Пять дней! Ты держишь слово перед собой.",
            intArrayOf(0xFF053B2E.toInt(), 0xFF065F46.toInt()),
            0xFF34D399.toInt(), 0xFF6EE7B7.toInt(),
            15, true, false, false, 0.38f
        ),
        Palette(
            7, "Неделя силы", "Целая неделя без DLS. Это уже характер.",
            intArrayOf(0xFF241C63.toInt(), 0xFF3B2A8C.toInt()),
            0xFF818CF8.toInt(), 0xFFC4B5FD.toInt(),
            19, true, false, false, 0.48f
        ),
        Palette(
            10, "Импульс", "Десять дней. Такой разгон уже не остановить.",
            intArrayOf(0xFF3B0A4A.toInt(), 0xFF5B1370.toInt()),
            0xFFE879F9.toInt(), 0xFFF5B8FF.toInt(),
            24, true, false, false, 0.55f
        ),
        Palette(
            14, "Половина пути", "Половина позади — вершина уже видна.",
            intArrayOf(0xFF5A1E0B.toInt(), 0xFF8A3412.toInt()),
            0xFFFB923C.toInt(), 0xFFFCD34D.toInt(),
            28, true, false, false, 0.62f
        ),
        Palette(
            18, "Три недели", "Три недели силы воли. Ты — скала.",
            intArrayOf(0xFF04302C.toInt(), 0xFF0A5F63.toInt(), 0xFF065F46.toInt()),
            0xFF22D3EE.toInt(), 0xFF34D399.toInt(),
            32, true, true, false, 0.70f
        ),
        Palette(
            22, "Космос", "Двадцать два дня. Ты вышел на орбиту.",
            intArrayOf(0xFF171449.toInt(), 0xFF3B1470.toInt(), 0xFF0F172A.toInt()),
            0xFFA78BFA.toInt(), 0xFFF472B6.toInt(),
            38, true, true, false, 0.82f
        ),
        Palette(
            27, "Финальный рывок", "Финишная прямая. Не сдавайся — ты почти там!",
            intArrayOf(0xFF3A1D05.toInt(), 0xFF6B2D0E.toInt(), 0xFF1A0E00.toInt()),
            0xFFFBBF24.toInt(), 0xFFFDE68A.toInt(),
            44, true, true, false, 0.92f
        ),
        Palette(
            30, "ПОБЕДА", "30 дней без DLS 2026. Ты победил себя!",
            intArrayOf(0xFF0A0A00.toInt(), 0xFF2E2600.toInt(), 0xFF4A3A00.toInt()),
            0xFFFFD700.toInt(), 0xFFFFF3B0.toInt(),
            52, true, true, true, 1.00f
        )
    )

    fun indexForStreak(streak: Int): Int {
        var idx = 0
        for (i in list.indices) {
            if (streak >= list[i].minStreak) idx = i
        }
        return idx
    }

    fun forStreak(streak: Int): Palette = list[indexForStreak(streak)]
}
