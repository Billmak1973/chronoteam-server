import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  build: {
    //  關鍵：將打包輸出目錄指向 Spring Boot 的靜態資源目錄
    outDir: path.resolve(__dirname, '../src/main/resources/static/react-assets'),
    emptyOutDir: true, // 每次打包前清空舊文件
    rollupOptions: {
      output: {
        //  為了初期測試方便，我們先固定檔案名稱，不帶 hash
        entryFileNames: `assets/[name].js`,
        chunkFileNames: `assets/[name].js`,
        assetFileNames: `assets/[name].[ext]`
      }
    }
  },
  server: {
    proxy: {
      //  開發時，將 React 發出的 /api 請求代理到 Spring Boot (8080 port)
      '/api': 'http://localhost:8080'
    }
  }
})
