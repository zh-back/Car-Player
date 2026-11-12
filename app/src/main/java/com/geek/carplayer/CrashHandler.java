package com.geek.carplayer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.PrintWriter;
import java.io.StringWriter;

public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";
    private static CrashHandler instance;
    private Context context;
    private Thread.UncaughtExceptionHandler defaultHandler;

    private CrashHandler() {}

    public static synchronized CrashHandler getInstance() {
        if (instance == null) {
            instance = new CrashHandler();
        }
        return instance;
    }

    public void init(Context context) {
        this.context = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        final String crashReport = sw.toString();

        Log.e(TAG, "FATAL CRASH DETECTED: \n" + crashReport);

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Toast.makeText(context, "播放器发生异常: " + e.getMessage(), Toast.LENGTH_LONG).show();
                } catch (Exception ignored) {}
            }
        });

        try {
            Thread.sleep(3000); // 留出 3 秒让车机吐出日志
        } catch (InterruptedException ignored) {}

        if (defaultHandler != null) {
            defaultHandler.uncaughtException(t, e);
        } else {
            System.exit(1);
        }
    }
}
