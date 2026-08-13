package app.hoerpraxis.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.hoerpraxis.data.AudioItem
import app.hoerpraxis.data.Repository
import app.hoerpraxis.data.Transcript
import app.hoerpraxis.data.Word
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Interaction modes of the transcript area. */
enum class TranscriptMode { LISTEN, LOOP, EDIT }

data class LoopRange(val startIndex: Int, val endIndex: Int)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = Repository.get(application)

    val player: ExoPlayer = ExoPlayer.Builder(application).build()

    private val _item = MutableStateFlow<AudioItem?>(null)
    val item: StateFlow<AudioItem?> = _item

    private val _words = MutableStateFlow<List<Word>>(emptyList())
    val words: StateFlow<List<Word>> = _words

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed

    private val _mode = MutableStateFlow(TranscriptMode.LISTEN)
    val mode: StateFlow<TranscriptMode> = _mode

    private val _loopRange = MutableStateFlow<LoopRange?>(null)
    val loopRange: StateFlow<LoopRange?> = _loopRange

    private val _loopAnchor = MutableStateFlow<Int?>(null)
    val loopAnchor: StateFlow<Int?> = _loopAnchor

    val highlightEnabled: StateFlow<Boolean> = repo.highlightEnabled

    private var itemId: String? = null

    fun load(id: String) {
        if (itemId == id) return
        itemId = id
        val item = repo.item(id) ?: return
        _item.value = item
        _words.value = repo.loadTranscript(id).words
        _speed.value = item.speed

        player.setMediaItem(MediaItem.fromUri(repo.audioFile(item).toURI().toString()))
        player.playbackParameters = PlaybackParameters(item.speed)
        player.prepare()
        player.seekTo(item.lastPositionMs)
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }
        })

        // Position ticker: drives word highlight, the seek bar and loop wrap-around.
        viewModelScope.launch {
            while (true) {
                _positionMs.value = player.currentPosition
                val loop = _loopRange.value
                if (loop != null && player.isPlaying) {
                    val words = _words.value
                    if (words.isNotEmpty()) {
                        val end = words[loop.endIndex.coerceIn(words.indices)].e
                        val start = words[loop.startIndex.coerceIn(words.indices)].s
                        if (player.currentPosition > end + 60) {
                            player.seekTo(start)
                        }
                    }
                }
                delay(50)
            }
        }
        // Persist listening progress a few times per minute while playing.
        viewModelScope.launch {
            while (true) {
                delay(3000)
                saveProgress()
            }
        }
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    fun seekTo(ms: Long) {
        player.seekTo(ms.coerceAtLeast(0))
        _positionMs.value = player.currentPosition
    }

    fun seekBy(deltaMs: Long) = seekTo(player.currentPosition + deltaMs)

    fun setSpeed(value: Float) {
        _speed.value = value
        player.playbackParameters = PlaybackParameters(value)
        val id = itemId ?: return
        viewModelScope.launch { repo.updateItem(id) { it.copy(speed = value) } }
    }

    fun setMode(newMode: TranscriptMode) {
        _mode.value = if (_mode.value == newMode) TranscriptMode.LISTEN else newMode
        if (_mode.value != TranscriptMode.LOOP) {
            _loopRange.value = null
            _loopAnchor.value = null
        }
    }

    /**
     * A word tap means different things per mode:
     * listening → continue playback from that word;
     * loop → first tap anchors the range, second tap completes it;
     * edit → handled by the UI (opens the edit dialog).
     */
    fun onWordTap(index: Int) {
        val words = _words.value
        if (index !in words.indices) return
        when (_mode.value) {
            TranscriptMode.LISTEN -> {
                seekTo(words[index].s)
                player.play()
            }
            TranscriptMode.LOOP -> {
                val anchor = _loopAnchor.value
                if (anchor == null || _loopRange.value != null) {
                    _loopAnchor.value = index
                    _loopRange.value = null
                } else {
                    val range = LoopRange(minOf(anchor, index), maxOf(anchor, index))
                    _loopRange.value = range
                    _loopAnchor.value = null
                    seekTo(words[range.startIndex].s)
                    player.play()
                }
            }
            TranscriptMode.EDIT -> Unit
        }
    }

    /** Loop a single word right away (long press in loop mode). */
    fun loopSingleWord(index: Int) {
        val words = _words.value
        if (index !in words.indices) return
        _loopRange.value = LoopRange(index, index)
        _loopAnchor.value = null
        seekTo(words[index].s)
        player.play()
    }

    fun clearLoop() {
        _loopRange.value = null
        _loopAnchor.value = null
    }

    fun editWord(index: Int, newText: String) {
        val id = itemId ?: return
        val words = _words.value.toMutableList()
        if (index !in words.indices) return
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) words.removeAt(index)
        else words[index] = words[index].copy(w = trimmed)
        _words.value = words
        repo.saveTranscript(id, Transcript(words))
    }

    /**
     * Replaces the whole transcript text. Old word timings are redistributed
     * over the new words by relative position, so highlighting and tap-to-seek
     * keep working after heavy edits.
     */
    fun editFullText(newText: String) {
        val id = itemId ?: return
        val old = _words.value
        val tokens = newText.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (old.isEmpty() || tokens.isEmpty()) return
        val result = tokens.mapIndexed { i, token ->
            val srcIndex = if (tokens.size == 1) 0
            else ((i.toDouble() * (old.size - 1)) / (tokens.size - 1)).toInt().coerceIn(old.indices)
            Word(w = token, s = old[srcIndex].s, e = old[srcIndex].e)
        }
        _words.value = result
        repo.saveTranscript(id, Transcript(result))
    }

    fun currentWordIndex(positionMs: Long): Int {
        val words = _words.value
        if (words.isEmpty()) return -1
        var lo = 0
        var hi = words.size - 1
        var best = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (words[mid].s <= positionMs) { best = mid; lo = mid + 1 } else hi = mid - 1
        }
        return best
    }

    private fun saveProgress() {
        val id = itemId ?: return
        val pos = player.currentPosition
        viewModelScope.launch { repo.updateItem(id) { it.copy(lastPositionMs = pos) } }
    }

    override fun onCleared() {
        val id = itemId
        val pos = player.currentPosition
        player.release()
        if (id != null) {
            // viewModelScope is already cancelled here, so persist synchronously.
            kotlinx.coroutines.runBlocking {
                repo.updateItem(id) { it.copy(lastPositionMs = pos) }
            }
        }
        super.onCleared()
    }
}
