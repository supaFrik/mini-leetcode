import { test, expect } from "@playwright/test";
import {
  setupMockApi,
  setMockAuthSession,
  installFullscreenMock,
} from "./fixtures/mock-api";

test.describe("Problem Exploration E2E Suite", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    await setupMockApi(page);
    await setMockAuthSession(page, { username: "student", role: "USER" });
  });

  test("displays problem listing table with titles, difficulties, tags, and status", async ({ page }) => {
    await page.goto("/problems", { waitUntil: "domcontentloaded" });

    const list = page.getByRole("region", { name: "Problem list" });
    await expect(list.getByText("No.", { exact: true })).toBeVisible();
    await expect(list.getByText("Title", { exact: true })).toBeVisible();
    await expect(list.getByText("Topics", { exact: true })).toBeVisible();
    await expect(list.getByText("Difficulty", { exact: true })).toBeVisible();

    // Verify key problem items are listed
    await expect(page.getByText("Two Sum").first()).toBeVisible();
    await expect(page.getByText("Add Two Numbers").first()).toBeVisible();
    await expect(page.getByText("Longest Substring Without Repeating Characters").first()).toBeVisible();

    // Topics column joins tags with a middle dot
    await expect(list.getByText("Array · Hash Table").first()).toBeVisible();

    // All 14 seeded problems fit on one page of 20, so there is no pager
    await expect(page.getByText(/Showing \d+–\d+ of/)).toHaveCount(0);
  });

  test("filters problems by difficulty (Easy, Medium, Hard)", async ({ page }) => {
    await page.goto("/problems", { waitUntil: "domcontentloaded" });
    const select = page.getByLabel("Filter by difficulty");

    // 1. Filter by EASY
    await select.selectOption("EASY");
    await expect(page).toHaveURL(/difficulty=EASY/);
    await expect(page.getByText("Two Sum").first()).toBeVisible();
    await expect(page.getByText("Valid Parentheses").first()).toBeVisible();

    // 2. Filter by HARD
    await select.selectOption("HARD");
    await expect(page).toHaveURL(/difficulty=HARD/);
    await expect(page.getByText("Median of Two Sorted Arrays").first()).toBeVisible();
    await expect(page.getByText("Merge k Sorted Lists").first()).toBeVisible();

    // 3. Reset filters
    await page.getByRole("button", { name: "Clear filters" }).first().click();
    await expect(page).not.toHaveURL(/difficulty=/);
    await expect(select).toHaveValue("");
    await expect(page.getByText("Two Sum").first()).toBeVisible();
  });

  test("searches problems by keywords with instant query results", async ({ page }) => {
    await page.goto("/problems", { waitUntil: "domcontentloaded" });

    const searchInput = page.getByLabel("Search problems").first();
    await expect(searchInput).toBeVisible();

    // Search for "Parentheses"
    await searchInput.fill("Parentheses");
    await searchInput.press("Enter");

    // Only Valid Parentheses should remain
    await expect(page).toHaveURL(/q=Parentheses/);
    await expect(page.getByText("Valid Parentheses").first()).toBeVisible();
    await expect(page.getByText("Two Sum")).not.toBeVisible();

    // Search for "NonExistentKeywordXYZ"
    await searchInput.fill("NonExistentKeywordXYZ");
    await searchInput.press("Enter");

    await expect(page.getByText("No problems match your filters")).toBeVisible();

    // Clear search (the button inside the empty state)
    await page.getByRole("button", { name: "Clear filters" }).last().click();
    await expect(page.getByText("Two Sum").first()).toBeVisible();
  });

  test("pick random opens the chosen problem", async ({ page }) => {
    await page.goto("/problems", { waitUntil: "domcontentloaded" });
    await expect(page.getByText("Two Sum").first()).toBeVisible();

    await page.getByRole("button", { name: "Pick random" }).click();
    await expect(page).toHaveURL(/\/problems\/[^/?]+$/);
  });

  test("shows an error, not an endless skeleton, when the API fails", async ({ page }) => {
    await page.route(/\/api\/v1\/problems(\?|$)/, (route) =>
      route.fulfill({ status: 500, contentType: "application/json", body: JSON.stringify({ title: "Internal Server Error", detail: "Boom" }) })
    );
    await page.goto("/problems", { waitUntil: "domcontentloaded" });

    await expect(page.getByText("Couldn't load problems")).toBeVisible();
    await expect(page.getByLabel("Loading problems")).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Try again" }).first()).toBeVisible();
  });
});

test.describe("Problem list states", () => {
  test("separates the empty catalogue from an empty filter result", async ({ page }) => {
    await installFullscreenMock(page);
    await setupMockApi(page, { customProblems: [] });
    await setMockAuthSession(page, { username: "student", role: "USER" });
    await page.goto("/problems", { waitUntil: "domcontentloaded" });

    await expect(page.getByText("No problems yet").first()).toBeVisible();
    await expect(page.getByText("No problems match your filters")).toHaveCount(0);
  });

  test("navigates pagination across multiple pages with Next, Prev, and page buttons", async ({ page }) => {
    await installFullscreenMock(page);
    const many = Array.from({ length: 25 }, (_, i) => ({
      id: i + 1,
      slug: `problem-${i + 1}`,
      title: `Practice Problem ${i + 1}`,
      difficulty: (["EASY", "MEDIUM", "HARD"] as const)[i % 3],
      tags: ["Array"],
      description: "",
      timeLimitMs: 1000,
      memoryLimitMb: 256,
      status: "PUBLISHED" as const,
    }));
    await setupMockApi(page, { customProblems: many });
    await setMockAuthSession(page, { username: "student", role: "USER" });
    await page.goto("/problems", { waitUntil: "domcontentloaded" });

    await expect(page.getByText("Showing 1–20 of 25")).toBeVisible();
    await expect(page.getByText("Practice Problem 1", { exact: true })).toBeVisible();

    const nextBtn = page.getByRole("button", { name: "Next" });
    await expect(nextBtn).toBeEnabled();
    await nextBtn.click();

    await expect(page).toHaveURL(/page=2/);
    await expect(page.getByText("Showing 21–25 of 25")).toBeVisible();
    await expect(page.getByRole("link", { name: /Practice Problem 25/ })).toBeVisible();
    await expect(page.getByText("Practice Problem 1", { exact: true })).not.toBeVisible();

    const prevBtn = page.getByRole("button", { name: "Prev" });
    await expect(prevBtn).toBeEnabled();
    await prevBtn.click();

    await expect(page).toHaveURL(/page=1/);
    await expect(page.getByText("Showing 1–20 of 25")).toBeVisible();

    await page.getByRole("button", { name: "2", exact: true }).click();
    await expect(page).toHaveURL(/page=2/);
    await expect(page.getByText("Showing 21–25 of 25")).toBeVisible();
  });
});
