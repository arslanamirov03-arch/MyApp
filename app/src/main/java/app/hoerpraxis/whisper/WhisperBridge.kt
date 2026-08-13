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
        val modelPath = ensureModel(context)
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

        init {
            System.loadLibrary("hoerpraxis")
        }

        @Synchronized
        fun ensureModel(context: Context): String {
            val dir = File(context.filesDir, "models").apply { mkdirs() }
            val target = File(dir, MODEL_FILE)
            val expected = context.assets.openFd(MODEL_ASSET).use { it.length }
            if (!target.exists() || target.length() != expected) {
                val tmp = File(dir, "$MODEL_FILE.tmp")
                context.assets.open(MODEL_ASSET).use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output, 1 shl 20) }
                }
                tmp.renameTo(target)
            }
            return target.absolutePath
        }
    }
}
