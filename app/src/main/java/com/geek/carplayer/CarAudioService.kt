package com.geek.carplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat

class CarAudioService : Service() {

    private val binder = LocalBinder()
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private lateinit var mediaSession: MediaSessionCompat

    private var playlist: List<MusicTrack> = emptyList()
    private var currentIndex = -1
    private var isPlaying = false

    var onTrackChangedListener: ((MusicTrack) -> Unit)? = null
    var onPlayStateChangedListener: ((Boolean) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): CarAudioService = this@CarAudioService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // 1. 初始化 C 语言底层 AAudio 引擎
        NativeAudioEngine.nativeInit(48000)

        // 2. 建立前台通知 (车机防杀)
        startCarForeground()

        // 3. 方向盘按键支持 (MediaSession)
        initMediaSession()

        // 4. 车载音频焦点支持 (导航压音 Ducking)
        initAudioFocus()
    }

    private fun startCarForeground() {
        val channelId = "car_player_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "车载播放器运行服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("极客车载播放器")
            .setContentText("车载 Hi-Res 引擎运行中...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()

        startForeground(1001, notification)
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "GeekCarPlayerSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { resume() }
                override fun onPause() { pause() }
                override fun onSkipToNext() { playNext() }
                override fun onSkipToPrevious() { playPrev() }
            })
            isActive = true
        }
    }

    private fun initAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> pause()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            // 车机导航播报：把音乐压低到 25% 音量
                            NativeAudioEngine.nativeSetVolume(0.25f)
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            // 导航结束：恢复 100% 音量并继续播放
                            NativeAudioEngine.nativeSetVolume(1.0f)
                            if (isPlaying) resume()
                        }
                    }
                }
                .build()
        }
    }

    private fun requestFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun setPlaylist(tracks: List<MusicTrack>) {
        this.playlist = tracks
    }

    fun playTrack(index: Int) {
        if (index !in playlist.indices) return
        if (!requestFocus()) return

        currentIndex = index
        val track = playlist[index]
        val success = NativeAudioEngine.nativePlayFile(track.path)
        if (success) {
            isPlaying = true
            onTrackChangedListener?.invoke(track)
            onPlayStateChangedListener?.invoke(true)
        }
    }

    fun resume() {
        if (!requestFocus()) return
        NativeAudioEngine.nativeResume()
        isPlaying = true
        onPlayStateChangedListener?.invoke(true)
    }

    fun pause() {
        NativeAudioEngine.nativePause()
        isPlaying = false
        onPlayStateChangedListener?.invoke(false)
    }

    fun togglePlayPause() {
        if (isPlaying) pause() else resume()
    }

    fun playNext() {
        if (playlist.isEmpty()) return
        val next = (currentIndex + 1) % playlist.size
        playTrack(next)
    }

    fun playPrev() {
        if (playlist.isEmpty()) return
        val prev = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
        playTrack(prev)
    }

    override fun onDestroy() {
        NativeAudioEngine.nativeStop()
        mediaSession.release()
        super.onDestroy()
    }
}
