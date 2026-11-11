package com.geek.carplayer

object NativeAudioEngine {
    init {
        System.loadLibrary("caraudio")
    }

    external fun nativeInit(sampleRate: Int): Boolean
    external fun nativePlayFile(path: String): Boolean
    external fun nativePause()
    external fun nativeResume()
    external fun nativeStop()
    external fun nativeSetEQ(bassDb: Float, midDb: Float, trebleDb: Float)
    external fun nativeSetStereoWidth(width: Float)
    external fun nativeSetVolume(volume: Float)
    external fun nativeGetProgressPercent(): Int
}
