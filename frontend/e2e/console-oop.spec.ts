import { test, expect } from "@playwright/test";
import { setupMockApi, setMockAuthSession, installFullscreenMock } from "./fixtures/mock-api";
import type { ProblemFull } from "../src/types/api";

/**
 * E2E cho tính năng console OOP (stdin/stdout tương tác).
 *
 * Bao phủ:
 * - Tab Console chỉ hiện với bài OOP_GRINDING, ngoài kỳ thi.
 * - Gửi lệnh → hiển thị stdout + exit code + runtime + peak memory.
 * - Lỗi biên dịch → hiển thị compile errors.
 * - Transcript được nạp lại khi mở lại tab.
 * - Xoá transcript.
 * - Console bị chặn (403) trong kỳ thi.
 */

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

  test("1.1: Console tab is visible for OOP problems", async ({ page }) => {
    await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

    const consoleTab = page.getByRole("tab", { name: "Console" });
    await expect(consoleTab).toBeVisible();
  });

  test("1.2: Console tab is hidden for algorithmic problems", async ({ page }) => {
    await page.goto("/problems/two-sum-oop-check", { waitUntil: "domcontentloaded" });

    await expect(page.getByRole("tab", { name: "Console" })).toHaveCount(0);
  });

  test("1.3: opening the Console tab shows the command input", async ({ page }) => {
    await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

    await page.getByRole("tab", { name: "Console" }).click();

    await expect(page.getByLabel("Console command")).toBeVisible();
    await expect(page.getByRole("button", { name: /Send/ })).toBeVisible();
  });

  test("2.1: sending a command shows stdout and the execution metrics", async ({ page }) => {
    await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });
    await page.getByRole("tab", { name: "Console" }).click();

    await page.getByLabel("Console command").fill("new Circle(2).getArea()");

    await Promise.all([
      page.waitForResponse((r) => r.url().includes("/console/execute")),
      page.getByRole("button", { name: /Send/ }).click(),
    ]);

    const log = page.getByRole("log", { name: "Console output" });
    await expect(log).toContainText("12.566370614359172");
    // Metrics line: exit code, runtime, peak memory.
    await expect(log).toContainText("exit=0");
    await expect(log).toContainText("runtime=87 ms");
    await expect(log).toContainText("peak memory=24.0 MB");
  });

  test("2.2: the echoed command appears in the log", async ({ page }) => {
    await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });
    await page.getByRole("tab", { name: "Console" }).click();

    await page.getByLabel("Console command").fill("new Circle(2).getArea()");
    await page.getByRole("button", { name: /Send/ }).click();

    const log = page.getByRole("log", { name: "Console output" });
    await expect(log).toContainText("$ new Circle(2).getArea()");
  });

  test("2.3: compile errors are surfaced in the log", async ({ page }) => {
    await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });
    await page.getByRole("tab", { name: "Console" }).click();

    await page.getByLabel("Console command").fill("BROKEN");
    await page.getByRole("button", { name: /Send/ }).click();

    const log = page.getByRole("log", { name: "Console output" });
    await expect(log).toContainText("error: ';' expected");
  });

  test("3.1: transcript is restored when the Console tab is reopened", async ({ page }) => {
    await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });
    await page.getByRole("tab", { name: "Console" }).click();

    await page.getByLabel("Console command").fill("42");
    await page.getByRole("button", { name: /Send/ }).click();
    await expect(page.getByRole("log", { name: "Console output" })).toContainText("42");

    // Chuyển sang tab khác rồi quay lại → transcript vẫn còn.
    await page.getByRole("tab", { name: "Test Result" }).click();
    await page.getByRole("tab", { name: "Console" }).click();

    await expect(page.getByRole("log", { name: "Console output" })).toContainText("42");
  });

  test("3.2: Clear wipes the console log", async ({ page }) => {
    await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });
    await page.getByRole("tab", { name: "Console" }).click();

    await page.getByLabel("Console command").fill("42");
    await page.getByRole("button", { name: /Send/ }).click();
    await expect(page.getByRole("log", { name: "Console output" })).toContainText("42");

    await page.getByRole("button", { name: /Clear/ }).click();

    await expect(page.getByRole("log", { name: "Console output" })).not.toContainText("42");
  });

  test("3.3: the remaining command counter is shown", async ({ page }) => {
    await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });
    await page.getByRole("tab", { name: "Console" }).click();

    await expect(page.getByTestId("console-remaining")).toContainText("50 commands left");

    await page.getByLabel("Console command").fill("42");
    await page.getByRole("button", { name: /Send/ }).click();

    await expect(page.getByTestId("console-remaining")).toContainText("49 commands left");
  });

  test("4.1: a 403 from the server is surfaced as an error", async ({ page }) => {
    await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });
    await page.getByRole("tab", { name: "Console" }).click();

    // Route này được đăng ký SAU mock catch-all ⇒ nó thắng (Playwright ưu tiên
    // route đăng ký sau cùng), nên không cần route.continue().
    await page.route("**/api/v1/problems/*/console/execute", async (route) => {
      return route.fulfill({
        status: 403,
        contentType: "application/problem+json",
        body: JSON.stringify({
          type: "https://api.mini-leetcode.com/errors/forbidden",
          title: "Forbidden",
          status: 403,
          detail: "Console is disabled during the exam",
        }),
      });
    });

    await page.getByLabel("Console command").fill("42");
    await page.getByRole("button", { name: /Send/ }).click();

    const alert = page.getByRole("tabpanel", { name: "Console" }).getByRole("alert");
    await expect(alert).toContainText("Console is disabled during the exam");
  });
});
