package com.geek.carplayer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQ_PERMISSIONS = 1001;

    private WebView webView;
    private CarAudioService carService;
    private boolean isBound = false;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                int progress = NativeAudioEngine.nativeGetProgressPercent();
                // 实时推送当前播放进度给 Vue 页面
                sendJs("if (window.onCarProgressChanged) window.onCarProgressChanged(" + progress + ");");
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

                // 监听切歌并通知 Vue 页面
                carService.setOnTrackChangedListener(track -> {
                    String title = track.getTitle().replace("'", "\\'");
                    String format = track.getFormat();
                    sendJs("if (window.onCarTrackChanged) window.onCarTrackChanged('" + title + "', '" + format + "');");
                });

                // 监听播放/暂停并通知 Vue 页面
                carService.setOnPlayStateChangedListener(isPlaying -> {
                    sendJs("if (window.onCarPlayStateChanged) window.onCarPlayStateChanged(" + isPlaying + ");");
                });

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

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashHandler.getInstance().init(this);

        // 1. 初始化全屏 WebView
        webView = new WebView(this);
        setContentView(webView);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);

        // 2. 注入 JS 桥梁：在 Vue 前端可以通过 window.CarPlayer 调用
        webView.addJavascriptInterface(new CarJsBridge(this), "CarPlayer");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.i(TAG, "Vue Car Player UI loaded successfully.");
            }
        });

        // 3. 加载 Vue 打包产物 (无需网络，秒开)
        webView.loadUrl("file:///android_asset/index.html");

        // 4. 检查权限并绑定服务
        checkAndRequestPermissions();
        bindAudioService();
        progressHandler.post(progressRunnable);
    }

    private void sendJs(String jsCode) {
        runOnUiThread(() -> {
            if (webView != null) {
                webView.evaluateJavascript(jsCode, null);
            }
        });
    }

    public CarAudioService getCarService() {
        return carService;
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
            // 授权成功后通知前端重新扫一次 U 盘
            sendJs("if (window.handleScanUsb) window.handleScanUsb();");
        }
    }

    private void bindAudioService() {
        try {
            Intent intent = new Intent(this, CarAudioService.class);
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind CarAudioService", e);
        }
    }

    @Override
    public void onBackPressed() {
        // 车机模式：按返回键退回后台，不中断音乐播放
        moveTaskToBack(true);
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
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
