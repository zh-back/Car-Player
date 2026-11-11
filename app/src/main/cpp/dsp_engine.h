#ifndef DSP_ENGINE_H
#define DSP_ENGINE_H

#include <math.h>

// 双二阶滤波器状态结构体 (Direct Form II Transposed)
typedef struct {
    float b0, b1, b2; // 前馈系数
    float a1, a2;     // 反馈系数
    float s1, s2;     // 内部历史状态
} BiquadFilter;

// 车载 DSP 控制参数与状态
typedef struct {
    // 3段均衡器增益 (dB, 例如 -12.0dB 到 +12.0dB)
    float bass_gain_db;    // 低音 (约 100Hz)
    float mid_gain_db;     // 中音/人声 (约 1000Hz)
    float treble_gain_db;  // 高音 (约 10000Hz)

    // 车载空间感：立体声拓宽 (0.0: 原声, 1.0: 极强环绕)
    float stereo_width;

    // 软限幅防止爆音
    float master_volume;

    // 左右声道独立的双二阶滤波器
    BiquadFilter bass_filter_L, bass_filter_R;
    BiquadFilter mid_filter_L,  mid_filter_R;
    BiquadFilter treble_filter_L, treble_filter_R;

    int sample_rate;
} CarDspEngine;

void dsp_init(CarDspEngine* dsp, int sample_rate);
void dsp_update_filters(CarDspEngine* dsp);
void dsp_set_eq(CarDspEngine* dsp, float bass_db, float mid_db, float treble_db);
void dsp_set_stereo_width(CarDspEngine* dsp, float width);
void dsp_set_volume(CarDspEngine* dsp, float volume);

// 对交错双声道 float PCM 帧进行原地 DSP 处理 (L, R, L, R...)
void dsp_process_interleaved(CarDspEngine* dsp, float* buffer, int num_frames);

#endif // DSP_ENGINE_H
