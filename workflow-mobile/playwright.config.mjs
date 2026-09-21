import { defineConfig } from '@playwright/test'
import { existsSync } from 'node:fs'
const chrome = process.env.PLAYWRIGHT_EXECUTABLE_PATH || (process.platform === 'darwin' ? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' : '')
export default defineConfig({
  testDir: './tests/e2e', timeout: 30000, fullyParallel: false, workers: 1,
  outputDir: '../.codex-artifacts/mobile/e2e-results', reporter: [['list']],
  use: { baseURL: 'http://127.0.0.1:43191/m/', viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, trace: 'retain-on-failure', launchOptions: chrome && existsSync(chrome) ? { executablePath: chrome } : {} },
  webServer: { command: 'npm run dev -- --host 127.0.0.1 --port 43191', url: 'http://127.0.0.1:43191/m/', reuseExistingServer: false, timeout: 30000 }
})
