package com.lexis.words.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lexis.words.AnswerState
import com.lexis.words.AppViewModel
import com.lexis.words.StudyMode
import com.lexis.words.StudyPhase
import com.lexis.words.StudyUiState
import com.lexis.words.StudyViewModel
import com.lexis.words.data.AppSettings
import com.lexis.words.data.TypeRanges
import com.lexis.words.data.WordEntity
import com.lexis.words.ui.components.IconTile
import com.lexis.words.ui.components.PrimaryButton
import com.lexis.words.ui.components.SecondaryButton
import com.lexis.words.ui.components.SheetScaffold
import com.lexis.words.ui.components.ThinProgressBar
import com.lexis.words.ui.theme.Accent
import com.lexis.words.ui.theme.AccentTintBg
import com.lexis.words.ui.theme.AccentTintLabel
import com.lexis.words.ui.theme.ErrorBg
import com.lexis.words.ui.theme.ErrorInk
import com.lexis.words.ui.theme.InfoBg
import com.lexis.words.ui.theme.InfoInk
import com.lexis.words.ui.theme.Ink
import com.lexis.words.ui.theme.NeutralBtnBg
import com.lexis.words.ui.theme.Nunito
import com.lexis.words.ui.theme.ScreenBg
import com.lexis.words.ui.theme.SuccessBg
import com.lexis.words.ui.theme.SuccessInk
import com.lexis.words.ui.theme.TextMuted2
import com.lexis.words.ui.theme.TextMuted3
import com.lexis.words.ui.theme.TrackBg

/** Card font picked in the study screen's appearance sheet. */
fun cardFont(choice: Int): FontFamily = when (choice) {
    1 -> FontFamily.SansSerif
    2 -> FontFamily.Serif
    3 -> FontFamily.Monospace
    else -> Nunito
}

@Composable
fun StudyScreen(
    blockId: Long,
    mode: StudyMode,
    nav: NavController,
    vm: StudyViewModel,
    settings: AppSettings,
    appVm: AppViewModel,
) {
    val state by vm.state.collectAsState()
    var typeSheet by remember { mutableStateOf(false) }
    LaunchedEffect(blockId, mode) { vm.start(blockId, mode) }

    // A solid background while the deck loads keeps the screen transition from flashing.
    if (state.loading) {
        Box(Modifier.fillMaxSize().background(ScreenBg))
        return
    }

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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconTile(onClick = { nav.popBackStack() }) {
                    Text("×", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextMuted2)
                }
                val progress = if (state.phase == StudyPhase.LEARN) {
                    (state.learnIndex + 1f) / state.deck.size
                } else state.testIndex.toFloat() / state.deck.size
                ThinProgressBar(fraction = progress, color = Accent, height = 8.dp, modifier = Modifier.weight(1f))
                val counter = if (state.phase == StudyPhase.LEARN) {
                    "${state.learnIndex + 1} / ${state.deck.size}"
                } else "${state.testIndex + 1} / ${state.deck.size}"
                Text(counter, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = TextMuted2)
                IconTile(onClick = { typeSheet = true }) {
                    Icon(Icons.Filled.Tune, contentDescription = "Вид карточки", tint = Accent, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 52.dp)) {
                val (bg, fg) = if (mode == StudyMode.NEW) AccentTintBg to AccentTintLabel else InfoBg to InfoInk
                Box(Modifier.clip(RoundedCornerShape(9.dp)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(
                        if (mode == StudyMode.NEW) "Новые слова" else "Повторение",
                        fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, color = fg
                    )
                }
                Text(
                    "Порция ${state.batchIndex} · по ${state.deck.size} слов",
                    fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = TextMuted3
                )
            }
            Spacer(Modifier.height(6.dp))

            if (state.phase == StudyPhase.LEARN) {
                LearnCard(state.deck.getOrNull(state.learnIndex), state.learnIndex, state.deck.size, vm, settings)
            } else {
                TestCard(state, vm, settings)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ScoreTile(state.rightCount.toString(), "верно", SuccessInk, Modifier.weight(1f))
                    ScoreTile(state.wrongCount.toString(), "ошибок", Color(0xFFEC4C8C), Modifier.weight(1f))
                }
            }
        }

        if (typeSheet) {
            TypographySheet(settings = settings, appVm = appVm, onDismiss = { typeSheet = false })
        }
    }
}

