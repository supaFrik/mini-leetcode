import { test, expect } from "@playwright/test";
import {
  setupMockApi,
  generateUniqueUser,
  installFullscreenMock,
} from "./fixtures/mock-api";

test.describe("Authentication and Registration E2E Suite", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    await setupMockApi(page);
  });

  test.describe("User Registration (/register)", () => {
    test("validates form fields and displays inline validation alerts", async ({ page }) => {
      await page.goto("/register");
      await page.waitForLoadState("domcontentloaded");

      // 1. Submit empty form -> checks username length < 3
      await page.getByRole("button", { name: "Create Account" }).click();
      await expect(page.getByText("Username must be at least 3 characters.")).toBeVisible();

      // 2. Fill short username (< 3 chars)
      await page.getByLabel("Username").fill("ab");
      await page.getByRole("button", { name: "Create Account" }).click();
      await expect(page.getByText("Username must be at least 3 characters.")).toBeVisible();

      // 3. Fill valid username, but leave email empty or invalid
      await page.getByLabel("Username").fill("validuser");
      await page.getByLabel("Email").fill("not-an-email");
      await page.getByRole("button", { name: "Create Account" }).click();
      await expect(page.getByText("Invalid email format.")).toBeVisible();

      // 4. Fill valid email, but short password (< 8 chars)
      await page.getByLabel("Email").fill("valid@example.com");
      await page.getByLabel("Password", { exact: true }).fill("12345");
      await page.getByRole("button", { name: "Create Account" }).click();
      await expect(page.getByText("Password must be at least 8 characters.")).toBeVisible();
    });

    test("handles RFC 7807 409 Conflict when username is already taken", async ({ page }) => {
      await page.goto("/register");
      await page.waitForLoadState("domcontentloaded");

      // 'student' is an existing registered user in mock-api
      await page.getByLabel("Username").fill("student");
      await page.getByLabel("Email").fill("student_new@example.com");
      await page.getByLabel("Password", { exact: true }).fill("Password123!");
      await page.getByRole("button", { name: "Create Account" }).click();

      // RFC 7807 409 conflict detail banner
      const alert = page.locator('p[role="alert"]').filter({ hasText: "is already taken" });
      await expect(alert).toBeVisible();
      await expect(alert).toContainText("Username 'student' is already taken");
    });

    test("handles RFC 7807 400 Bad Request with invalid_params error mapping", async ({ page }) => {
      await page.goto("/register");
      await page.waitForLoadState("domcontentloaded");

      // Trigger 400 Bad Request mock simulation
      await page.getByLabel("Username").fill("trigger_400_validation");
      await page.getByLabel("Email").fill("forbidden@example.com");
      await page.getByLabel("Password", { exact: true }).fill("Password123!");
      await page.getByRole("button", { name: "Create Account" }).click();

      // Form level RFC 7807 error banner
      await expect(page.getByText("Validation failed for request fields")).toBeVisible();

      // Field level invalid_params
      await expect(page.getByText("Tên đăng nhập không hợp lệ theo RFC 7807")).toBeVisible();
    });

    test("successfully creates account with dynamic credentials and authenticates session", async ({ page }) => {
      const newUser = generateUniqueUser("e2e_reg");

      await page.goto("/register");
      await page.waitForLoadState("domcontentloaded");

      await page.getByLabel("Username").fill(newUser.username);
      await page.getByLabel("Email").fill(newUser.email);
      await page.getByLabel("Password", { exact: true }).fill(newUser.password);
      await page.getByRole("button", { name: "Create Account" }).click();

      // User should be redirected away from /register
      await page.waitForURL((url) => !url.pathname.includes("/register") && !url.pathname.includes("/login"));

      // Verify JWT tokens exist in localStorage
      const accessToken = await page.evaluate(() => localStorage.getItem("access_token"));
      expect(accessToken).toBeTruthy();

      // Header reflects authenticated username
      await expect(page.getByText(newUser.username)).toBeVisible();
    });
  });

  test.describe("User Login (/login)", () => {
    test("displays RFC 7807 401 error message upon invalid login credentials", async ({ page }) => {
      await page.goto("/login");
      await page.waitForLoadState("networkidle");

      await page.getByLabel("Username").fill("student");
      await page.getByLabel("Password", { exact: true }).fill("IncorrectPassword123!");
      await page.getByRole("button", { name: "Log in" }).click();

      const alert = page.locator('p[role="alert"]').filter({ hasText: "Invalid username or password" });
      await expect(alert).toBeVisible();
    });

    test("successfully authenticates, stores JWT tokens, and updates header state", async ({ page }) => {
      await page.goto("/login");
      await page.waitForLoadState("networkidle");

      await page.getByLabel("Username").fill("student");
      await page.getByLabel("Password", { exact: true }).fill("Password123!");

      const submitBtn = page.getByRole("button", { name: "Log in" });
      await expect(submitBtn).toBeEnabled();
      await Promise.all([
        page.waitForResponse((r) => r.url().includes("/api/v1/auth/login")),
        submitBtn.click(),
      ]);

      // Redirection away from login
      await page.waitForURL((url) => !url.pathname.includes("/login"));

      // Verify tokens stored
      const accessToken = await page.evaluate(() => localStorage.getItem("access_token"));
      const refreshToken = await page.evaluate(() => localStorage.getItem("refresh_token"));
      expect(accessToken).toBeTruthy();
      expect(refreshToken).toBeTruthy();

      // Header shows student username and user role
      await expect(page.getByText("student", { exact: true })).toBeVisible();
      await expect(page.getByText("#USER")).toBeVisible();
    });

    test("persists authenticated session across full page reloads", async ({ page }) => {
      await page.goto("/login");
      await page.waitForLoadState("networkidle");

      await page.getByLabel("Username").fill("student");
      await page.getByLabel("Password", { exact: true }).fill("Password123!");

      const submitBtn = page.getByRole("button", { name: "Log in" });
      await expect(submitBtn).toBeEnabled();
      await Promise.all([
        page.waitForResponse((r) => r.url().includes("/api/v1/auth/login")),
        submitBtn.click(),
      ]);

      await page.waitForURL((url) => !url.pathname.includes("/login"));
      await expect(page.getByText("student", { exact: true })).toBeVisible();

      // Perform full page reload
      await page.reload({ waitUntil: "networkidle" });

      // User session persists
      await expect(page.getByText("student", { exact: true })).toBeVisible();
      const accessToken = await page.evaluate(() => localStorage.getItem("access_token"));
      expect(accessToken).toBeTruthy();
    });

    test("enforces AuthGuard redirection when navigating to protected route unauthenticated", async ({ page }) => {
      // Clear all tokens to simulate guest state
      await page.goto("/login");
      await page.evaluate(() => localStorage.clear());

      // Attempt navigating to protected /problems route
      await page.goto("/problems");

      // AuthGuard redirects back to /login with next query parameter
      await page.waitForURL((url) => url.pathname.includes("/login"));
      expect(page.url()).toContain("next=");
      await expect(page.getByRole("heading", { name: "Welcome back" })).toBeVisible();
    });
  });
});
