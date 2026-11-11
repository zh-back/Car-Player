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

import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;

public class CarAudioService extends Service {

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

        // 1. 初始化 C 语言底层 AAudio 引擎
        NativeAudioEngine.nativeInit(48000);

        // 2. 建立前台通知 (比亚迪省电策略防杀)
        startCarForeground();

        // 3. 方向盘按键支持 (MediaSession)
        initMediaSession();

        // 4. 车载音频焦点支持 (导航播报压音 Ducking)
        initAudioFocus();
    }

    private void startCarForeground() {
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
                .setContentTitle("极客车载播放器 (Java)")
                .setContentText("车载 Hi-Res 引擎运行中...")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build();

        startForeground(1001, notification);
    }

    private void initMediaSession() {
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
                playNext(); // 方向盘下一曲
            }

            @Override
            public void onSkipToPrevious() {
                playPrev(); // 方向盘上一曲
            }
        });
        mediaSession.setActive(true);
    }

    private void initAudioFocus() {
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
                                    // 车机导航播报：把音乐压低到 25% 音量
                                    NativeAudioEngine.nativeSetVolume(0.25f);
                                    break;
                                case AudioManager.AUDIOFOCUS_GAIN:
                                    // 导航结束：恢复 100% 音量并继续播放
                                    NativeAudioEngine.nativeSetVolume(1.0f);
                                    if (isPlaying) {
                                        resume();
                                    }
                                    break;
                            }
                        }
                    })
                    .build();
        }
    }

    private boolean requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        } else {
            return audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
    }

    public void setPlaylist(List<MusicTrack> tracks) {
        this.playlist = tracks;
    }

    public void playTrack(int index) {
        if (index < 0 || index >= playlist.size()) return;
        if (!requestFocus()) return;

        currentIndex = index;
        MusicTrack track = playlist.get(index);
        boolean success = NativeAudioEngine.nativePlayFile(track.getPath());
        if (success) {
            isPlaying = true;
            if (trackListener != null) trackListener.onTrackChanged(track);
            if (stateListener != null) stateListener.onPlayStateChanged(true);
        }
    }

    public void resume() {
        if (!requestFocus()) return;
        NativeAudioEngine.nativeResume();
        isPlaying = true;
        if (stateListener != null) stateListener.onPlayStateChanged(true);
    }

    public void pause() {
        NativeAudioEngine.nativePause();
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
        NativeAudioEngine.nativeStop();
        if (mediaSession != null) {
            mediaSession.release();
        }
        super.onDestroy();
    }
}
