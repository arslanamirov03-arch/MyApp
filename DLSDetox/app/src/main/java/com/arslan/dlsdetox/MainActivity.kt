package com.arslan.dlsdetox

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.CycleInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class MainActivity : Activity() {

    private lateinit var bg: AnimatedBackgroundView
    private lateinit var ring: StreakRingView
    private lateinit var tierName: TextView
    private lateinit var messageText: TextView
    private lateinit var infoText: TextView
    private lateinit var content: LinearLayout

    private val cells = arrayOfNulls<DayCellView>(Challenge.TOTAL_DAYS)
    private lateinit var completed: MutableSet<Int>
    private var currentTierIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        completed = Challenge.load(this)

        // draw behind the system bars for an immersive look
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val root = FrameLayout(this)
        root.setBackgroundColor(0xFF14161C.toInt())

        bg = AnimatedBackgroundView(this)
        root.addView(bg, FrameLayout.LayoutParams(MATCH, MATCH))

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        root.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))

        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.gravity = Gravity.CENTER_HORIZONTAL
        val padH = dp(18)
        content.setPadding(padH, dp(14), padH, dp(28))
        scroll.addView(content, FrameLayout.LayoutParams(MATCH, WRAP))

        buildHeader()
        buildRing()
        buildTierBlock()
        buildGrid()
        buildFooter()

        setContentView(root)

        // pad content around the system bars
        root.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            content.setPadding(
                padH,
                dp(14) + insets.systemWindowInsetTop,
                padH,
                dp(28) + insets.systemWindowInsetBottom
            )
            insets
        }

        refresh(animateTier = false)
    }

    // ---------------- UI construction ----------------

    private fun buildHeader() {
        val kicker = TextView(this).apply {
            text = "ЧЕЛЛЕНДЖ · 30 ДНЕЙ"
            setTextColor(0xB3FFFFFF.toInt())
            textSize = 12f
            letterSpacing = 0.28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        content.addView(kicker, WRAP, WRAP)

        val title = TextView(this).apply {
            text = "Без DLS 2026"
            setTextColor(Color.WHITE)
            textSize = 32f
            letterSpacing = 0.02f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setShadowLayer(dp(10).toFloat(), 0f, dp(2).toFloat(), 0x66000000)
        }
        content.addView(title, lp(WRAP, WRAP, top = dp(2)))

        val subtitle = TextView(this).apply {
            text = "Ни разу не устанавливать Dream League Soccer 2026"
            setTextColor(0xAAFFFFFF.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
        }
        content.addView(subtitle, lp(WRAP, WRAP, top = dp(4)))
    }

    private fun buildRing() {
        ring = StreakRingView(this)
        val size = dp(210)
        content.addView(ring, lp(size, size, top = dp(16)))
    }

    private fun buildTierBlock() {
        tierName = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            letterSpacing = 0.06f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        content.addView(tierName, lp(WRAP, WRAP, top = dp(10)))

        messageText = TextView(this).apply {
            setTextColor(0xCCFFFFFF.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
        }
        content.addView(messageText, lp(WRAP, WRAP, top = dp(4)))

        infoText = TextView(this).apply {
            setTextColor(0x99FFFFFF.toInt())
            textSize = 12.5f
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        content.addView(infoText, lp(WRAP, WRAP, top = dp(8)))
    }

    private fun buildGrid() {
        val columns = 6
        val rows = Challenge.TOTAL_DAYS / columns
        val screenW = resources.displayMetrics.widthPixels
        val available = screenW - dp(18) * 2
        val cellH = (available / columns * 1.16f).toInt()

        val grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        content.addView(grid, lp(MATCH, WRAP, top = dp(20)))

        var index = 0
        for (r in 0 until rows) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            grid.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
            for (c in 0 until columns) {
                val cell = DayCellView(this)
                val i = index
                cell.setOnClickListener { onCellTap(i) }
                val p = LinearLayout.LayoutParams(0, cellH, 1f)
                val m = dp(3)
                p.setMargins(m, m, m, m)
                row.addView(cell, p)
                cells[index] = cell
                index++
            }
        }
    }

    private fun buildFooter() {
        val hint = TextView(this).apply {
            text = "Нажми на день, чтобы отметить, что ты не играл.\nЗабыл вчера? Отметь прошедший день — прогресс сохранится."
            setTextColor(0x88FFFFFF.toInt())
            textSize = 11.5f
            gravity = Gravity.CENTER
        }
        content.addView(hint, lp(WRAP, WRAP, top = dp(18)))

        val reset = TextView(this).apply {
            text = "Сбросить прогресс"
            setTextColor(0x77FFFFFF.toInt())
            textSize = 12.5f
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { confirmReset() }
        }
        content.addView(reset, lp(WRAP, WRAP, top = dp(14)))
    }

    // ---------------- interaction ----------------

    private fun onCellTap(index: Int) {
        if (!Challenge.isTickable(index)) {
            cells[index]?.let { shake(it) }
            val t = Challenge.todayIndex()
            val msg = if (t < 0) "Челлендж стартует 11 июля" else "Этот день ещё не наступил"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            return
        }

        val oldTier = currentTierIndex
        val marking = !completed.contains(index)
        if (marking) completed.add(index) else completed.remove(index)
        Challenge.save(this, completed)

        cells[index]?.let { pop(it) }
        refresh(animateTier = true)

        if (marking) {
            val pal = Palettes.forStreak(Challenge.bestStreak(completed))
            bg.celebrate(pal.accent2)
        }
        if (currentTierIndex > oldTier) {
            val name = Palettes.list[currentTierIndex].name
            bg.celebrate(Palettes.list[currentTierIndex].accent)
            Toast.makeText(this, "Новый уровень: $name!", Toast.LENGTH_SHORT).show()
        }
        if (currentTierIndex == Palettes.list.size - 1 && oldTier != currentTierIndex) {
            Toast.makeText(this, "🎉 30 дней! Ты сделал это!", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmReset() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Сбросить прогресс?")
            .setMessage("Все отметки будут удалены. Это действие нельзя отменить.")
            .setPositiveButton("Сбросить") { _, _ ->
                completed.clear()
                Challenge.clear(this)
                refresh(animateTier = true)
                Toast.makeText(this, "Прогресс сброшен", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ---------------- state -> UI ----------------

    private fun refresh(animateTier: Boolean) {
        val today = Challenge.todayIndex()
        val streak = Challenge.bestStreak(completed)
        val tierIdx = Palettes.indexForStreak(streak)
        val pal = Palettes.list[tierIdx]
        currentTierIndex = tierIdx

        bg.applyPalette(pal)

        ring.setData(streak, completed.size, Challenge.TOTAL_DAYS, pal.accent, pal.accent2)
        tierName.text = pal.name.uppercase()
        tierName.setTextColor(pal.accent2)
        messageText.text = pal.message

        infoText.text = buildInfo(today)

        for (i in 0 until Challenge.TOTAL_DAYS) {
            val st = when {
                completed.contains(i) -> DayCellView.State.DONE
                Challenge.isTickable(i) -> DayCellView.State.AVAILABLE
                else -> DayCellView.State.FUTURE
            }
            cells[i]?.setData(
                dayNumber = i + 1,
                dateLabel = Challenge.shortLabel(i),
                state = st,
                isToday = (i == today),
                accent = pal.accent,
                accent2 = pal.accent2
            )
        }
    }

    private fun buildInfo(today: Int): String {
        return when {
            today < 0 -> {
                val left = ChronoUnit.DAYS.between(LocalDate.now(), Challenge.START)
                "Старт 11 июля · осталось $left дн."
            }
            today >= Challenge.TOTAL_DAYS -> {
                if (completed.size >= Challenge.TOTAL_DAYS) "Челлендж пройден полностью! 30 / 30"
                else "Челлендж завершён · отмечено ${completed.size} из 30"
            }
            else -> {
                val remaining = Challenge.TOTAL_DAYS - (today + 1)
                "День ${today + 1} из 30 · впереди ещё $remaining"
            }
        }
    }

    // ---------------- little animations ----------------

    private fun pop(v: View) {
        v.animate().cancel()
        v.scaleX = 0.82f
        v.scaleY = 0.82f
        v.animate().scaleX(1f).scaleY(1f)
            .setInterpolator(OvershootInterpolator(3.2f))
            .setDuration(320).start()
    }

    private fun shake(v: View) {
        val a = TranslateAnimation(0f, dp(6).toFloat(), 0f, 0f)
        a.duration = 320
        a.interpolator = CycleInterpolator(3f)
        v.startAnimation(a)
    }

    // ---------------- helpers ----------------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun lp(w: Int, h: Int, top: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h)
        p.topMargin = top
        p.gravity = Gravity.CENTER_HORIZONTAL
        return p
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
