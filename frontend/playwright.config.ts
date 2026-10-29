import { defineConfig, devices } from "@playwright/test";

/**
 * Playwright E2E configuration for Mini LeetCode.
 * Configured with dedicated port 3005 to avoid collisions with host services (e.g. Grafana on 3000).
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [
    ["html", { open: "never" }],
    ["list"],
  ],
  use: {
    baseURL: process.env.BASE_URL || "http://localhost:3005",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    actionTimeout: 15000,
    navigationTimeout: 30000,
  },
  projects: [
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
        headless: true,
      },
    },
  ],
  webServer: {
    command: "npm run dev -- -p 3005",
    url: "http://localhost:3005",
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
});
