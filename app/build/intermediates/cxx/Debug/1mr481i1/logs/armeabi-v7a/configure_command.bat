@echo off
"C:\\Users\\Administrator\\AppData\\Local\\Android\\Sdk\\cmake\\3.22.1\\bin\\cmake.exe" ^
  "-HD:\\car-player\\app\\src\\main\\cpp" ^
  "-DCMAKE_SYSTEM_NAME=Android" ^
  "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON" ^
  "-DCMAKE_SYSTEM_VERSION=26" ^
  "-DANDROID_PLATFORM=android-26" ^
  "-DANDROID_ABI=armeabi-v7a" ^
  "-DCMAKE_ANDROID_ARCH_ABI=armeabi-v7a" ^
  "-DANDROID_NDK=C:\\Users\\Administrator\\AppData\\Local\\Android\\Sdk\\ndk\\25.2.9519653" ^
  "-DCMAKE_ANDROID_NDK=C:\\Users\\Administrator\\AppData\\Local\\Android\\Sdk\\ndk\\25.2.9519653" ^
  "-DCMAKE_TOOLCHAIN_FILE=C:\\Users\\Administrator\\AppData\\Local\\Android\\Sdk\\ndk\\25.2.9519653\\build\\cmake\\android.toolchain.cmake" ^
  "-DCMAKE_MAKE_PROGRAM=C:\\Users\\Administrator\\AppData\\Local\\Android\\Sdk\\cmake\\3.22.1\\bin\\ninja.exe" ^
  "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=D:\\car-player\\app\\build\\intermediates\\cxx\\Debug\\1mr481i1\\obj\\armeabi-v7a" ^
  "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=D:\\car-player\\app\\build\\intermediates\\cxx\\Debug\\1mr481i1\\obj\\armeabi-v7a" ^
  "-DCMAKE_BUILD_TYPE=Debug" ^
  "-BD:\\car-player\\app\\.cxx\\Debug\\1mr481i1\\armeabi-v7a" ^
  -GNinja
