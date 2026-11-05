import { test, expect, type Page } from "@playwright/test";
import { createMockJwt, installFullscreenMock, setupMockApi } from "./fixtures/mock-api";

/**
 * Token refresh in src/lib/api.ts:
 * - an expired access token is refreshed before the request (public endpoints never answer 401);
 * - concurrent requests share ONE refresh call;
 * - login / register / refresh never trigger a refresh;
 * - a 401 is retried at most once (no refresh loop);
 * - tokens are cleared only when the server rejects the refresh token;
 *   network errors and 5xx keep the tokens and surface an error.
 */

const b64url = (o: object) => Buffer.from(JSON.stringify(o)).toString("base64url");
const expiredJwt = () =>
  `${b64url({ alg: "HS256", typ: "JWT" })}.${b64url({
    userId: 1,
    sub: "student",
    username: "student",
    role: "USER",
    exp: Math.floor(Date.now() / 1000) - 3600,
  })}.mock_sig`;

/** Put tokens in storage once, before the first navigation only. */
async function seedTokensOnce(page: Page, access: string, refresh = "mock_refresh_token") {
  await page.addInitScript(
    ({ access, refresh }) => {
      if (sessionStorage.getItem("__seeded")) return;
      sessionStorage.setItem("__seeded", "1");
      localStorage.setItem("access_token", access);
      localStorage.setItem("refresh_token", refresh);
    },
    { access, refresh }
  );
}

function countRefreshCalls(page: Page) {
  const calls: string[] = [];
  page.on("request", (r) => {
    if (r.url().includes("/api/v1/auth/refresh")) calls.push(r.url());
  });
  return calls;
}

const storedTokens = (page: Page) =>
  page.evaluate(() => ({ access: localStorage.getItem("access_token"), refresh: localStorage.getItem("refresh_token") }));

