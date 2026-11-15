import { test, expect, type Page, type Route } from "@playwright/test";
import { installFullscreenMock, setMockAuthSession, setupMockApi } from "./fixtures/mock-api";
import type { ProblemFull } from "../src/types/api";

/** AC-3 (specs/practice-ide-rich-editor.spec.md): bài code một file làm và chấm ngay trong bài học. */

const problem: ProblemFull = {
  id: 610, slug: "double-it", title: "Double it", difficulty: "EASY", tags: [], type: "ALGORITHMIC",
  description: "Read an integer **n** and print `2n`.", timeLimitMs: 1000, memoryLimitMb: 256, status: "PUBLISHED",
};

const json = (route: Route, body: unknown, status = 200) =>
  route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });

async function serve(page: Page) {
  const state = { accepted: false, submitted: [] as string[] };
  const topic = () => ({
    id: 21, slug: "code-topic", title: "Input and output", estMinutes: 5, status: "PUBLISHED",
    progress: state.accepted ? "DONE" : "IN_PROGRESS", stepCount: 1, doneSteps: state.accepted ? 1 : 0,
    steps: [{ id: 1, position: 1, type: "CODE", title: "Double it", passed: state.accepted, attempts: 0,
      content: { problemId: problem.id, problemSlug: problem.slug, problemTitle: problem.title, difficulty: "EASY", published: true } }],
  });
  await page.route(/\/api\/v1\/topics\/code-topic$/, (route) => json(route, topic()));
  await page.route(new RegExp(`/api/v1/problems/${problem.id}/test-cases$`), (route) => json(route, [
    { id: 1, orderIndex: 0, input: "21\n", expectedOutput: "42\n", isHidden: false },
    { id: 2, orderIndex: 1, input: "5\n", expectedOutput: "10\n", isHidden: true },
  ]));
  await page.route(new RegExp(`/api/v1/problems/${problem.id}/submit$`), (route, request) => {
    state.submitted.push((request.postDataJSON() as { code: string }).code);
    return json(route, { id: 7000 + state.submitted.length, problemId: problem.id, status: "PENDING", verdict: null,
      passTestCases: 0, totalTestCases: 2, executionTimeMs: null, memoryUsedKb: null, errorMessage: null }, 202);
  });
  await page.route(/\/api\/v1\/submissions\/70\d\d$/, (route) => {
    const ok = state.submitted.at(-1)?.includes("* 2");
    if (ok) state.accepted = true;
    return json(route, { id: 7000 + state.submitted.length, problemId: problem.id, status: "COMPLETED", verdict: ok ? "AC" : "WA",
      passTestCases: ok ? 2 : 0, totalTestCases: 2, executionTimeMs: 40, memoryUsedKb: 9000, errorMessage: null });
  });
  await page.route(/\/api\/v1\/submissions\/70\d\d\/results$/, (route) => json(route, state.accepted
    ? [{ index: 0, verdict: "AC", hidden: false }, { index: 1, verdict: "AC", hidden: true }]
    : [{ index: 0, verdict: "WA", hidden: false, input: "21\n", expected: "42\n", actual: "21\n" }, { index: 1, verdict: "SKIPPED", hidden: true }]));
  return state;
}

test.describe("Practice: code exercise inside the lesson", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    const mock = await setupMockApi(page);
    mock.addProblem(problem);
    await setMockAuthSession(page, { username: "student", role: "USER" });
  });

  test("shows samples and a light editor; wrong answer then accepted without leaving the page", async ({ page }) => {
    const state = await serve(page);
    await page.goto("/topics/code-topic", { waitUntil: "domcontentloaded" });
    const step = page.getByRole("region", { name: "Step 1 of 1" });
    await expect(step).toContainText("Read an integer");
    await expect(step.getByLabel("Sample input 1")).toHaveText("21");
    await expect(step.getByLabel("Sample output 1")).toHaveText("42");
    await expect(step.getByLabel("Sample input 2")).toHaveCount(0); // testcase ẩn không lộ
    await expect(step.locator(".monaco-editor")).toHaveCount(0);

    const editor = step.getByRole("textbox", { name: "Code editor" });
    await editor.click();
    await page.keyboard.press("ControlOrMeta+a");
    await page.keyboard.type("class Solution { public static void main(String[] a) { System.out.println(new java.util.Scanner(System.in).nextInt()); } }");
    await step.getByRole("button", { name: "Submit" }).click();
    const verdict = step.getByRole("status");
    await expect(verdict).toContainText("Wrong answer", { timeout: 10_000 });
    await expect(verdict).toContainText("Failed on test 1");
    await expect(verdict).toContainText("Your output");

    await editor.click();
    await page.keyboard.press("ControlOrMeta+a");
    await page.keyboard.type("class Solution { public static void main(String[] a) { System.out.println(new java.util.Scanner(System.in).nextInt() * 2); } }");
    await step.getByRole("button", { name: "Submit" }).click();
    await expect(verdict).toContainText("Correct!", { timeout: 10_000 });
    await expect(page).toHaveURL(/\/topics\/code-topic$/);
    await expect(page.getByRole("button", { name: /Finish topic|Continue/ })).toBeVisible();
    expect(state.submitted).toHaveLength(2);
  });
});
