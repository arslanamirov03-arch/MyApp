package com.lexis.words.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import androidx.navigation.NavController
import com.lexis.words.AppViewModel
import com.lexis.words.ExportKind
import com.lexis.words.WordUi
import com.lexis.words.data.WordStatus
import com.lexis.words.ui.components.BackChevron
import com.lexis.words.ui.components.IconTile
import com.lexis.words.ui.components.PrimaryButton
import com.lexis.words.ui.components.SecondaryButton
import com.lexis.words.ui.components.SheetScaffold
import com.lexis.words.ui.components.SheetTextField
import com.lexis.words.ui.components.ToastHost
import com.lexis.words.ui.theme.Accent
import com.lexis.words.ui.theme.BorderDashed
import com.lexis.words.ui.theme.DividerFaint
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

@Composable
fun ListScreen(listId: Long, nav: NavController, vm: AppViewModel) {
    val context = LocalContext.current
    val words by remember(listId) { vm.wordsForList(listId) }.collectAsState(initial = emptyList())
    val settings by vm.settings.collectAsState()
    val toast by vm.toast.collectAsState()

    var listName by remember { mutableStateOf("") }
    var blockName by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(listId) {
        val list = vm.listOnce(listId)
        listName = list?.name ?: ""
        blockName = list?.let { vm.blockOnce(it.blockId)?.name } ?: ""
    }

    var de by remember { mutableStateOf("") }
    var ru by remember { mutableStateOf("") }
    var pickedImage by remember { mutableStateOf<Uri?>(null) }
    var wordsOpen by remember { mutableStateOf(true) }
    var sheet by remember { mutableStateOf<String?>(null) } // "bulk" | "export" | null
    var editing by remember { mutableStateOf<WordUi?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) pickedImage = uri }

    Box(Modifier.fillMaxSize().background(ScreenBg)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 62.dp, start = 18.dp, end = 18.dp, bottom = 40.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BackChevron(onClick = { nav.popBackStack() })
                    Column(Modifier.weight(1f)) {
                        Text(listName, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 22.sp, color = Ink)
                        Text("$blockName · ${words.size} / ${settings.wordLimitPerList}", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextMuted3)
                    }
                    IconTile(onClick = { sheet = "export" }) {
                        Icon(Icons.Filled.Share, contentDescription = "Экспорт", tint = TextMuted2, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(18.dp))

                Column(
                    Modifier
                        .fillMaxWidth()
                        .shadow(3.dp, RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White)
                        .padding(16.dp)
                ) {
                    Text("НОВОЕ СЛОВО", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, color = TextMuted3)
                    Spacer(Modifier.height(8.dp))
                    UnderlineField(value = de, onValueChange = { de = it }, placeholder = "das Wort", fontSize = 21.sp)
                    Spacer(Modifier.height(18.dp))
                    Text("ПЕРЕВОД", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, color = TextMuted3)
                    Spacer(Modifier.height(8.dp))
                    UnderlineField(value = ru, onValueChange = { ru = it }, placeholder = "перевод", fontSize = 19.sp)

                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        DashedAction("Картинка", Modifier.weight(1f)) { pickImage.launch("image/*") }
                        DashedAction("Из буфера", Modifier.weight(1f)) {
                            val uri = clipboardImageUri(context)
                            if (uri != null) pickedImage = uri else vm.showToast("В буфере нет картинки")
                        }
                    }

                    if (pickedImage != null) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(15.dp))
                                .background(Color(0xFFFDFBF8))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp)
                        ) {
                            Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEFE7DC)))
                            Column(Modifier.weight(1f)) {
                                Text("Картинка выбрана", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Ink)
                                Text("будет показана на карточке", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = TextMuted3)
                            }
                            Text("×", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color(0xFFC0B2A0), modifier = Modifier.clickable { pickedImage = null })
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Добавить слово", enabled = de.isNotBlank() && ru.isNotBlank()) {
                        vm.addWord(listId, de, ru, pickedImage, settings.wordLimitPerList)
                        de = ""; ru = ""; pickedImage = null; wordsOpen = true
                    }
                    Spacer(Modifier.height(9.dp))
                    SecondaryButton("Вставить списком") { sheet = "bulk" }
                }
                Spacer(Modifier.height(16.dp))

                Column(
                    Modifier
                        .fillMaxWidth()
                        .shadow(3.dp, RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { wordsOpen = !wordsOpen }
                            .padding(17.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Слова в списке", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 15.5.sp, color = Ink)
                            Text("${words.size} / ${settings.wordLimitPerList} · осталось ${(settings.wordLimitPerList - words.size).coerceAtLeast(0)} мест", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextMuted2)
                        }
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(NeutralBtnBg), contentAlignment = Alignment.Center) {
                            Text(if (wordsOpen) "︿" else "⌄", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF7A6B58))
                        }
                    }
                    if (wordsOpen) {
                        Column(Modifier.fillMaxWidth()) {
                            words.forEach { w -> WordRow(w, onEdit = { editing = w }) }
                        }
                    }
                }
            }
        }

        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)) { ToastHost(toast) }

        when (sheet) {
            "bulk" -> BulkSheet(listId = listId, vm = vm, wordLimit = settings.wordLimitPerList, currentCount = words.size, onDismiss = { sheet = null })
            "export" -> ExportSheet(listId = listId, listName = listName, vm = vm, onDismiss = { sheet = null })
        }

        editing?.let { word ->
            EditWordSheet(word = word, vm = vm, onDismiss = { editing = null })
        }
    }
}

