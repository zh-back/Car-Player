<template>
  <div class="flat-container">
    <!-- 1. 最左侧：极简扁平功能导航条 (Flat Navigation Rail) -->
    <aside class="nav-rail">
      <div class="brand-logo">
        <div class="logo-square">CP</div>
        <span class="logo-sub">CAR</span>
      </div>

      <nav class="nav-menu">
        <button
          class="nav-item"
          :class="{ active: currentTab === 'player' }"
          @click="currentTab = 'player'"
        >
          <span class="nav-icon">🎵</span>
          <span class="nav-text">播放器</span>
        </button>
        <button
          class="nav-item"
          :class="{ active: currentTab === 'library' }"
          @click="currentTab = 'library'"
        >
          <span class="nav-icon">📁</span>
          <span class="nav-text">U 盘曲库</span>
        </button>
        <button
          class="nav-item"
          :class="{ active: currentTab === 'dsp' }"
          @click="currentTab = 'dsp'"
        >
          <span class="nav-icon">🎛️</span>
          <span class="nav-text">极客调音</span>
        </button>
      </nav>

      <div class="nav-footer">
        <span class="status-indicator"></span>
        <span class="status-text">车载就绪</span>
      </div>
    </aside>

    <!-- 2. 中间：主播放与封面视觉区 (Now Playing Area) -->
    <main class="main-stage">
      <div class="stage-top">
        <div class="flat-badge-group">
          <span class="badge primary">HI-RES AUDIO</span>
          <span class="badge muted">{{ currentSong.format || 'PCM' }} • 48kHz / 24-bit</span>
          <span class="badge success">AAUDIO 直驱</span>
        </div>
        <div class="car-model-tag">比亚迪 DiLink 智享空间</div>
      </div>

      <div class="album-hero">
        <!-- 扁平几何艺术黑胶卡片 -->
        <div class="flat-disc-card" :class="{ spinning: isPlaying }">
          <div class="disc-pattern">
            <div class="disc-core">
              <span class="music-note">♪</span>
            </div>
          </div>
        </div>

        <div class="meta-info">
          <h1 class="song-title" :title="currentSong.title">{{ currentSong.title }}</h1>
          <p class="song-artist">本地无损音频 • U 盘车载极客引擎播放</p>
          
          <!-- 扁平极简频谱波形柱条 -->
          <div class="flat-spectrum" :class="{ active: isPlaying }">
            <div v-for="i in 18" :key="i" class="spectrum-bar"></div>
          </div>
        </div>
      </div>

      <!-- 扁平进度条与控制区域 -->
      <div class="stage-bottom">
        <div class="progress-section">
          <div class="progress-bar-flat" @click="handleProgressClick">
            <div class="progress-track-fill" :style="{ width: `${progress}%` }"></div>
          </div>
          <div class="time-row">
            <span>{{ formatTime(progress) }}</span>
            <span class="progress-pct">{{ progress }}%</span>
            <span>04:18</span>
          </div>
        </div>

        <!-- 扁平无边框交互控制组 (大尺寸防误触) -->
        <div class="flat-controls">
          <button class="flat-btn square" @click="handlePrev" title="上一曲">
            <svg viewBox="0 0 24 24" class="icon"><path fill="currentColor" d="M6 6h2v12H6zm3.5 6l8.5 6V6z"/></svg>
          </button>

          <button class="flat-btn hero-play" @click="handleTogglePlay" :title="isPlaying ? '暂停' : '播放'">
            <svg v-if="!isPlaying" viewBox="0 0 24 24" class="icon-play"><path fill="currentColor" d="M8 5v14l11-7z"/></svg>
            <svg v-else viewBox="0 0 24 24" class="icon-play"><path fill="currentColor" d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>
          </button>

          <button class="flat-btn square" @click="handleNext" title="下一曲">
            <svg viewBox="0 0 24 24" class="icon"><path fill="currentColor" d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"/></svg>
          </button>

          <button class="flat-btn action-scan" @click="handleScanUsb">
            <span class="scan-icon">⟳</span> 扫描 U 盘
          </button>
        </div>
      </div>
    </main>

    <!-- 3. 右侧：多功能平铺面板 (调音台 / U 盘曲库) -->
    <aside class="side-panel">
      <!-- A. 调音台视图 -->
      <section v-if="currentTab === 'player' || currentTab === 'dsp'" class="panel-section">
        <div class="panel-header">
          <h2 class="panel-title">车载 DSP 调音台</h2>
          <span class="panel-badge">硬件 EQ</span>
        </div>

        <!-- 扁平预设胶囊标签 -->
        <div class="preset-pills">
          <button class="preset-pill" :class="{ active: activePreset === 'default' }" @click="applyPreset('default')">标准均衡</button>
          <button class="preset-pill" :class="{ active: activePreset === 'bass' }" @click="applyPreset('bass')">动感低音</button>
          <button class="preset-pill" :class="{ active: activePreset === 'vocal' }" @click="applyPreset('vocal')">清澈人声</button>
          <button class="preset-pill" :class="{ active: activePreset === 'wide' }" @click="applyPreset('wide')">座舱环绕</button>
        </div>

        <div class="flat-slider-group">
          <div class="slider-block">
            <div class="slider-header">
              <span class="slider-name">重低音 (100Hz)</span>
              <span class="slider-val">{{ bass > 0 ? `+${bass}` : bass }} dB</span>
            </div>
            <input type="range" min="-12" max="12" v-model.number="bass" @input="syncEQ" />
          </div>

          <div class="slider-block">
            <div class="slider-header">
              <span class="slider-name">中音人声 (1kHz)</span>
              <span class="slider-val">{{ mid > 0 ? `+${mid}` : mid }} dB</span>
            </div>
            <input type="range" min="-12" max="12" v-model.number="mid" @input="syncEQ" />
          </div>

          <div class="slider-block">
            <div class="slider-header">
              <span class="slider-name">明亮高音 (10kHz)</span>
              <span class="slider-val">{{ treble > 0 ? `+${treble}` : treble }} dB</span>
            </div>
            <input type="range" min="-12" max="12" v-model.number="treble" @input="syncEQ" />
          </div>

          <div class="slider-block">
            <div class="slider-header">
              <span class="slider-name">声场立体声拓宽</span>
              <span class="slider-val">{{ stereoWidth }}%</span>
            </div>
            <input type="range" min="0" max="100" v-model.number="stereoWidth" @input="syncStereoWidth" />
          </div>
        </div>
      </section>

      <!-- B. U 盘曲库视图 -->
      <section class="panel-section flex-1">
        <div class="panel-header">
          <h2 class="panel-title">U 盘曲库</h2>
          <span class="count-tag">{{ playlist.length }} 首</span>
        </div>

        <div class="flat-list">
          <div
            v-for="(track, index) in playlist"
            :key="index"
            class="flat-row"
            :class="{ playing: currentSong.title === track.title }"
            @click="handleSelectTrack(index)"
          >
            <div class="row-num">{{ String(index + 1).padStart(2, '0') }}</div>
            <div class="row-info">
              <div class="row-title">{{ track.title }}</div>
              <div class="row-sub">本地存储 • 无损解码</div>
            </div>
            <div class="row-tag">{{ track.format }}</div>
          </div>

          <div v-if="playlist.length === 0" class="flat-empty">
            <div class="empty-icon">📁</div>
            <p>暂无扫描数据</p>
            <button class="flat-btn-outline" @click="handleScanUsb">点击扫描 U 盘</button>
          </div>
        </div>
      </section>
    </aside>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { carBridge } from './utils/carBridge'

