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

    // Table header columns live inside the "Problem list" region; the rail on
    // the left has its own "Status" / "Difficulty" labels.
    const list = page.getByRole("region", { name: "Problem list" });
    await expect(list.getByText("Status", { exact: true })).toBeVisible();
    await expect(list.getByText("Title", { exact: true })).toBeVisible();
    await expect(list.getByText("Difficulty", { exact: true })).toBeVisible();

    // Verify key problem items are listed
    await expect(page.getByText("Two Sum").first()).toBeVisible();
    await expect(page.getByText("Add Two Numbers").first()).toBeVisible();
    await expect(page.getByText("Longest Substring Without Repeating Characters").first()).toBeVisible();

    // Verify tags are displayed (e.g. Array, Hash Table)
    await expect(page.getByText("Array").first()).toBeVisible();
    await expect(page.getByText("Hash Table").first()).toBeVisible();

    // Verify pagination summary
    await expect(page.getByText(/Showing 1–8 of \d+/)).toBeVisible();
  });

  test("filters problems by difficulty (Easy, Medium, Hard)", async ({ page }) => {
    await page.goto("/problems", { waitUntil: "domcontentloaded" });

    // 1. Filter by EASY
    await page.getByRole("button", { name: /^Filter/i }).click();
    await page.getByRole("dialog").getByRole("button", { name: "Easy", exact: true }).click();
    await page.getByRole("button", { name: "Apply" }).click();

    // Verify URL and the active-filter chip
    await expect(page).toHaveURL(/difficulty=EASY/);
    await expect(page.getByLabel("Active filters").getByText("EASY", { exact: true })).toBeVisible();
    await expect(page.getByText("Two Sum").first()).toBeVisible();
    await expect(page.getByText("Valid Parentheses").first()).toBeVisible();

    // 2. Filter by HARD
    await page.getByRole("button", { name: /^Filter/i }).click();
    await page.getByRole("dialog").getByRole("button", { name: "Hard", exact: true }).click();
    await page.getByRole("button", { name: "Apply" }).click();

    await expect(page).toHaveURL(/difficulty=HARD/);
    await expect(page.getByLabel("Active filters").getByText("HARD", { exact: true })).toBeVisible();
    await expect(page.getByText("Median of Two Sorted Arrays").first()).toBeVisible();
    await expect(page.getByText("Merge k Sorted Lists").first()).toBeVisible();

    // 3. Reset filters
    await page.getByRole("button", { name: /Remove filter HARD/i }).click();
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

    // Clear search
    await page.getByRole("button", { name: "Clear filters" }).click();
    await expect(page.getByText("Two Sum").first()).toBeVisible();
  });

  test("navigates pagination across multiple pages with Next, Prev, and page buttons", async ({ page }) => {
    await page.goto("/problems", { waitUntil: "domcontentloaded" });

    // Page 1 is active initially
    await expect(page.getByText("Showing 1–8 of 14")).toBeVisible();
    await expect(page.getByText("Two Sum").first()).toBeVisible();

    // Click Next button to navigate to Page 2
    const nextBtn = page.getByRole("button", { name: "Next" });
    await expect(nextBtn).toBeEnabled();
    await nextBtn.click();

    await expect(page).toHaveURL(/page=2/);
    await expect(page.getByText("Showing 9–14 of 14")).toBeVisible();
    await expect(page.getByRole("link", { name: /Binary Search/ }).first()).toBeVisible();
    await expect(page.getByText("Two Sum")).not.toBeVisible();

    // Click Prev button to navigate back to Page 1
    const prevBtn = page.getByRole("button", { name: "Prev" });
    await expect(prevBtn).toBeEnabled();
    await prevBtn.click();

    await expect(page).toHaveURL(/page=1/);
    await expect(page.getByText("Showing 1–8 of 14")).toBeVisible();
    await expect(page.getByText("Two Sum").first()).toBeVisible();

    // Click Page Number '2' directly
    await page.getByRole("button", { name: "2", exact: true }).click();
    await expect(page).toHaveURL(/page=2/);
    await expect(page.getByText("Showing 9–14 of 14")).toBeVisible();
  });
});
