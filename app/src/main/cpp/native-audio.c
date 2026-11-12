#include <jni.h>
#include <aaudio/AAudio.h>
#include <android/log.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <pthread.h>
#include "dsp_engine.h"

#define TAG "CarAudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#pragma pack(push, 1)
typedef struct {
    char riff[4]; uint32_t size; char wave[4];
    char fmt[4];  uint32_t fmt_size; uint16_t format;
    uint16_t channels; uint32_t sample_rate;
    uint32_t byte_rate; uint16_t block_align;
    uint16_t bits; char data[4]; uint32_t data_size;
} SimpleWavHeader;
#pragma pack(pop)

typedef struct {
    AAudioStream* stream;
    CarDspEngine dsp;
    FILE* current_file;
    uint32_t data_bytes_left;
    uint32_t total_data_bytes;
    int channels;
    int sample_rate;
    int bits_per_sample;
    int is_playing;
    pthread_mutex_t lock;
} CarAudioState;

static CarAudioState g_engine;

// AAudio 硬件数据回调函数：驱动定期向该回调索取数据
static aaudio_data_callback_result_t aaudio_data_callback(
        AAudioStream* stream,
        void* userData,
        void* audioData,
        int32_t numFrames) {

    CarAudioState* state = (CarAudioState*)userData;
    float* out_buffer = (float*)audioData;

    pthread_mutex_lock(&state->lock);

    if (!state->is_playing || state->current_file == NULL) {
        memset(out_buffer, 0, numFrames * 2 * sizeof(float));
        pthread_mutex_unlock(&state->lock);
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    int frames_read = 0;
    for (int i = 0; i < numFrames; i++) {
        if (state->data_bytes_left < (uint32_t)(state->channels * sizeof(int16_t))) {
            // 文件播放结束
            state->is_playing = 0;
            break;
        }

        int16_t s_left = 0, s_right = 0;
        if (state->channels == 1) {
            fread(&s_left, sizeof(int16_t), 1, state->current_file);
            s_right = s_left;
            state->data_bytes_left -= sizeof(int16_t);
        } else {
            fread(&s_left, sizeof(int16_t), 1, state->current_file);
            fread(&s_right, sizeof(int16_t), 1, state->current_file);
            state->data_bytes_left -= sizeof(int16_t) * 2;
        }

        out_buffer[i * 2]     = s_left  / 32768.0f;
        out_buffer[i * 2 + 1] = s_right / 32768.0f;
        frames_read++;
    }

    // 剩余帧补 0
    if (frames_read < numFrames) {
        memset(out_buffer + frames_read * 2, 0, (numFrames - frames_read) * 2 * sizeof(float));
    }

    // 执行车载专用 DSP 算法：均衡器 + 声场拓宽 + 软限幅防破音
    dsp_process_interleaved(&state->dsp, out_buffer, numFrames);

    pthread_mutex_unlock(&state->lock);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

// ----------------- JNI 接口实现 -----------------

JNIEXPORT jboolean JNICALL
Java_com_geek_carplayer_NativeAudioEngine_nativeInit(JNIEnv* env, jclass clazz, jint sample_rate) {
    memset(&g_engine, 0, sizeof(CarAudioState));
    pthread_mutex_init(&g_engine.lock, NULL);

    int target_rate = sample_rate > 0 ? sample_rate : 48000;
    dsp_init(&g_engine.dsp, target_rate);

    AAudioStreamBuilder* builder = NULL;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) {
        LOGE("Failed to create AAudioStreamBuilder");
        return JNI_FALSE;
    }

    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setChannelCount(builder, 2);
    AAudioStreamBuilder_setSampleRate(builder, target_rate);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setDataCallback(builder, aaudio_data_callback, &g_engine);

    aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &g_engine.stream);
    AAudioStreamBuilder_delete(builder);

    if (result != AAUDIO_OK || g_engine.stream == NULL) {
        LOGE("Failed to open AAudio stream: %d", result);
        return JNI_FALSE;
    }

    AAudioStream_requestStart(g_engine.stream);
    LOGI("Car AAudio Engine initialized successfully @ %d Hz", target_rate);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_geek_carplayer_NativeAudioEngine_nativePlayFile(JNIEnv* env, jclass clazz, jstring path) {
    const char* file_path = (*env)->GetStringUTFChars(env, path, NULL);
    if (!file_path) return JNI_FALSE;

    pthread_mutex_lock(&g_engine.lock);

    if (g_engine.current_file) {
        fclose(g_engine.current_file);
        g_engine.current_file = NULL;
    }

    FILE* fp = fopen(file_path, "rb");
    if (!fp) {
        LOGE("Cannot open file: %s", file_path);
        pthread_mutex_unlock(&g_engine.lock);
        (*env)->ReleaseStringUTFChars(env, path, file_path);
        return JNI_FALSE;
    }

    SimpleWavHeader header;
    if (fread(&header, sizeof(header), 1, fp) != 1 || strncmp(header.riff, "RIFF", 4) != 0) {
        LOGE("Unsupported audio format or non-WAV: %s", file_path);
        fclose(fp);
        pthread_mutex_unlock(&g_engine.lock);
        (*env)->ReleaseStringUTFChars(env, path, file_path);
        return JNI_FALSE;
    }

    g_engine.current_file = fp;
    g_engine.channels = header.channels;
    g_engine.sample_rate = header.sample_rate;
    g_engine.bits_per_sample = header.bits;
    g_engine.total_data_bytes = header.data_size;
    g_engine.data_bytes_left = header.data_size;
    g_engine.is_playing = 1;

    LOGI("Playing: %s (%dHz, %d ch, %d bits)", file_path, header.sample_rate, header.channels, header.bits);

    pthread_mutex_unlock(&g_engine.lock);
    (*env)->ReleaseStringUTFChars(env, path, file_path);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_geek_carplayer_NativeAudioEngine_nativePause(JNIEnv* env, jclass clazz) {
    pthread_mutex_lock(&g_engine.lock);
    g_engine.is_playing = 0;
    pthread_mutex_unlock(&g_engine.lock);
}

JNIEXPORT void JNICALL
Java_com_geek_carplayer_NativeAudioEngine_nativeResume(JNIEnv* env, jclass clazz) {
    pthread_mutex_lock(&g_engine.lock);
    if (g_engine.current_file != NULL) {
        g_engine.is_playing = 1;
    }
    pthread_mutex_unlock(&g_engine.lock);
}

JNIEXPORT void JNICALL
Java_com_geek_carplayer_NativeAudioEngine_nativeStop(JNIEnv* env, jclass clazz) {
    pthread_mutex_lock(&g_engine.lock);
    g_engine.is_playing = 0;
    if (g_engine.current_file) {
        fclose(g_engine.current_file);
        g_engine.current_file = NULL;
    }
    pthread_mutex_unlock(&g_engine.lock);
}

JNIEXPORT void JNICALL
Java_com_geek_carplayer_NativeAudioEngine_nativeSetEQ(JNIEnv* env, jclass clazz, jfloat bass, jfloat mid, jfloat treble) {
    pthread_mutex_lock(&g_engine.lock);
    dsp_set_eq(&g_engine.dsp, bass, mid, treble);
    pthread_mutex_unlock(&g_engine.lock);
}

JNIEXPORT void JNICALL
Java_com_geek_carplayer_NativeAudioEngine_nativeSetStereoWidth(JNIEnv* env, jclass clazz, jfloat width) {
    pthread_mutex_lock(&g_engine.lock);
    dsp_set_stereo_width(&g_engine.dsp, width);
    pthread_mutex_unlock(&g_engine.lock);
}

JNIEXPORT void JNICALL
Java_com_geek_carplayer_NativeAudioEngine_nativeSetVolume(JNIEnv* env, jclass clazz, jfloat volume) {
    pthread_mutex_lock(&g_engine.lock);
    dsp_set_volume(&g_engine.dsp, volume);
    pthread_mutex_unlock(&g_engine.lock);
}

JNIEXPORT jint JNICALL
Java_com_geek_carplayer_NativeAudioEngine_nativeGetProgressPercent(JNIEnv* env, jclass clazz) {
    if (g_engine.total_data_bytes == 0) return 0;
    int progress = (int)((1.0f - (float)g_engine.data_bytes_left / (float)g_engine.total_data_bytes) * 100);
    return progress < 0 ? 0 : (progress > 100 ? 100 : progress);
}
