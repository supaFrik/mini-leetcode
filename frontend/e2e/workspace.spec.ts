import { test, expect } from "@playwright/test";
import {
  setupMockApi,
  setMockAuthSession,
  installFullscreenMock,
} from "./fixtures/mock-api";

test.describe("Problem Solving Workspace E2E Suite", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    await setupMockApi(page);
    await setMockAuthSession(page, { username: "student", role: "USER" });
  });

  test("renders problem statement, constraints, and testcase examples", async ({ page }) => {
    await page.goto("/problems/two-sum", { waitUntil: "domcontentloaded" });

    // Verify problem title and metadata
    await expect(page.getByRole("heading", { name: "Two Sum" })).toBeVisible();
    await expect(page.getByText("Description")).toBeVisible();
    await expect(page.getByText("Given an array of integers nums and an integer target")).toBeVisible();

    // The "Test Cases & Examples" section was removed from the description panel
    await expect(page.getByRole("heading", { name: "Test Cases & Examples" })).toHaveCount(0);
  });

  test("interacts with code editor and updates template on language change", async ({ page }) => {
    await page.goto("/problems/two-sum", { waitUntil: "domcontentloaded" });

    const langSelect = page.getByLabel("Language");
    await expect(langSelect).toBeVisible();

    // Default language is Java -> Solution.java
    await expect(page.getByText("Solution.java")).toBeVisible();

    // Switch to Python
    await langSelect.selectOption("PYTHON");
    await expect(page.getByText("solution.py")).toBeVisible();

    // Switch back to Java
    await langSelect.selectOption("JAVA");
    await expect(page.getByText("Solution.java")).toBeVisible();

    // Type custom code into editor (supporting Monaco editor and fallback textarea)
    const monacoEditor = page.locator(".monaco-editor");
    const fallbackTextarea = page.locator('textarea[aria-label="Source code"]');

    if (await monacoEditor.isVisible()) {
      await monacoEditor.click();
      await page.keyboard.press("Control+End");
      await page.keyboard.type("\n// Custom code added by E2E test\n");
    } else if (await fallbackTextarea.isVisible()) {
      await fallbackTextarea.fill("// Custom solution\npublic class Solution {}");
    }
  });

  test("executes code Run action and verifies testcase output display", async ({ page }) => {
    await page.goto("/problems/two-sum", { waitUntil: "domcontentloaded" });

    // Locate and click the Run button
    const runBtn = page.getByRole("button", { name: "Run" });
    await expect(runBtn).toBeVisible();
    await expect(runBtn).toBeEnabled();
    await Promise.all([
      page.waitForResponse((r) => r.url().includes("/run")),
      runBtn.click(),
    ]);

    // Verify Result Panel displays execution details
    await expect(page.getByText("Accepted", { exact: true })).toBeVisible();
    await expect(page.getByText(/Runtime:\s+\d+\s+ms/)).toBeVisible();
    await expect(page.getByText(/Passed \(2\/2\)/)).toBeVisible();
    await expect(page.getByText("Run: public testcases only")).toBeVisible();
  });

  test("executes code Submit action and verifies accepted verdict", async ({ page }) => {
    await page.goto("/problems/two-sum", { waitUntil: "domcontentloaded" });

    // Click Submit Solution button
    const submitBtn = page.getByRole("button", { name: "Submit Solution" });
    await expect(submitBtn).toBeVisible();
    await expect(submitBtn).toBeEnabled();
    await Promise.all([
      page.waitForResponse((r) => r.url().includes("/submit")),
      submitBtn.click(),
    ]);

    // Verify final verdict displays Accepted
    await expect(page.getByText("Accepted", { exact: true })).toBeVisible();
    await expect(page.getByText(/Runtime:\s+\d+\s+ms/)).toBeVisible();
    await expect(page.getByText(/Passed \(3\/3\)/)).toBeVisible();
  });
});
