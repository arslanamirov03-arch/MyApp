package app.hoerpraxis.whisper

import android.content.Context
import java.io.File

/**
 * Speech models the user can choose between. Bigger models recognise German
 * more accurately but take longer per recording.
 */
enum class WhisperModel(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val label: String,
    val note: String,
) {
    SMALL(
        id = "small",
        fileName = "ggml-small-q8_0.bin",
        sizeBytes = 264_464_607L,
        label = "Обычная",
        note = "252 МБ · быстрая, хорошее качество на чистой речи",
    ),
    MEDIUM(
        id = "medium",
        fileName = "ggml-medium-q8_0.bin",
        sizeBytes = 823_369_779L,
        label = "Точная",
        note = "785 МБ · заметно точнее, примерно втрое медленнее",
    ),
    TURBO(
        id = "large-v3-turbo",
        fileName = "ggml-large-v3-turbo-q8_0.bin",
        sizeBytes = 874_188_075L,
        label = "Максимальная",
        note = "833 МБ · самая точная и при этом быстрая, для мощных телефонов",
    );

    val url: String
        get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$fileName"

    fun file(context: Context): File = File(File(context.filesDir, "models"), fileName)

    fun isDownloaded(context: Context): Boolean = file(context).length() == sizeBytes

    companion object {
        fun byId(id: String?): WhisperModel = entries.find { it.id == id } ?: SMALL

        /** The strongest model already on the phone — used for calibration. */
        fun bestDownloaded(context: Context): WhisperModel? =
            entries.filter { it.isDownloaded(context) }.maxByOrNull { it.sizeBytes }
    }
}
