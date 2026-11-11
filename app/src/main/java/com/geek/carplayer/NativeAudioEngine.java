package com.geek.carplayer;

public class NativeAudioEngine {

    static {
        System.loadLibrary("caraudio");
    }

    public static native boolean nativeInit(int sampleRate);
    public static native boolean nativePlayFile(String path);
    public static native void nativePause();
    public static native void nativeResume();
    public static native void nativeStop();
    public static native void nativeSetEQ(float bassDb, float midDb, float trebleDb);
    public static native void nativeSetStereoWidth(float width);
    public static native void nativeSetVolume(float volume);
    public static native int nativeGetProgressPercent();
}
