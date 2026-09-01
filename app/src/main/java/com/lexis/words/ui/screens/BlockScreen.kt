package com.lexis.words.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lexis.words.AppViewModel
import com.lexis.words.BlockUi
import com.lexis.words.ListUi
import com.lexis.words.StudyMode
import com.lexis.words.ui.Routes
import com.lexis.words.ui.components.BackChevron
import com.lexis.words.ui.components.IconTile
import com.lexis.words.ui.components.PrimaryButton
import com.lexis.words.ui.components.SheetScaffold
import com.lexis.words.ui.components.SheetTextField
import com.lexis.words.ui.components.ThinProgressBar
import com.lexis.words.ui.components.ToastHost
import com.lexis.words.ui.theme.ChevronSoft
import com.lexis.words.ui.theme.Ink
import com.lexis.words.ui.theme.NeutralBtnBg
import com.lexis.words.ui.theme.NeutralBtnFg
import com.lexis.words.ui.theme.Nunito
import com.lexis.words.ui.theme.ScreenBg
import com.lexis.words.ui.theme.TextMuted2
import com.lexis.words.ui.theme.TextMuted3

@Composable
fun BlockScreen(blockId: Long, nav: NavController, vm: AppViewModel) {
    val block by remember(blockId) { vm.blockUi(blockId) }.collectAsState(initial = null)
    val lists by remember(blockId) { vm.listsForBlock(blockId) }.collectAsState(initial = emptyList())
    val toast by vm.toast.collectAsState()
    var sheetOpen by remember { mutableStateOf(false) }

    // Paint the background while the block loads — rendering nothing for the first
    // frames made the screen flash during the navigation transition.
    val b = block
    if (b == null) {
        Box(Modifier.fillMaxSize().background(ScreenBg))
        return
    }

    val coverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) vm.setBlockCover(blockId, uri)
    }

    Box(Modifier.fillMaxSize().background(ScreenBg)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 62.dp, start = 18.dp, end = 18.dp, bottom = 104.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BackChevron(onClick = { nav.popBackStack() })
                    IconTile(onClick = { nav.navigate(Routes.SETTINGS) }) {
                        Icon(
                            Icons.Filled.Tune, contentDescription = "Настройки",
                            tint = com.lexis.words.ui.theme.Accent, modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))

                val color = Color(android.graphics.Color.parseColor(b.colorHex))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(58.dp).clip(RoundedCornerShape(19.dp)).background(color), contentAlignment = Alignment.Center) {
                        if (b.coverImagePath != null) {
                            AsyncImage(model = java.io.File(b.coverImagePath), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(19.dp)))
                        } else {
                            Text(b.name.take(1).uppercase(), fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 24.sp, color = Color.White)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(b.name, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 27.sp, color = Ink)
                        val listWord = wordForm(b.listCount, "список", "списка", "списков")
                        val wordWord = wordForm(b.wordCount, "слово", "слова", "слов")
                        Text("${b.listCount} $listWord · ${b.wordCount} $wordWord", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextMuted3)
                        Spacer(Modifier.height(9.dp))
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF6F0E7))
                                .clickable { coverLauncher.launch("image/*") }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text(
                                if (b.coverImagePath != null) "Заменить фото" else "Добавить фото",
                                fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp, color = Color(0xFF7A6B58)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .shadow(3.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .padding(16.dp)
                ) {
                    StatCol(b.newCount.toString(), "новых", com.lexis.words.ui.theme.Accent, Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(30.dp).background(com.lexis.words.ui.theme.TrackBg))
                    StatCol(b.repeatCount.toString(), "на повтор", Color(0xFF2E9BF0), Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(30.dp).background(com.lexis.words.ui.theme.TrackBg))
                    StatCol(b.masteredCount.toString(), "освоено", Color(0xFF10B393), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))

                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Ink)
                            .clickable(enabled = b.newCount > 0) { nav.navigate(Routes.study(blockId, StudyMode.NEW)) }
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Учить новые слова", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 19.sp, color = Color.White)
                            Text("${b.newCount} новых слов", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.62f))
                        }
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(com.lexis.words.ui.theme.Accent), contentAlignment = Alignment.Center) {
                            Text("›", color = Color.White, fontSize = 20.sp, fontFamily = Nunito, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .shadow(3.dp, RoundedCornerShape(22.dp))
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.White)
                            .clickable(enabled = b.repeatCount > 0) { nav.navigate(Routes.study(blockId, StudyMode.REPEAT)) }
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Повторять", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 19.sp, color = Ink)
                            val sub = if (b.repeatCount > 0) "Открылись в 6:00 · сразу тест" else "Сегодня повторов нет"
                            Text("${b.repeatCount} слов на повтор · $sub", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = TextMuted2)
                        }
                        val repeatBg = if (b.repeatCount > 0) Color(0xFF2E9BF0) else com.lexis.words.ui.theme.ChevronSoft
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(repeatBg), contentAlignment = Alignment.Center) {
                            Text("›", color = Color.White, fontSize = 20.sp, fontFamily = Nunito, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(26.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Списки", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Ink)
                    val label = "${lists.size} " + wordForm(lists.size, "список", "списка", "списков")
                    Text(label, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = TextMuted3)
                }
                Spacer(Modifier.height(12.dp))
            }

            items(lists, key = { it.id }) { l ->
                ListRow(l, color = Color(android.graphics.Color.parseColor(b.colorHex)), onClick = { nav.navigate(Routes.list(l.id)) })
                Spacer(Modifier.height(10.dp))
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 46.dp)
                .size(58.dp)
                .shadow(10.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(com.lexis.words.ui.theme.Accent)
                .clickable { sheetOpen = true },
            contentAlignment = Alignment.Center
        ) { Text("+", fontFamily = Nunito, fontWeight = FontWeight.Light, fontSize = 34.sp, color = Color.White) }

        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp)) { ToastHost(toast) }

        if (sheetOpen) {
            NewListSheet(blockName = b.name, vm = vm, blockId = blockId, onDismiss = { sheetOpen = false }, onCreated = { nav.navigate(Routes.list(it)) })
        }
    }
}

