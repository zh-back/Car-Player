# Car-Player (极客车载音乐播放器)

专为车载座舱（如比亚迪 DiLink 等基于 Android 系统的车载大屏）深度定制的高性能极客音频播放器。

## 核心特性

- **底层 C 语言 + AAudio 直驱**：
  - 绕过 Java 冗余层，直接使用 Android 8.0+ 底层高性能音频 API `AAudio` 以 32 位浮点（float PCM）直接驱动声卡。
- **车载专用 DSP 实时调音引擎**：
  - **RBJ 双二阶 3 段参数均衡器（Peaking EQ）**：100Hz 低音、1kHz 中音人声、10kHz 高音独立实时调节。
  - **Mid-Side 立体声声场拓宽（Stereo Widener）**：增强 Side 环绕信号，大幅拓展座舱声场空间感。
  - **软限幅器（Soft Clipper）**：多项式平滑抗过载，防止推大低音时车载扬声器爆音破音。
- **车载专属适配**：
  - **导航智能压音（Audio Focus Ducking）**：高德/车机导航路况播报时，音乐自动压低至 25% 音量，播报完毕恢复。
  - **方向盘按键（方控）支持**：集成 `MediaSessionCompat`，完美支持方向盘切歌、音量滚轮与播放/暂停。
  - **U 盘音乐自动扫描**：自动识别并扫描扶手箱 U 盘根目录及各子文件夹无损音乐。
  - **大屏防误触 UI**：适配横屏与旋转大屏的大图标与高对比度操作界面。

## 目录结构

```text
D:\car-player\
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── cpp/                       # C 语言底层音频驱动与 DSP 算法
│       │   ├── CMakeLists.txt
│       │   ├── dsp_engine.h / .c      # 3段EQ + 声场拓宽 + 软限幅
│       │   └── native-audio.c         # AAudio 输出 + JNI 桥梁
│       ├── java/com/geek/carplayer/   # 车机业务逻辑与 UI 控制
│       │   ├── NativeAudioEngine.kt
│       │   ├── UsbScanner.kt
│       │   ├── CarAudioService.kt
│       │   └── MainActivity.kt
│       └── res/layout/
│           └── activity_main.xml
├── build.gradle
└── settings.gradle
```

## 编译与安装

1. 用 **Android Studio** 打开本项目目录；
2. 点击菜单 **`Build` -> `Build Bundle(s) / APK(s)` -> `Build APK(s)`**；
3. 将生成的 `app-debug.apk` 拷入 U 盘，插在车机数据 USB 接口上，通过车机“文件管理”点击安装即可。
