import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': new URL('./src', import.meta.url).pathname },
  },
  server: {
    // Bound to all interfaces so the dev server is reachable from outside the
    // container (Docker, remote preview environments).
    host: '0.0.0.0',
    port: 5173,
    // Any host may connect. The dev server is never exposed publicly; this
    // avoids host-header rejections behind proxies and preview tunnels.
    allowedHosts: true,
    proxy: {
      // The browser is not necessarily on the same host as the API, so calls
      // use relative URLs and the dev server forwards them. This also means
      // no CORS preflight in development.
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  preview: {
    host: '0.0.0.0',
    port: 4173,
    allowedHosts: true,
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        // Split rarely-changing vendor code into its own chunks so an app
        // deploy does not invalidate the cached framework bundle.
        manualChunks(id: string) {
          if (!id.includes('node_modules')) return undefined;
          if (/[\\/]node_modules[\\/](react|react-dom|react-router|react-router-dom|scheduler)[\\/]/.test(id)) {
            return 'react-vendor';
          }
          if (/[\\/]node_modules[\\/](@tanstack|axios)[\\/]/.test(id)) {
            return 'query-vendor';
          }
          if (/[\\/]node_modules[\\/](react-hook-form|zod|@hookform)[\\/]/.test(id)) {
            return 'form-vendor';
          }
          return undefined;
        },
      },
    },
  },
});