private fun wordForm(n: Int, one: String, few: String, many: String): String = when {
    n % 10 == 1 && n % 100 != 11 -> one
    n % 10 in 2..4 && n % 100 !in 12..14 -> few
    else -> many
}

@Composable
private fun StatCol(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 21.sp, color = color)
        Spacer(Modifier.height(4.dp))
        Text(label, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextMuted2)
    }
}

@Composable
private fun ListRow(l: ListUi, color: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(l.name, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 15.5.sp, color = Ink)
                Spacer(Modifier.height(4.dp))
                val wordWord = wordForm(l.wordCount, "слово", "слова", "слов")
                Text("${l.wordCount} $wordWord · ${l.newCount} новых · освоено ${l.progressPct}%", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextMuted2)
            }
            Text("›", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ChevronSoft)
        }
        Spacer(Modifier.height(10.dp))
        ThinProgressBar(fraction = l.progressPct / 100f, color = color, height = 5.dp)
    }
}

@Composable
fun NewListSheet(blockName: String, blockId: Long, vm: AppViewModel, onDismiss: () -> Unit, onCreated: (Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    val settings by vm.settings.collectAsState()

    SheetScaffold(onDismiss = onDismiss) {
        Text("Новый список", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 22.sp, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text("В блоке «$blockName» · до ${settings.wordLimitPerList} слов в списке", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextMuted2)
        Spacer(Modifier.height(16.dp))
        SheetTextField(value = name, onValueChange = { name = it }, placeholder = "например, Im Supermarkt")
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Создать список") {
            vm.createList(blockId, name) { id -> onDismiss(); onCreated(id) }
        }
    }
}
