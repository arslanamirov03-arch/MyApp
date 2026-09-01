package com.lexis.words.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lexis.words.AnswerState
import com.lexis.words.StudyMode
import com.lexis.words.StudyPhase
import com.lexis.words.StudyViewModel
import com.lexis.words.data.WordEntity
import com.lexis.words.ui.components.IconTile
import com.lexis.words.ui.components.PrimaryButton
import com.lexis.words.ui.components.SecondaryButton
import com.lexis.words.ui.components.ThinProgressBar
import com.lexis.words.ui.theme.Accent
import com.lexis.words.ui.theme.AccentTintBg
import com.lexis.words.ui.theme.AccentTintLabel
import com.lexis.words.ui.theme.ErrorBg
import com.lexis.words.ui.theme.ErrorInk
import com.lexis.words.ui.theme.Ink
import com.lexis.words.ui.theme.InfoBg
import com.lexis.words.ui.theme.InfoInk
import com.lexis.words.ui.theme.NeutralBtnBg
import com.lexis.words.ui.theme.Nunito
import com.lexis.words.ui.theme.ScreenBg
import com.lexis.words.ui.theme.SuccessBg
import com.lexis.words.ui.theme.SuccessInk
import com.lexis.words.ui.theme.TextMuted2
import com.lexis.words.ui.theme.TextMuted3
import com.lexis.words.ui.theme.TrackBg

@Composable
fun StudyScreen(blockId: Long, mode: StudyMode, nav: NavController, vm: StudyViewModel, imagesEnabled: Boolean = true) {
    val state by vm.state.collectAsState()
    LaunchedEffect(blockId, mode) { vm.start(blockId, mode) }

    if (state.loading) return

    if (state.finished) {
        ResultView(vm = vm, nav = nav)
        return
    }

    Box(Modifier.fillMaxSize().background(ScreenBg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 62.dp, start = 18.dp, end = 18.dp, bottom = 40.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(onClick = { nav.popBackStack() }) {
                    Text("×", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextMuted2)
                }
                val progress = if (state.phase == StudyPhase.LEARN) (state.learnIndex + 1f) / state.deck.size else state.testIndex.toFloat() / state.deck.size
                ThinProgressBar(fraction = progress, color = Accent, height = 8.dp, modifier = Modifier.weight(1f))
                val counter = if (state.phase == StudyPhase.LEARN) "${state.learnIndex + 1} / ${state.deck.size}" else "${state.testIndex + 1} / ${state.deck.size}"
                Text(counter, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = TextMuted2)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 52.dp)) {
                val (bg, fg) = if (mode == StudyMode.NEW) AccentTintBg to AccentTintLabel else InfoBg to InfoInk
                Box(Modifier.clip(RoundedCornerShape(9.dp)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(if (mode == StudyMode.NEW) "Новые слова" else "Повторение", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, color = fg)
                }
                Text("Порция ${state.batchIndex} · по ${state.deck.size} слов", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = TextMuted3)
            }
            Spacer(Modifier.height(6.dp))

            if (state.phase == StudyPhase.LEARN) {
                LearnCard(state.deck.getOrNull(state.learnIndex), state.learnIndex, state.deck.size, vm, imagesEnabled)
            } else {
                TestCard(state, vm, imagesEnabled)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ScoreTile(state.rightCount.toString(), "верно", SuccessInk, Modifier.weight(1f))
                    ScoreTile(state.wrongCount.toString(), "ошибок", Color(0xFFEC4C8C), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LearnCard(card: WordEntity?, index: Int, total: Int, vm: StudyViewModel, imagesEnabled: Boolean) {
    if (card == null) return
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White)
            .padding(22.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("СЛОВО ${index + 1}", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, color = TextMuted3)
        }
        Spacer(Modifier.height(16.dp))
        Text(card.de, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 36.sp, color = Ink, lineHeight = 40.sp)
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFFF4EDE3)).padding(vertical = 9.dp))
        Spacer(Modifier.height(9.dp))
        Text("ПЕРЕВОД", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, color = TextMuted3)
        Spacer(Modifier.height(8.dp))
        Text(card.ru, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = Accent)
        if (card.imagePath != null && imagesEnabled) {
            Spacer(Modifier.height(18.dp))
            coil.compose.AsyncImage(
                model = java.io.File(card.imagePath), contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFEFE7DC))
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            if (index > 0) {
                Box(
                    Modifier.clip(RoundedCornerShape(17.dp)).background(NeutralBtnBg).clickable { vm.learnPrev() }.padding(horizontal = 20.dp, vertical = 16.dp)
                ) { Text("Назад", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, color = Color(0xFF7A6B58)) }
            }
            PrimaryButton(if (index + 1 >= total) "Перейти к тесту" else "Дальше", modifier = Modifier.weight(1f)) { vm.learnNext() }
        }
    }
    Spacer(Modifier.height(14.dp))
    ThinProgressBar(fraction = (index + 1f) / total, color = Ink, height = 4.dp)
    Spacer(Modifier.height(14.dp))
    SecondaryButton("Я готов, сразу к тесту") { vm.skipToTest() }
}

