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
    // También las rutas de acceso (entrar, salir, passkeys): así la cookie de sesión es del mismo origen
    // que la pantalla, y WebAuthn ve el origen configurado en el backend (http://localhost:5173).
    proxy: Object.fromEntries(
      ['/api', '/login', '/logout', '/webauthn', '/default-ui.css'].map((ruta) => [
        ruta,
        // changeOrigin: false conserva la cabecera Host (localhost:5173): así las redirecciones del backend
        // vuelven a la pantalla y no a 127.0.0.1:8080, donde el navegador no tiene la cookie.
        { target: 'http://127.0.0.1:8080', changeOrigin: false },
      ]),
    ),
  },
})
