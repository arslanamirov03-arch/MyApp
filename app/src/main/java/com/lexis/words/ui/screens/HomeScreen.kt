package com.lexis.words.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lexis.words.AppViewModel
import com.lexis.words.BlockUi
import com.lexis.words.GLOBAL_BLOCK_ID
import com.lexis.words.StudyMode
import com.lexis.words.ui.Routes
import com.lexis.words.ui.components.ConfirmDeleteDialog
import com.lexis.words.ui.components.DangerButton
import com.lexis.words.ui.components.IconTile
import com.lexis.words.ui.components.PrimaryButton
import com.lexis.words.ui.components.SecondaryButton
import com.lexis.words.ui.components.SheetScaffold
import com.lexis.words.ui.components.SheetTextField
import com.lexis.words.ui.components.ThinProgressBar
import com.lexis.words.ui.components.ToastHost
import com.lexis.words.ui.theme.Accent
import com.lexis.words.ui.theme.AccentTintBg
import com.lexis.words.ui.theme.AccentTintInk
import com.lexis.words.ui.theme.AccentTintMuted
import com.lexis.words.ui.theme.BlockColors
import com.lexis.words.ui.theme.ChevronSoft
import com.lexis.words.ui.theme.Ink
import com.lexis.words.ui.theme.NeutralBtnBg
import com.lexis.words.ui.theme.NeutralBtnFg
import com.lexis.words.ui.theme.Nunito
import com.lexis.words.ui.theme.ScreenBg
import com.lexis.words.ui.theme.TextMuted1
import com.lexis.words.ui.theme.TextMuted2
import com.lexis.words.ui.theme.TextMuted3

@Composable
fun HomeScreen(nav: NavController, vm: AppViewModel) {
    val blocks by vm.blocks.collectAsState()
    val repeatCount by vm.todayRepeatCount.collectAsState()
    val repeatBlockNames by vm.todayRepeatBlockNames.collectAsState()
    val toast by vm.toast.collectAsState()

    var sheetOpen by remember { mutableStateOf(false) }
    var editingBlock by remember { mutableStateOf<BlockUi?>(null) }
    val haptics = LocalHapticFeedback.current

    Box(Modifier.fillMaxSize().background(ScreenBg)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 62.dp, start = 18.dp, end = 18.dp, bottom = 104.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Text("Lexis", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 30.sp, color = Ink)
                    IconTile(onClick = { nav.navigate(Routes.SETTINGS) }, size = 44.dp) {
                        Icon(Icons.Filled.Tune, contentDescription = "Настройки", tint = Accent, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(22.dp))
            }

            item {
                val totalWords = blocks.sumOf { it.wordCount }
                val totalNew = blocks.sumOf { it.newCount }
                val totalMastered = blocks.sumOf { it.masteredCount }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(totalWords.toString(), "слова\nвсего", Color(0xFF2E9BF0), Modifier.weight(1f))
                    StatTile(totalNew.toString(), "новых\nучить", Accent, Modifier.weight(1f))
                    StatTile(totalMastered.toString(), "освоено\nполностью", Color(0xFF10B393), Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
            }

            if (repeatCount > 0) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(AccentTintBg)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Accent),
                            contentAlignment = Alignment.Center
                        ) { Text("$repeatCount", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 19.sp, color = Color.White) }
                        Column(Modifier.weight(1f)) {
                            Text("На повторение сегодня", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = AccentTintInk)
                            Text(
                                "Открылось в 6:00" + (if (repeatBlockNames.isNotEmpty()) " · " + repeatBlockNames.joinToString(", ") else ""),
                                fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = AccentTintMuted, maxLines = 1
                            )
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Accent)
                                .clickable { nav.navigate(Routes.study(GLOBAL_BLOCK_ID, StudyMode.REPEAT)) }
                                .padding(horizontal = 16.dp, vertical = 11.dp)
                        ) { Text("Повторить", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color.White) }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("Блоки", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Ink)
                    val label = blocks.size.toString() + " " + blockWord(blocks.size)
                    Text(label, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = TextMuted3)
                }
                Spacer(Modifier.height(12.dp))
            }

            items(blocks, key = { it.id }) { block ->
                BlockRow(
                    block,
                    onClick = { nav.navigate(Routes.block(block.id)) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        editingBlock = block
                    },
                )
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
                .background(Accent)
                .clickable { sheetOpen = true },
            contentAlignment = Alignment.Center
        ) { Text("+", fontFamily = Nunito, fontWeight = FontWeight.Light, fontSize = 34.sp, color = Color.White) }

        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp)) { ToastHost(toast) }

        if (sheetOpen) {
            NewBlockSheet(vm = vm, onDismiss = { sheetOpen = false })
        }

        editingBlock?.let { block ->
            EditBlockSheet(block = block, vm = vm, onDismiss = { editingBlock = null })
        }
    }
}

