import { test, expect } from "@playwright/test";
import { setupMockApi, setMockAuthSession, installFullscreenMock } from "./fixtures/mock-api";
import type { ProblemFull } from "../src/types/api";

/** E2E console OOP kiểu terminal IDE: chạy main với stdin, hiển thị output. */
const oopProblem: ProblemFull = {
  id: 42,
  slug: "shape-hierarchy",
  title: "Shape Hierarchy & Area Calculation",
  difficulty: "MEDIUM",
  tags: ["OOP", "Polymorphism"],
  description: "Design an abstract class `Shape` and two concrete subclasses.",
  timeLimitMs: 2000,
  memoryLimitMb: 256,
  status: "PUBLISHED",
  type: "OOP_GRINDING",
  likes: 420,
  dislikes: 12,
};

const algorithmicProblem: ProblemFull = {
  id: 43,
  slug: "two-sum-oop-check",
  title: "Two Sum",
  difficulty: "EASY",
  tags: ["Array"],
  description: "Given an array of integers.",
  timeLimitMs: 1000,
  memoryLimitMb: 256,
  status: "PUBLISHED",
  type: "ALGORITHMIC",
};

test.describe("OOP Interactive Console E2E", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    const mockController = await setupMockApi(page);
    mockController.addProblem(oopProblem);
    mockController.addProblem(algorithmicProblem);
    await setMockAuthSession(page, { username: "student", role: "USER" });
  });

  const openConsole = async (page: import("@playwright/test").Page) => {
    await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });
    await page.getByRole("tab", { name: "Console" }).click();
  };
  const output = (page: import("@playwright/test").Page) => page.getByRole("log", { name: "Console output" });

  test("Console tab only on OOP problems", async ({ page }) => {
    await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });
    await expect(page.getByRole("tab", { name: "Console" })).toBeVisible();
    await page.goto("/problems/two-sum-oop-check", { waitUntil: "domcontentloaded" });
    await expect(page.getByRole("tab", { name: "Console" })).toHaveCount(0);
  });

  test("Run sends stdin and shows stdout + metrics; stdin is kept", async ({ page }) => {
    await openConsole(page);
    await page.getByLabel("Standard input").fill("hello stdin");
    await Promise.all([
      page.waitForResponse((r) => r.url().includes("/console/execute")),
      page.getByRole("button", { name: "Run main" }).click(),
    ]);

    await expect(output(page)).toContainText("HELLO STDIN");
    await expect(output(page)).toContainText("exit=0 | runtime=87 ms | peak memory=24.0 MB");
    await expect(page.getByLabel("Standard input")).toHaveValue("hello stdin");
  });

  test("Ctrl+Enter runs; compile errors are shown", async ({ page }) => {
    await openConsole(page);
    await page.getByLabel("Standard input").fill("BROKEN");
    await page.getByLabel("Standard input").press("Control+Enter");

    await expect(output(page)).toContainText("error: ';' expected");
  });

  test("Server errors (e.g. exam 403) are surfaced", async ({ page }) => {
    await openConsole(page);
    // Đăng ký sau catch-all ⇒ thắng.
    await page.route("**/api/v1/problems/*/console/execute", (route) =>
      route.fulfill({
        status: 403,
        contentType: "application/problem+json",
        body: JSON.stringify({ title: "Forbidden", status: 403, detail: "Console is disabled during the exam" }),
      }),
    );
    await page.getByRole("button", { name: "Run main" }).click();

    await expect(output(page).getByRole("alert")).toContainText("Console is disabled during the exam");
  });
});

