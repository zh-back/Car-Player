package com.geek.carplayer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

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
            int progress = NativeAudioEngine.nativeGetProgressPercent();
            progressBar.setProgress(progress);
            progressHandler.postDelayed(this, 500);
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
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
        setContentView(R.layout.activity_main);

        initViews();
        bindAudioService();
        setupListeners();
        progressHandler.post(progressRunnable);
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
        Intent intent = new Intent(this, CarAudioService.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void setupListeners() {
        btnPlayPause.setOnClickListener(v -> {
            if (carService != null) carService.togglePlayPause();
        });

        btnPrev.setOnClickListener(v -> {
            if (carService != null) carService.playPrev();
        });

        btnNext.setOnClickListener(v -> {
            if (carService != null) carService.playNext();
        });

        btnScan.setOnClickListener(v -> refreshUsbTracks());

        lvTracks.setOnItemClickListener((parent, view, position, id) -> {
            if (carService != null) carService.playTrack(position);
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

                NativeAudioEngine.nativeSetEQ(bass, mid, treble);
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
                NativeAudioEngine.nativeSetStereoWidth(width);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void refreshUsbTracks() {
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
    }

    @Override
    protected void onDestroy() {
        progressHandler.removeCallbacks(progressRunnable);
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
        super.onDestroy();
    }
}
