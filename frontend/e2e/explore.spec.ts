import { test, expect, type Page } from "@playwright/test";
import { setMockAuthSession, setupMockApi } from "./fixtures/mock-api";

const row = (id: number, slug: string, title: string) => ({ id, slug, title, difficulty: "EASY", tags: [], type: "ALGORITHMIC" });
const PROBLEMS = [
  row(7, "number-of-islands", "Number of Islands"),
  row(1, "two-sum", "Two Sum"),
  row(2, "valid-parentheses", "Valid Parentheses"),
  row(30, "not-in-any-collection", "Loose Problem"),
];

async function serve(page: Page, content: object[] | "error") {
  await page.route(/\/api\/v1\/problems\?/, (route) => {
    if (content === "error")
      return route.fulfill({ status: 500, contentType: "application/json", body: JSON.stringify({ title: "Internal Server Error", detail: "Boom" }) });
    const p = { content, totalElements: content.length, totalPages: 1, number: 0, size: 100, first: true, last: true };
    return route.fulfill({ contentType: "application/json", body: JSON.stringify({ success: true, data: p, ...p }) });
  });
  await page.route(/\/api\/v1\/problems\/tags/, (route) =>
    route.fulfill({ contentType: "application/json", body: JSON.stringify({ success: true, data: [{ tag: "Graph", count: 3 }] }) })
  );
}

test.describe("/explore", () => {
  test.beforeEach(async ({ page }) => {
    await setupMockApi(page);
    await setMockAuthSession(page, { username: "student", role: "USER" });
  });

  test("counts collections from real problems, lists recent problems and topics", async ({ page }) => {
    await serve(page, PROBLEMS);
    await page.goto("/explore");

    // Socket lab không có bài nào trong API nên bị ẩn.
    await expect(page.getByRole("button", { name: /Start here/ })).toContainText("2 problems");
    await expect(page.getByRole("button", { name: /Graphs in practice/ })).toContainText("1 problem");
    await expect(page.getByRole("button", { name: /Socket lab/ })).toHaveCount(0);

    await page.getByRole("button", { name: /Start here/ }).click();
    const list = page.getByRole("region", { name: "Start here" });
    await expect(list.getByRole("link")).toHaveCount(2);

    await expect(page.locator("section[aria-labelledby=newest]").getByRole("link")).toHaveCount(4);
    await expect(page.getByRole("link", { name: /Graph/ }).last()).toHaveAttribute("href", "/problems?tags=Graph");
  });

  test("shows empty and error states", async ({ page }) => {
    await serve(page, "error");
    await page.goto("/explore");
    await expect(page.getByRole("alert").filter({ hasText: "Couldn't load problems" })).toContainText("Boom");

    await page.unroute(/\/api\/v1\/problems\?/);
    await serve(page, []);
    await page.getByRole("button", { name: "Try again" }).click();
    await expect(page.getByText("No problems yet")).toBeVisible();
  });
});
