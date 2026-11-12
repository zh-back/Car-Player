<template>
  <div class="car-dashboard">
    <!-- 左侧主播放区 (占据 60% 宽度，大按钮大字体适合行车交互) -->
    <div class="player-main">
      <div class="header-tag">
        <span class="badge-hires">HI-RES AUDIO</span>
        <span class="badge-format">{{ currentSong.format || 'PCM' }} 48kHz / 24-bit</span>
      </div>

      <div class="song-info">
        <h1 class="song-title">{{ currentSong.title }}</h1>
        <p class="song-desc">已连接比亚迪 DiLink 音响系统 • C 语言 AAudio 直驱</p>
      </div>

      <!-- 动态音波律动动画 (随播放状态跳动) -->
      <div class="waveform" :class="{ playing: isPlaying }">
        <span v-for="i in 24" :key="i" class="wave-bar" :style="{ animationDelay: `${(i % 5) * 0.15}s` }"></span>
      </div>

      <!-- 进度条 -->
      <div class="progress-wrap">
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: `${progress}%` }"></div>
        </div>
        <div class="progress-text">{{ progress }}%</div>
      </div>

      <!-- 大尺寸物理感控制按钮 -->
      <div class="controls-row">
        <button class="btn btn-side" @click="handlePrev" title="上一曲">⏮</button>
        <button class="btn btn-play" @click="handleTogglePlay" :title="isPlaying ? '暂停' : '播放'">
          {{ isPlaying ? '⏸' : '▶' }}
        </button>
        <button class="btn btn-side" @click="handleNext" title="下一曲">⏭</button>
        <button class="btn btn-scan" @click="handleScanUsb">📂 扫描 U 盘</button>
      </div>
    </div>

    <!-- 右侧 DSP 极客调音台与曲库 (占据 40% 宽度) -->
    <div class="player-sidebar">
      <div class="section-title">🎛️ 车载极客 DSP 实时调音台</div>

      <div class="eq-control">
        <div class="eq-label">
          <span>重低音 (100Hz)</span>
          <span class="val-tag">{{ bass > 0 ? `+${bass}` : bass }} dB</span>
        </div>
        <input type="range" min="-12" max="12" v-model.number="bass" @input="syncEQ" />
      </div>

      <div class="eq-control">
        <div class="eq-label">
          <span>中音人声 (1kHz)</span>
          <span class="val-tag">{{ mid > 0 ? `+${mid}` : mid }} dB</span>
        </div>
        <input type="range" min="-12" max="12" v-model.number="mid" @input="syncEQ" />
      </div>

      <div class="eq-control">
        <div class="eq-label">
          <span>明亮高音 (10kHz)</span>
          <span class="val-tag">{{ treble > 0 ? `+${treble}` : treble }} dB</span>
        </div>
        <input type="range" min="-12" max="12" v-model.number="treble" @input="syncEQ" />
      </div>

      <div class="eq-control">
        <div class="eq-label">
          <span>车载立体声场拓宽 (Mid-Side)</span>
          <span class="val-tag">{{ stereoWidth }}%</span>
        </div>
        <input type="range" min="0" max="100" v-model.number="stereoWidth" @input="syncStereoWidth" />
      </div>

      <div class="section-title mt-4">📁 U 盘曲目列表 ({{ playlist.length }} 首)</div>
      <div class="track-list">
        <div
          v-for="(track, index) in playlist"
          :key="index"
          class="track-item"
          :class="{ active: currentSong.title === track.title }"
          @click="handleSelectTrack(index)"
        >
          <span class="track-name">{{ index + 1 }}. {{ track.title }}</span>
          <span class="track-tag">{{ track.format }}</span>
        </div>
        <div v-if="playlist.length === 0" class="empty-hint">暂未扫描到歌曲，请插入 U 盘并点击扫描</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { carBridge } from './utils/carBridge'

const isPlaying = ref(false)
const progress = ref(0)
const bass = ref(3)
const mid = ref(1)
const treble = ref(0)
const stereoWidth = ref(30)

const playlist = ref([])
const currentSong = ref({
  title: '请点击扫描 U 盘选择音乐',
  format: 'WAV'
})

function syncEQ() {
  carBridge.setEQ(bass.value, mid.value, treble.value)
}

function syncStereoWidth() {
  carBridge.setStereoWidth(stereoWidth.value / 100)
}

function handleTogglePlay() {
  isPlaying.value = !isPlaying.value
  carBridge.togglePlay()
}

function handlePrev() {
  carBridge.playPrev()
}

function handleNext() {
  carBridge.playNext()
}

function handleSelectTrack(index) {
  if (playlist.value[index]) {
    currentSong.value = playlist.value[index]
    isPlaying.value = true
    carBridge.play(index)
  }
}

