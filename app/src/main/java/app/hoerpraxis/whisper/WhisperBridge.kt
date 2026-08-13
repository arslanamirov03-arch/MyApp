package app.hoerpraxis.whisper

import android.content.Context
import app.hoerpraxis.data.Word
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Thin JNI wrapper around whisper.cpp. Models live in private storage and are
 * downloaded once; after that recognition runs fully offline.
 */
class WhisperBridge {

    @Volatile var progressListener: ((Int) -> Unit)? = null

    private external fun nativeInit(modelPath: String, modelId: String): Long
    private external fun nativeFree(ctxPtr: Long)
    private external fun nativeCancel()
    private external fun nativeTranscribe(ctxPtr: Long, pcm: FloatArray): String

    // Called from native code on the transcription thread.
    @Suppress("unused")
    fun onNativeProgress(progress: Int) {
        progressListener?.invoke(progress)
    }

    fun cancel() = nativeCancel()

    /** Runs full transcription; returns null when cancelled. */
    fun transcribe(context: Context, model: WhisperModel, pcm: FloatArray): List<Word>? {
        val modelPath = model.file(context).absolutePath
        val ctx = nativeInit(modelPath, model.id)
        check(ctx != 0L) { "Не удалось загрузить модель распознавания — скачайте её заново в настройках" }
        try {
            val result = nativeTranscribe(ctx, pcm)
            if (result == "CANCELLED") return null
            return Json { ignoreUnknownKeys = true }.decodeFromString(result)
        } finally {
            nativeFree(ctx)
        }
    }

    companion object {
        init {
            System.loadLibrary("hoerpraxis")
        }

        /**
         * Downloads the model unless it is already complete. Supports resuming
         * a partial download so a dropped connection doesn't start over.
         */
        @Synchronized
        fun download(context: Context, model: WhisperModel, onProgress: (Int) -> Unit) {
            val target = model.file(context)
            if (target.length() == model.sizeBytes) return
            target.parentFile?.mkdirs()

            val part = File(target.parentFile, "${model.fileName}.part")
            var done = part.length()
            if (done > model.sizeBytes) { part.delete(); done = 0 }

            try {
                val connection = java.net.URL(model.url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 20_000
                connection.readTimeout = 60_000
                if (done > 0) connection.setRequestProperty("Range", "bytes=$done-")
                connection.connect()
                // Server ignored our resume request, so start from scratch.
                if (done > 0 && connection.responseCode != 206) done = 0

                java.io.FileOutputStream(part, done > 0).use { output ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(1 shl 20)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            onProgress(((done * 100) / model.sizeBytes).toInt().coerceIn(0, 100))
                        }
                    }
                }
            } catch (t: Throwable) {
                throw IllegalStateException(
                    "Не удалось скачать модель (нужен интернет). Скачанная часть сохранена — " +
                        "нажмите ещё раз, загрузка продолжится с того же места.",
                    t,
                )
            }

            check(part.length() == model.sizeBytes) {
                "Модель скачалась не полностью — нажмите «Скачать» ещё раз"
            }
            target.delete()
            part.renameTo(target)
        }
    }
}
