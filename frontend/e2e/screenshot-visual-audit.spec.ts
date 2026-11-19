import { test, expect, type Page, type Route } from "@playwright/test";
import { setupMockApi, setMockAuthSession } from "./fixtures/mock-api";
import type { ProblemFull } from "../src/types/api";
import path from "path";

const SCREENSHOT_DIR = path.resolve(process.cwd(), "test-results", "screenshots");

const sampleOopProblem: ProblemFull = {
  id: 77,
  slug: "mini-bank-console",
  title: "Mini Bank Console",
  difficulty: "MEDIUM",
  tags: ["OOP", "Java"],
  description: "Implement a robust console banking application with account management, encapsulation, and transactions.",
  timeLimitMs: 2000,
  memoryLimitMb: 256,
  status: "DRAFT",
  type: "OOP_GRINDING",
  starterTemplates: JSON.stringify([
    { path: "Account.java", content: "public class Account {\n    private double balance;\n}\n" },
    { path: "Main.java", content: "public class Main {\n    public static void main(String[] args) {\n    }\n}\n" },
  ]),
};

async function setupAuthoringMock(page: Page) {
  const authoringState = {
    problemId: sampleOopProblem.id,
    status: sampleOopProblem.status,
    problemType: sampleOopProblem.type,
    gradingConfig: JSON.stringify({
      rules: [
        {
          ruleId: "preset-encapsulation",
          ruleName: "Enforce Encapsulation",
          targetClassName: "*",
          expectedType: "ANY",
          enforceEncapsulation: true,
          weight: 1.0,
        },
      ],
    }),
    referenceSolution: [
      {
        path: "Account.java",
        content: "public class Account {\n    private double balance;\n    public Account(double b) { this.balance = b; }\n    public double getBalance() { return balance; }\n}\n",
      },
      {
        path: "Main.java",
        content: "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Bank ready\");\n    }\n}\n",
      },
    ],
    gradingFrozen: false,
    timeLimitMs: 2000,
    memoryLimitMb: 256,
  };

  const json = (route: Route, body: unknown, status = 200) =>
    route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });

  await page.route(`**/api/v1/admin/problems/${sampleOopProblem.id}/**`, async (route, request) => {
    const url = new URL(request.url()).pathname;
    if (url.endsWith("/authoring")) return json(route, authoringState);
    if (url.endsWith("/test-cases")) {
      return json(route, [
        { id: 1, input: "deposit 100\nbalance", expectedOutput: "100", isHidden: false, orderIndex: 1 },
        { id: 2, input: "withdraw 50\nbalance", expectedOutput: "50", isHidden: true, orderIndex: 2 },
      ]);
    }
    if (url.endsWith("/grading-config/validate")) {
      return json(route, { valid: true, errors: [], ruleCount: 1, socketScenarioCount: 0, totalRuleWeight: 1 });
    }
    return json(route, authoringState);
  });
}

test.describe("Visual Audit Screenshots — User & Admin Roles", () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
  });

  test("User Role — Problems, Study Plans, Contest, Workspace", async ({ page }) => {
    await setupMockApi(page, { customProblems: [sampleOopProblem] });
    await setMockAuthSession(page, { id: 10, username: "student", role: "USER" });

    // 1. Problems page
    await page.goto("/problems");
    await page.waitForSelector("main");
    await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "01_user_problems.png"), fullPage: true });

    // 2. Study Plans page
    await page.goto("/study-plans");
    await page.waitForSelector("main");
    await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "02_user_study_plans.png"), fullPage: true });

    // 3. Contest page
    await page.goto("/contest");
    await page.waitForSelector("main");
    await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "03_user_contest.png"), fullPage: true });

    // 4. Workspace page
    await page.goto(`/problems/${sampleOopProblem.slug}`);
    await page.waitForSelector("header");
    await page.waitForTimeout(800);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "04_user_workspace.png") });
  });

  test("Admin Role — Admin Problems list and Problem Authoring tabs", async ({ page }) => {
    await setupMockApi(page, { customProblems: [sampleOopProblem] });
    await setupAuthoringMock(page);
    await setMockAuthSession(page, { id: 1, username: "devadmin", role: "ADMIN" });

    // 5. Admin Problems list
    await page.goto("/admin/problems");
    await page.waitForSelector("main");
    await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "05_admin_problems_list.png"), fullPage: true });

    // 6. Admin Problem Authoring — Statement Tab
    await page.goto(`/admin/problems/${sampleOopProblem.id}`);
    await page.waitForSelector('[role="tablist"]');
    await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "06_admin_authoring_statement.png"), fullPage: true });

    // 7. Admin Problem Authoring — Tests Tab
    await page.getByRole("tab", { name: "Tests" }).click();
    await page.waitForTimeout(500);
    // Verify subtitle is removed
    const testsSubtitle = page.locator("text=Run uses public cases; Submit uses all of them");
    await expect(testsSubtitle).toHaveCount(0);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "07_admin_authoring_tests.png"), fullPage: true });

    // 8. Admin Problem Authoring — Grading Tab
    await page.getByRole("tab", { name: "Grading" }).click();
    await page.waitForTimeout(500);
    // Verify verbose subtitles are removed
    await expect(page.locator("text=Structural reflection rules (40%)")).toHaveCount(0);
    await expect(page.locator("text=Toggle common OOP structural constraints")).toHaveCount(0);
    await expect(page.locator("text=All fields must be private or protected")).toHaveCount(0);
    await expect(page.locator("text=Requires at least one interface or abstract class")).toHaveCount(0);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "08_admin_authoring_grading.png"), fullPage: true });

    // 9. Admin Problem Authoring — Reference Tab
    await page.getByRole("tab", { name: "Reference" }).click();
    await page.waitForTimeout(500);
    // Verify reference subtitles are removed
    await expect(page.locator("text=Working reference program used to generate")).toHaveCount(0);
    await expect(page.locator("text=Type a line and press Enter to send it to stdin")).toHaveCount(0);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "09_admin_authoring_reference.png"), fullPage: true });

    // 10. Admin Contests
    await page.goto("/admin/contests");
    await page.waitForSelector("main");
    await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, "10_admin_contests.png"), fullPage: true });
  });
});
