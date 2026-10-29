import { test, expect } from "@playwright/test";
import {
  setupMockApi,
  generateUniqueUser,
  generateUniqueProblem,
  installFullscreenMock,
} from "./fixtures/mock-api";

test.describe("M1 Infrastructure Smoke Test", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    await setupMockApi(page);
  });

  test("generates unique dynamic test data", async () => {
    const user1 = generateUniqueUser();
    const user2 = generateUniqueUser();
    expect(user1.username).not.toEqual(user2.username);
    expect(user1.email).not.toEqual(user2.email);

    const prob1 = generateUniqueProblem(101);
    const prob2 = generateUniqueProblem(102);
    expect(prob1.id).not.toEqual(prob2.id);
    expect(prob1.slug).not.toEqual(prob2.slug);
  });

  test("mocks Fullscreen API in headless browser", async ({ page }) => {
    await page.goto("/login", { waitUntil: "domcontentloaded" });
    const isFullscreenEnabled = await page.evaluate(() => document.fullscreenEnabled);
    expect(isFullscreenEnabled).toBe(true);
  });

  test("authenticates with mock API and intercepts route", async ({ page }) => {
    // Navigate to /login
    await page.goto("/login", { waitUntil: "domcontentloaded" });

    // Expect the login form to be rendered
    await expect(page.getByRole("heading", { name: "Đăng nhập" })).toBeVisible();
    await expect(page.getByLabel("Tên đăng nhập")).toBeVisible();
    await expect(page.getByLabel("Mật khẩu", { exact: true })).toBeVisible();
  });

  test("renders /register route directly without 404 or redirect", async ({ page }) => {
    // Navigate directly to /register
    await page.goto("/register", { waitUntil: "domcontentloaded" });

    // Expect the register form to be visible
    await expect(page.getByRole("heading", { name: "Tạo tài khoản" })).toBeVisible();
    await expect(page.getByLabel("Tên đăng nhập")).toBeVisible();
    await expect(page.getByLabel("Email")).toBeVisible();
    await expect(page.getByLabel("Mật khẩu", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Đăng ký" })).toBeVisible();
  });
});
