package com.geek.carplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;

public class CarAudioService extends Service {

    private static final String TAG = "CarAudioService";

    public interface OnTrackChangedListener {
        void onTrackChanged(MusicTrack track);
    }

    public interface OnPlayStateChangedListener {
        void onPlayStateChanged(boolean isPlaying);
    }

    private final IBinder binder = new LocalBinder();
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private MediaSessionCompat mediaSession;

    private List<MusicTrack> playlist = new ArrayList<>();
    private int currentIndex = -1;
    private boolean isPlaying = false;
    private boolean isEngineInitialized = false;

    private OnTrackChangedListener trackListener;
    private OnPlayStateChangedListener stateListener;

    public class LocalBinder extends Binder {
        public CarAudioService getService() {
            return CarAudioService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // 1. 初始化 C 语言底层 AAudio 引擎 (安全捕获)
        try {
            isEngineInitialized = NativeAudioEngine.nativeInit(48000);
            Log.i(TAG, "Native audio engine init result: " + isEngineInitialized);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load/init native audio engine", t);
        }

        // 2. 建立前台通知 (加固保护，即便车机系统限制通知也不崩溃)
        startCarForegroundSafe();

        // 3. 方向盘按键支持 (MediaSession)
        initMediaSessionSafe();

        // 4. 车载音频焦点支持 (导航播报压音 Ducking)
        initAudioFocusSafe();
    }

    private void startCarForegroundSafe() {
        try {
            String channelId = "car_player_channel";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        channelId,
                        "车载播放器运行服务",
                        NotificationManager.IMPORTANCE_LOW
                );
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            }

            Notification notification = new NotificationCompat.Builder(this, channelId)
                    .setContentTitle("极客车载播放器")
                    .setContentText("车载 Hi-Res 引擎运行中...")
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();

            startForeground(1001, notification);
        } catch (Throwable t) {
            Log.w(TAG, "startForeground failed (safely ignored for car system): " + t.getMessage());
        }
    }

    private void initMediaSessionSafe() {
        try {
            mediaSession = new MediaSessionCompat(this, "GeekCarPlayerSession");
            mediaSession.setCallback(new MediaSessionCompat.Callback() {
                @Override
                public void onPlay() {
                    resume();
                }

                @Override
                public void onPause() {
                    pause();
                }

                @Override
                public void onSkipToNext() {
                    playNext();
                }

                @Override
                public void onSkipToPrevious() {
                    playPrev();
                }
            });
            mediaSession.setActive(true);
        } catch (Throwable t) {
            Log.w(TAG, "MediaSession init warning: " + t.getMessage());
        }
    }

    private void initAudioFocusSafe() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();

                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(playbackAttributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                            @Override
                            public void onAudioFocusChange(int focusChange) {
                                switch (focusChange) {
                                    case AudioManager.AUDIOFOCUS_LOSS:
                                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                                        pause();
                                        break;
                                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                                        try {
                                            NativeAudioEngine.nativeSetVolume(0.25f);
                                        } catch (Throwable ignored) {}
                                        break;
                                    case AudioManager.AUDIOFOCUS_GAIN:
                                        try {
                                            NativeAudioEngine.nativeSetVolume(1.0f);
                                        } catch (Throwable ignored) {}
                                        if (isPlaying) {
                                            resume();
                                        }
                                        break;
                                }
                            }
                        })
                        .build();
            }
        } catch (Throwable t) {
            Log.w(TAG, "AudioFocus init warning: " + t.getMessage());
        }
    }

    private boolean requestFocus() {
        try {
            if (audioManager == null) return true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                return audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            } else {
                return audioManager.requestAudioFocus(
                        null,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            }
        } catch (Throwable t) {
            return true;
        }
    }

    public void setPlaylist(List<MusicTrack> tracks) {
        this.playlist = tracks;
    }

    public void playTrack(int index) {
        if (index < 0 || index >= playlist.size()) return;
        requestFocus();

        currentIndex = index;
        MusicTrack track = playlist.get(index);
        boolean success = false;
        try {
            success = NativeAudioEngine.nativePlayFile(track.getPath());
        } catch (Throwable t) {
            Log.e(TAG, "nativePlayFile error", t);
        }

        if (success) {
            isPlaying = true;
            if (trackListener != null) trackListener.onTrackChanged(track);
            if (stateListener != null) stateListener.onPlayStateChanged(true);
        }
    }

    public void resume() {
        requestFocus();
        try {
            NativeAudioEngine.nativeResume();
        } catch (Throwable ignored) {}
        isPlaying = true;
        if (stateListener != null) stateListener.onPlayStateChanged(true);
    }

    public void pause() {
        try {
            NativeAudioEngine.nativePause();
        } catch (Throwable ignored) {}
        isPlaying = false;
        if (stateListener != null) stateListener.onPlayStateChanged(false);
    }

    public void togglePlayPause() {
        if (isPlaying) pause(); else resume();
    }

    public void playNext() {
        if (playlist.isEmpty()) return;
        int next = (currentIndex + 1) % playlist.size();
        playTrack(next);
    }

    public void playPrev() {
        if (playlist.isEmpty()) return;
        int prev = (currentIndex - 1 < 0) ? playlist.size() - 1 : currentIndex - 1;
        playTrack(prev);
    }

    public void setOnTrackChangedListener(OnTrackChangedListener listener) {
        this.trackListener = listener;
    }

    public void setOnPlayStateChangedListener(OnPlayStateChangedListener listener) {
        this.stateListener = listener;
    }

    @Override
    public void onDestroy() {
        try {
            NativeAudioEngine.nativeStop();
        } catch (Throwable ignored) {}
        if (mediaSession != null) {
            try {
                mediaSession.release();
            } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }
}
