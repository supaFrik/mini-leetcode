import { test, expect } from "@playwright/test";
import { setupMockApi, setMockAuthSession } from "./fixtures/mock-api";

/** Admin Tags: categories on the left filter the tag table; the category chip moves a tag. */
test("admin filters tags by category and moves a tag with its chip", async ({ page }) => {
  await setupMockApi(page);
  const tags = [
    { name: "Array", category: "DSA", problemCount: 3 },
    { name: "Graph", category: "DSA", problemCount: 0 },
    { name: "Misc", category: "Other", problemCount: 1 },
  ];
  const moves: unknown[] = [];
  await page.route(/\/api\/v1\/admin\/tags(\/.*)?$/, async (route, request) => {
    if (request.method() === "PUT") {
      moves.push(request.postDataJSON());
      return route.fulfill({ status: 200, contentType: "application/json", body: request.postData() ?? "{}" });
    }
    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(tags) });
  });
  await setMockAuthSession(page, { username: "admin", role: "ADMIN" });
  await page.goto("/admin/tags", { waitUntil: "domcontentloaded" });

  await expect(page.getByRole("heading", { name: "Categories" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Tags" })).toBeVisible();
  const table = page.getByRole("region", { name: "Tags" });
  await expect(table.getByRole("row")).toHaveCount(4);

  await page.getByRole("button", { name: "DSA", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(3);
  await expect(page.getByLabel("Category for new tag")).toHaveValue("DSA");

  await page.getByRole("button", { name: "Category for Graph" }).click();
  await page.getByRole("menuitemradio", { name: "Other" }).click();
  await expect(table.getByRole("row")).toHaveCount(2);
  expect(moves).toEqual([{ category: "Other" }]);

  await page.getByRole("button", { name: "All", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(4);
  await page.screenshot({ path: process.env.SHOT ?? "test-results/admin-tags.png", fullPage: true });
});