const currentTab = ref('player')
const isPlaying = ref(false)
const progress = ref(0)
const bass = ref(3)
const mid = ref(1)
const treble = ref(0)
const stereoWidth = ref(30)
const activePreset = ref('default')

const playlist = ref([])
const currentSong = ref({
  title: '请点击扫描 U 盘选择音乐',
  format: 'WAV'
})

function syncEQ() {
  activePreset.value = 'custom'
  carBridge.setEQ(bass.value, mid.value, treble.value)
}

function syncStereoWidth() {
  carBridge.setStereoWidth(stereoWidth.value / 100)
}

function applyPreset(type) {
  activePreset.value = type
  if (type === 'default') {
    bass.value = 0; mid.value = 0; treble.value = 0; stereoWidth.value = 20;
  } else if (type === 'bass') {
    bass.value = 7; mid.value = 1; treble.value = -1; stereoWidth.value = 35;
  } else if (type === 'vocal') {
    bass.value = 1; mid.value = 5; treble.value = 2; stereoWidth.value = 15;
  } else if (type === 'wide') {
    bass.value = 3; mid.value = 2; treble.value = 3; stereoWidth.value = 80;
  }
  syncEQ()
  syncStereoWidth()
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

function handleProgressClick(e) {
  const rect = e.currentTarget.getBoundingClientRect()
  const clickX = e.clientX - rect.left
  const pct = Math.round((clickX / rect.width) * 100)
  progress.value = Math.max(0, Math.min(100, pct))
}

function formatTime(pct) {
  const totalSeconds = 258 // 4分18秒
  const current = Math.floor((pct / 100) * totalSeconds)
  const m = String(Math.floor(current / 60)).padStart(2, '0')
  const s = String(current % 60).padStart(2, '0')
  return `${m}:${s}`
}

onMounted(() => {
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
  handleScanUsb()
})
</script>

<style scoped>
/* ================= 全局扁平化设计变量与重置 ================= */
:root {
  --flat-bg: #101114;
  --flat-surface: #18191E;
  --flat-card: #202228;
  --flat-border: #2A2D36;
  --flat-primary: #00E5FF;
  --flat-accent: #3A86FF;
  --flat-text: #FFFFFF;
  --flat-sub: #8E92A4;
  --flat-radius: 12px;
}

.flat-container {
  display: flex;
  width: 100vw;
  height: 100vh;
  background-color: #101114;
  color: #FFFFFF;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Microsoft YaHei", sans-serif;
  overflow: hidden;
}

/* ================= 1. 最左侧扁平导航 Rail ================= */
.nav-rail {
  width: 90px;
  background: #14151A;
  border-right: 1px solid #23252E;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 0;
  flex-shrink: 0;
}

.brand-logo {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  margin-bottom: 36px;
}

.logo-square {
  width: 44px;
  height: 44px;
  background: #00E5FF;
  color: #000000;
  font-weight: 900;
  font-size: 18px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  letter-spacing: -1px;
}

.logo-sub {
  font-size: 10px;
  font-weight: 700;
  color: #8E92A4;
  letter-spacing: 2px;
}

.nav-menu {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  padding: 0 10px;
}

.nav-item {
  width: 100%;
  padding: 14px 0;
  background: transparent;
  border: none;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #8E92A4;
  transition: all 0.2s ease;
}

.nav-item:hover {
  color: #FFFFFF;
  background: #1C1E26;
}

.nav-item.active {
  color: #000000;
  background: #00E5FF;
  font-weight: 700;
}

.nav-icon {
  font-size: 20px;
}

.nav-text {
  font-size: 11px;
}

.nav-footer {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.status-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #00E676;
}

.status-text {
  font-size: 10px;
  color: #6C7082;
}

/* ================= 2. 中间主控制台 ================= */
.main-stage {
  flex: 1.35;
  padding: 32px 40px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  border-right: 1px solid #23252E;
}

.stage-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.flat-badge-group {
  display: flex;
  gap: 8px;
}

.badge {
  font-size: 11px;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 6px;
  letter-spacing: 0.5px;
}

.badge.primary { background: #00E5FF; color: #000000; }
.badge.muted { background: #20222B; color: #A6AAB8; }
.badge.success { background: rgba(0, 230, 118, 0.15); color: #00E676; }

.car-model-tag {
  font-size: 13px;
  color: #72778B;
  font-weight: 500;
}

/* 唱片与歌曲信息区 */
.album-hero {
  display: flex;
  align-items: center;
  gap: 36px;
  margin: 20px 0;
}

.flat-disc-card {
  width: 170px;
  height: 170px;
  background: #181920;
  border-radius: 20px;
  border: 1px solid #2A2D3A;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.disc-pattern {
  width: 130px;
  height: 130px;
  border-radius: 50%;
  background: repeating-radial-gradient(circle, #1F212A, #1F212A 6px, #16171E 7px, #16171E 12px);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 4px solid #282B36;
}

.flat-disc-card.spinning .disc-pattern {
  animation: discSpin 14s linear infinite;
}

@keyframes discSpin {
  100% { transform: rotate(360deg); }
}

.disc-core {
  width: 44px;
  height: 44px;
  background: #00E5FF;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #000000;
  font-weight: 900;
  font-size: 20px;
}

.meta-info {
  flex: 1;
  overflow: hidden;
}

.song-title {
  font-size: 34px;
  font-weight: 800;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 8px;
  letter-spacing: -0.5px;
}

.song-artist {
  font-size: 15px;
  color: #8E92A4;
  margin-bottom: 20px;
}

/* 扁平纯色频谱柱条 */
.flat-spectrum {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  height: 28px;
}

.spectrum-bar {
  width: 4px;
  height: 6px;
  background: #252834;
  border-radius: 2px;
  transition: height 0.2s ease;
}

.flat-spectrum.active .spectrum-bar {
  background: #00E5FF;
  animation: spectrumBounce 0.9s ease-in-out infinite alternate;
}

.spectrum-bar:nth-child(2n) { animation-delay: 0.15s; }
.spectrum-bar:nth-child(3n) { animation-delay: 0.3s; }
.spectrum-bar:nth-child(4n) { animation-delay: 0.45s; }

@keyframes spectrumBounce {
  0% { height: 6px; }
  100% { height: 26px; }
}

/* 扁平进度条 */
.progress-section {
  margin-bottom: 24px;
}

.progress-bar-flat {
  width: 100%;
  height: 8px;
  background: #22242E;
  border-radius: 4px;
  cursor: pointer;
  overflow: hidden;
}

.progress-track-fill {
  height: 100%;
  background: #00E5FF;
  border-radius: 4px;
  transition: width 0.2s linear;
}

.time-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #72778B;
  font-weight: 600;
  margin-top: 8px;
}

.progress-pct {
  color: #00E5FF;
}

/* 扁平无边框交互控制组 */
.flat-controls {
  display: flex;
  align-items: center;
  gap: 20px;
}

.flat-btn {
  border: none;
  background: #1C1E26;
  color: #FFFFFF;
  border-radius: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, transform 0.1s;
}

.flat-btn:active {
  transform: scale(0.96);
}

.flat-btn.square {
  width: 68px;
  height: 60px;
}

.flat-btn.square:hover {
  background: #282B36;
}

.flat-btn .icon {
  width: 28px;
  height: 28px;
}

.flat-btn.hero-play {
  width: 110px;
  height: 68px;
  background: #00E5FF;
  color: #000000;
  border-radius: 16px;
}

.flat-btn.hero-play:hover {
  background: #33EBFF;
}

.icon-play {
  width: 38px;
  height: 38px;
}

.flat-btn.action-scan {
  height: 60px;
  padding: 0 24px;
  background: #222530;
  color: #FFFFFF;
  font-size: 15px;
  font-weight: 600;
  margin-left: auto;
  gap: 8px;
}

.flat-btn.action-scan:hover {
  background: #2E3240;
}

.scan-icon {
  font-size: 18px;
}

/* ================= 3. 右侧多功能平铺面板 ================= */
.side-panel {
  flex: 1.15;
  background: #131419;
  padding: 28px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  overflow: hidden;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.panel-title {
  font-size: 16px;
  font-weight: 700;
  color: #E2E4EC;
  letter-spacing: -0.3px;
}

.panel-badge {
  font-size: 11px;
  background: #222530;
  color: #8E92A4;
  padding: 3px 8px;
  border-radius: 6px;
}

.count-tag {
  font-size: 12px;
  color: #00E5FF;
  font-weight: 700;
}

/* 扁平胶囊预设 */
.preset-pills {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.preset-pill {
  flex: 1;
  padding: 7px 0;
  background: #1C1E26;
  border: 1px solid #282A36;
  border-radius: 8px;
  color: #A6AAB8;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}

.preset-pill.active {
  background: #00E5FF;
  color: #000000;
  border-color: #00E5FF;
}

/* 扁平滑块组 */
.flat-slider-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: #17181F;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid #22242E;
}

.slider-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.slider-header {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #8E92A4;
}

.slider-val {
  color: #00E5FF;
  font-weight: 700;
}

input[type='range'] {
  width: 100%;
  height: 6px;
  background: #282B36;
  border-radius: 3px;
  outline: none;
  accent-color: #00E5FF;
  cursor: pointer;
}

/* 扁平曲目列表 */
.flat-list {
  flex: 1;
  overflow-y: auto;
  background: #17181F;
  border-radius: 12px;
  border: 1px solid #22242E;
  padding: 8px;
}

.flat-row {
  display: flex;
  align-items: center;
  padding: 12px 14px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: background 0.15s;
}

.flat-row:hover {
  background: #222530;
}

.flat-row.playing {
  background: #202430;
  border-left: 3px solid #00E5FF;
}

.row-num {
  font-size: 13px;
  color: #6C7082;
  font-weight: 700;
  width: 28px;
}

.row-info {
  flex: 1;
  overflow: hidden;
  padding-right: 12px;
}

.row-title {
  font-size: 14px;
  font-weight: 600;
  color: #FFFFFF;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.flat-row.playing .row-title {
  color: #00E5FF;
}

.row-sub {
  font-size: 11px;
  color: #6C7082;
  margin-top: 2px;
}

.row-tag {
  font-size: 11px;
  background: #242733;
  color: #8E92A4;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 700;
}

.flat-empty {
  text-align: center;
  padding: 30px 0;
  color: #6C7082;
}

.empty-icon {
  font-size: 36px;
  margin-bottom: 8px;
}

.flat-btn-outline {
  margin-top: 12px;
  background: transparent;
  border: 1px solid #00E5FF;
  color: #00E5FF;
  padding: 8px 16px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
}
</style>
