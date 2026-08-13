package app.hoerpraxis.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.hoerpraxis.data.Word
import app.hoerpraxis.ui.library.formatDuration
import app.hoerpraxis.ui.theme.AmberTint
import app.hoerpraxis.ui.theme.BlueTint
import app.hoerpraxis.ui.theme.GoogleAmber
import app.hoerpraxis.ui.theme.GoogleBlue

private val SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

/** Words grouped into a paragraph when the speaker pauses. */
private data class Paragraph(val firstIndex: Int, val words: List<Word>)

private fun buildParagraphs(words: List<Word>): List<Paragraph> {
    if (words.isEmpty()) return emptyList()
    val result = ArrayList<Paragraph>()
    var start = 0
    for (i in 1 until words.size) {
        val gap = words[i].s - words[i - 1].e
        val sentenceEnd = words[i - 1].w.lastOrNull() in listOf('.', '!', '?')
        if (gap > 900 || (sentenceEnd && gap > 350)) {
            result.add(Paragraph(start, words.subList(start, i)))
            start = i
        }
    }
    result.add(Paragraph(start, words.subList(start, words.size)))
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(itemId: String, onBack: () -> Unit) {
    val vm: PlayerViewModel = viewModel()
    LaunchedEffect(itemId) { vm.load(itemId) }

    val item by vm.item.collectAsState()
    val words by vm.words.collectAsState()
    val positionMs by vm.positionMs.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val speed by vm.speed.collectAsState()
    val mode by vm.mode.collectAsState()
    val loopRange by vm.loopRange.collectAsState()
    val loopAnchor by vm.loopAnchor.collectAsState()
    val highlightEnabled by vm.highlightEnabled.collectAsState()

    var editingWordIndex by remember { mutableStateOf<Int?>(null) }
    var editingFullText by remember { mutableStateOf(false) }
    var loopMenuOpen by remember { mutableStateOf(false) }

    val paragraphs = remember(words) { buildParagraphs(words) }
    val currentWordIndex by remember(words) {
        derivedStateOf { vm.currentWordIndex(positionMs) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(item?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    Box {
                        ModeIconButton(
                            icon = Icons.Filled.RepeatOne,
                            contentDescription = "Разбор",
                            active = mode == TranscriptMode.LOOP_WORD || mode == TranscriptMode.LOOP_RANGE,
                            activeColor = GoogleAmber,
                            onClick = { loopMenuOpen = true },
                        )
                        DropdownMenu(
                            expanded = loopMenuOpen,
                            onDismissRequest = { loopMenuOpen = false },
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Слово", fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "Нажмите слово — оно повторяется бесконечно",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    vm.setMode(TranscriptMode.LOOP_WORD)
                                    loopMenuOpen = false
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Предложение", fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "Отметьте начало и конец — повторяется весь кусок",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    vm.setMode(TranscriptMode.LOOP_RANGE)
                                    loopMenuOpen = false
                                },
                            )
                        }
                    }
                    ModeIconButton(
                        icon = Icons.Filled.Edit,
                        contentDescription = "Редактировать",
                        active = mode == TranscriptMode.EDIT,
                        activeColor = GoogleBlue,
                        onClick = { vm.setMode(TranscriptMode.EDIT) },
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            ModeBanner(
                mode = mode,
                hasLoop = loopRange != null,
                hasAnchor = loopAnchor != null,
                onRestartSelection = { vm.restartSelection() },
                onEditFullText = { editingFullText = true },
                onClose = { vm.setMode(TranscriptMode.LISTEN) },
            )

            TranscriptView(
                modifier = Modifier.weight(1f),
                paragraphs = paragraphs,
                currentWordIndex = if (highlightEnabled) currentWordIndex else -1,
                followPlayback = highlightEnabled && isPlaying,
                loopRange = loopRange,
                loopAnchor = loopAnchor,
                editMode = mode == TranscriptMode.EDIT,
                onWordTap = { index ->
                    if (mode == TranscriptMode.EDIT) editingWordIndex = index
                    else vm.onWordTap(index)
                },
            )

            PlayerControls(
                positionMs = positionMs,
                durationMs = item?.durationMs ?: 0L,
                isPlaying = isPlaying,
                speed = speed,
                onSeek = { vm.seekTo(it) },
                onSeekBy = { vm.seekBy(it) },
                onTogglePlay = { vm.togglePlay() },
                onSpeed = { vm.setSpeed(it) },
            )
        }
    }

    editingWordIndex?.let { index ->
        val word = words.getOrNull(index)
        if (word != null) {
            WordEditDialog(
                initial = word.w,
                onDismiss = { editingWordIndex = null },
                onSave = { text ->
                    vm.editWord(index, text)
                    editingWordIndex = null
                },
            )
        } else {
            editingWordIndex = null
        }
    }

    if (editingFullText) {
        FullTextEditDialog(
            initial = words.joinToString(" ") { it.w },
            onDismiss = { editingFullText = false },
            onSave = { text ->
                vm.editFullText(text)
                editingFullText = false
            },
        )
    }
}

@Composable
private fun ModeIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (active) activeColor else Color.Transparent,
            contentColor = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
private fun ModeBanner(
    mode: TranscriptMode,
    hasLoop: Boolean,
    hasAnchor: Boolean,
    onRestartSelection: () -> Unit,
    onEditFullText: () -> Unit,
    onClose: () -> Unit,
) {
    AnimatedVisibility(
        visible = mode != TranscriptMode.LISTEN,
        enter = expandVertically(tween(300)) + fadeIn(tween(300)),
        exit = shrinkVertically(tween(300)) + fadeOut(tween(300)),
    ) {
        val loopMode = mode == TranscriptMode.LOOP_WORD || mode == TranscriptMode.LOOP_RANGE
        Surface(
            shape = MaterialTheme.shapes.large,
            color = if (loopMode) AmberTint else BlueTint,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 4.dp, 16.dp, 8.dp),
        ) {
            Row(
                Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        mode == TranscriptMode.LOOP_WORD && hasLoop ->
                            "Слово повторяется — нажмите другое, чтобы переключиться"
                        mode == TranscriptMode.LOOP_WORD ->
                            "Разбор по слову: нажмите любое слово"
                        mode == TranscriptMode.LOOP_RANGE && hasLoop ->
                            "Кусок повторяется — нажимайте слова, чтобы менять конец"
                        mode == TranscriptMode.LOOP_RANGE && hasAnchor ->
                            "Теперь нажмите последнее слово куска"
                        mode == TranscriptMode.LOOP_RANGE ->
                            "Разбор по предложению: нажмите первое слово"
                        else -> "Нажмите слово, чтобы исправить его"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (loopMode && (hasLoop || hasAnchor)) {
                    TextButton(onClick = onRestartSelection) { Text("Заново") }
                }
                if (mode == TranscriptMode.EDIT) {
                    TextButton(onClick = onEditFullText) { Text("Весь текст") }
                }
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Закрыть режим",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TranscriptView(
    modifier: Modifier,
    paragraphs: List<Paragraph>,
    currentWordIndex: Int,
    followPlayback: Boolean,
    loopRange: LoopRange?,
    loopAnchor: Int?,
    editMode: Boolean,
    onWordTap: (Int) -> Unit,
) {
    val listState = rememberLazyListState()

    // Follow the speaker: keep the paragraph with the current word in view.
    val currentParagraph = remember(paragraphs, currentWordIndex) {
        if (currentWordIndex < 0) -1
        else paragraphs.indexOfLast { it.firstIndex <= currentWordIndex }
    }
    LaunchedEffect(currentParagraph, followPlayback) {
        if (followPlayback && currentParagraph >= 0) {
            val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == currentParagraph }
            if (!visible) listState.animateScrollToItem(currentParagraph)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(paragraphs) { _, paragraph ->
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                paragraph.words.forEachIndexed { local, word ->
                    val global = paragraph.firstIndex + local
                    WordChip(
                        word = word.w,
                        isCurrent = global == currentWordIndex,
                        inLoop = loopRange != null &&
                            global >= loopRange.startIndex && global <= loopRange.endIndex,
                        isAnchor = global == loopAnchor,
                        editMode = editMode,
                        onTap = { onWordTap(global) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WordChip(
    word: String,
    isCurrent: Boolean,
    inLoop: Boolean,
    isAnchor: Boolean,
    editMode: Boolean,
    onTap: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = when {
            isCurrent -> GoogleBlue
            isAnchor -> GoogleAmber
            inLoop -> AmberTint
            editMode -> BlueTint.copy(alpha = 0.55f)
            else -> Color.Transparent
        },
        animationSpec = tween(160),
        label = "wordBg",
    )
    val textColor by animateColorAsState(
        targetValue = if (isCurrent || isAnchor) Color.White else MaterialTheme.colorScheme.onBackground,
        animationSpec = tween(160),
        label = "wordFg",
    )
    Text(
        text = word,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onTap)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun PlayerControls(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    speed: Float,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onSpeed: (Float) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp, 14.dp, 24.dp, 22.dp)) {
            var dragValue by remember { mutableStateOf<Float?>(null) }
            Slider(
                value = dragValue ?: if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                onValueChange = { dragValue = it },
                onValueChangeFinished = {
                    dragValue?.let { onSeek((it * durationMs).toLong()) }
                    dragValue = null
                },
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    formatDuration(dragValue?.let { (it * durationMs).toLong() } ?: positionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatDuration(durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SpeedButton(speed = speed, onSpeed = onSpeed)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onSeekBy(-5000) }) {
                    Icon(
                        Icons.Filled.Replay5,
                        contentDescription = "Назад 5 секунд",
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                FilledIconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(68.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = GoogleBlue,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Слушать",
                        modifier = Modifier.size(36.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                IconButton(onClick = { onSeekBy(5000) }) {
                    Icon(
                        Icons.Filled.Forward5,
                        contentDescription = "Вперёд 5 секунд",
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(56.dp))
            }
        }
    }
}

@Composable
private fun SpeedButton(speed: Float, onSpeed: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = CircleShape,
            color = BlueTint,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { open = true },
        ) {
            Text(
                text = formatSpeed(speed),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = MaterialTheme.shapes.medium,
        ) {
            SPEEDS.forEach { s ->
                DropdownMenuItem(
                    text = {
                        Text(
                            formatSpeed(s),
                            fontWeight = if (s == speed) FontWeight.Bold else FontWeight.Normal,
                            color = if (s == speed) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = { onSpeed(s); open = false },
                )
            }
        }
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}×"
    else "${"%.2f".format(speed).trimEnd('0').trimEnd(',', '.')}×"

@Composable
private fun WordEditDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("Исправить слово") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                supportingText = { Text("Пустое поле удалит слово") },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun FullTextEditDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text("Редактировать текст") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().height(320.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
