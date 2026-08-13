package app.hoerpraxis.whisper

import android.content.Context
import app.hoerpraxis.data.Word
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Thin JNI wrapper around whisper.cpp. The model ships inside the APK assets
 * and is copied once into private storage so whisper can mmap it.
 */
class WhisperBridge {

    @Volatile var progressListener: ((Int) -> Unit)? = null

    private external fun nativeInit(modelPath: String): Long
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
    fun transcribe(context: Context, pcm: FloatArray): List<Word>? {
        val modelPath = ensureModel(context) {}
        val ctx = nativeInit(modelPath)
        check(ctx != 0L) { "Не удалось загрузить модель распознавания" }
        try {
            val result = nativeTranscribe(ctx, pcm)
            if (result == "CANCELLED") return null
            return Json { ignoreUnknownKeys = true }.decodeFromString(result)
        } finally {
            nativeFree(ctx)
        }
    }

    companion object {
        private const val MODEL_ASSET = "models/ggml-small-q8_0.bin"
        private const val MODEL_FILE = "ggml-small-q8_0.bin"
        private const val MODEL_SIZE = 264_464_607L
        private const val MODEL_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q8_0.bin"

        init {
            System.loadLibrary("hoerpraxis")
        }

        fun modelReady(context: Context): Boolean =
            File(File(context.filesDir, "models"), MODEL_FILE).length() == MODEL_SIZE

        /**
         * Makes sure the Whisper model is on disk: copies it from APK assets
         * when bundled, otherwise downloads it once. After that the app is
         * fully offline.
         */
        @Synchronized
        fun ensureModel(context: Context, onProgress: (Int) -> Unit): String {
            val dir = File(context.filesDir, "models").apply { mkdirs() }
            val target = File(dir, MODEL_FILE)
            if (target.length() == MODEL_SIZE) return target.absolutePath

            val tmp = File(dir, "$MODEL_FILE.tmp")
            val fromAssets = runCatching {
                context.assets.open(MODEL_ASSET).use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output, 1 shl 20) }
                }
                true
            }.getOrDefault(false)

            if (!fromAssets) {
                try {
                    val connection = java.net.URL(MODEL_URL).openConnection()
                    connection.connectTimeout = 20_000
                    connection.readTimeout = 60_000
                    connection.getInputStream().use { input ->
                        tmp.outputStream().use { output ->
                            val buffer = ByteArray(1 shl 20)
                            var done = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                done += read
                                onProgress(((done * 100) / MODEL_SIZE).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                } catch (t: Throwable) {
                    tmp.delete()
                    throw IllegalStateException(
                        "Для первой загрузки модели распознавания нужен интернет (~264 МБ, один раз)", t
                    )
                }
            }
            check(tmp.length() == MODEL_SIZE) { "Модель загрузилась не полностью — попробуйте ещё раз" }
            tmp.renameTo(target)
            return target.absolutePath
        }
    }
}
