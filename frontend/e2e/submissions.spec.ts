import { test, expect, type Page, type Route } from "@playwright/test";
import { installFullscreenMock, setMockAuthSession, setupMockApi } from "./fixtures/mock-api";

const minutesAgo = (m: number) => new Date(Date.now() - m * 60000).toISOString();

const SUBMISSIONS = [
  { id: 11, problemId: 7, problemTitle: "Coin Change", language: "PYTHON", status: "PENDING", verdict: null, executionTimeMs: null, memoryUsedKb: null, createdAt: minutesAgo(0) },
  { id: 10, problemId: 7, problemTitle: "Coin Change", language: "PYTHON", status: "COMPLETED", verdict: "WA", executionTimeMs: 84, memoryUsedKb: 31744, createdAt: minutesAgo(6) },
  { id: 9, problemId: 2, problemTitle: "Valid Parentheses", language: "JAVA", status: "COMPLETED", verdict: "AC", executionTimeMs: 112, memoryUsedKb: 49152, createdAt: minutesAgo(60) },
  { id: 8, problemId: 1, problemTitle: "Two Sum", language: "JAVA", status: "COMPLETED", verdict: "AC", executionTimeMs: 98, memoryUsedKb: 45056, createdAt: minutesAgo(180), contestId: 1 },
].map((s) => ({ passTestCases: 0, totalTestCases: 0, errorMessage: null, ...s }));

type Sub = { verdict: string | null; status: string; language: string };
const keyOf = (x: { verdict: string | null; status: string }) => x.verdict ?? (x.status === "FAILED" ? "RE" : "PENDING");

/** Giả lập API: lọc verdict/language, phân trang ở "server", và /summary đếm toàn bộ. */
function respond(route: Route, all: Sub[]) {
  const url = new URL(route.request().url());
  if (url.pathname.endsWith("/summary")) {
    const counts: Record<string, number> = {};
    all.forEach((x) => (counts[keyOf(x)] = (counts[keyOf(x)] ?? 0) + 1));
    return route.fulfill({ contentType: "application/json", body: JSON.stringify({ success: true, data: counts }) });
  }
  const verdict = url.searchParams.get("verdict");
  const language = url.searchParams.get("language");
  const number = Number(url.searchParams.get("page") ?? 0);
  const size = Number(url.searchParams.get("size") ?? 20);
  const list = all.filter((x) => (!verdict || keyOf(x) === verdict) && (!language || x.language === language));
  const content = list.slice(number * size, number * size + size);
  const pageInfo = { number, size, totalElements: list.length, totalPages: Math.max(1, Math.ceil(list.length / size)) };
  return route.fulfill({ contentType: "application/json", body: JSON.stringify({ success: true, data: { content, page: pageInfo } }) });
}

async function serveSubmissions(page: Page, all: Sub[]) {
  await page.route(/\/api\/v1\/submissions\/my/, (route) => respond(route, all));
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
    await expect(summary.getByText("Runtime Error").locator("..")).toContainText("0");
  });

  test("filters by verdict and language, with a no-match state", async ({ page }) => {
    await serveSubmissions(page, SUBMISSIONS);
    await page.goto("/submissions");
    await expect(rows(page)).toHaveCount(4);

    const filteredRequest = page.waitForRequest((r) => /submissions\/my\?.*verdict=AC/.test(r.url()));
    await page.getByLabel("Filter by verdict").selectOption("AC");
    await filteredRequest;
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
        : respond(route, SUBMISSIONS)
    );
    await page.goto("/submissions");

    await expect(page.getByText("Couldn't load submissions")).toBeVisible();
    await expect(page.getByText("0 / 0 accepted")).toHaveCount(0);
    fail = false;
    await page.getByRole("button", { name: "Try again" }).click();
    await expect(rows(page)).toHaveCount(4);
  });

  test("a quick run is left out of the list and the summary", async ({ page }) => {
    await page.goto("/submissions");
    await expect(page.getByText("No submissions yet")).toBeVisible();

    await page.evaluate(async () => {
      const post = (url: string) =>
        fetch(url, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ language: "JAVA", code: "class A {}" }) });
      await post("/api/v1/problems/1/run");
      await post("/api/v1/problems/1/submit");
    });
    await page.reload();

    await expect(rows(page)).toHaveCount(1);
    await expect(page.getByRole("complementary", { name: "Submission summary" })).toContainText("1 / 1 accepted");
  });

  test("pages on the server while the summary counts every submission", async ({ page }) => {
    const many = Array.from({ length: 25 }, (_, i) => ({
      ...SUBMISSIONS[2],
      id: 100 + i,
      verdict: i < 20 ? "AC" : "WA",
      createdAt: minutesAgo(i + 1),
    }));
    await serveSubmissions(page, many);
    await page.goto("/submissions");

    await expect(rows(page)).toHaveCount(20);
    await expect(page.getByRole("complementary", { name: "Submission summary" })).toContainText("20 / 25 accepted");
    await expect(page.getByText("Showing 1–20 of 25")).toBeVisible();

    await page.getByRole("button", { name: "2", exact: true }).click();
    await expect(rows(page)).toHaveCount(5);
    await expect(page.getByText("Showing 21–25 of 25")).toBeVisible();
  });
});
