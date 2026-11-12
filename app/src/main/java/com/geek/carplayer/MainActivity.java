package com.geek.carplayer;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQ_PERMISSIONS = 1001;

    private CarAudioService carService;
    private boolean isBound = false;
    private List<MusicTrack> trackList = new ArrayList<>();

    private TextView tvTitle;
    private TextView tvFormat;
    private Button btnPlayPause;
    private Button btnPrev;
    private Button btnNext;
    private Button btnScan;
    private ProgressBar progressBar;
    private ListView lvTracks;

    private SeekBar sbBass;
    private SeekBar sbMid;
    private SeekBar sbTreble;
    private SeekBar sbWidth;
    private TextView tvBass;
    private TextView tvMid;
    private TextView tvTreble;
    private TextView tvWidth;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                int progress = NativeAudioEngine.nativeGetProgressPercent();
                progressBar.setProgress(progress);
            } catch (Throwable ignored) {}
            progressHandler.postDelayed(this, 500);
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                CarAudioService.LocalBinder binder = (CarAudioService.LocalBinder) service;
                carService = binder.getService();
                isBound = true;

                carService.setOnTrackChangedListener(new CarAudioService.OnTrackChangedListener() {
                    @Override
                    public void onTrackChanged(final MusicTrack track) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                tvTitle.setText(track.getTitle());
                                tvFormat.setText("HI-RES PCM (" + track.getFormat() + ")");
                            }
                        });
                    }
                });

                carService.setOnPlayStateChangedListener(new CarAudioService.OnPlayStateChangedListener() {
                    @Override
                    public void onPlayStateChanged(final boolean isPlaying) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                btnPlayPause.setText(isPlaying ? "⏸" : "▶");
                            }
                        });
                    }
                });

                refreshUsbTracks();
            } catch (Exception e) {
                Log.e(TAG, "Error in onServiceConnected", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            carService = null;
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 1. 初始化崩溃监控保护
        CrashHandler.getInstance().init(this);

        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();

        // 2. 检查并申请车机 U 盘与存储读取权限
        checkAndRequestPermissions();

        // 3. 绑定车载音频服务
        bindAudioService();

        progressHandler.post(progressRunnable);
    }

    private void checkAndRequestPermissions() {
        List<String> neededPermissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (!neededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    neededPermissions.toArray(new String[0]),
                    REQ_PERMISSIONS
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS) {
            refreshUsbTracks();
        }
    }

    private void initViews() {
        tvTitle = findViewById(R.id.tvTitle);
        tvFormat = findViewById(R.id.tvFormat);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        btnScan = findViewById(R.id.btnScan);
        progressBar = findViewById(R.id.progressBar);
        lvTracks = findViewById(R.id.lvTracks);

        sbBass = findViewById(R.id.sbBass);
        sbMid = findViewById(R.id.sbMid);
        sbTreble = findViewById(R.id.sbTreble);
        sbWidth = findViewById(R.id.sbWidth);

        tvBass = findViewById(R.id.tvBass);
        tvMid = findViewById(R.id.tvMid);
        tvTreble = findViewById(R.id.tvTreble);
        tvWidth = findViewById(R.id.tvWidth);
    }

    private void bindAudioService() {
        try {
            Intent intent = new Intent(this, CarAudioService.class);
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind CarAudioService", e);
        }
    }

    private void setupListeners() {
        btnPlayPause.setOnClickListener(v -> {
            try {
                if (carService != null) carService.togglePlayPause();
            } catch (Throwable t) {
                Log.e(TAG, "PlayPause error", t);
            }
        });

        btnPrev.setOnClickListener(v -> {
            try {
                if (carService != null) carService.playPrev();
            } catch (Throwable t) {
                Log.e(TAG, "Prev error", t);
            }
        });

        btnNext.setOnClickListener(v -> {
            try {
                if (carService != null) carService.playNext();
            } catch (Throwable t) {
                Log.e(TAG, "Next error", t);
            }
        });

        btnScan.setOnClickListener(v -> refreshUsbTracks());

        lvTracks.setOnItemClickListener((parent, view, position, id) -> {
            try {
                if (carService != null) carService.playTrack(position);
            } catch (Throwable t) {
                Log.e(TAG, "PlayTrack error", t);
            }
        });

        SeekBar.OnSeekBarChangeListener eqListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float bass = sbBass.getProgress() - 12f;
                float mid = sbMid.getProgress() - 12f;
                float treble = sbTreble.getProgress() - 12f;

                tvBass.setText("重低音 (100Hz): " + (int) bass + " dB");
                tvMid.setText("中音人声 (1kHz): " + (int) mid + " dB");
                tvTreble.setText("明亮高音 (10kHz): " + (int) treble + " dB");

                try {
                    NativeAudioEngine.nativeSetEQ(bass, mid, treble);
                } catch (Throwable ignored) {}
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        sbBass.setOnSeekBarChangeListener(eqListener);
        sbMid.setOnSeekBarChangeListener(eqListener);
        sbTreble.setOnSeekBarChangeListener(eqListener);

        sbWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float width = progress / 100f;
                tvWidth.setText("车载声场拓宽: " + progress + "%");
                try {
                    NativeAudioEngine.nativeSetStereoWidth(width);
                } catch (Throwable ignored) {}
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void refreshUsbTracks() {
        try {
            trackList = UsbScanner.scanTracks();
            ArrayAdapter<MusicTrack> adapter = new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_list_item_1,
                    trackList
            );
            lvTracks.setAdapter(adapter);
            if (carService != null) {
                carService.setPlaylist(trackList);
            }
            if (trackList.isEmpty()) {
                tvTitle.setText("未检测到 U 盘歌曲，请插入 U 盘");
            }
        } catch (Throwable t) {
            Log.e(TAG, "Scan tracks error", t);
        }
    }

    @Override
    protected void onDestroy() {
        progressHandler.removeCallbacks(progressRunnable);
        if (isBound) {
            try {
                unbindService(connection);
            } catch (Exception ignored) {}
            isBound = false;
        }
        super.onDestroy();
    }
}
