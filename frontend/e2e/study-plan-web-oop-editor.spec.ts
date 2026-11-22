import { test, expect, type Page, type Route } from "@playwright/test";
import { installFullscreenMock, setMockAuthSession, setupMockApi } from "./fixtures/mock-api";
import type { ProblemFull } from "../src/types/api";

const oopProblem: ProblemFull = {
  id: 101,
  slug: "shape-hierarchy",
  title: "Shape Hierarchy",
  difficulty: "EASY",
  tags: ["OOP", "Inheritance"],
  type: "OOP_GRINDING",
  description: "Build a shape hierarchy with `Shape`, `Circle`, and `Rectangle`.",
  timeLimitMs: 3000,
  memoryLimitMb: 256,
  status: "PUBLISHED",
  starterTemplates: JSON.stringify([
    { path: "Main.java", content: "public class Main {\n    public static void main(String[] args) {}\n}\n" },
    { path: "Shape.java", content: "public abstract class Shape {\n    public abstract double getArea();\n}\n" },
    { path: "Circle.java", content: "public class Circle extends Shape {\n    private double radius;\n    public Circle(double r) { this.radius = r; }\n    @Override public double getArea() { return Math.PI * radius * radius; }\n}\n" },
  ]),
};

const PLAN = {
  id: 1,
  slug: "oop-mastery",
  title: "OOP Mastery",
  description: "Complete OOP Course",
  status: "PUBLISHED",
  stageCount: 2,
  doneStages: 0,
  items: [
    {
      id: 10,
      position: 0,
      kind: "PROJECT",
      title: "Shape Hierarchy Project",
      done: false,
      stages: [
        {
          stageNo: 1,
          problemId: oopProblem.id,
          slug: oopProblem.slug,
          title: "Stage 1: Shapes",
          difficulty: "EASY",
          problemType: "OOP_GRINDING",
          published: true,
          status: "IN_PROGRESS",
        },
      ],
    },
  ],
};

const json = (route: Route, body: unknown, status = 200) =>
  route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });

async function setupOopMock(page: Page) {
  const state = { submittedFiles: [] as unknown[], lastRun: false };

  await page.route(/\/api\/v1\/study-plans\/oop-mastery$/, (route) => {
    const isDone = state.submittedFiles.length >= 2 && !state.lastRun;
    return json(route, {
      ...PLAN,
      doneStages: isDone ? 1 : 0,
      items: [
        {
          ...PLAN.items[0],
          done: isDone,
          stages: [
            {
              ...PLAN.items[0].stages[0],
              status: isDone ? "DONE" : "IN_PROGRESS",
            },
          ],
        },
      ],
    });
  });
  await page.route(new RegExp(`/api/v1/problems/${oopProblem.id}/test-cases$`), (route) =>
    json(route, [
      { id: 1, orderIndex: 0, input: "Circle 5\n", expectedOutput: "78.54\n", isHidden: false },
      { id: 2, orderIndex: 1, input: "Rectangle 4 6\n", expectedOutput: "24.00\n", isHidden: true },
    ])
  );

  await page.route(new RegExp(`/api/v1/problems/${oopProblem.id}/(submit|run)$`), (route, req) => {
    const postData = req.postDataJSON() as { language: string; files: unknown[] };
    state.submittedFiles.push(postData.files);
    state.lastRun = req.url().endsWith("/run") || new URL(req.url()).searchParams.get("run") === "true";

    const subId = 9900 + state.submittedFiles.length;
    return json(
      route,
      {
        id: subId,
        problemId: oopProblem.id,
        status: "PENDING",
        verdict: null,
        passTestCases: 0,
        totalTestCases: 2,
        score: null,
        executionTimeMs: null,
        memoryUsedKb: null,
        errorMessage: null,
      },
      202
    );
  });

  await page.route(/\/api\/v1\/submissions\/99\d\d$/, (route) => {
    const subId = Number(route.request().url().split("/").pop());
    if (state.lastRun) {
      return json(route, {
        id: subId,
        problemId: oopProblem.id,
        status: "COMPLETED",
        verdict: "AC",
        passTestCases: 1,
        totalTestCases: 1,
        score: null,
        executionTimeMs: 45,
        memoryUsedKb: 8000,
        errorMessage: null,
      });
    }

    return json(route, {
      id: subId,
      problemId: oopProblem.id,
      status: "COMPLETED",
      verdict: "AC",
      passTestCases: 2,
      totalTestCases: 2,
      score: 100.0,
      executionTimeMs: 65,
      memoryUsedKb: 12000,
      errorMessage: null,
    });
  });

  await page.route(/\/api\/v1\/submissions\/99\d\d\/results$/, (route) =>
    json(route, [
      { index: 0, verdict: "AC", hidden: false, input: "Circle 5\n", expected: "78.54\n", actual: "78.54\n" },
      { index: 1, verdict: "AC", hidden: true },
    ])
  );

  return state;
}

