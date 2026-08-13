package app.hoerpraxis.ui.library

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hoerpraxis.data.AudioItem
import app.hoerpraxis.data.ItemStatus
import app.hoerpraxis.data.Repository
import app.hoerpraxis.transcribe.TranscribeService
import app.hoerpraxis.ui.theme.BlueTint
import app.hoerpraxis.ui.theme.GoogleBlue
import app.hoerpraxis.ui.theme.GreenTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenItem: (String) -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val library by repo.library.collectAsState()
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<AudioItem?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            scope.launch {
                withContext(Dispatchers.IO) { importFile(context, repo, uri) }
                importing = false
                TranscribeService.start(context)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Hörpraxis") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Настройки")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(arrayOf("audio/*", "video/*")) },
                containerColor = GoogleBlue,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Добавить аудио") },
            )
        },
    ) { padding ->
        if (library.items.isEmpty() && !importing) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (importing) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = BlueTint,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                                Spacer(Modifier.width(16.dp))
                                Text("Импорт файла…", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                items(library.items.sortedByDescending { it.addedAt }, key = { it.id }) { item ->
                    LibraryCard(
                        item = item,
                        onClick = { if (item.status == ItemStatus.READY) onOpenItem(item.id) },
                        onLongClick = { itemToDelete = item },
                        onRetry = {
                            scope.launch {
                                repo.updateItem(item.id) {
                                    it.copy(status = ItemStatus.PENDING, progress = 0, errorMessage = null)
                                }
                                TranscribeService.start(context)
                            }
                        },
                    )
                }
            }
        }
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            shape = MaterialTheme.shapes.large,
            title = { Text("Удалить запись?") },
            text = { Text("«${item.title}» и её транскрипция будут удалены.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.removeItem(item.id) }
                    itemToDelete = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Отмена") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryCard(
    item: AudioItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRetry: () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = item.progress / 100f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "progress",
    )
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (item.status == ItemStatus.READY) GreenTint else BlueTint),
                contentAlignment = Alignment.Center,
            ) {
                when (item.status) {
                    ItemStatus.READY -> Icon(
                        Icons.Filled.Headphones, null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    ItemStatus.ERROR -> Icon(
                        Icons.Outlined.ErrorOutline, null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    else -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                when (item.status) {
                    ItemStatus.PENDING -> StatusText("В очереди…")
                    ItemStatus.MODEL_DOWNLOAD -> StatusText("Загрузка модели (один раз) · ${item.progress}%")
                    ItemStatus.DECODING -> StatusText("Чтение аудио · ${item.progress}%")
                    ItemStatus.TRANSCRIBING -> StatusText("Распознавание · ${item.progress}%")
                    ItemStatus.READY -> StatusText(formatDuration(item.durationMs))
                    ItemStatus.ERROR -> Text(
                        item.errorMessage ?: "Ошибка",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (item.status == ItemStatus.ERROR) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = onRetry,
                            shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Повторить")
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "удерживайте карточку, чтобы удалить",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (item.status == ItemStatus.MODEL_DOWNLOAD || item.status == ItemStatus.DECODING ||
                    item.status == ItemStatus.TRANSCRIBING
                ) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(BlueTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.GraphicEq, null,
                tint = GoogleBlue,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("Пока пусто", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Нажмите «Добавить аудио» и выберите MP3 или видео — " +
                "приложение распознает немецкую речь и покажет текст.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private fun importFile(context: android.content.Context, repo: Repository, uri: Uri) {
    val id = UUID.randomUUID().toString()
    var displayName = "Аудио"
    var sourceSize = -1L
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) sourceSize = cursor.getLong(sizeIdx)
        }
    }
    val ext = displayName.substringAfterLast('.', "dat")
    val fileName = "$id.$ext"
    val target = File(repo.audioDir, fileName)

    var copyError: String? = null
    try {
        context.contentResolver.openInputStream(uri)!!.use { input ->
            target.outputStream().use { output -> input.copyTo(output, 1 shl 20) }
        }
        // A short copy means the source was a cloud placeholder or the read was cut off.
        if (sourceSize > 0 && target.length() != sourceSize) {
            copyError = "Файл скопировался не полностью (${target.length() / 1024} КБ из " +
                "${sourceSize / 1024} КБ). Убедитесь, что он скачан на телефон, и добавьте снова."
        } else if (target.length() == 0L) {
            copyError = "Файл пустой. Так бывает с файлами из облака, которые не скачаны на телефон."
        }
    } catch (t: Throwable) {
        copyError = "Не удалось прочитать файл: ${t.message ?: "нет доступа"}"
    }

    var durationMs = 0L
    runCatching {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(target.absolutePath)
        durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        mmr.release()
    }
    kotlinx.coroutines.runBlocking {
        repo.addItem(
            AudioItem(
                id = id,
                title = displayName.substringBeforeLast('.'),
                fileName = fileName,
                durationMs = durationMs,
                addedAt = System.currentTimeMillis(),
                status = if (copyError != null) ItemStatus.ERROR else ItemStatus.PENDING,
                errorMessage = copyError,
            )
        )
    }
}

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