private fun clipboardImageUri(context: Context): Uri? {
    val cm = context.getSystemService<ClipboardManager>() ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    val item = clip.getItemAt(0)
    val uri = item.uri ?: return null
    return if (uri.toString().let { it.startsWith("content://") || it.startsWith("file://") }) uri else null
}

@Composable
private fun UnderlineField(value: String, onValueChange: (String) -> Unit, placeholder: String, fontSize: androidx.compose.ui.unit.TextUnit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
    ) {
        Column {
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = fontSize, color = TextMuted3)
                }
                BasicTextField(
                    value = value, onValueChange = onValueChange,
                    textStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = fontSize, color = Ink),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(11.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(DividerFaint))
        }
    }
}

@Composable
private fun DashedAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0xFFFDFBF8))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color(0xFF5E5245))
    }
}

@Composable
private fun WordRow(w: WordUi, onEdit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = 17.dp, end = 9.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (w.imagePath != null) {
            coil.compose.AsyncImage(
                model = java.io.File(w.imagePath), contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFEFE7DC))
            )
        } else {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(NeutralBtnBg))
        }
        Column(Modifier.weight(1f)) {
            Text(w.de, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, color = Ink)
            Text(w.ru, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = TextMuted2)
        }
        val (bg, fg, label) = when (w.status) {
            WordStatus.MASTERED -> Triple(SuccessBg, SuccessInk, "освоено")
            WordStatus.NEW -> Triple(com.lexis.words.ui.theme.AccentTintBg, com.lexis.words.ui.theme.AccentTintLabel, "новое")
            WordStatus.REPEAT -> Triple(InfoBg, InfoInk, "повтор")
        }
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 9.dp, vertical = 4.dp)) {
            Text(label, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, color = fg)
        }
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(NeutralBtnBg)
                .clickable(onClick = onEdit),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Edit, contentDescription = "Изменить слово", tint = Color(0xFF7A6B58), modifier = Modifier.size(15.dp))
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(DividerFaint))
}

