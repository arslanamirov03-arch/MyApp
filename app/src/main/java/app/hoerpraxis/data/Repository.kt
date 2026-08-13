package app.hoerpraxis.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Single source of truth for the library index, transcripts and settings.
 * Everything is plain JSON files inside the app's private storage, so the
 * library, transcripts and listening progress survive restarts offline.
 */
class Repository private constructor(private val appContext: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    private val libraryFile = File(appContext.filesDir, "library.json")
    val audioDir = File(appContext.filesDir, "audio").apply { mkdirs() }
    val transcriptDir = File(appContext.filesDir, "transcripts").apply { mkdirs() }

    private val _library = MutableStateFlow(loadLibrary())
    val library: StateFlow<Library> = _library

    private val prefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _highlightEnabled = MutableStateFlow(prefs.getBoolean("highlight", true))
    val highlightEnabled: StateFlow<Boolean> = _highlightEnabled

    fun setHighlightEnabled(value: Boolean) {
        prefs.edit().putBoolean("highlight", value).apply()
        _highlightEnabled.value = value
    }

    private val _selectedModel = MutableStateFlow(prefs.getString("model", "small") ?: "small")
    val selectedModel: StateFlow<String> = _selectedModel

    fun setSelectedModel(id: String) {
        prefs.edit().putString("model", id).apply()
        _selectedModel.value = id
    }

    /** Download progress of a model in percent, or null when nothing is downloading. */
    private val _modelProgress = MutableStateFlow<Int?>(null)
    val modelProgress: StateFlow<Int?> = _modelProgress

    fun setModelProgress(value: Int?) {
        _modelProgress.value = value
    }

    private val _modelError = MutableStateFlow<String?>(null)
    val modelError: StateFlow<String?> = _modelError

    fun setModelError(message: String?) {
        _modelError.value = message
    }

    /** Bumped whenever a model finishes downloading so the UI re-checks disk state. */
    private val _modelsChanged = MutableStateFlow(0)
    val modelsChanged: StateFlow<Int> = _modelsChanged

    fun notifyModelsChanged() {
        _modelsChanged.value += 1
    }

    private fun loadLibrary(): Library = runCatching {
        if (libraryFile.exists()) json.decodeFromString<Library>(libraryFile.readText()) else Library()
    }.getOrDefault(Library())

    private fun persist() {
        libraryFile.writeText(json.encodeToString(Library.serializer(), _library.value))
    }

    suspend fun addItem(item: AudioItem) = mutex.withLock {
        _library.value = Library(_library.value.items + item)
        persist()
    }

    suspend fun updateItem(id: String, transform: (AudioItem) -> AudioItem) = mutex.withLock {
        _library.value = Library(_library.value.items.map { if (it.id == id) transform(it) else it })
        persist()
    }

    suspend fun removeItem(id: String) = mutex.withLock {
        val item = _library.value.items.find { it.id == id } ?: return@withLock
        _library.value = Library(_library.value.items.filter { it.id != id })
        persist()
        File(audioDir, item.fileName).delete()
        transcriptFile(id).delete()
    }

    fun item(id: String): AudioItem? = _library.value.items.find { it.id == id }

    fun audioFile(item: AudioItem): File = File(audioDir, item.fileName)

    private fun transcriptFile(id: String) = File(transcriptDir, "$id.json")

    fun loadTranscript(id: String): Transcript = runCatching {
        val f = transcriptFile(id)
        if (f.exists()) json.decodeFromString<Transcript>(f.readText()) else Transcript()
    }.getOrDefault(Transcript())

    fun saveTranscript(id: String, transcript: Transcript) {
        transcriptFile(id).writeText(json.encodeToString(Transcript.serializer(), transcript))
    }

    companion object {
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository =
            instance ?: synchronized(this) {
                instance ?: Repository(context.applicationContext).also { instance = it }
            }
    }
}
