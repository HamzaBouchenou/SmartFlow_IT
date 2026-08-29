/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // ADR-01 - en dev local (npm run dev, sans Docker Compose), le proxy Vite tient le
    // même rôle que nginx.conf en production : le navigateur ne voit qu'une seule origine
    // (localhost:5173) donc le cookie de session et le cookie CSRF circulent sans
    // configuration CORS, exactement comme en conteneur.
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/v3/api-docs': { target: 'http://localhost:8080', changeOrigin: true },
      '/swagger-ui': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // Sans ça, les compteurs d'appel d'un mock (vi.mocked(...).mock.calls) s'accumulent
    // d'un test à l'autre dans le même fichier - un test qui affirme "appelé N fois"
    // compterait aussi les appels des tests précédents du même describe.
    clearMocks: true,
  },
})