@Composable
private fun EditWordSheet(word: WordUi, vm: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var de by remember { mutableStateOf(word.de) }
    var ru by remember { mutableStateOf(word.ru) }
    var newImage by remember { mutableStateOf<Uri?>(null) }
    var removeImage by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { newImage = uri; removeImage = false }
    }
    val currentImage: Any? = when {
        newImage != null -> newImage
        !removeImage && word.imagePath != null -> java.io.File(word.imagePath)
        else -> null
    }

    SheetScaffold(onDismiss = onDismiss) {
        Text("Изменить слово", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 22.sp, color = Ink)
        Spacer(Modifier.height(16.dp))

        Text("НЕМЕЦКОЕ СЛОВО", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, color = TextMuted3)
        Spacer(Modifier.height(7.dp))
        SheetTextField(value = de, onValueChange = { de = it }, placeholder = "das Wort")
        Spacer(Modifier.height(14.dp))
        Text("ПЕРЕВОД", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, color = TextMuted3)
        Spacer(Modifier.height(7.dp))
        SheetTextField(value = ru, onValueChange = { ru = it }, placeholder = "перевод")
        Spacer(Modifier.height(16.dp))

        Text("КАРТИНКА", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, color = TextMuted3)
        Spacer(Modifier.height(9.dp))
        if (currentImage != null) {
            coil.compose.AsyncImage(
                model = currentImage, contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFEFE7DC))
            )
            Spacer(Modifier.height(9.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            DashedAction(if (currentImage != null) "Заменить" else "Картинка", Modifier.weight(1f)) { pickImage.launch("image/*") }
            DashedAction("Из буфера", Modifier.weight(1f)) {
                val uri = clipboardImageUri(context)
                if (uri != null) { newImage = uri; removeImage = false } else vm.showToast("В буфере нет картинки")
            }
            if (currentImage != null) {
                DashedAction("Убрать", Modifier.weight(1f)) { newImage = null; removeImage = true }
            }
        }
        Spacer(Modifier.height(20.dp))

        PrimaryButton("Сохранить", enabled = de.isNotBlank() && ru.isNotBlank()) {
            vm.updateWord(word.id, de, ru, newImage, removeImage) { onDismiss() }
        }
        Spacer(Modifier.height(9.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ErrorBg)
                .clickable { confirmDelete = true }
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Удалить слово", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, color = ErrorInk)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text("Удалить слово?", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 19.sp, color = Ink)
            },
            text = {
                Text(
                    "«${word.de}» и его картинка будут удалены навсегда. Прогресс по этому слову тоже пропадёт.",
                    fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextMuted2, lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteWord(word.id)
                    onDismiss()
                }) { Text("Удалить", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = ErrorInk) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Отмена", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = TextMuted2)
                }
            },
        )
    }
}

@Composable
private fun BulkSheet(listId: Long, vm: AppViewModel, wordLimit: Int, currentCount: Int, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val parsedCount = remember(text) {
        text.split("\n").map { it.trim() }.count { line ->
            line.isNotEmpty() && Regex("\\s+[—–-]\\s+|\\t|;").split(line, 2).let { it.size == 2 && it[0].isNotBlank() && it[1].isNotBlank() }
        }
    }
    SheetScaffold(onDismiss = onDismiss) {
        Text("Вставить списком", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 22.sp, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text("Одна пара на строку. Разделитель: тире, точка с запятой или таб.", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = TextMuted2)
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(15.dp)
        ) {
            if (text.isEmpty()) {
                Text("der Löffel — ложка\ndas Messer — нож", fontFamily = Nunito, fontSize = 14.5.sp, color = TextMuted3)
            }
            BasicTextField(
                value = text, onValueChange = { text = it },
                textStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = Ink, lineHeight = 24.sp),
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(12.dp))
        val bg = if (parsedCount > 0) SuccessBg else NeutralBtnBg
        val fg = if (parsedCount > 0) SuccessInk else TextMuted2
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 14.dp, vertical = 12.dp)) {
            val remain = (wordLimit - currentCount - parsedCount).coerceAtLeast(0)
            Text(
                if (parsedCount > 0) "Найдено $parsedCount слов · останется $remain мест" else "Пока ничего не распознано",
                fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = fg
            )
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(if (parsedCount > 0) "Добавить $parsedCount слов" else "Добавить", enabled = parsedCount > 0) {
            vm.addWordsBulk(listId, text, wordLimit)
            onDismiss()
        }
    }
}

@Composable
private fun ExportSheet(listId: Long, listName: String, vm: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    fun share(uri: Uri, mime: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, listName))
    }
    SheetScaffold(onDismiss = onDismiss) {
        Text("Скачать список", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 22.sp, color = Ink)
        Spacer(Modifier.height(16.dp))
        ExportRow("PDF", "две колонки, с картинками", Color(0xFFEC4C8C)) {
            vm.exportList(listId, listName, ExportKind.PDF) { uri -> share(uri, "application/pdf") }
            onDismiss()
        }
        Spacer(Modifier.height(9.dp))
        ExportRow("DOC", "Word, можно править", Color(0xFF2E9BF0)) {
            vm.exportList(listId, listName, ExportKind.DOCX) { uri -> share(uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document") }
            onDismiss()
        }
        Spacer(Modifier.height(9.dp))
        ExportRow("TXT", "слово — перевод, по строке", Color(0xFF7A6B58)) {
            vm.exportList(listId, listName, ExportKind.TXT) { uri -> share(uri, "text/plain") }
            onDismiss()
        }
    }
}

@Composable
private fun ExportRow(badge: String, desc: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(color), contentAlignment = Alignment.Center) {
            Text(badge, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(if (badge == "DOC") "Word" else badge, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Ink)
            Text(desc, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextMuted2)
        }
    }
}
