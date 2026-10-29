# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: problems.spec.ts >> Problem Exploration E2E Suite >> searches problems by keywords with instant query results
- Location: e2e\problems.spec.ts:65:7

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: getByLabel('Tìm trong danh sách')
Expected: visible
Timeout: 5000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" getByLabel('Tìm trong danh sách') with timeout 5000ms
  - waiting for getByLabel('Tìm trong danh sách')

```

```yaml
- text: Đang tải…
```

# Test source

```ts
  1   | import { test, expect } from "@playwright/test";
  2   | import {
  3   |   setupMockApi,
  4   |   setMockAuthSession,
  5   |   installFullscreenMock,
  6   | } from "./fixtures/mock-api";
  7   | 
  8   | test.describe("Problem Exploration E2E Suite", () => {
  9   |   test.beforeEach(async ({ page }) => {
  10  |     await installFullscreenMock(page);
  11  |     await setupMockApi(page);
  12  |     await setMockAuthSession(page, { username: "student", role: "USER" });
  13  |   });
  14  | 
  15  |   test("displays problem listing table with titles, difficulties, tags, and status", async ({ page }) => {
  16  |     await page.goto("/problems", { waitUntil: "domcontentloaded" });
  17  | 
  18  |     // Table header columns
  19  |     await expect(page.getByText("Status", { exact: true })).toBeVisible();
  20  |     await expect(page.getByText("Problem title", { exact: true })).toBeVisible();
  21  |     await expect(page.getByText("Difficulty", { exact: true })).toBeVisible();
  22  | 
  23  |     // Verify key problem items are listed
  24  |     await expect(page.getByText("Two Sum")).toBeVisible();
  25  |     await expect(page.getByText("Add Two Numbers")).toBeVisible();
  26  |     await expect(page.getByText("Longest Substring Without Repeating Characters")).toBeVisible();
  27  | 
  28  |     // Verify tags are displayed (e.g. Array, Hash Table)
  29  |     await expect(page.getByText("Array").first()).toBeVisible();
  30  |     await expect(page.getByText("Hash Table").first()).toBeVisible();
  31  | 
  32  |     // Verify pagination summary
  33  |     await expect(page.getByText(/Showing 1-8 of \d+ Problems/)).toBeVisible();
  34  |   });
  35  | 
  36  |   test("filters problems by difficulty (Easy, Medium, Hard)", async ({ page }) => {
  37  |     await page.goto("/problems", { waitUntil: "domcontentloaded" });
  38  | 
  39  |     // 1. Filter by EASY
  40  |     await page.getByRole("button", { name: /Filter/i }).click();
  41  |     await page.getByRole("button", { name: "Easy", exact: true }).click();
  42  |     await page.getByRole("button", { name: "Apply" }).click();
  43  | 
  44  |     // Verify URL and filter indicator
  45  |     await expect(page).toHaveURL(/difficulty=EASY/);
  46  |     await expect(page.getByText("Độ khó: EASY")).toBeVisible();
  47  |     await expect(page.getByText("Two Sum")).toBeVisible();
  48  |     await expect(page.getByText("Valid Parentheses")).toBeVisible();
  49  | 
  50  |     // 2. Filter by HARD
  51  |     await page.getByRole("button", { name: /Filter/i }).click();
  52  |     await page.getByRole("button", { name: "Hard", exact: true }).click();
  53  |     await page.getByRole("button", { name: "Apply" }).click();
  54  | 
  55  |     await expect(page).toHaveURL(/difficulty=HARD/);
  56  |     await expect(page.getByText("Độ khó: HARD")).toBeVisible();
  57  |     await expect(page.getByText("Median of Two Sorted Arrays")).toBeVisible();
  58  |     await expect(page.getByText("Merge k Sorted Lists")).toBeVisible();
  59  | 
  60  |     // 3. Reset filters
  61  |     await page.getByRole("button", { name: /Bỏ lọc Độ khó: HARD/i }).click();
  62  |     await expect(page.getByText("Two Sum")).toBeVisible();
  63  |   });
  64  | 
  65  |   test("searches problems by keywords with instant query results", async ({ page }) => {
  66  |     await page.goto("/problems", { waitUntil: "domcontentloaded" });
  67  | 
  68  |     const searchInput = page.getByLabel("Tìm trong danh sách");
> 69  |     await expect(searchInput).toBeVisible();
      |                               ^ Error: expect(locator).toBeVisible() failed
  70  | 
  71  |     // Search for "Parentheses"
  72  |     await searchInput.fill("Parentheses");
  73  |     await searchInput.press("Enter");
  74  | 
  75  |     // Only Valid Parentheses should remain
  76  |     await expect(page).toHaveURL(/q=Parentheses/);
  77  |     await expect(page.getByText("Valid Parentheses")).toBeVisible();
  78  |     await expect(page.getByText("Two Sum")).not.toBeVisible();
  79  | 
  80  |     // Search for "NonExistentKeywordXYZ"
  81  |     await searchInput.fill("NonExistentKeywordXYZ");
  82  |     await searchInput.press("Enter");
  83  | 
  84  |     await expect(page.getByText("Không có bài nào khớp bộ lọc.")).toBeVisible();
  85  | 
  86  |     // Clear search
  87  |     await page.getByRole("button", { name: "Xoá bộ lọc" }).click();
  88  |     await expect(page.getByText("Two Sum")).toBeVisible();
  89  |   });
  90  | 
  91  |   test("navigates pagination across multiple pages with Next, Prev, and page buttons", async ({ page }) => {
  92  |     await page.goto("/problems", { waitUntil: "domcontentloaded" });
  93  | 
  94  |     // Page 1 is active initially
  95  |     await expect(page.getByText("Showing 1-8 of 14 Problems")).toBeVisible();
  96  |     await expect(page.getByText("Two Sum")).toBeVisible();
  97  | 
  98  |     // Click Next button to navigate to Page 2
  99  |     const nextBtn = page.getByRole("button", { name: "Next" });
  100 |     await expect(nextBtn).toBeEnabled();
  101 |     await nextBtn.click();
  102 | 
  103 |     await expect(page).toHaveURL(/page=2/);
  104 |     await expect(page.getByText("Showing 9-14 of 14 Problems")).toBeVisible();
  105 |     await expect(page.getByRole("link", { name: /Binary Search/ })).toBeVisible();
  106 |     await expect(page.getByText("Two Sum")).not.toBeVisible();
  107 | 
  108 |     // Click Prev button to navigate back to Page 1
  109 |     const prevBtn = page.getByRole("button", { name: "Prev" });
  110 |     await expect(prevBtn).toBeEnabled();
  111 |     await prevBtn.click();
  112 | 
  113 |     await expect(page).toHaveURL(/page=1/);
  114 |     await expect(page.getByText("Showing 1-8 of 14 Problems")).toBeVisible();
  115 |     await expect(page.getByText("Two Sum")).toBeVisible();
  116 | 
  117 |     // Click Page Number '2' directly
  118 |     await page.getByRole("button", { name: "2", exact: true }).click();
  119 |     await expect(page).toHaveURL(/page=2/);
  120 |     await expect(page.getByText("Showing 9-14 of 14 Problems")).toBeVisible();
  121 |   });
  122 | });
  123 | 
```