@Composable
private fun LearnCard(card: WordEntity?, index: Int, total: Int, vm: StudyViewModel, settings: AppSettings) {
    if (card == null) return
    val font = cardFont(settings.fontChoice)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White)
            .padding(22.dp)
    ) {
        Text("СЛОВО ${index + 1}", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, color = TextMuted3)
        Spacer(Modifier.height(16.dp))
        Text(
            card.de, fontFamily = font, fontWeight = FontWeight.Black,
            fontSize = settings.wordSizeSp.sp, lineHeight = (settings.wordSizeSp * 1.15f).sp, color = Ink
        )
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFFF4EDE3)))
        Spacer(Modifier.height(18.dp))
        Text("ПЕРЕВОД", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, color = TextMuted3)
        Spacer(Modifier.height(8.dp))
        Text(
            card.ru, fontFamily = font, fontWeight = FontWeight.ExtraBold,
            fontSize = settings.translationSizeSp.sp, lineHeight = (settings.translationSizeSp * 1.2f).sp, color = Accent
        )
        if (card.imagePath != null && settings.imagesEnabled) {
            Spacer(Modifier.height(18.dp))
            coil.compose.AsyncImage(
                model = java.io.File(card.imagePath), contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(settings.imageHeightDp.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFEFE7DC))
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            if (index > 0) {
                Box(
                    Modifier.clip(RoundedCornerShape(17.dp)).background(NeutralBtnBg)
                        .clickable { vm.learnPrev() }.padding(horizontal = 20.dp, vertical = 16.dp)
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
private fun TestCard(state: StudyUiState, vm: StudyViewModel, settings: AppSettings) {
    val card = state.deck.getOrNull(state.testIndex) ?: return
    val font = cardFont(settings.fontChoice)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White)
            .padding(22.dp)
    ) {
        Text("НАПИШИТЕ ПО-НЕМЕЦКИ", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, color = TextMuted3)
        Spacer(Modifier.height(16.dp))
        // The prompt is the translation — the German word is what gets typed in.
        Text(
            card.ru, fontFamily = font, fontWeight = FontWeight.Black,
            fontSize = settings.translationSizeSp.sp, lineHeight = (settings.translationSizeSp * 1.15f).sp, color = Ink
        )
        if (card.imagePath != null && settings.imagesEnabled) {
            Spacer(Modifier.height(18.dp))
            coil.compose.AsyncImage(
                model = java.io.File(card.imagePath), contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(settings.imageHeightDp.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFEFE7DC))
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
                    Text(
                        "немецкое слово", fontFamily = font, fontWeight = FontWeight.Bold,
                        fontSize = settings.answerSizeSp.sp, color = TextMuted3
                    )
                }
                BasicTextField(
                    value = state.input,
                    onValueChange = { vm.setInput(it) },
                    textStyle = TextStyle(
                        fontFamily = font, fontWeight = FontWeight.ExtraBold,
                        fontSize = settings.answerSizeSp.sp, color = Ink
                    ),
                    // Password type + no visual masking: Gboard/T9 stops offering word
                    // predictions and autocorrect, but the typed word stays readable.
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { vm.checkAnswer() }),
                    visualTransformation = VisualTransformation.None,
                    singleLine = true,
                    cursorBrush = SolidColor(Accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(17.dp)).background(NeutralBtnBg)
                        .clickable { vm.skipCard() }.padding(horizontal = 18.dp, vertical = 16.dp)
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
                Text(
                    card.de, fontFamily = font, fontWeight = FontWeight.Black,
                    fontSize = settings.wordSizeSp.sp, lineHeight = (settings.wordSizeSp * 1.15f).sp,
                    color = if (right) SuccessInk else ErrorInk
                )
                if (!right && state.input.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "вы ввели: ${state.input}", fontFamily = font, fontWeight = FontWeight.SemiBold,
                        fontSize = settings.answerSizeSp.sp, color = fg
                    )
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
private fun TypographySheet(settings: AppSettings, appVm: AppViewModel, onDismiss: () -> Unit) {
    val font = cardFont(settings.fontChoice)
    SheetScaffold(onDismiss = onDismiss) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Вид карточки", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 22.sp, color = Ink)
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(NeutralBtnBg)
                    .clickable { appVm.resetTypography() }.padding(horizontal = 12.dp, vertical = 8.dp)
            ) { Text("Сбросить", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp, color = Color(0xFF7A6B58)) }
        }
        Spacer(Modifier.height(14.dp))

        // Live preview at the current settings.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp)) {
            Text(
                "das Beispiel", fontFamily = font, fontWeight = FontWeight.Black,
                fontSize = settings.wordSizeSp.sp, lineHeight = (settings.wordSizeSp * 1.15f).sp, color = Ink, maxLines = 1
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "пример", fontFamily = font, fontWeight = FontWeight.ExtraBold,
                fontSize = settings.translationSizeSp.sp, lineHeight = (settings.translationSizeSp * 1.2f).sp, color = Accent, maxLines = 1
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "ваш ответ", fontFamily = font, fontWeight = FontWeight.SemiBold,
                fontSize = settings.answerSizeSp.sp, color = TextMuted2, maxLines = 1
            )
        }
        Spacer(Modifier.height(16.dp))

        SizeSlider("Немецкое слово", settings.wordSizeSp, TypeRanges.WORD, "sp") { appVm.setWordSize(it) }
        SizeSlider("Перевод", settings.translationSizeSp, TypeRanges.TRANSLATION, "sp") { appVm.setTranslationSize(it) }
        SizeSlider("Поле ответа", settings.answerSizeSp, TypeRanges.ANSWER, "sp") { appVm.setAnswerSize(it) }
        SizeSlider("Картинка", settings.imageHeightDp, TypeRanges.IMAGE, "dp") { appVm.setImageHeight(it) }

        Spacer(Modifier.height(6.dp))
        Text("ШРИФТ", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, color = TextMuted3)
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            TypeRanges.FONT_NAMES.forEachIndexed { i, name ->
                val selected = settings.fontChoice == i
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (selected) Accent else NeutralBtnBg)
                        .clickable { appVm.setFontChoice(i) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name, fontFamily = cardFont(i), fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp,
                        color = if (selected) Color.White else Color(0xFF7A6B58), maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Готово") { onDismiss() }
    }
}

@Composable
private fun SizeSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Ink)
            Text("${local.toInt()} $unit", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 14.sp, color = Accent)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local) },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = TrackBg
            ),
        )
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
                    Text(
                        if (canContinue) "Следующие ${minOf(state.deck.size, state.remainingAfterBatch)} слов" else "Начать порцию заново",
                        fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White
                    )
                    Text(
                        if (canContinue) "Осталось ${state.remainingAfterBatch} слов в очереди" else "Очередь на сегодня пройдена",
                        fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White.copy(alpha = 0.78f)
                    )
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