private fun blockWord(n: Int): String = when {
    n % 10 == 1 && n % 100 != 11 -> "блок"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "блока"
    else -> "блоков"
}

@Composable
private fun StatTile(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .shadow(3.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 15.dp)
    ) {
        Text(value, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 27.sp, color = color)
        Spacer(Modifier.height(6.dp))
        Text(label, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = TextMuted2, lineHeight = 14.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BlockRow(block: BlockUi, onClick: () -> Unit, onLongClick: () -> Unit) {
    val color = Color(android.graphics.Color.parseColor(block.colorHex))
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(color), contentAlignment = Alignment.Center) {
                if (block.coverImagePath != null) {
                    coil.compose.AsyncImage(
                        model = java.io.File(block.coverImagePath), contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(50.dp).clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Text(block.name.take(1).uppercase(), fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(block.name, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Ink)
                Spacer(Modifier.height(4.dp))
                val listWord = if (block.listCount == 1) "список" else if (block.listCount in 2..4) "списка" else "списков"
                val wordWord = if (block.wordCount % 10 == 1 && block.wordCount % 100 != 11) "слово" else if (block.wordCount % 10 in 2..4 && block.wordCount % 100 !in 12..14) "слова" else "слов"
                Text("${block.listCount} $listWord · ${block.wordCount} $wordWord", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = TextMuted2)
                Spacer(Modifier.height(7.dp))
                ThinProgressBar(fraction = block.progressPct / 100f, color = color)
            }
            Text("›", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = ChevronSoft)
        }
    }
}

@Composable
fun NewBlockSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var colorIdx by remember { mutableStateOf(1) }

    SheetScaffold(onDismiss = onDismiss) {
        Text("Новый блок", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 22.sp, color = Ink)
        Spacer(Modifier.height(16.dp))
        Text("НАЗВАНИЕ", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextMuted3)
        Spacer(Modifier.height(7.dp))
        SheetTextField(value = name, onValueChange = { name = it }, placeholder = "например, Essen & Trinken")
        Spacer(Modifier.height(18.dp))
        Text("ЦВЕТ БЛОКА", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextMuted3)
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BlockColors.forEachIndexed { i, c ->
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(c)
                        .then(
                            if (colorIdx == i)
                                Modifier.border(2.5.dp, Ink, RoundedCornerShape(15.dp))
                            else Modifier
                        )
                        .clickable { colorIdx = i }
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        PrimaryButton("Создать блок") {
            val hex = String.format("#%06X", 0xFFFFFF and BlockColors[colorIdx].toArgbInt())
            vm.createBlock(name, hex) { onDismiss() }
        }
    }
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
)

private fun Color.toHex(): String = String.format("#%06X", 0xFFFFFF and toArgbInt())

@Composable
private fun EditBlockSheet(block: BlockUi, vm: AppViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(block.name) }
    var colorIdx by remember {
        mutableStateOf(BlockColors.indexOfFirst { it.toHex().equals(block.colorHex, ignoreCase = true) }.coerceAtLeast(0))
    }
    var confirmDelete by remember { mutableStateOf(false) }

    SheetScaffold(onDismiss = onDismiss) {
        Text("Блок «${block.name}»", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 22.sp, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "${block.listCount} списка · ${block.wordCount} слов",
            fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextMuted2
        )
        Spacer(Modifier.height(16.dp))
        Text("НАЗВАНИЕ", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextMuted3)
        Spacer(Modifier.height(7.dp))
        SheetTextField(value = name, onValueChange = { name = it }, placeholder = "название блока")
        Spacer(Modifier.height(18.dp))
        Text("ЦВЕТ БЛОКА", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextMuted3)
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BlockColors.forEachIndexed { i, c ->
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(c)
                        .then(if (colorIdx == i) Modifier.border(2.5.dp, Ink, RoundedCornerShape(15.dp)) else Modifier)
                        .clickable { colorIdx = i }
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        PrimaryButton("Сохранить", enabled = name.isNotBlank()) {
            vm.updateBlock(block.id, name, BlockColors[colorIdx].toHex()) { onDismiss() }
        }
        Spacer(Modifier.height(9.dp))
        DangerButton("Удалить блок") { confirmDelete = true }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "Удалить блок?",
            message = "«${block.name}» удалится вместе со всеми списками (${block.listCount}) и словами (${block.wordCount}) внутри. Вернуть их будет нельзя.",
            onConfirm = {
                confirmDelete = false
                vm.deleteBlock(block.id)
                onDismiss()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
