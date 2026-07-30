package com.discipline.hoeren.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.discipline.hoeren.ui.theme.Gold
import com.discipline.hoeren.ui.theme.Ink
import com.discipline.hoeren.ui.theme.InkLine
import com.discipline.hoeren.ui.theme.InkSoft
import com.discipline.hoeren.ui.theme.Paper
import com.discipline.hoeren.ui.theme.PaperDim
import com.discipline.hoeren.ui.theme.RedDeep
import kotlinx.coroutines.delay

@Composable
fun AppRoot(state: AppState) {

    // Часы приложения. Живого секундомера нет — обновления раз в полминуты
    // достаточно, чтобы «СТАРТ СЕЙЧАС» проставлял верное время и чтобы дата
    // сменилась сама, если приложение осталось открытым за полночь.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            state.refreshNow()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Box(Modifier.weight(1f)) {
            when (state.screen) {
                Screen.TODAY -> TodayScreen(state)
                Screen.PLAN -> PlanScreen(state)
                Screen.STATS -> StatsScreen(state)
            }
        }
        BottomBar(state)
    }

    state.editorDay?.let { day ->
        DayEditorDialog(
            state = state,
            day = day,
            onDismiss = { state.editorDay = null },
        )
    }
}

@Composable
private fun BottomBar(state: AppState) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(InkSoft),
    ) {
        Tab("Сегодня", state.screen == Screen.TODAY, Modifier.weight(1f)) { state.screen = Screen.TODAY }
        Tab("План", state.screen == Screen.PLAN, Modifier.weight(1f)) { state.screen = Screen.PLAN }
        Tab("Сводка", state.screen == Screen.STATS, Modifier.weight(1f)) { state.screen = Screen.STATS }
    }
}

@Composable
private fun Tab(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (selected) RedDeep else InkSoft)
            .clickable { onClick() },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(if (selected) Gold else InkLine),
        )
        Box(Modifier.weight(1f).fillMaxWidth().padding(4.dp), contentAlignment = Alignment.Center) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) Paper else PaperDim,
                textAlign = TextAlign.Center,
            )
        }
    }
}
