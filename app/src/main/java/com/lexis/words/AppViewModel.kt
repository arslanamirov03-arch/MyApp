package com.lexis.words

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lexis.words.data.BlockEntity
import com.lexis.words.data.SpacedRepetition
import com.lexis.words.data.WordEntity
import com.lexis.words.data.WordStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BlockUi(
    val id: Long, val name: String, val colorHex: String, val coverImagePath: String?,
    val listCount: Int, val wordCount: Int,
    val newCount: Int, val repeatCount: Int, val masteredCount: Int,
) {
    val progressPct: Int get() = if (wordCount == 0) 0 else (masteredCount * 100 / wordCount)
}

data class ListUi(val id: Long, val blockId: Long, val name: String, val wordCount: Int, val newCount: Int, val progressPct: Int)

data class WordUi(val id: Long, val de: String, val ru: String, val imagePath: String?, val status: WordStatus, val intervalLabel: String?)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val lexisApp get() = getApplication<LexisApp>()
    private val repo get() = lexisApp.repository

    val settings = lexisApp.settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.lexis.words.data.AppSettings())

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    private var toastJob: Job? = null

    /** Fades on its own after a couple of seconds; a swipe or tap clears it sooner. */
    fun showToast(text: String) {
        toastJob?.cancel()
        _toast.value = text
        toastJob = viewModelScope.launch {
            delay(2200)
            _toast.value = null
        }
    }

    fun clearToast() {
        toastJob?.cancel()
        _toast.value = null
    }

    val blocks: StateFlow<List<BlockUi>> = combine(
        repo.blockDao.observeAll(), repo.wordListDao.observeAll(), repo.wordDao.observeAll()
    ) { blocks, lists, words ->
        blocks.map { b ->
            val blockListIds = lists.filter { it.blockId == b.id }.map { it.id }.toSet()
            val blockWords = words.filter { it.listId in blockListIds }
            toBlockUi(b, lists.count { it.blockId == b.id }, blockWords)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun toBlockUi(b: BlockEntity, listCount: Int, blockWords: List<WordEntity>): BlockUi {
        val newCount = blockWords.count { SpacedRepetition.isNew(it) }
        val repeatCount = blockWords.count { SpacedRepetition.isDueNow(it) }
        val masteredCount = blockWords.count { it.mastered }
        return BlockUi(b.id, b.name, b.colorHex, b.coverImagePath, listCount, blockWords.size, newCount, repeatCount, masteredCount)
    }

    fun blockUi(blockId: Long): Flow<BlockUi?> = combine(
        repo.blockDao.observeAll(), repo.wordListDao.observeAll(), repo.wordDao.observeAll()
    ) { blocks, lists, words ->
        val b = blocks.find { it.id == blockId } ?: return@combine null
        val blockListIds = lists.filter { it.blockId == b.id }.map { it.id }.toSet()
        toBlockUi(b, blockListIds.size, words.filter { it.listId in blockListIds })
    }

    fun listsForBlock(blockId: Long): Flow<List<ListUi>> = combine(
        repo.wordListDao.observeForBlock(blockId), repo.wordDao.observeAll()
    ) { lists, words ->
        lists.map { l ->
            val lw = words.filter { it.listId == l.id }
            val newCount = lw.count { SpacedRepetition.isNew(it) }
            val pct = if (lw.isEmpty()) 0 else (lw.count { it.mastered } * 100 / lw.size)
            ListUi(l.id, l.blockId, l.name, lw.size, newCount, pct)
        }
    }

    fun wordsForList(listId: Long): Flow<List<WordUi>> = repo.wordDao.observeForList(listId).map { words ->
        words.map { w ->
            WordUi(w.id, w.de, w.ru, w.imagePath, SpacedRepetition.statusOf(w), if (w.stage >= 0) SpacedRepetition.intervalLabel(w.stage) else null)
        }
    }

    /** Today's review summary across all blocks, for the home screen banner. */
    val todayRepeatCount: StateFlow<Int> = repo.wordDao.observeAll()
        .map { words -> words.count { SpacedRepetition.isDueNow(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todayRepeatBlockNames: StateFlow<List<String>> = combine(
        repo.blockDao.observeAll(), repo.wordListDao.observeAll(), repo.wordDao.observeAll()
    ) { blocks, lists, words ->
        val dueListIds = words.filter { SpacedRepetition.isDueNow(it) }.map { it.listId }.toSet()
        val dueBlockIds = lists.filter { it.id in dueListIds }.map { it.blockId }.toSet()
        blocks.filter { it.id in dueBlockIds }.map { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createBlock(name: String, colorHex: String, onDone: () -> Unit) = viewModelScope.launch {
        if (name.isBlank()) { showToast("Введите название блока"); return@launch }
        repo.createBlock(name.trim(), colorHex)
        showToast("Блок «${name.trim()}» создан")
        onDone()
    }

    fun createList(blockId: Long, name: String, onDone: (Long) -> Unit) = viewModelScope.launch {
        if (name.isBlank()) { showToast("Введите название списка"); return@launch }
        val id = repo.createList(blockId, name.trim())
        showToast("Список «${name.trim()}» создан")
        onDone(id)
    }

    fun addWord(listId: Long, de: String, ru: String, imageUri: Uri?, wordLimit: Int) = viewModelScope.launch {
        if (de.isBlank() || ru.isBlank()) { showToast("Заполните слово и перевод"); return@launch }
        val count = repo.wordDao.countForList(listId)
        if (count >= wordLimit) { showToast("В списке уже $wordLimit слов"); return@launch }
        val path = imageUri?.let { repo.importImage(it) }
        repo.addWord(listId, de, ru, path)
        showToast("«${de.trim()}» добавлено")
    }

    fun addWordsBulk(listId: Long, text: String, wordLimit: Int) = viewModelScope.launch {
        val parsed = repo.parseBulkText(text)
        if (parsed.isEmpty()) { showToast("Ничего не распознано"); return@launch }
        val existing = repo.wordDao.countForList(listId)
        val allowed = (wordLimit - existing).coerceAtLeast(0)
        val toAdd = parsed.take(allowed)
        repo.addWordsBulk(listId, toAdd)
        showToast("${toAdd.size} слов добавлено")
    }

    fun updateBlock(id: Long, name: String, colorHex: String, onDone: () -> Unit) = viewModelScope.launch {
        if (name.isBlank()) { showToast("Введите название блока"); return@launch }
        val block = repo.blockDao.getById(id) ?: return@launch
        repo.blockDao.update(block.copy(name = name.trim(), colorHex = colorHex))
        showToast("Блок «${name.trim()}» сохранён")
        onDone()
    }

    /** Deletes a block with everything inside it — lists and words cascade, images are cleaned up. */
    fun deleteBlock(id: Long) = viewModelScope.launch {
        val block = repo.blockDao.getById(id) ?: return@launch
        repo.wordDao.getForBlock(id).mapNotNull { it.imagePath }.forEach { path ->
            runCatching { java.io.File(path).delete() }
        }
        block.coverImagePath?.let { path -> runCatching { java.io.File(path).delete() } }
        repo.blockDao.delete(block)
        showToast("Блок «${block.name}» удалён")
    }

    fun updateList(id: Long, name: String, onDone: () -> Unit) = viewModelScope.launch {
        if (name.isBlank()) { showToast("Введите название списка"); return@launch }
        val list = repo.wordListDao.getById(id) ?: return@launch
        repo.wordListDao.update(list.copy(name = name.trim()))
        showToast("Список «${name.trim()}» сохранён")
        onDone()
    }

    fun deleteList(id: Long) = viewModelScope.launch {
        val list = repo.wordListDao.getById(id) ?: return@launch
        repo.wordDao.getForList(id).mapNotNull { it.imagePath }.forEach { path ->
            runCatching { java.io.File(path).delete() }
        }
        repo.wordListDao.delete(list)
        showToast("Список «${list.name}» удалён")
    }

    /**
     * Edits an existing word. Learning progress (stage, due date, counters) is kept —
     * only the text and picture change. Replacing or clearing the picture also deletes
     * the old file, so storage doesn't fill up with orphans.
     */
    fun updateWord(id: Long, de: String, ru: String, newImage: Uri?, removeImage: Boolean, onDone: () -> Unit) = viewModelScope.launch {
        if (de.isBlank() || ru.isBlank()) { showToast("Заполните слово и перевод"); return@launch }
        val word = repo.wordDao.getById(id) ?: return@launch
        val newPath = when {
            newImage != null -> repo.importImage(newImage)
            removeImage -> null
            else -> word.imagePath
        }
        if (word.imagePath != null && word.imagePath != newPath) {
            runCatching { java.io.File(word.imagePath).delete() }
        }
        repo.wordDao.update(word.copy(de = de.trim(), ru = ru.trim(), imagePath = newPath))
        showToast("«${de.trim()}» сохранено")
        onDone()
    }

    fun deleteWord(id: Long) = viewModelScope.launch {
        val word = repo.wordDao.getById(id) ?: return@launch
        word.imagePath?.let { path -> runCatching { java.io.File(path).delete() } }
        repo.wordDao.delete(word)
        showToast("«${word.de}» удалено")
    }

    fun setBlockCover(blockId: Long, uri: Uri?) = viewModelScope.launch {
        val block = repo.blockDao.getById(blockId) ?: return@launch
        val path = uri?.let { repo.importImage(it) }
        repo.blockDao.update(block.copy(coverImagePath = path))
    }

    fun setBatchSize(size: Int) = viewModelScope.launch { lexisApp.settingsStore.setBatchSize(size) }
    fun setWordSize(v: Float) = viewModelScope.launch { lexisApp.settingsStore.setWordSize(v) }
    fun setTranslationSize(v: Float) = viewModelScope.launch { lexisApp.settingsStore.setTranslationSize(v) }
    fun setAnswerSize(v: Float) = viewModelScope.launch { lexisApp.settingsStore.setAnswerSize(v) }
    fun setImageHeight(v: Float) = viewModelScope.launch { lexisApp.settingsStore.setImageHeight(v) }
    fun setFontChoice(v: Int) = viewModelScope.launch { lexisApp.settingsStore.setFontChoice(v) }
    fun resetTypography() = viewModelScope.launch { lexisApp.settingsStore.resetTypography() }
    fun setImagesEnabled(v: Boolean) = viewModelScope.launch { lexisApp.settingsStore.setImagesEnabled(v) }
    fun setSoundEnabled(v: Boolean) = viewModelScope.launch { lexisApp.settingsStore.setSoundEnabled(v) }
    fun setVibrationEnabled(v: Boolean) = viewModelScope.launch { lexisApp.settingsStore.setVibrationEnabled(v) }

    fun exportList(listId: Long, listName: String, kind: ExportKind, onReady: (Uri) -> Unit) = viewModelScope.launch {
        val words = repo.wordDao.getForList(listId)
        val file = when (kind) {
            ExportKind.PDF -> lexisApp.exportManager.exportPdf(listName, words)
            ExportKind.DOCX -> lexisApp.exportManager.exportDocx(listName, words)
            ExportKind.TXT -> lexisApp.exportManager.exportTxt(listName, words)
        }
        onReady(lexisApp.exportManager.uriFor(file))
        showToast(
            when (kind) {
                ExportKind.PDF -> "PDF сохранён"
                ExportKind.DOCX -> "Word-файл сохранён"
                ExportKind.TXT -> "Текстовый файл сохранён"
            }
        )
    }

    fun createBackup(onReady: (Uri, String) -> Unit) = viewModelScope.launch {
        val file = lexisApp.backupManager.createBackup()
        lexisApp.settingsStore.setLastBackupAt(System.currentTimeMillis())
        val sizeMb = "%.1f".format(file.length() / 1024.0 / 1024.0)
        onReady(lexisApp.backupManager.uriFor(file), sizeMb)
        showToast("Резервная копия сохранена · $sizeMb МБ")
    }

    fun restoreBackup(uri: Uri) = viewModelScope.launch {
        try {
            lexisApp.backupManager.restoreBackup(uri)
            showToast("Данные восстановлены из копии")
        } catch (e: Exception) {
            showToast(e.message ?: "Не удалось восстановить копию")
        }
    }

    fun answerWord(word: WordEntity, correct: Boolean) = viewModelScope.launch {
        repo.wordDao.update(SpacedRepetition.answer(word, correct))
    }

    suspend fun wordById(id: Long): WordEntity? = repo.wordDao.getById(id)
    suspend fun wordsForListOnce(listId: Long): List<WordEntity> = repo.wordDao.getForList(listId)
    suspend fun wordsForBlockOnce(blockId: Long): List<WordEntity> = repo.wordDao.getForBlock(blockId)
    suspend fun listOnce(listId: Long) = repo.wordListDao.getById(listId)
    suspend fun blockOnce(blockId: Long) = repo.blockDao.getById(blockId)
}

enum class ExportKind { PDF, DOCX, TXT }
