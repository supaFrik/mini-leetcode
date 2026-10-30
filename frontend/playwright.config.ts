import { defineConfig, devices } from "@playwright/test";

/**
 * Playwright E2E configuration for Mini LeetCode.
 * Configured with dedicated port 3005 to avoid collisions with host services
 * (e.g. Grafana on 3000) and with the Docker stack's frontend on 3100.
 *
 * By default the suite runs against a production build (`next build` +
 * `next start`). A dev server pays the route-compile cost on the *first* hit
 * of each route, which regularly exceeds Playwright's action timeout and
 * makes results depend on test order. Set `E2E_DEV=1` to opt back into the
 * dev server.
 */
const useDevServer = process.env.E2E_DEV === "1";
const port = Number(process.env.E2E_PORT ?? 3005);
const baseURL = process.env.BASE_URL || `http://localhost:${port}`;
// When BASE_URL points at an already-running server (e.g. the Docker stack),
// don't try to start one.
const manageServer = !process.env.BASE_URL;

/**
 * The suite drives the app through Playwright route interception, so the
 * in-browser mock server MUST be off — otherwise the app never issues the
 * network requests the tests are intercepting. A `NEXT_PUBLIC_USE_MOCK=true`
 * left in the shell environment would silently invalidate every assertion,
 * so the flag is pinned here rather than left to the ambient environment.
 */
const e2eEnv = {
  ...process.env,
  NEXT_PUBLIC_USE_MOCK: "false",
  NEXT_PUBLIC_ENABLE_RUN: process.env.NEXT_PUBLIC_ENABLE_RUN ?? "true",
  NEXT_PUBLIC_ENABLE_DRAFT_SYNC: process.env.NEXT_PUBLIC_ENABLE_DRAFT_SYNC ?? "false",
};

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
    baseURL,
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
  webServer: manageServer
    ? {
        command: useDevServer
          ? `npm run dev -- -p ${port}`
          : `npm run build && npm run start -- -p ${port}`,
        url: baseURL,
        reuseExistingServer: !process.env.CI,
        timeout: 300000,
        stdout: "pipe",
        stderr: "pipe",
        env: e2eEnv,
      }
    : undefined,
});
