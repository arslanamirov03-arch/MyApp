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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Foreground service that decodes and transcribes queued items one by one,
 * so recognition survives the app going to background.
 */
class TranscribeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var running = false
    private val bridge = WhisperBridge()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground("Подготовка…")
        if (!running) {
            running = true
            scope.launch { processQueue() }
        }
        return START_NOT_STICKY
    }

    private suspend fun processQueue() {
        val repo = Repository.get(this)
        while (true) {
            val next = repo.library.value.items.firstOrNull {
                it.status == ItemStatus.PENDING || it.status == ItemStatus.DECODING ||
                    it.status == ItemStatus.TRANSCRIBING
            } ?: break
            processItem(next.id)
        }
        stopSelf()
    }

    private suspend fun processItem(id: String) {
        val repo = Repository.get(this)
        val item = repo.item(id) ?: return
        try {
            repo.updateItem(id) { it.copy(status = ItemStatus.DECODING, progress = 0, errorMessage = null) }
            updateNotification("Чтение аудио: ${item.title}", 0)

            val pcm = AudioDecoder.decodeTo16kMono(repo.audioFile(item).absolutePath) { p ->
                runBlocking { repo.updateItem(id) { it.copy(progress = p) } }
            }

            repo.updateItem(id) { it.copy(status = ItemStatus.TRANSCRIBING, progress = 0) }
            bridge.progressListener = { p ->
                runBlocking { repo.updateItem(id) { it.copy(progress = p) } }
                updateNotification("Распознавание: ${item.title}", p)
            }
            val words = bridge.transcribe(this, pcm)
            bridge.progressListener = null

            if (words == null) {
                repo.updateItem(id) { it.copy(status = ItemStatus.ERROR, errorMessage = "Отменено") }
            } else {
                repo.saveTranscript(id, Transcript(words))
                repo.updateItem(id) { it.copy(status = ItemStatus.READY, progress = 100) }
            }
        } catch (t: Throwable) {
            repo.updateItem(id) {
                it.copy(status = ItemStatus.ERROR, errorMessage = t.message ?: "Ошибка распознавания")
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

        fun start(context: Context) {
            val intent = Intent(context, TranscribeService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}
