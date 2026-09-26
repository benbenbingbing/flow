import { defineConfig } from '@playwright/test'
import { existsSync } from 'node:fs'
const chrome = process.env.PLAYWRIGHT_EXECUTABLE_PATH || (process.platform === 'darwin' ? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' : '')
export default defineConfig({
  testDir: './tests/e2e', timeout: 30000, fullyParallel: false, workers: 1,
  outputDir: '../.codex-artifacts/frontend-refactor/pc-results', reporter: [['list']],
  use: { baseURL: 'http://127.0.0.1:43192', viewport: { width: 1440, height: 1000 }, trace: 'retain-on-failure', screenshot: 'only-on-failure', launchOptions: chrome && existsSync(chrome) ? { executablePath: chrome } : {} },
  webServer: { command: 'npm run dev -- --host 127.0.0.1 --port 43192 --strictPort', url: 'http://127.0.0.1:43192', reuseExistingServer: false, timeout: 30000 }
})
