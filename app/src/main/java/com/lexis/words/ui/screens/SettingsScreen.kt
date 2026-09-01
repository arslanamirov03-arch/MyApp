package com.lexis.words.ui.screens

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lexis.words.AppViewModel
import com.lexis.words.ui.Routes
import com.lexis.words.ui.components.BackChevron
import com.lexis.words.ui.theme.Accent
import com.lexis.words.ui.theme.AccentTintBg
import com.lexis.words.ui.theme.AccentTintInk
import com.lexis.words.ui.theme.Ink
import com.lexis.words.ui.theme.NeutralBtnBg
import com.lexis.words.ui.theme.Nunito
import com.lexis.words.ui.theme.ScreenBg
import com.lexis.words.ui.theme.TextMuted2
import com.lexis.words.ui.theme.TextMuted3

@Composable
fun SettingsScreen(nav: NavController, vm: AppViewModel) {
    val settings by vm.settings.collectAsState()
    var customBatch by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(ScreenBg)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 62.dp, start = 18.dp, end = 18.dp, bottom = 40.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BackChevron(onClick = { nav.popBackStack() })
                    Text("Настройки", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 26.sp, color = Ink)
                }
                Spacer(Modifier.height(22.dp))

                Column(Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(18.dp)) {
                    Text("Интервалы повторения", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Ink)
                    Text("Через сколько дней слово вернётся", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = TextMuted2)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(1, 3, 7, 14, 30).forEach { d ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(AccentTintBg).padding(vertical = 13.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("$d", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 17.sp, color = Accent)
                                Text(dayWord(d), fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = AccentTintInk)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color(0xFFFDFBF8)).padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Ink), contentAlignment = Alignment.Center) {
                            Text("6:00", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 12.5.sp, color = Color.White)
                        }
                        Column {
                            Text("Слова возвращаются ровно в 6:00", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, color = Ink)
                            Text("все повторы за день открываются утром, одной очередью", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = TextMuted2)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Column(Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color.White)) {
                    ToggleRow("Картинки на карточках", "если картинка добавлена к слову", settings.imagesEnabled) { vm.setImagesEnabled(it) }
                    Divider()
                    ToggleRow("Звук ответа", "звуковой сигнал при проверке", settings.soundEnabled) { vm.setSoundEnabled(it) }
                    Divider()
                    ToggleRow("Вибрация", "короткий отклик при ответе", settings.vibrationEnabled) { vm.setVibrationEnabled(it) }
                }
                Spacer(Modifier.height(12.dp))

                Column(Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color.White)) {
                    Column(Modifier.padding(17.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Слов за один подход", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, color = Ink)
                            Text("${settings.batchSize}", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 16.sp, color = Accent)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf(10, 20, 50).forEach { size ->
                                val selected = customBatch.isEmpty() && settings.batchSize == size
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(13.dp))
                                        .background(if (selected) Accent else NeutralBtnBg)
                                        .clickable { customBatch = ""; vm.setBatchSize(size) }
                                        .padding(vertical = 11.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text("$size", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = if (selected) Color.White else Color(0xFF7A6B58)) }
                            }
                            Box(
                                Modifier.width(70.dp).clip(RoundedCornerShape(13.dp)).background(Color.White)
                                    .padding(horizontal = 12.dp, vertical = 11.dp)
                            ) {
                                if (customBatch.isEmpty()) Text("своё", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = TextMuted3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                BasicTextField(
                                    value = customBatch,
                                    onValueChange = { v ->
                                        val digits = v.filter { it.isDigit() }
                                        customBatch = digits
                                        digits.toIntOrNull()?.let { if (it > 0) vm.setBatchSize(it) }
                                    },
                                    textStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Ink, textAlign = TextAlign.Center),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    Divider()
                    SimpleRow("Язык интерфейса", "Русский")
                    Divider()
                    SimpleRow("Лимит слов в списке", "${settings.wordLimitPerList}")
                }
                Spacer(Modifier.height(22.dp))

                Text("СОХРАННОСТЬ ДАННЫХ", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, color = TextMuted3, letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(10.dp))
                Column(Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color.White)) {
                    NavRow("Резервная копия", "Всё целиком: слова, картинки, интервалы, ключ") { nav.navigate(Routes.BACKUP) }
                    Divider()
                    NavRow("О приложении и подпись", "Keystore, alias, package · только чтение") { nav.navigate(Routes.ABOUT) }
                }
            }
        }
    }
}

private fun dayWord(n: Int): String = when {
    n % 10 == 1 && n % 100 != 11 -> "день"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "дня"
    else -> "дней"
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFFAF5EE)))

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle(!checked) }.padding(17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, color = Ink)
            Text(subtitle, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextMuted2)
        }
        val thumbX by animateDpAsState(if (checked) 23.dp else 3.dp, label = "thumb")
        Box(
            Modifier.size(width = 50.dp, height = 30.dp).clip(RoundedCornerShape(15.dp)).background(if (checked) Accent else com.lexis.words.ui.theme.BorderDashed)
        ) {
            Box(Modifier.padding(start = thumbX, top = 3.dp).size(24.dp).clip(RoundedCornerShape(12.dp)).background(Color.White))
        }
    }
}

@Composable
private fun SimpleRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, color = Ink)
        Text(value, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = TextMuted2)
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(17.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, color = Ink)
            Text(subtitle, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextMuted2)
        }
        Text("›", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = com.lexis.words.ui.theme.ChevronSoft)
    }
}
