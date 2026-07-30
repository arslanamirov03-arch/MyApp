package com.discipline.hoeren

import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.discipline.hoeren.ui.AppRoot
import com.discipline.hoeren.ui.AppState
import com.discipline.hoeren.ui.Screen
import com.discipline.hoeren.ui.theme.DisziplinTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Рендерит экраны приложения и складывает картинки в build/screens.
 *
 * Тест проверяет, что композиция не падает на всех фазах плана; снимки нужны для
 * глазной проверки вёрстки без установки APK на устройство.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1500dp-normal-long-notround-any-420dpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun state(): AppState = AppState(ApplicationProvider.getApplicationContext())

    private fun render(state: AppState, name: String) {
        compose.setContent {
            DisziplinTheme { AppRoot(state) }
        }
        compose.onRoot().captureRoboImage("build/screens/$name.png")
    }

    /** Диалог живёт в отдельном окне, поэтому снимается своим корнем. */
    private fun renderDialog(state: AppState, name: String) {
        compose.setContent {
            DisziplinTheme { AppRoot(state) }
        }
        compose.onNode(isDialog()).captureRoboImage("build/screens/$name.png")
    }

    @Test
    fun today_beforeAugust() {
        val s = state()
        s.overrideNow(2026, 7, 30, 18 * 60 + 42)
        render(s, "01_today_before")
    }

    @Test
    fun today_freshDay() {
        val s = state()
        s.overrideNow(2026, 8, 5, 9 * 60 + 15)
        // Первые четыре дня отработаны, пятый ещё нет
        repeat(4) { i -> s.store.addSession(i + 1, 8 * 60, 10 * 60) }
        render(s, "02_today_fresh")
    }

    @Test
    fun today_practiceRunning() {
        val s = state()
        s.overrideNow(2026, 8, 5, 15 * 60 + 30)
        s.store.addSession(5, 8 * 60, 9 * 60)
        s.store.startPractice(5, 14 * 60 + 30)
        render(s, "03_today_running")
    }

    @Test
    fun today_normFulfilled() {
        val s = state()
        s.overrideNow(2026, 8, 5, 20 * 60)
        s.store.addSession(5, 8 * 60, 9 * 60 + 30)
        s.store.addSession(5, 18 * 60, 19 * 60)
        render(s, "04_today_done")
    }

    @Test
    fun today_afterAugust() {
        val s = state()
        s.overrideNow(2026, 9, 3, 12 * 60)
        repeat(31) { i -> s.store.addSession(i + 1, 8 * 60, 10 * 60) }
        render(s, "05_today_after")
    }

    @Test
    fun planCalendar() {
        val s = state()
        s.overrideNow(2026, 8, 12, 11 * 60)
        s.screen = Screen.PLAN
        // разнородная картина: выполнено, недовыполнено, прогулы, перевыполнение
        listOf(1, 2, 4, 6, 7, 9, 10).forEach { s.store.addSession(it, 8 * 60, 10 * 60) }
        s.store.addSession(3, 8 * 60, 8 * 60 + 40)
        s.store.addSession(8, 6 * 60, 12 * 60)
        s.store.addSession(11, 21 * 60, 22 * 60 + 30)
        render(s, "06_plan")
    }

    @Test
    fun statsSummary() {
        val s = state()
        s.overrideNow(2026, 8, 12, 11 * 60)
        s.screen = Screen.STATS
        listOf(1, 2, 4, 6, 7, 9, 10).forEach { s.store.addSession(it, 8 * 60, 10 * 60) }
        s.store.addSession(3, 8 * 60, 8 * 60 + 40)
        s.store.addSession(8, 6 * 60, 12 * 60)
        render(s, "07_stats")
    }

    @Test
    fun dayEditor() {
        val s = state()
        s.overrideNow(2026, 8, 12, 11 * 60)
        s.store.addSession(9, 8 * 60, 9 * 60 + 20)
        s.store.addSession(9, 19 * 60, 19 * 60 + 45)
        s.editorDay = 9
        renderDialog(s, "08_day_editor")
    }
}
