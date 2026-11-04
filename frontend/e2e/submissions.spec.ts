import { test, expect, type Page } from "@playwright/test";
import { installFullscreenMock, setMockAuthSession, setupMockApi } from "./fixtures/mock-api";

const minutesAgo = (m: number) => new Date(Date.now() - m * 60000).toISOString();

const SUBMISSIONS = [
  { id: 11, problemId: 7, problemTitle: "Coin Change", language: "PYTHON", status: "PENDING", verdict: null, executionTimeMs: null, memoryUsedKb: null, createdAt: minutesAgo(0) },
  { id: 10, problemId: 7, problemTitle: "Coin Change", language: "PYTHON", status: "COMPLETED", verdict: "WA", executionTimeMs: 84, memoryUsedKb: 31744, createdAt: minutesAgo(6) },
  { id: 9, problemId: 2, problemTitle: "Valid Parentheses", language: "JAVA", status: "COMPLETED", verdict: "AC", executionTimeMs: 112, memoryUsedKb: 49152, createdAt: minutesAgo(60) },
  { id: 8, problemId: 1, problemTitle: "Two Sum", language: "JAVA", status: "COMPLETED", verdict: "AC", executionTimeMs: 98, memoryUsedKb: 45056, createdAt: minutesAgo(180), contestId: 1 },
].map((s) => ({ passTestCases: 0, totalTestCases: 0, errorMessage: null, ...s }));

async function serveSubmissions(page: Page, content: unknown[]) {
  await page.route(/\/api\/v1\/submissions\/my/, (route) =>
    route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({ success: true, data: { content, page: { number: 0, size: 100, totalElements: content.length, totalPages: 1 } } }),
    })
  );
}

const rows = (page: Page) => page.getByRole("region", { name: "Submission history" }).getByRole("link");

test.describe("/submissions", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    await setupMockApi(page);
    await setMockAuthSession(page, { username: "student", role: "USER" });
  });

  test("lists submissions with verdict, problem and summary from the API", async ({ page }) => {
    await serveSubmissions(page, SUBMISSIONS);
    await page.goto("/submissions");

    await expect(rows(page)).toHaveCount(4);
    await expect(rows(page).first()).toContainText("Pending");
    await expect(rows(page).first()).toContainText("Coin Change");
    await expect(rows(page).nth(2)).toContainText("Accepted");
    await expect(rows(page).nth(2)).toContainText("Java 17");
    await expect(rows(page).nth(2)).toContainText("112 ms");
    await expect(rows(page).nth(1)).toContainText("6 min ago");
    await expect(rows(page).nth(2)).toHaveAttribute("href", "/submissions/9");

    const summary = page.getByRole("complementary", { name: "Submission summary" });
    await expect(summary).toContainText("2 / 4 accepted");
    await expect(summary.getByText("Wrong Answer").locator("..")).toContainText("1");
  });

  test("filters by verdict and language, with a no-match state", async ({ page }) => {
    await serveSubmissions(page, SUBMISSIONS);
    await page.goto("/submissions");
    await expect(rows(page)).toHaveCount(4);

    await page.getByLabel("Filter by verdict").selectOption("AC");
    await expect(page).toHaveURL(/verdict=AC/);
    await expect(rows(page)).toHaveCount(2);

    await page.getByLabel("Filter by language").selectOption("PYTHON");
    await expect(page.getByText("No submissions match your filters")).toBeVisible();
    await expect(rows(page)).toHaveCount(0);

    await page.getByLabel("Filter by verdict").selectOption("");
    await expect(rows(page)).toHaveCount(2);
  });

  test("shows the empty state when there are no submissions", async ({ page }) => {
    await serveSubmissions(page, []);
    await page.goto("/submissions");

    await expect(page.getByText("No submissions yet")).toBeVisible();
    await expect(page.getByRole("link", { name: "Browse problems" })).toHaveAttribute("href", "/problems");
    await expect(page.getByRole("complementary", { name: "Submission summary" })).toContainText("0 / 0 accepted");
  });

  test("shows an error with retry when the API fails", async ({ page }) => {
    let fail = true;
    await page.route(/\/api\/v1\/submissions\/my/, (route) =>
      fail
        ? route.fulfill({ status: 500, contentType: "application/json", body: JSON.stringify({ title: "Internal Server Error", detail: "Boom" }) })
        : route.fulfill({
            contentType: "application/json",
            body: JSON.stringify({ success: true, data: { content: SUBMISSIONS, page: { number: 0, size: 100, totalElements: 4, totalPages: 1 } } }),
          })
    );
    await page.goto("/submissions");

    await expect(page.getByText("Couldn't load submissions")).toBeVisible();
    await expect(page.getByText("0 / 0 accepted")).toHaveCount(0);
    fail = false;
    await page.getByRole("button", { name: "Try again" }).click();
    await expect(rows(page)).toHaveCount(4);
  });
});