test.describe("Study Plan: Web Multi-File OOP Code Editor", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    const mock = await setupMockApi(page);
    mock.addProblem(oopProblem);
    await setMockAuthSession(page, { username: "student", role: "USER" });
  });

  test("renders multi-file tabs, supports adding/deleting files, running sample tests, and submitting solution on web", async ({
    page,
  }) => {
    const state = await setupOopMock(page);
    await page.goto("/study-plans/oop-mastery/10/1", { waitUntil: "domcontentloaded" });

    // 1. Verify stage heading and vertical layout
    await expect(page.getByRole("heading", { name: /Stage 1\/1/, level: 1 })).toBeVisible();

    // 2. Verify starter files tabs
    const stageRegion = page.getByRole("region", { name: "Stage" });
    const tabList = stageRegion.getByRole("tablist", { name: "Java files" });
    await expect(tabList).toBeVisible();

    await expect(tabList.getByRole("tab", { name: "Main.java" })).toBeVisible();
    await expect(tabList.getByRole("tab", { name: "Shape.java" })).toBeVisible();
    await expect(tabList.getByRole("tab", { name: "Circle.java" })).toBeVisible();

    // 3. Verify CodeMirror is present with active file
    const editor = stageRegion.getByRole("textbox", { name: "Code editor" });
    await expect(editor).toBeVisible();

    // 4. Switch tabs to Shape.java
    await tabList.getByRole("tab", { name: "Shape.java" }).click();
    await expect(editor).toContainText("abstract class Shape");

    // 5. Add a new Java file (Rectangle.java)
    page.once("dialog", async (dialog) => {
      expect(dialog.message()).toContain("Enter new Java class name");
      await dialog.accept("Rectangle.java");
    });
    await tabList.getByRole("button", { name: "Add Java file" }).click();

    await expect(tabList.getByRole("tab", { name: "Rectangle.java" })).toBeVisible();
    await expect(editor).toContainText("public class Rectangle");

    // 6. Delete a file (Rectangle.java)
    page.once("dialog", async (dialog) => {
      expect(dialog.message()).toContain("Delete Rectangle.java?");
      await dialog.accept();
    });
    await tabList.getByRole("tab", { name: "Rectangle.java" }).getByRole("button", { name: "Delete Rectangle.java" }).click();
    await expect(tabList.getByRole("tab", { name: "Rectangle.java" })).toHaveCount(0);

    // 7. Verify Run button runs public sample testcases
    const runBtn = stageRegion.getByRole("button", { name: "Run" });
    await expect(runBtn).toBeVisible();
    await runBtn.click();

    const verdict = stageRegion.getByRole("status").filter({ hasText: /evaluated|Correct/ });
    await expect(verdict).toContainText("Run mode", { timeout: 10_000 });
    await expect(verdict).toContainText("Correct!");
    expect(state.lastRun).toBe(true);

    // 8. Verify Submit button submits all files for official grading
    const submitBtn = stageRegion.getByRole("button", { name: "Submit" });
    await expect(submitBtn).toBeVisible();
    await submitBtn.click();

    await expect(verdict).toContainText("Correct!", { timeout: 10_000 });
    await expect(verdict).toContainText("Score: 100.0 / 100.0");
    expect(state.lastRun).toBe(false);

    // 9. Verify IntelliJ button remains accessible alongside the web editor
    await expect(stageRegion.getByText("Prefer desktop IDE?")).toBeVisible();
    await expect(
      stageRegion.getByRole("button", { name: "Connect IntelliJ" })
        .or(stageRegion.getByRole("link", { name: "Install the IntelliJ plugin" }))
    ).toBeVisible();
  });
});
