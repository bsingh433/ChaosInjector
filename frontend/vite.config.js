import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// During development the React app runs on :5173 and proxies API + SSE calls to
// the Spring Boot backend on :8080, so each project can be launched and debugged
// in its own IDE session. The production build emits to dist/, which the backend
// bundles into its JAR (see chaos_injector_spec.md §5).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js',
  },
});