@Composable
private fun TestCard(state: com.lexis.words.StudyUiState, vm: StudyViewModel, imagesEnabled: Boolean) {
    val card = state.deck.getOrNull(state.testIndex) ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White)
            .padding(22.dp)
    ) {
        Text("ПЕРЕВЕДИТЕ НА РУССКИЙ", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, color = TextMuted3)
        Spacer(Modifier.height(16.dp))
        Text(card.de, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 36.sp, color = Ink, lineHeight = 40.sp)
        if (card.imagePath != null && imagesEnabled) {
            Spacer(Modifier.height(18.dp))
            coil.compose.AsyncImage(
                model = java.io.File(card.imagePath), contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFEFE7DC))
            )
        }

        if (state.answerState == AnswerState.ASKING) {
            Spacer(Modifier.height(22.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFFDFBF8))
                    .padding(horizontal = 17.dp, vertical = 16.dp)
            ) {
                if (state.input.isEmpty()) {
                    Text("ваш перевод", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = TextMuted3)
                }
                BasicTextField(
                    value = state.input, onValueChange = { vm.setInput(it) },
                    textStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = Ink),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(17.dp)).background(NeutralBtnBg).clickable { vm.skipCard() }.padding(horizontal = 18.dp, vertical = 16.dp)
                ) { Text("Не помню", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, color = Color(0xFF7A6B58)) }
                PrimaryButton("Проверить", modifier = Modifier.weight(1f), enabled = state.input.isNotBlank()) { vm.checkAnswer() }
            }
        } else {
            val right = state.answerState == AnswerState.RIGHT
            val (bg, fg) = if (right) SuccessBg to SuccessInk else ErrorBg to ErrorInk
            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg).padding(horizontal = 17.dp, vertical = 16.dp)) {
                Text(if (right) "ВЕРНО" else "ПРАВИЛЬНЫЙ ОТВЕТ", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = fg)
                Spacer(Modifier.height(7.dp))
                Text(card.ru, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 22.sp, color = if (right) SuccessInk else ErrorInk)
                if (!right && state.input.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("вы ввели: ${state.input}", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = fg)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color(0xFFFDFBF8)).padding(horizontal = 15.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(if (right) SuccessInk else ErrorInk))
                Text(
                    if (right) "Слово вернётся через ${com.lexis.words.data.SpacedRepetition.intervalLabel(card.stage)}" else "Слово вернётся завтра в 6:00",
                    fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF5E5245)
                )
            }
            Spacer(Modifier.height(14.dp))
            val isLast = state.testIndex + 1 >= state.deck.size
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink).clickable { vm.nextCard() }.padding(vertical = 17.dp),
                contentAlignment = Alignment.Center
            ) { Text(if (isLast) "Итоги тренировки" else "Дальше", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White) }
        }
    }
}

@Composable
private fun ScoreTile(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(value, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 20.sp, color = color)
        Text(label, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextMuted2)
    }
}

@Composable
private fun ResultView(vm: StudyViewModel, nav: NavController) {
    val state by vm.state.collectAsState()
    val total = state.rightCount + state.wrongCount
    val pct = if (total > 0) (state.rightCount * 100 / total) else 0

    Box(Modifier.fillMaxSize().background(ScreenBg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 96.dp, start = 18.dp, end = 18.dp, bottom = 40.dp)) {
            Text("ПОРЦИЯ ${state.batchIndex} ЗАВЕРШЕНА", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 12.sp, color = TextMuted3, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                if (state.rightCount >= state.wrongCount) "Хороший подход" else "Есть над чем поработать",
                fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 34.sp, color = Ink, lineHeight = 38.sp
            )
            Spacer(Modifier.height(24.dp))

            Column(
                Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp)).background(Color.White).padding(22.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("$pct%", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 54.sp, color = Accent)
                    Text("точность", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = TextMuted3, modifier = Modifier.padding(bottom = 8.dp))
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(TrackBg)) {
                    if (total > 0) {
                        Box(Modifier.weight(state.rightCount.toFloat().coerceAtLeast(0.001f)).fillMaxSize().background(SuccessInk))
                        Box(Modifier.weight(state.wrongCount.toFloat().coerceAtLeast(0.001f)).fillMaxSize().background(Color(0xFFEC4C8C)))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    LegendDot(SuccessInk, "${state.rightCount} верно")
                    LegendDot(Color(0xFFEC4C8C), "${state.wrongCount} ошибок")
                }
            }
            Spacer(Modifier.height(12.dp))

            Column(Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(18.dp)) {
                Text("Когда повторим", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Ink)
                Spacer(Modifier.height(14.dp))
                WhenRow(state.wrongCount.toString(), "завтра в 6:00 · с ошибками", AccentTintBg, Accent)
                Spacer(Modifier.height(11.dp))
                WhenRow(state.rightCount.toString(), "через 3 дня в 6:00 · шаг вперёд", SuccessBg, SuccessInk)
                Spacer(Modifier.height(11.dp))
                WhenRow("7·14", "дальше 7 → 14 → 30 дней", InfoBg, InfoInk)
            }
            Spacer(Modifier.height(12.dp))

            val canContinue = state.remainingAfterBatch > 0
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Accent).clickable { vm.nextBatch() }.padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (canContinue) "Следующие ${minOf(state.deck.size, state.remainingAfterBatch)} слов" else "Начать порцию заново", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White)
                    Text(if (canContinue) "Осталось ${state.remainingAfterBatch} слов в очереди" else "Очередь на сегодня пройдена", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White.copy(alpha = 0.78f))
                }
            }
            Spacer(Modifier.height(9.dp))
            val backLabel = if (state.isGlobal) "На главный" else "В блок «${state.blockName}»"
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(NeutralBtnBg)
                    .clickable { nav.popBackStack() }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) { Text(backLabel, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color(0xFF7A6B58)) }
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Text(text, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF5E5245))
    }
}

@Composable
private fun WhenRow(badge: String, text: String, bg: Color, fg: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
            Text(badge, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 13.sp, color = fg)
        }
        Text(text, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color(0xFF5E5245))
    }
}
