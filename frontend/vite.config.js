import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  base: './', // 关键：使用相对路径以支持 Android WebView 的本地 file:///android_asset 协议
  build: {
    // 关键：打包直接输出到 Android 项目的 assets 目录！
    outDir: path.resolve(__dirname, '../app/src/main/assets'),
    emptyOutDir: true,
    assetsDir: 'static',
  },
  server: {
    port: 5173,
    open: true
  }
})
