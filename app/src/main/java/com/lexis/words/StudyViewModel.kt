package com.lexis.words

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lexis.words.data.SpacedRepetition
import com.lexis.words.data.WordEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StudyMode { NEW, REPEAT }
enum class StudyPhase { LEARN, TEST }
enum class AnswerState { ASKING, RIGHT, WRONG }

/** blockId sent to a study session to mean "every block" — used by the home screen's "Повторить". */
const val GLOBAL_BLOCK_ID = -1L

data class StudyUiState(
    val loading: Boolean = true,
    val blockId: Long = 0,
    val blockName: String = "",
    val mode: StudyMode = StudyMode.NEW,
    val phase: StudyPhase = StudyPhase.TEST,
    val deck: List<WordEntity> = emptyList(),
    val learnIndex: Int = 0,
    val testIndex: Int = 0,
    val input: String = "",
    val answerState: AnswerState = AnswerState.ASKING,
    val rightCount: Int = 0,
    val wrongCount: Int = 0,
    val batchIndex: Int = 1,
    val batchTotal: Int = 1,
    val poolAtBatchStart: Int = 0,
    val remainingAfterBatch: Int = 0,
    val finished: Boolean = false,
) {
    val isGlobal get() = blockId == GLOBAL_BLOCK_ID
}

class StudyViewModel(app: Application) : AndroidViewModel(app) {
    private val repo get() = getApplication<LexisApp>().repository
    private val settingsStore get() = getApplication<LexisApp>().settingsStore

    private val _state = MutableStateFlow(StudyUiState())
    val state: StateFlow<StudyUiState> = _state.asStateFlow()

    fun start(blockId: Long, mode: StudyMode) = viewModelScope.launch {
        val blockName = if (blockId == GLOBAL_BLOCK_ID) "Повторение" else (repo.blockDao.getById(blockId)?.name ?: return@launch)
        val batchSize = settingsStore.currentOnce().batchSize.coerceAtLeast(1)
        val pool = poolFor(blockId, mode)
        val deck = pool.take(batchSize)
        _state.value = StudyUiState(
            loading = false, blockId = blockId, blockName = blockName, mode = mode,
            phase = if (mode == StudyMode.NEW) StudyPhase.LEARN else StudyPhase.TEST,
            deck = deck, batchIndex = 1, batchTotal = totalBatches(pool.size, batchSize),
            poolAtBatchStart = pool.size, remainingAfterBatch = (pool.size - deck.size).coerceAtLeast(0),
        )
    }

    private suspend fun poolFor(blockId: Long, mode: StudyMode): List<WordEntity> {
        val words = if (blockId == GLOBAL_BLOCK_ID) repo.wordDao.observeAllOnce() else repo.wordDao.getForBlock(blockId)
        return words.filter { if (mode == StudyMode.NEW) SpacedRepetition.isNew(it) else SpacedRepetition.isDueNow(it) }
            .sortedBy { it.id }
    }

    private fun totalBatches(pool: Int, batchSize: Int) = if (pool == 0) 1 else ((pool + batchSize - 1) / batchSize)

    fun setInput(text: String) { _state.update { it.copy(input = text) } }

    fun learnNext() {
        val s = _state.value
        if (s.learnIndex + 1 >= s.deck.size) {
            _state.update { it.copy(phase = StudyPhase.TEST, testIndex = 0, input = "", answerState = AnswerState.ASKING) }
        } else {
            _state.update { it.copy(learnIndex = it.learnIndex + 1) }
        }
    }

    fun learnPrev() { _state.update { it.copy(learnIndex = (it.learnIndex - 1).coerceAtLeast(0)) } }

    fun skipToTest() { _state.update { it.copy(phase = StudyPhase.TEST, testIndex = 0, input = "", answerState = AnswerState.ASKING) } }

    private fun base(s: String) = s.trim().lowercase()
        .replace('ё', 'е')
        .replace(Regex("[.,!?]"), "")
        .replace(Regex("\\s+"), " ")

    /** Umlauts folded to plain letters: "Löffel" == "loffel". */
    private fun normStrip(s: String) = base(s)
        .replace("ß", "ss").replace("ä", "a").replace("ö", "o").replace("ü", "u")

    /** Umlauts spelled out: "Löffel" == "loeffel" — how you type without a German keyboard. */
    private fun normExpand(s: String) = base(s)
        .replace("ß", "ss").replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")

    private fun answerMatches(input: String, expected: String) =
        normStrip(input) == normStrip(expected) || normExpand(input) == normExpand(expected)

    fun checkAnswer() = viewModelScope.launch {
        val s = _state.value
        if (s.input.isBlank()) return@launch
        val card = s.deck.getOrNull(s.testIndex) ?: return@launch
        // The prompt is the translation; the answer typed in is the German word.
        applyAnswer(card, answerMatches(s.input, card.de))
    }

    fun skipCard() = viewModelScope.launch {
        val s = _state.value
        val card = s.deck.getOrNull(s.testIndex) ?: return@launch
        applyAnswer(card, false)
    }

    private suspend fun applyAnswer(card: WordEntity, correct: Boolean) {
        val updated = SpacedRepetition.answer(card, correct)
        repo.wordDao.update(updated)
        _state.update { cur ->
            cur.copy(
                answerState = if (correct) AnswerState.RIGHT else AnswerState.WRONG,
                rightCount = cur.rightCount + if (correct) 1 else 0,
                wrongCount = cur.wrongCount + if (correct) 0 else 1,
                deck = cur.deck.toMutableList().also { d -> d[cur.testIndex] = updated },
            )
        }
    }

    fun nextCard() {
        val s = _state.value
        if (s.testIndex + 1 >= s.deck.size) {
            _state.update { it.copy(finished = true) }
        } else {
            _state.update { it.copy(testIndex = it.testIndex + 1, input = "", answerState = AnswerState.ASKING) }
        }
    }

    fun nextBatch() = viewModelScope.launch {
        val s = _state.value
        val batchSize = settingsStore.currentOnce().batchSize.coerceAtLeast(1)
        val pool = poolFor(s.blockId, s.mode)
        val deck = pool.take(batchSize)
        _state.update {
            it.copy(
                phase = if (s.mode == StudyMode.NEW) StudyPhase.LEARN else StudyPhase.TEST,
                deck = deck, learnIndex = 0, testIndex = 0, input = "", answerState = AnswerState.ASKING,
                rightCount = 0, wrongCount = 0, finished = false,
                batchIndex = it.batchIndex + 1,
                poolAtBatchStart = pool.size, remainingAfterBatch = (pool.size - deck.size).coerceAtLeast(0),
            )
        }
    }
}
