import { test, expect } from "@playwright/test";
import { setupMockApi, setMockAuthSession } from "./fixtures/mock-api";

/** Admin Tags: one-row-per-tag list with search and category filter; "New tag" validates inline; tag dialog moves and recolors a tag. */
test("admin lists and filters tags, validates a new tag, moves and recolors a tag", async ({ page }) => {
  await setupMockApi(page);
  const tags = [
    { name: "Array", category: "DSA", problemCount: 3 },
    { name: "Graph", category: "DSA", problemCount: 0 },
    { name: "Misc", category: "Other", problemCount: 1 },
  ];
  const puts: unknown[] = [];
  const posts: unknown[] = [];
  await page.route(/\/api\/v1\/admin\/tags(\/.*)?$/, async (route, request) => {
    if (request.method() === "PUT") {
      const body = request.postDataJSON() as { category?: string; color?: string };
      puts.push(body);
      const graph: { category: string; color?: string } = tags.find((t) => t.name === "Graph")!;
      if (body.category) graph.category = body.category;
      if (body.color) graph.color = body.color;
      return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(graph) });
    }
    if (request.method() === "POST") {
      const body = request.postDataJSON() as { name: string; category: string };
      posts.push(body);
      tags.push({ ...body, problemCount: 0 });
      return route.fulfill({ status: 201, contentType: "application/json", body: JSON.stringify({ ...body, problemCount: 0 }) });
    }
    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(tags) });
  });
  await setMockAuthSession(page, { username: "admin", role: "ADMIN" });
  await page.goto("/admin/tags", { waitUntil: "domcontentloaded" });

  const list = page.getByRole("region", { name: "Tags" });
  const rows = list.getByRole("listitem");
  await expect(rows).toHaveCount(3);
  await expect(rows.filter({ hasText: "Graph" })).toContainText("Unused");
  await expect(rows.filter({ hasText: "Array" })).toContainText("3 problems");
  await page.getByLabel("Filter by category").selectOption("DSA");
  await expect(rows).toHaveCount(2);
  await page.getByLabel("Filter by category").selectOption("");
  await page.getByLabel("Search tags").fill("mis");
  await expect(rows).toHaveCount(1);
  await page.getByLabel("Search tags").fill("");

  // Empty name: the button still works and the dialog shows the error.
  await page.getByRole("button", { name: "New tag" }).click();
  const dialog = page.getByRole("dialog", { name: "New tag" });
  await dialog.getByRole("button", { name: "Add tag" }).click();
  await expect(dialog.getByRole("alert")).toHaveText("Enter a tag name.");
  await dialog.getByLabel("Name").fill("Heap");
  await dialog.getByLabel("Category").selectOption("DSA");
  await dialog.getByRole("button", { name: "Add tag" }).click();
  await expect(dialog).toBeHidden();
  expect(posts).toEqual([{ name: "Heap", category: "DSA" }]);
  await expect(rows).toHaveCount(4);

  await list.getByRole("button", { name: "Edit Graph" }).click();
  const edit = page.getByRole("dialog", { name: "Edit tag" });
  await edit.getByLabel("Category").selectOption("Other");
  await edit.getByRole("button", { name: "Tag color" }).click();
  await page.getByRole("button", { name: "#dc2626" }).click();
  await edit.getByRole("button", { name: "Save" }).click();
  await expect(edit).toBeHidden();
  expect(puts).toEqual([{ category: "Other", color: "#dc2626" }]);
  await page.getByLabel("Filter by category").selectOption("Other");
  await expect(rows).toHaveCount(2);

  await page.getByRole("button", { name: "Categories" }).click();
  await expect(page.getByRole("list", { name: "Categories" }).getByRole("listitem")).toHaveCount(2);
  await page.screenshot({ path: process.env.SHOT ?? "test-results/admin-tags.png", fullPage: true });
});
