/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
    // For index.css.test.ts's `?raw` import of the repo-root DESIGN.md. Entries match as directory prefixes, so
    // naming the file itself is denied; widening to `..` is test-only because the root holds dev.properties.
    fs: mode === 'test' ? { allow: ['.', '..'] } : undefined,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/shared/test/animationEvent.ts', './src/shared/test/setup.ts'],
    css: true,
    reporters: [
      'default',
      ['html', { outputDir: './test-report' }],
      ['junit', { outputFile: './test-report/junit.xml' }],
    ],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/shared/test/**', 'src/main.tsx', 'src/shared/api/types.ts'],
      thresholds: {
        lines: 80,
        statements: 80,
        functions: 80,
        branches: 80,
      },
    },
  },
}));
