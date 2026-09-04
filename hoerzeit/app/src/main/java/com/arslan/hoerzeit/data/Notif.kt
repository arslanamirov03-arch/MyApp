package com.arslan.hoerzeit.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.arslan.hoerzeit.MainActivity
import com.arslan.hoerzeit.R

/**
 * Пока сессия идёт, в шторке висит уведомление со счётчиком времени —
 * так видно, что приложение считает, даже когда оно свёрнуто.
 */
object Notif {

    private const val CHANNEL_ID = "tracking"
    private const val NOTIF_ID = 1001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_desc)
            setShowBadge(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun showRunning(context: Context, startedAt: Long) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_hoeren)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(context.getString(R.string.notif_text))
            .setWhen(startedAt)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, notification) }
    }

    fun clear(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIF_ID) }
    }
}
