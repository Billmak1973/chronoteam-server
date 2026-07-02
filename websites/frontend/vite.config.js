import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  build: {
    //  關鍵 1：直接使用相對路徑，將打包輸出目錄指向 Spring Boot 的靜態資源目錄
    // 因為 vite.config.js 在 frontend 目錄下，所以 ../ 會回到 website 目錄
    outDir: '../src/main/resources/static/react-assets',
    emptyOutDir: true, // 每次打包前清空舊文件，保持目錄整潔
    rollupOptions: {
      output: {
        //  關鍵 2：固定檔案名稱，不帶 hash，確保 HTML 中的 th:src 能準確找到
        entryFileNames: `assets/[name].js`,
        chunkFileNames: `assets/[name].js`,
        assetFileNames: `assets/[name].[ext]`
      }
    }
  },
  server: {
    proxy: {
      // 關鍵 3：開發時，將 React 發出的 /api 請求代理到 Spring Boot (8080 port)
      '/api': 'http://localhost:8080'
    }
  }
})