test.describe("API token refresh", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    await setupMockApi(page);
  });

  test("concurrent requests with an expired token share a single refresh", async ({ page }) => {
    const expired = expiredJwt();
    await seedTokensOnce(page, expired);
    const refreshCalls = countRefreshCalls(page);
    const authHeaders: string[] = [];
    page.on("request", (r) => {
      if (r.url().includes("/api/v1/problems")) authHeaders.push(r.headers()["authorization"] ?? "");
    });

    await page.goto("/problems");
    await expect(page.locator("h1 + p")).toHaveText(/14 problems, 0 solved/);

    expect(authHeaders.length).toBeGreaterThan(3); // list, tags, progress counts... fired together
    expect(refreshCalls).toHaveLength(1);
    // Every problems request went out with the refreshed token, never the expired one.
    expect(authHeaders.every((h) => h.startsWith("Bearer ") && !h.includes(expired))).toBe(true);
    const tokens = await storedTokens(page);
    expect(tokens.access).not.toBe(expired);
    expect(tokens.refresh).toMatch(/^mock_refresh_token_refreshed_/);
  });

  test("login, register and refresh never trigger a refresh", async ({ page }) => {
    const refreshCalls = countRefreshCalls(page);

    // Login. The form redirects away on mount if a token exists, so only put the
    // expired token in storage once the page has settled and the form is filled in.
    await page.goto("/login");
    await page.waitForLoadState("networkidle");
    await page.getByLabel("Username").fill("student");
    await page.getByLabel("Password", { exact: true }).fill("Password123!");
    await page.evaluate((tok) => {
      localStorage.setItem("access_token", tok);
      localStorage.setItem("refresh_token", "mock_refresh_token");
    }, expiredJwt());
    const login = page.waitForResponse((r) => r.url().includes("/api/v1/auth/login"));
    await page.getByRole("button", { name: "Log in" }).click();
    expect((await login).status()).toBe(200);

    // Register
    await page.evaluate(() => localStorage.clear());
    await page.goto("/register");
    await page.waitForLoadState("networkidle");
    await page.getByLabel("Username").fill("fresh_user");
    await page.getByLabel("Email").fill("fresh_user@example.com");
    await page.getByLabel("Password", { exact: true }).fill("Password123!");
    await page.evaluate((tok) => {
      localStorage.setItem("access_token", tok);
      localStorage.setItem("refresh_token", "mock_refresh_token");
    }, expiredJwt());
    const register = page.waitForResponse((r) => r.url().includes("/api/v1/auth/register"));
    await page.getByRole("button", { name: "Create Account" }).click();
    await register;
    await page.waitForURL((u) => !u.pathname.startsWith("/register"));

    expect(refreshCalls).toHaveLength(0);
  });

  test("a 401 after a successful refresh is not retried forever", async ({ page }) => {
    await seedTokensOnce(page, createMockJwt({ id: 1, username: "student", role: "USER" }));
    const refreshCalls = countRefreshCalls(page);
    // The page loads the list and the summary together; each must be retried exactly once.
    const submissionCalls: Record<string, number> = {};
    await page.route(/\/api\/v1\/submissions\/my/, (route) => {
      const path = new URL(route.request().url()).pathname;
      submissionCalls[path] = (submissionCalls[path] ?? 0) + 1;
      return route.fulfill({ status: 401, contentType: "application/json", body: JSON.stringify({ title: "Unauthorized" }) });
    });

    await page.goto("/submissions");
    await page.waitForURL(/\/login/);

    expect(refreshCalls).toHaveLength(1);
    expect(submissionCalls).toEqual({ "/api/v1/submissions/my": 2, "/api/v1/submissions/my/summary": 2 }); // original + exactly one retry
    expect((await storedTokens(page)).access).toBeNull();
  });

  test("a rejected refresh token clears the session", async ({ page }) => {
    await seedTokensOnce(page, expiredJwt());
    await page.route("**/api/v1/auth/refresh", (route) =>
      route.fulfill({ status: 401, contentType: "application/json", body: JSON.stringify({ title: "Invalid refresh token" }) })
    );

    await page.goto("/problems");
    await expect(page.locator("h1 + p")).toHaveText("14 problems, 0 solved");
    expect(await storedTokens(page)).toEqual({ access: null, refresh: null });
    await expect(page.getByRole("banner").getByRole("link", { name: "Log in" })).toBeVisible();
  });

  test("a network error during refresh keeps the tokens and shows an error", async ({ page }) => {
    const expired = expiredJwt();
    await seedTokensOnce(page, expired);
    await page.route("**/api/v1/auth/refresh", (route) => route.abort("internetdisconnected"));

    await page.goto("/problems");
    await expect(page.getByText("Couldn't load problems")).toBeVisible();
    await expect(page.getByText("Unable to connect to the server", { exact: false }).first()).toBeVisible();
    await expect(page).toHaveURL(/\/problems$/);
    expect(await storedTokens(page)).toEqual({ access: expired, refresh: "mock_refresh_token" });
  });

  test("a 5xx during refresh keeps the tokens and shows an error", async ({ page }) => {
    const expired = expiredJwt();
    await seedTokensOnce(page, expired);
    await page.route("**/api/v1/auth/refresh", (route) =>
      route.fulfill({ status: 503, contentType: "application/json", body: JSON.stringify({ title: "Service Unavailable" }) })
    );

    await page.goto("/problems");
    await expect(page.getByText("Couldn't load problems")).toBeVisible();
    await expect(page).toHaveURL(/\/problems$/);
    expect(await storedTokens(page)).toEqual({ access: expired, refresh: "mock_refresh_token" });
  });

  test("a network error while refreshing after a 401 does not log the user out", async ({ page }) => {
    const token = createMockJwt({ id: 1, username: "student", role: "USER" });
    await seedTokensOnce(page, token);
    await page.route(/\/api\/v1\/submissions\/my/, (route) =>
      route.fulfill({ status: 401, contentType: "application/json", body: JSON.stringify({ title: "Unauthorized" }) })
    );
    await page.route("**/api/v1/auth/refresh", (route) => route.abort("internetdisconnected"));

    await page.goto("/submissions");
    await expect(page.getByRole("alert").filter({ hasText: "Unable to connect to the server" })).toBeVisible();
    await expect(page).toHaveURL(/\/submissions$/);
    expect(await storedTokens(page)).toEqual({ access: token, refresh: "mock_refresh_token" });
  });
});
