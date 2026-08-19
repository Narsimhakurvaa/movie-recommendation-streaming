import { defineConfig, mergeConfig } from 'vitest/config';
import viteConfig from './vite.config.ts';

/**
 * Test configuration.
 *
 * Kept separate from `vite.config.ts` so the build config stays typed against
 * Vite's own `UserConfig`, which does not include the `test` key.
 */
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      css: false,
      coverage: {
        provider: 'v8',
        reporter: ['text', 'html', 'lcov'],
        exclude: [
          'src/test/**',
          '**/*.d.ts',
          'src/main.tsx',
          '**/*.config.*',
          'src/types/**',
        ],
      },
    },
  }),
);
