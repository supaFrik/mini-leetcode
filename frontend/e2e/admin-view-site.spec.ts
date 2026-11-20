import { test, expect } from "@playwright/test";
import { setupMockApi, setMockAuthSession } from "./fixtures/mock-api";

/** ADMIN is sent to /admin from learner pages unless they chose "View site"; other roles are not redirected. */
test.describe("Admin: view site", () => {
  test("admin is redirected to /admin until View site, Back to admin re-enables the redirect", async ({ page }) => {
    await setupMockApi(page);
    await setMockAuthSession(page, { username: "admin", role: "ADMIN" });

    await page.goto("/problems", { waitUntil: "domcontentloaded" });
    await expect(page).toHaveURL(/\/admin$/);

    await page.getByRole("link", { name: /View site/ }).click();
    await expect(page).toHaveURL(/\/problems$/);
    await page.reload({ waitUntil: "domcontentloaded" });
    await expect(page).toHaveURL(/\/problems$/);

    await page.getByRole("link", { name: "Back to admin" }).click();
    await expect(page).toHaveURL(/\/admin$/);
    await page.goto("/", { waitUntil: "domcontentloaded" });
    await expect(page).toHaveURL(/\/admin$/);
  });

  test("instructor stays on learner pages and keeps the Teaching link", async ({ page }) => {
    await setupMockApi(page);
    await setMockAuthSession(page, { username: "teacher1", role: "INSTRUCTOR" });

    await page.goto("/problems", { waitUntil: "domcontentloaded" });
    await expect(page.getByRole("link", { name: "Teaching" })).toBeVisible();
    await expect(page).toHaveURL(/\/problems$/);
  });
});
