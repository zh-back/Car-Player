#include "dsp_engine.h"
#include <string.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// 计算 RBJ Peaking EQ (峰值均衡滤波器) 系数
static void setup_peaking_eq(BiquadFilter* f, float freq, float gain_db, float Q, float sample_rate) {
    if (sample_rate <= 0) sample_rate = 44100;

    float A = powf(10.0f, gain_db / 40.0f); // 增益线性幅度
    float w0 = 2.0f * (float)M_PI * freq / sample_rate;
    float alpha = sinf(w0) / (2.0f * Q);

    float b0 = 1.0f + alpha * A;
    float b1 = -2.0f * cosf(w0);
    float b2 = 1.0f - alpha * A;
    float a0 = 1.0f + alpha / A;
    float a1 = -2.0f * cosf(w0);
    float a2 = 1.0f - alpha / A;

    // 归一化并赋值
    f->b0 = b0 / a0;
    f->b1 = b1 / a0;
    f->b2 = b2 / a0;
    f->a1 = a1 / a0;
    f->a2 = a2 / a0;
}

// 单个采样的双二阶滤波运算
static inline float biquad_process(BiquadFilter* f, float in) {
    float out = f->b0 * in + f->s1;
    f->s1 = f->b1 * in - f->a1 * out + f->s2;
    f->s2 = f->b2 * in - f->a2 * out;
    return out;
}

void dsp_init(CarDspEngine* dsp, int sample_rate) {
    memset(dsp, 0, sizeof(CarDspEngine));
    dsp->sample_rate = sample_rate > 0 ? sample_rate : 44100;
    dsp->bass_gain_db = 3.0f;     // 车载默认稍微推一点低频，氛围更好
    dsp->mid_gain_db = 1.0f;      // 人声稍微前凸一点
    dsp->treble_gain_db = 0.0f;
    dsp->stereo_width = 0.25f;    // 默认开启轻度声场拓宽
    dsp->master_volume = 1.0f;
    dsp_update_filters(dsp);
}

void dsp_update_filters(CarDspEngine* dsp) {
    // 低音: 100Hz, Q=1.0
    setup_peaking_eq(&dsp->bass_filter_L, 100.0f, dsp->bass_gain_db, 1.0f, (float)dsp->sample_rate);
    dsp->bass_filter_R = dsp->bass_filter_L;

    // 中音/人声: 1000Hz, Q=1.2
    setup_peaking_eq(&dsp->mid_filter_L, 1000.0f, dsp->mid_gain_db, 1.2f, (float)dsp->sample_rate);
    dsp->mid_filter_R = dsp->mid_filter_L;

    // 高音: 10000Hz, Q=0.8
    setup_peaking_eq(&dsp->treble_filter_L, 10000.0f, dsp->treble_gain_db, 0.8f, (float)dsp->sample_rate);
    dsp->treble_filter_R = dsp->treble_filter_L;
}

void dsp_set_eq(CarDspEngine* dsp, float bass_db, float mid_db, float treble_db) {
    dsp->bass_gain_db = bass_db;
    dsp->mid_gain_db = mid_db;
    dsp->treble_gain_db = treble_db;
    dsp_update_filters(dsp);
}

void dsp_set_stereo_width(CarDspEngine* dsp, float width) {
    if (width < 0.0f) width = 0.0f;
    if (width > 1.5f) width = 1.5f;
    dsp->stereo_width = width;
}

void dsp_set_volume(CarDspEngine* dsp, float volume) {
    if (volume < 0.0f) volume = 0.0f;
    if (volume > 2.0f) volume = 2.0f;
    dsp->master_volume = volume;
}

// 快速多项式软限幅 (Soft Clipper)，防止低频推太大产生破音/破喇叭
static inline float soft_clip(float x) {
    if (x > 1.2f) return 1.0f;
    if (x < -1.2f) return -1.0f;
    return x - (x * x * x) * 0.16666f;
}

void dsp_process_interleaved(CarDspEngine* dsp, float* buffer, int num_frames) {
    float width = 1.0f + dsp->stereo_width;
    float vol = dsp->master_volume;

    for (int i = 0; i < num_frames; i++) {
        float left = buffer[i * 2];
        float right = buffer[i * 2 + 1];

        // 1. 三段均衡器处理 (EQ)
        left = biquad_process(&dsp->bass_filter_L, left);
        left = biquad_process(&dsp->mid_filter_L, left);
        left = biquad_process(&dsp->treble_filter_L, left);

        right = biquad_process(&dsp->bass_filter_R, right);
        right = biquad_process(&dsp->mid_filter_R, right);
        right = biquad_process(&dsp->treble_filter_R, right);

        // 2. 车载立体声空间声场拓宽 (Mid-Side Matrix)
        // Mid (中置信号,人声居多) = (L + R) * 0.5
        // Side (立体声环绕信号) = (L - R) * 0.5
        float mid = (left + right) * 0.5f;
        float side = (left - right) * 0.5f;

        // 适度强化 Side 信号，扩大人耳对车厢宽度的感知
        side *= width;

        left = mid + side;
        right = mid - side;

        // 3. 总音量缩放 + 软限幅防爆音
        buffer[i * 2]     = soft_clip(left * vol);
        buffer[i * 2 + 1] = soft_clip(right * vol);
    }
}
