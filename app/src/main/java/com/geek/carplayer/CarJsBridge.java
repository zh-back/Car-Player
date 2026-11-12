package com.geek.carplayer;

import android.webkit.JavascriptInterface;
import java.util.List;

public class CarJsBridge {

    private final MainActivity activity;

    public CarJsBridge(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void play(int index) {
        activity.runOnUiThread(() -> {
            if (activity.getCarService() != null) {
                activity.getCarService().playTrack(index);
            }
        });
    }

    @JavascriptInterface
    public void togglePlay() {
        activity.runOnUiThread(() -> {
            if (activity.getCarService() != null) {
                activity.getCarService().togglePlayPause();
            }
        });
    }

    @JavascriptInterface
    public void playPrev() {
        activity.runOnUiThread(() -> {
            if (activity.getCarService() != null) {
                activity.getCarService().playPrev();
            }
        });
    }

    @JavascriptInterface
    public void playNext() {
        activity.runOnUiThread(() -> {
            if (activity.getCarService() != null) {
                activity.getCarService().playNext();
            }
        });
    }

    @JavascriptInterface
    public void setEQ(float bass, float mid, float treble) {
        NativeAudioEngine.nativeSetEQ(bass, mid, treble);
    }

    @JavascriptInterface
    public void setStereoWidth(float width) {
        NativeAudioEngine.nativeSetStereoWidth(width);
    }

    @JavascriptInterface
    public String scanUsb() {
        List<MusicTrack> tracks = UsbScanner.scanTracks();
        if (activity.getCarService() != null) {
            activity.getCarService().setPlaylist(tracks);
        }
        // 构建简易 JSON 数组字符串返回给 Vue
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < tracks.size(); i++) {
            MusicTrack t = tracks.get(i);
            sb.append("{")
              .append("\"title\":\"").append(escapeJson(t.getTitle())).append("\",")
              .append("\"format\":\"").append(escapeJson(t.getFormat())).append("\",")
              .append("\"path\":\"").append(escapeJson(t.getPath())).append("\"")
              .append("}");
            if (i < tracks.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