function handleScanUsb() {
  playlist.value = carBridge.scanUsb()
  if (playlist.value.length > 0 && currentSong.value.title.includes('请点击扫描')) {
    currentSong.value = playlist.value[0]
  }
}

onMounted(() => {
  // 注册车机端主动推送的事件监听
  carBridge.registerCallbacks({
    onTrackChanged: (track) => {
      currentSong.value = track
    },
    onPlayStateChanged: (state) => {
      isPlaying.value = state
    },
    onProgressChanged: (pct) => {
      progress.value = pct
    }
  })

  // 默认启动时扫描一次 U 盘
  handleScanUsb()
})
</script>

<style scoped>
.car-dashboard {
  display: flex;
  width: 100vw;
  height: 100vh;
  background: #0f0f13;
  color: #ffffff;
  overflow: hidden;
}

/* 左侧主控制区 */
.player-main {
  flex: 1.25;
  padding: 40px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  border-right: 1px solid #1f1f28;
}

.header-tag {
  display: flex;
  gap: 12px;
  align-items: center;
}

.badge-hires {
  background: #ffb300;
  color: #000;
  font-size: 11px;
  font-weight: 800;
  padding: 3px 8px;
  border-radius: 4px;
  letter-spacing: 1px;
}

.badge-format {
  background: rgba(0, 229, 255, 0.12);
  color: #00e5ff;
  font-size: 12px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 4px;
}

.song-info {
  margin-top: 10px;
}

.song-title {
  font-size: 36px;
  font-weight: 800;
  color: #ffffff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.song-desc {
  font-size: 15px;
  color: #7a7a90;
  margin-top: 6px;
}

/* 音频律动波形 */
.waveform {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  height: 48px;
  margin: 10px 0;
}

.wave-bar {
  width: 5px;
  height: 8px;
  background: #252535;
  border-radius: 3px;
  transition: all 0.2s;
}

.waveform.playing .wave-bar {
  background: linear-gradient(180deg, #00e5ff, #0077ff);
  animation: waveAnim 0.8s ease-in-out infinite alternate;
}

@keyframes waveAnim {
  0% { height: 8px; }
  100% { height: 44px; }
}

/* 进度条 */
.progress-wrap {
  display: flex;
  align-items: center;
  gap: 16px;
}

.progress-bar {
  flex: 1;
  height: 8px;
  background: #22222e;
  border-radius: 4px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #00b0ff, #00e5ff);
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 14px;
  color: #8888aa;
  min-width: 45px;
}

/* 控制按钮组 */
.controls-row {
  display: flex;
  align-items: center;
  gap: 24px;
}

.btn {
  border: none;
  border-radius: 14px;
  cursor: pointer;
  transition: transform 0.15s, background-color 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn:active {
  transform: scale(0.95);
}

.btn-side {
  width: 75px;
  height: 64px;
  background: #20202a;
  color: #ffffff;
  font-size: 26px;
}

.btn-play {
  width: 120px;
  height: 75px;
  background: #00e5ff;
  color: #000000;
  font-size: 34px;
  font-weight: 800;
  box-shadow: 0 8px 25px rgba(0, 229, 255, 0.3);
}

.btn-scan {
  height: 64px;
  padding: 0 24px;
  background: #2a2a38;
  color: #ffffff;
  font-size: 16px;
  font-weight: 600;
  margin-left: auto;
}

/* 右侧边栏 */
.player-sidebar {
  flex: 1;
  background: #14141a;
  padding: 30px;
  display: flex;
  flex-direction: column;
}

.section-title {
  font-size: 16px;
  font-weight: 700;
  color: #e0e0f0;
  margin-bottom: 12px;
}

.mt-4 {
  margin-top: 18px;
}

.eq-control {
  margin-bottom: 10px;
}

.eq-label {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #8f8fa8;
  margin-bottom: 5px;
}

.val-tag {
  color: #00e5ff;
  font-weight: 700;
}

input[type='range'] {
  width: 100%;
  height: 6px;
  background: #252535;
  border-radius: 3px;
  outline: none;
  accent-color: #00e5ff;
}

/* 曲目列表 */
.track-list {
  flex: 1;
  overflow-y: auto;
  background: #0d0d12;
  border-radius: 12px;
  border: 1px solid #1f1f2a;
  padding: 8px;
}

.track-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px;
  border-radius: 8px;
  font-size: 14px;
  margin-bottom: 4px;
  cursor: pointer;
  background: #15151c;
  transition: all 0.15s;
}

.track-item:hover,
.track-item.active {
  background: #1f1f2c;
  color: #00e5ff;
  border-left: 3px solid #00e5ff;
}

.track-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 80%;
}

.track-tag {
  font-size: 11px;
  background: #262635;
  padding: 2px 6px;
  border-radius: 4px;
  color: #888899;
}

.empty-hint {
  text-align: center;
  color: #555566;
  font-size: 13px;
  margin-top: 30px;
}
</style>
