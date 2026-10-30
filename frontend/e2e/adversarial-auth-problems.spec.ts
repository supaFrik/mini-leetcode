import { test, expect } from "@playwright/test";
import {
  setupMockApi,
  setMockAuthSession,
  installFullscreenMock,
} from "./fixtures/mock-api";

test.describe("Adversarial Stress Testing: Auth and Problems Flows", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    await setupMockApi(page);
  });

  // =========================================================================
  // 1. ADVERSARIAL REGISTRATION & RFC 7807 SCHEMAS
  // =========================================================================
  test.describe("1. Adversarial Registration", () => {
    test("handles SQL injection payloads in username and email safely", async ({ page }) => {
      await page.goto("/register");
      await page.waitForLoadState("domcontentloaded");

      const ts = Date.now();
      const sqliUsername = `sqli_${ts}' OR '1'='1`;
      const sqliEmail = `sqli_${ts}'--@example.com`;

      await page.getByLabel("Username").fill(sqliUsername);
      await page.getByLabel("Email").fill(sqliEmail);
      await page.getByLabel("Password", { exact: true }).fill("Password123!");

      const submitBtn = page.getByRole("button", { name: "Create Account" });
      await expect(submitBtn).toBeEnabled();
      await Promise.all([
        page.waitForResponse((r) => r.url().includes("/api/v1/auth/register")),
        submitBtn.click(),
      ]);

      // Successfully creates account and transitions without SQL parsing or unhandled error
      await page.waitForURL((url) => !url.pathname.includes("/register") && !url.pathname.includes("/login"), { timeout: 10000 });
      const storedToken = await page.evaluate(() => localStorage.getItem("access_token"));
      expect(storedToken).toBeTruthy();
    });

    test("neutralizes XSS script injection in username without execution", async ({ page }) => {
      let alertTriggered = false;
      page.on("dialog", (dialog) => {
        alertTriggered = true;
        dialog.dismiss();
      });

      await page.goto("/register");
      await page.waitForLoadState("domcontentloaded");

      const ts = Date.now();
      const xssUsername = `<script>alert('xss_${ts}')</script>`;
      await page.getByLabel("Username").fill(xssUsername);
      await page.getByLabel("Email").fill(`xss_${ts}@example.com`);
      await page.getByLabel("Password", { exact: true }).fill("Password123!");

      const submitBtn = page.getByRole("button", { name: "Create Account" });
      await expect(submitBtn).toBeEnabled();
      await Promise.all([
        page.waitForResponse((r) => r.url().includes("/api/v1/auth/register")),
        submitBtn.click(),
      ]);

      await page.waitForURL((url) => !url.pathname.includes("/register") && !url.pathname.includes("/login"), { timeout: 10000 });

      // Crucial security invariant: No XSS dialog was ever executed
      expect(alertTriggered).toBe(false);

      // Successfully stored valid token for the escaped user
      const storedToken = await page.evaluate(() => localStorage.getItem("access_token"));
      expect(storedToken).toBeTruthy();
    });

    test("enforces maximum length boundary strings with RFC 7807 Bad Request", async ({ page }) => {
      await page.goto("/register");
      await page.waitForLoadState("domcontentloaded");

      // 455-character username violates maximum 50-character limit
      const longUsername = "user_" + "a".repeat(450);
      await page.getByLabel("Username").fill(longUsername);
      await page.getByLabel("Email").fill("boundary_user@example.com");
      await page.getByLabel("Password", { exact: true }).fill("Password123!");

      const submitBtn = page.getByRole("button", { name: "Create Account" });
      await expect(submitBtn).toBeEnabled();
      await Promise.all([
        page.waitForResponse((r) => r.url().includes("/api/v1/auth/register")),
        submitBtn.click(),
      ]);

      // Form remains on /register, showing RFC 7807 validation problem details
      await expect(page.getByText("Validation failed for request fields")).toBeVisible();
      await expect(page.getByText("Username must be between 3 and 50 characters")).toBeVisible();
    });

    test("maps multiple field-level RFC 7807 invalid_params simultaneously", async ({ page }) => {
      // Mock route to return multiple invalid_params on registration
      await page.route("**/api/v1/auth/register", async (route) => {
        await route.fulfill({
          status: 400,
          contentType: "application/problem+json",
          body: JSON.stringify({
            type: "https://api.mini-leetcode.com/errors/bad-request",
            title: "Bad Request",
            status: 400,
            detail: "Multiple fields failed validation",
            instance: "/api/v1/auth/register",
            invalid_params: {
              username: "Username contains illegal characters",
              email: "Email domain is blacklisted",
              password: "Password is too common",
            },
          }),
        });
      });

      await page.goto("/register");
      await page.waitForLoadState("domcontentloaded");

      await page.getByLabel("Username").fill("bad_user_name");
      await page.getByLabel("Email").fill("blocked@spam.com");
      await page.getByLabel("Password", { exact: true }).fill("12345678"); // valid length to bypass client check

      const submitBtn = page.getByRole("button", { name: "Create Account" });
      await expect(submitBtn).toBeEnabled();
      await Promise.all([
        page.waitForResponse((r) => r.url().includes("/api/v1/auth/register")),
        submitBtn.click(),
      ]);

      // Top-level RFC 7807 banner
      await expect(page.getByText("Multiple fields failed validation")).toBeVisible();

      // Field-level RFC 7807 alerts
      await expect(page.getByText("Username contains illegal characters")).toBeVisible();
      await expect(page.getByText("Email domain is blacklisted")).toBeVisible();
      await expect(page.getByText("Password is too common")).toBeVisible();
    });

    test("retains UI interactivity when backend returns RFC 7807 500 Internal Server Error", async ({ page }) => {
      await page.route("**/api/v1/auth/register", async (route) => {
        await route.fulfill({
          status: 500,
          contentType: "application/problem+json",
          body: JSON.stringify({
            type: "https://api.mini-leetcode.com/errors/internal-error",
            title: "Internal Server Error",
            status: 500,
            detail: "Database cluster synchronization timed out",
            instance: "/api/v1/auth/register",
          }),
        });
      });

      await page.goto("/register");
      await page.waitForLoadState("domcontentloaded");

      await page.getByLabel("Username").fill("test_user_500");
      await page.getByLabel("Email").fill("test500@example.com");
      await page.getByLabel("Password", { exact: true }).fill("Password123!");

      const submitBtn = page.getByRole("button", { name: "Create Account" });
      await expect(submitBtn).toBeEnabled();
      await Promise.all([
        page.waitForResponse((r) => r.url().includes("/api/v1/auth/register")),
        submitBtn.click(),
      ]);

      // Error banner must display RFC 7807 detail
      await expect(page.getByText("Database cluster synchronization timed out")).toBeVisible();

      // Form button should be re-enabled and form interactive
      await expect(submitBtn).toBeEnabled();
    });
  });

  // =========================================================================
  // 2. ADVERSARIAL LOGIN, JWT HANDLING & OPEN-REDIRECT RESILIENCE
  // =========================================================================
  test.describe("2. Adversarial Login and Security", () => {
    test("neutralizes open-redirect attempts via malicious query parameters", async ({ page }) => {
      const maliciousTargets = [
        "//evil.com",
        "https://attacker.com",
        "javascript:alert(1)",
        "/login",
        "/register",
      ];

      for (const target of maliciousTargets) {
        await page.goto(`/login?next=${encodeURIComponent(target)}`);
        await page.waitForLoadState("domcontentloaded");

        await page.getByLabel("Username").fill("student");
        await page.getByLabel("Password", { exact: true }).fill("Password123!");

        const submitBtn = page.getByRole("button", { name: "Log in" });
        await expect(submitBtn).toBeEnabled();
        await Promise.all([
          page.waitForResponse((r) => r.url().includes("/api/v1/auth/login")),
          submitBtn.click(),
        ]);

        // Must redirect to safe internal root '/', NOT to evil.com or loop back
        await page.waitForURL((url) => url.pathname === "/" || !url.pathname.includes("/login"), { timeout: 10000 });
        const currentUrl = page.url();
        expect(currentUrl).not.toContain("evil.com");
        expect(currentUrl).not.toContain("attacker.com");
        expect(currentUrl).not.toContain("javascript:");

        // Clear tokens for next iteration
        await page.evaluate(() => localStorage.clear());
      }
    });

    test("preserves deep internal route with query parameters on legitimate login", async ({ page }) => {
      const deepUrl = "/problems?page=2&difficulty=EASY";
      await page.goto(`/login?next=${encodeURIComponent(deepUrl)}`);
      await page.waitForLoadState("domcontentloaded");

      await page.getByLabel("Username").fill("student");
      await page.getByLabel("Password", { exact: true }).fill("Password123!");

      const submitBtn = page.getByRole("button", { name: "Log in" });
      await expect(submitBtn).toBeEnabled();
      await Promise.all([
        page.waitForResponse((r) => r.url().includes("/api/v1/auth/login")),
        submitBtn.click(),
      ]);

      // Must redirect to the full deep route with all query params
      await page.waitForURL((url) => url.pathname.includes("/problems") && url.search.includes("page=2"), { timeout: 10000 });
      expect(page.url()).toContain("difficulty=EASY");
    });

    test("handles corrupted/malformed JWT in localStorage by safely degrading to unauthenticated state", async ({ page }) => {
      // Set corrupted tokens before navigation
      await page.addInitScript(() => {
        localStorage.setItem("access_token", "invalid.not-base64-json.mock_sig");
        localStorage.setItem("refresh_token", "corrupted_refresh");
      });

      await page.goto("/");
      await page.waitForLoadState("domcontentloaded");

      // App must not crash; decodeUser handles JSON parse failure gracefully
      // Header reflects unauthenticated Guest state
      await expect(page.getByText("Guest")).toBeVisible();
      await expect(page.getByText("#—")).toBeVisible();
    });

    test("handles expired JWT and forces re-authentication on 401 API response", async ({ page }) => {
      // Create an expired JWT token (exp in 1970)
      const expiredPayload = {
        userId: 1,
        username: "student",
        role: "USER",
        exp: 1000,
      };
      const header = Buffer.from(JSON.stringify({ alg: "HS256" })).toString("base64url");
      const payload = Buffer.from(JSON.stringify(expiredPayload)).toString("base64url");
      const expiredToken = `${header}.${payload}.expired_sig`;

      await page.addInitScript((tok) => {
        localStorage.setItem("access_token", tok);
        localStorage.setItem("refresh_token", "mock_refresh_expired");
      }, expiredToken);

      // Mock API to return 401 Unauthorized for expired token and 401 for refresh attempt
      await page.route("**/api/v1/auth/refresh", async (route) => {
        await route.fulfill({ status: 401, body: JSON.stringify({ message: "Token expired" }) });
      });
      await page.route("**/api/v1/problems*", async (route) => {
        await route.fulfill({ status: 401, body: JSON.stringify({ message: "Expired JWT" }) });
      });

      // Navigate to /problems: api request 401 triggers clearTokens() and window.location.href = "/login"
      await page.goto("/problems", { waitUntil: "commit" });

      await expect(page).toHaveURL(/.*\/login/, { timeout: 10000 });
      const clearedToken = await page.evaluate(() => localStorage.getItem("access_token"));
      expect(clearedToken).toBeNull();
    });
  });

  // =========================================================================
  // 3. ADVERSARIAL PROBLEMS SEARCH, FILTERING & BOUNDARIES
  // =========================================================================
  test.describe("3. Adversarial Problems Exploration", () => {
    test.beforeEach(async ({ page }) => {
      await setMockAuthSession(page, { username: "student", role: "USER" });
    });

    test("safely escapes special characters and script tags in search query", async ({ page }) => {
      await page.goto("/problems");
      await page.waitForLoadState("domcontentloaded");

      const searchInput = page.getByLabel("Tìm trong danh sách");
      await expect(searchInput).toBeVisible({ timeout: 10000 });

      // Adversarial search string with HTML tags, ampersands, and quotes
      const adversarialQuery = '?query="foo"&<script>alert(1)</script>';
      await searchInput.fill(adversarialQuery);
      await searchInput.press("Enter");

      // Verify URL is properly encoded without breaking
      await page.waitForURL((url) => url.searchParams.has("q"), { timeout: 5000 });
      expect(page.url()).toContain("q=");

      // Filter chip displays text safely without HTML injection
      await expect(page.getByText("Không có bài nào khớp bộ lọc.")).toBeVisible();

      // Clear filter returns to full problem list
      await page.getByRole("button", { name: "Xoá bộ lọc" }).click();
      await expect(page.getByText("Two Sum")).toBeVisible();
    });

    test("handles extreme out-of-bounds page index without crashing", async ({ page }) => {
      await page.goto("/problems?page=999999");
      await page.waitForLoadState("domcontentloaded");

      // Table should render cleanly with empty message instead of crashing
      await expect(page.getByText("Status", { exact: true })).toBeVisible({ timeout: 10000 });
      await expect(page.getByText("Không có bài nào khớp bộ lọc.")).toBeVisible();

      // Clicking reset restores list
      await page.getByRole("button", { name: "Xoá bộ lọc" }).click();
      await expect(page.getByText("Two Sum")).toBeVisible();
    });

    test("normalizes negative and non-numeric page parameter to Page 1", async ({ page }) => {
      await page.goto("/problems?page=-42");
      await page.waitForLoadState("domcontentloaded");

      // Page is normalized to 1, Two Sum is visible
      await expect(page.getByText("Two Sum")).toBeVisible({ timeout: 10000 });
      await expect(page.getByText("Showing 1-8 of 14 Problems")).toBeVisible();

      // Test non-numeric page parameter
      await page.goto("/problems?page=not_a_number");
      await page.waitForLoadState("domcontentloaded");
      await expect(page.getByText("Two Sum")).toBeVisible({ timeout: 10000 });
      await expect(page.getByText("Showing 1-8 of 14 Problems")).toBeVisible();
    });

    test("survives rapid consecutive filter toggling without stale UI state", async ({ page }) => {
      await page.goto("/problems");
      await page.waitForLoadState("domcontentloaded");

      await expect(page.getByText("Two Sum")).toBeVisible({ timeout: 10000 });

      // Rapidly toggle tags in succession
      const arrayTagBtn = page.getByRole("button", { name: "Array", exact: true });
      const hashTagBtn = page.getByRole("button", { name: "Hash Table", exact: true });

      // Click multiple times rapidly
      await arrayTagBtn.click();
      await hashTagBtn.click();
      await arrayTagBtn.click();

      // Allow DOM to settle
      await expect(page.getByText("Status", { exact: true })).toBeVisible();
      const problemRows = page.locator('a[href^="/problems/"]');
      const count = await problemRows.count();
      expect(count).toBeGreaterThan(0);
    });
  });
});
