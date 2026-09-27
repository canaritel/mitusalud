import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Configuración de Vite. "server" solo afecta al servidor de desarrollo (npm run dev).
export default defineConfig({
  plugins: [react()],
  server: {
    // Solo accesible desde este equipo, igual que el backend (server.address=127.0.0.1).
    host: '127.0.0.1',
    // El navegador solo habla con Vite, y Vite reenvía /api al backend: así no hace falta CORS.
    // 127.0.0.1 y no "localhost": Node puede resolver localhost a IPv6 (::1), donde el backend no escucha.
    proxy: {
      '/api': 'http://127.0.0.1:8080',
    },
  },
})
