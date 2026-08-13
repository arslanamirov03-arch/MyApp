package app.hoerpraxis.transcribe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.hoerpraxis.R
import app.hoerpraxis.audio.AudioDecoder
import app.hoerpraxis.data.ItemStatus
import app.hoerpraxis.data.Repository
import app.hoerpraxis.data.Transcript
import app.hoerpraxis.whisper.WhisperBridge
import app.hoerpraxis.whisper.WhisperModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Foreground service that downloads speech models and transcribes queued
 * items one by one, so long work survives the app going to background.
 */
class TranscribeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var running = false
    private val bridge = WhisperBridge()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground("Подготовка…")
        val downloadModelId = intent?.getStringExtra(EXTRA_DOWNLOAD_MODEL)
        if (downloadModelId != null) {
            scope.launch {
                downloadModel(WhisperModel.byId(downloadModelId))
                if (!running) stopSelf()
            }
            return START_NOT_STICKY
        }
        if (!running) {
            running = true
            scope.launch { processQueue() }
        }
        return START_NOT_STICKY
    }

    private suspend fun downloadModel(model: WhisperModel): Boolean {
        val repo = Repository.get(this)
        repo.setModelError(null)
        repo.setModelProgress(0)
        return try {
            WhisperBridge.download(this, model) { p ->
                repo.setModelProgress(p)
                updateNotification("Загрузка модели «${model.label}»", p)
            }
            repo.setModelProgress(null)
            repo.notifyModelsChanged()
            true
        } catch (t: Throwable) {
            repo.setModelProgress(null)
            repo.setModelError(t.message ?: "Не удалось скачать модель")
            false
        }
    }

    private suspend fun processQueue() {
        val repo = Repository.get(this)
        while (true) {
            val next = repo.library.value.items.firstOrNull {
                it.status == ItemStatus.PENDING || it.status == ItemStatus.MODEL_DOWNLOAD ||
                    it.status == ItemStatus.DECODING || it.status == ItemStatus.TRANSCRIBING
            } ?: break
            processItem(next.id)
        }
        running = false
        stopSelf()
    }

    private suspend fun processItem(id: String) {
        val repo = Repository.get(this)
        val item = repo.item(id) ?: return
        val model = WhisperModel.byId(repo.selectedModel.value)
        try {
            if (!model.isDownloaded(this)) {
                repo.updateItem(id) {
                    it.copy(status = ItemStatus.MODEL_DOWNLOAD, progress = 0, errorMessage = null)
                }
                updateNotification("Загрузка модели «${model.label}»", 0)
                WhisperBridge.download(this, model) { p ->
                    runBlocking { repo.updateItem(id) { it.copy(progress = p) } }
                    repo.setModelProgress(p)
                    updateNotification("Загрузка модели «${model.label}»", p)
                }
                repo.setModelProgress(null)
                repo.notifyModelsChanged()
            }

            repo.updateItem(id) { it.copy(status = ItemStatus.DECODING, progress = 0, errorMessage = null) }
            updateNotification("Чтение аудио: ${item.title}", 0)

            val pcm = AudioDecoder.decodeTo16kMono(repo.audioFile(item).absolutePath) { p ->
                runBlocking { repo.updateItem(id) { it.copy(progress = p) } }
            }
            check(pcm.isNotEmpty()) { "В файле не оказалось звука" }

            repo.updateItem(id) { it.copy(status = ItemStatus.TRANSCRIBING, progress = 0) }
            bridge.progressListener = { p ->
                runBlocking { repo.updateItem(id) { it.copy(progress = p) } }
                updateNotification("Распознавание: ${item.title}", p)
            }
            val words = bridge.transcribe(this, model, pcm)
            bridge.progressListener = null

            if (words == null) {
                repo.updateItem(id) { it.copy(status = ItemStatus.ERROR, errorMessage = "Отменено") }
            } else {
                repo.saveTranscript(id, Transcript(words))
                repo.updateItem(id) { it.copy(status = ItemStatus.READY, progress = 100) }
            }
        } catch (t: Throwable) {
            repo.setModelProgress(null)
            repo.updateItem(id) {
                it.copy(
                    status = ItemStatus.ERROR,
                    errorMessage = t.message ?: "Не удалось распознать запись",
                )
            }
        }
    }

    private fun startInForeground(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Распознавание речи", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = buildNotification(text, 0)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(text: String, progress: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Hörpraxis")
            .setContentText(text)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String, progress: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text, progress))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "transcribe"
        private const val NOTIF_ID = 1
        private const val EXTRA_DOWNLOAD_MODEL = "download_model"

        fun start(context: Context) = launch(context, Intent(context, TranscribeService::class.java))

        fun downloadModel(context: Context, model: WhisperModel) = launch(
            context,
            Intent(context, TranscribeService::class.java).putExtra(EXTRA_DOWNLOAD_MODEL, model.id),
        )

        private fun launch(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}
