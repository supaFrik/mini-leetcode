import { test, expect } from "@playwright/test";
import { setupMockApi, setMockAuthSession } from "./fixtures/mock-api";

/** ADMIN is sent to /admin from learner pages, except preview pages (one problem, plan or topic) shown with a banner. */
test.describe("Admin: view site", () => {
  test("admin is redirected to /admin, previews a problem page, Back to admin returns", async ({ page }) => {
    await setupMockApi(page);
    await setMockAuthSession(page, { username: "admin", role: "ADMIN" });

    await page.goto("/problems", { waitUntil: "domcontentloaded" });
    await expect(page).toHaveURL(/\/admin$/);
    await page.goto("/", { waitUntil: "domcontentloaded" });
    await expect(page).toHaveURL(/\/admin$/);

    await page.goto("/problems/two-sum", { waitUntil: "domcontentloaded" });
    await expect(page).toHaveURL(/\/problems\/two-sum$/);
    await expect(page.getByRole("status").filter({ hasText: "Preview mode" })).toBeVisible();
    await page.getByRole("link", { name: "Back to admin" }).click();
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
