import { test, expect, type Page } from "@playwright/test";
import { createMockJwt, installFullscreenMock, setMockAuthSession, setupMockApi } from "./fixtures/mock-api";

/**
 * "Solved" must only count problems the signed-in user has an Accepted submission for.
 * The summary line, Your progress, the per-row ticks and the Status filter must agree.
 * The default fixture has 14 problems (5 Easy, 5 Medium, 4 Hard) and no submissions.
 */

const rows = (page: Page) => page.getByRole("region", { name: "Problem list" }).getByRole("link");

async function expectNothingSolved(page: Page) {
  await expect(page.locator("h1 + p")).toHaveText("14 problems, 0 solved");
  const progress = page.getByRole("complementary", { name: "Filters and progress" });
  await expect(progress.getByText("/ 14 solved")).toBeVisible();
  await expect(progress.locator("b").first()).toHaveText("0");
  await expect(page.getByRole("progressbar", { name: "Easy solved" })).toHaveAttribute("aria-valuenow", "0");
  await expect(page.getByRole("progressbar", { name: "Easy solved" })).toHaveAttribute("aria-valuemax", "5");
  await expect(page.getByRole("progressbar", { name: "Medium solved" })).toHaveAttribute("aria-valuemax", "5");
  await expect(page.getByRole("progressbar", { name: "Hard solved" })).toHaveAttribute("aria-valuemax", "4");

  await expect(rows(page)).toHaveCount(14);
  // No row is marked solved or attempted.
  for (const label of await rows(page).evaluateAll((els) => els.map((e) => e.getAttribute("aria-label") ?? ""))) {
    expect(label).not.toMatch(/, (solved|attempted)$/);
  }
}

async function expectStatusFilterConsistent(page: Page) {
  const status = page.getByRole("group", { name: "Filter by status" });

  await status.getByRole("button", { name: "Solved" }).click();
  await expect(page).toHaveURL(/progress=DONE/);
  await expect(page.getByText("No problems match your filters")).toBeVisible();

  await status.getByRole("button", { name: "Attempted" }).click();
  await expect(page).toHaveURL(/progress=IN_PROGRESS/);
  await expect(page.getByText("No problems match your filters")).toBeVisible();

  await status.getByRole("button", { name: "Todo" }).click();
  await expect(page).toHaveURL(/progress=NEVER/);
  await expect(rows(page)).toHaveCount(14);
}

test.describe("/problems progress when the user has solved nothing", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
  });

  test("signed-in user with no Accepted submission: 0 solved everywhere", async ({ page }) => {
    await setupMockApi(page);
    await setMockAuthSession(page, { username: "student", role: "USER" });
    await page.goto("/problems");

    await expectNothingSolved(page);
    // Rows are recognised by the server and are all "todo".
    await expect(rows(page).first()).toHaveAttribute("aria-label", /, todo$/);
    await expectStatusFilterConsistent(page);
  });

  test("server does not recognise the stored token: still 0 solved, not the total", async ({ page }) => {
    const token = createMockJwt({ id: 1, username: "student", role: "USER" });
    await setupMockApi(page, { rejectedTokens: [token] });
    await page.addInitScript((tok) => {
      localStorage.setItem("access_token", tok);
      localStorage.setItem("refresh_token", "mock_refresh_token");
    }, token);
    await page.goto("/problems");

    await expectNothingSolved(page);
    await expect(page.getByRole("link", { name: "Log in" }).last()).toBeVisible();
    await expectStatusFilterConsistent(page);
  });

  test("signed-out visitor: summary and progress show 0 solved and stay visible", async ({ page }) => {
    await setupMockApi(page);
    await page.goto("/problems");

    await expectNothingSolved(page);
    await expectStatusFilterConsistent(page);
  });

  test("progress API failure shows an error instead of a fake 0 solved", async ({ page }) => {
    await setupMockApi(page);
    await setMockAuthSession(page, { username: "student", role: "USER" });
    // Only the counting requests (size=1) fail; the list itself still loads.
    await page.route(/\/api\/v1\/problems\?.*size=1(&|$)/, (route) =>
      route.fulfill({ status: 500, contentType: "application/json", body: JSON.stringify({ title: "Internal Server Error", detail: "Boom" }) })
    );
    await page.goto("/problems");

    await expect(page.getByText("Couldn't load progress.", { exact: false })).toBeVisible();
    await expect(page.locator("h1 + p")).not.toContainText("solved");
    await expect(page.getByText("/ 14 solved")).toHaveCount(0);
  });
});
