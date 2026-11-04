import { test, expect, type Page } from "@playwright/test";
import { setMockAuthSession, setupMockApi } from "./fixtures/mock-api";

const row = (slug: string, title: string, progress?: string) => ({
  id: slug.length,
  slug,
  title,
  difficulty: "EASY",
  tags: [],
  type: "ALGORITHMIC",
  ...(progress ? { progress } : {}),
});

async function serveProblems(page: Page, content: object[] | "error") {
  await page.route(/\/api\/v1\/problems\?/, (route) => {
    if (content === "error")
      return route.fulfill({ status: 500, contentType: "application/json", body: JSON.stringify({ title: "Internal Server Error", detail: "Boom" }) });
    const p = { content, totalElements: content.length, totalPages: 1, number: 0, size: 100, first: true, last: true };
    return route.fulfill({ contentType: "application/json", body: JSON.stringify({ success: true, data: p, ...p }) });
  });
}

test.describe("/study-plan", () => {
  test.beforeEach(async ({ page }) => {
    await setupMockApi(page);
    await setMockAuthSession(page, { username: "student", role: "USER" });
  });

  test("shows only plan problems the API returns, with progress and next up", async ({ page }) => {
    // binary-search và các slug khác không có trong API nên phải bị bỏ qua.
    await serveProblems(page, [row("two-sum", "Two Sum", "DONE"), row("valid-parentheses", "Valid Parentheses", "NEVER")]);
    await page.goto("/study-plan");

    const modules = page.getByRole("region", { name: "Plan modules" });
    await expect(modules.getByRole("link")).toHaveCount(2);
    await expect(modules.getByText("Binary Search")).toHaveCount(0);
    await expect(modules.getByRole("img", { name: "Solved" })).toHaveCount(1);
    await expect(page.getByRole("complementary", { name: "Plan progress" })).toContainText("1 / 2 completed");
    await expect(page.getByRole("link", { name: "Continue" })).toHaveAttribute("href", "/problems/valid-parentheses");

    await page.getByRole("button", { name: "OOP & Socket" }).click();
    await expect(page.getByText("No problems in this plan yet")).toBeVisible();
  });

  test("shows an error with retry when the API fails", async ({ page }) => {
    await serveProblems(page, "error");
    await page.goto("/study-plan");
    await expect(page.getByRole("alert").filter({ hasText: "Couldn't load the study plan" })).toContainText("Boom");
    await page.unroute(/\/api\/v1\/problems\?/);
    await serveProblems(page, [row("two-sum", "Two Sum")]);
    await page.getByRole("button", { name: "Try again" }).click();
    await expect(page.getByText("0 / 1 completed")).toBeVisible();
  });
});
