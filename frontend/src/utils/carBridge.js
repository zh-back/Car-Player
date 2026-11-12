/**
 * 车机 JSBridge 跨端桥梁
 * 运行在车机时直接调用 window.CarPlayer
 * 运行在电脑浏览器时自动启用 Mock 模拟数据
 */

const isCar = () => typeof window.CarPlayer !== 'undefined'

export const carBridge = {
  // 1. 播放指定索引的歌曲
  play(index) {
    if (isCar()) {
      window.CarPlayer.play(index)
    } else {
      console.log('[浏览器模拟] 播放曲目索引:', index)
    }
  },

  // 2. 暂停/继续切换
  togglePlay() {
    if (isCar()) {
      window.CarPlayer.togglePlay()
    } else {
      console.log('[浏览器模拟] 切换播放/暂停')
    }
  },

  // 3. 上一曲
  playPrev() {
    if (isCar()) {
      window.CarPlayer.playPrev()
    } else {
      console.log('[浏览器模拟] 上一曲')
    }
  },

  // 4. 下一曲
  playNext() {
    if (isCar()) {
      window.CarPlayer.playNext()
    } else {
      console.log('[浏览器模拟] 下一曲')
    }
  },

  // 5. 实时调节三段 EQ (低音、中音人声、高音)
  setEQ(bass, mid, treble) {
    if (isCar()) {
      window.CarPlayer.setEQ(bass, mid, treble)
    } else {
      console.log(`[浏览器模拟] 调音: 低音=${bass}dB, 中音=${mid}dB, 高音=${treble}dB`)
    }
  },

  // 6. 调节车载声场拓宽 (0.0 ~ 1.0)
  setStereoWidth(width) {
    if (isCar()) {
      window.CarPlayer.setStereoWidth(width)
    } else {
      console.log(`[浏览器模拟] 声场拓宽: ${width * 100}%`)
    }
  },

  // 7. 扫描 U 盘歌曲
  scanUsb() {
    if (isCar()) {
      try {
        const jsonStr = window.CarPlayer.scanUsb()
        return JSON.parse(jsonStr)
      } catch (e) {
        console.error('解析车机歌曲列表失败:', e)
        return []
      }
    } else {
      // 电脑浏览器环境下的假数据
      return [
        { title: 'Hotel California (现场加长版)', format: 'FLAC', path: '/usb/hotel_california.flac' },
        { title: '渡口 - 蔡琴 (专业试音曲)', format: 'WAV', path: '/usb/dukou.wav' },
        { title: '加州旅馆 (吉他独奏开场)', format: 'FLAC', path: '/usb/hotel_solo.flac' },
        { title: '偏偏喜欢你 - 陈百强 (弦乐重奏)', format: 'WAV', path: '/usb/love_you.wav' },
        { title: '达坂城的姑娘 (八音盒人声)', format: 'MP3', path: '/usb/daban.mp3' }
      ]
    }
  },

  // 8. 注册车机推送到前端的回调函数
  registerCallbacks(callbacks) {
    window.onCarTrackChanged = (title, format) => {
      if (callbacks.onTrackChanged) callbacks.onTrackChanged({ title, format })
    }
    window.onCarPlayStateChanged = (isPlaying) => {
      if (callbacks.onPlayStateChanged) callbacks.onPlayStateChanged(isPlaying)
    }
    window.onCarProgressChanged = (progressPercent) => {
      if (callbacks.onProgressChanged) callbacks.onProgressChanged(progressPercent)
    }
  }
}
