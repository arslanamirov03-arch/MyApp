package com.perfectaudio.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

/**
 * Keeps playback alive when the app is in the background and mirrors the
 * WebView player into a media notification. The audio itself still plays in
 * the WebView; this service owns the session, the notification and audio focus.
 */
public class PlaybackService extends Service {

    static final String CHANNEL_ID = "perfect_audio_playback";
    static final int NOTIF_ID = 1;

    public static final String ACTION_UPDATE = "com.perfectaudio.app.UPDATE";
    public static final String ACTION_STOP = "com.perfectaudio.app.STOP";

    /** Commands the notification sends back to the web player. */
    public interface CommandListener {
        void onCommand(String command, long extra);
    }

    private static CommandListener listener;

    public static void setCommandListener(CommandListener l) {
        listener = l;
    }

    private MediaSession session;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private boolean started;
    private boolean hasFocus;
    private long focusGrantedAt;

    private String title = "Perfect Audio";
    private long positionMs = 0;
    private long durationMs = 0;
    private boolean playing = false;

    private static void send(String command, long extra) {
        CommandListener l = listener;
        if (l != null) l.onCommand(command, extra);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        createChannel();

        session = new MediaSession(this, "PerfectAudio");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                send("play", 0);
            }

            @Override
            public void onPause() {
                send("pause", 0);
            }

            @Override
            public void onStop() {
                send("pause", 0);
            }

            @Override
            public void onSeekTo(long pos) {
                send("seek", pos);
            }

            @Override
            public void onSkipToNext() {
                send("forward", 0);
            }

            @Override
            public void onSkipToPrevious() {
                send("rewind", 0);
            }

            @Override
            public void onFastForward() {
                send("forward", 0);
            }

            @Override
            public void onRewind() {
                send("rewind", 0);
            }
        });
        session.setActive(true);
    }

    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Воспроизведение",
                NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Управление аудио, когда приложение свёрнуто");
        ch.setShowBadge(false);
        ch.setSound(null, null);
        nm.createNotificationChannel(ch);
    }

    /**
     * Asks for audio focus once and keeps it. Requesting again while we already
     * hold it makes the system report a loss for the previous request, which
     * would immediately pause our own playback.
     */
    private void requestFocus() {
        if (audioManager == null || hasFocus) return;
        if (focusRequest == null) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(this::onFocusChange)
                    .build();
        }
        int res = audioManager.requestAudioFocus(focusRequest);
        hasFocus = res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        focusGrantedAt = System.currentTimeMillis();
    }

    private void onFocusChange(int change) {
        if (change == AudioManager.AUDIOFOCUS_GAIN) {
            hasFocus = true;
            return;
        }
        if (change == AudioManager.AUDIOFOCUS_LOSS
                || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            if (change == AudioManager.AUDIOFOCUS_LOSS) hasFocus = false;
            // Ignore a loss that arrives right after our own request: it is an
            // echo of that request, not another app taking over.
            if (System.currentTimeMillis() - focusGrantedAt < 800) return;
            send("pause", 0);
        }
    }

    private void abandonFocus() {
        if (audioManager != null && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
        }
        hasFocus = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopPlayback();
            return START_NOT_STICKY;
        }

        if (ACTION_UPDATE.equals(action)) {
            Bundle b = intent.getExtras();
            if (b != null) {
                title = b.getString("title", title);
                positionMs = b.getLong("position", positionMs);
                durationMs = b.getLong("duration", durationMs);
                playing = b.getBoolean("playing", playing);
            }
            if (playing) requestFocus();
            updateSession();
            Notification n = buildNotification();
            if (!started) {
                startForeground(NOTIF_ID, n);
                started = true;
            } else {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(NOTIF_ID, n);
            }
        }
        return START_NOT_STICKY;
    }

    private void stopPlayback() {
        abandonFocus();
        started = false;
        stopForeground(true);
        stopSelf();
    }

    private void updateSession() {
        MediaMetadata meta = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Perfect Audio")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
                .build();
        session.setMetadata(meta);

        PlaybackState state = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY
                        | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SEEK_TO
                        | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackState.ACTION_STOP)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        positionMs, playing ? 1f : 0f)
                .build();
        session.setPlaybackState(state);
    }

    private PendingIntent cmd(String action) {
        Intent i = new Intent(this, NotificationReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(this, action.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(fmt(positionMs) + " / " + fmt(durationMs))
                .setContentIntent(contentIntent)
                .setDeleteIntent(cmd(NotificationReceiver.ACTION_CLOSE))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setShowWhen(false);

        try {
            b.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
        } catch (Exception ignored) {
        }

        b.addAction(new Notification.Action.Builder(
                Icon_rewind(), "Назад", cmd(NotificationReceiver.ACTION_REWIND)).build());
        b.addAction(new Notification.Action.Builder(
                playing ? Icon_pause() : Icon_play(),
                playing ? "Пауза" : "Играть",
                cmd(playing ? NotificationReceiver.ACTION_PAUSE : NotificationReceiver.ACTION_PLAY)).build());
        b.addAction(new Notification.Action.Builder(
                Icon_forward(), "Вперёд", cmd(NotificationReceiver.ACTION_FORWARD)).build());

        Notification.MediaStyle style = new Notification.MediaStyle()
                .setMediaSession(session.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2);
        b.setStyle(style);
        return b.build();
    }

    private android.graphics.drawable.Icon Icon_play() {
        return android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_notif_play);
    }

    private android.graphics.drawable.Icon Icon_pause() {
        return android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_notif_pause);
    }

    private android.graphics.drawable.Icon Icon_rewind() {
        return android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_notif_rewind);
    }

    private android.graphics.drawable.Icon Icon_forward() {
        return android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_notif_forward);
    }

    private static String fmt(long ms) {
        long total = Math.max(0, ms / 1000);
        long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%d:%02d", m, s);
    }

    @Override
    public void onDestroy() {
        abandonFocus();
        if (session != null) {
            session.setActive(false);
            session.release();
            session = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Relays notification button taps into the web player. */
    public static class NotificationReceiver extends android.content.BroadcastReceiver {
        public static final String ACTION_PLAY = "com.perfectaudio.app.PLAY";
        public static final String ACTION_PAUSE = "com.perfectaudio.app.PAUSE";
        public static final String ACTION_REWIND = "com.perfectaudio.app.REWIND";
        public static final String ACTION_FORWARD = "com.perfectaudio.app.FORWARD";
        public static final String ACTION_CLOSE = "com.perfectaudio.app.CLOSE";

        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (a == null) return;
            switch (a) {
                case ACTION_PLAY:
                    send("play", 0);
                    break;
                case ACTION_PAUSE:
                    send("pause", 0);
                    break;
                case ACTION_REWIND:
                    send("rewind", 0);
                    break;
                case ACTION_FORWARD:
                    send("forward", 0);
                    break;
                case ACTION_CLOSE:
                    send("pause", 0);
                    context.startService(new Intent(context, PlaybackService.class)
                            .setAction(ACTION_STOP));
                    break;
            }
        }
    }
}
