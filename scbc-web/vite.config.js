import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'node:path';

export default defineConfig({
  plugins: [react(), tailwindcss()],

  resolve: {
    alias: {
      '@': path.resolve(process.cwd(), 'src'),
    },
  },

  server: {
    port: 5173,
    // Fail rather than drift. Without this, a second `npm run dev` quietly
    // starts on 5174, a third on 5175, and the API rejects them: the proxy
    // below forwards the browser's Origin header unchanged, and anything not
    // in scbc.cors.allowed-origins is refused by Spring's CORS filter with a
    // bare 403 and no body. That failure surfaces as "login returns 403" with
    // nothing in it to suggest the port is the problem, so it is much better
    // to be told the port is taken up front.
    strictPort: true,

    // Proxying /api keeps the browser on a single origin during development,
    // so the JSESSIONID and XSRF-TOKEN cookies are first-party. Note that
    // changeOrigin rewrites Host but not Origin, so the API still sees the dev
    // server's origin and its CORS rules still apply.
    // In production point VITE_API_BASE_URL at the API host instead and rely
    // on the server's CORS configuration.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
