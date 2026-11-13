import { test, expect, type Page, type Route } from "@playwright/test";
import { setupMockApi, setMockAuthSession, installFullscreenMock } from "./fixtures/mock-api";
import type { ProblemFull } from "../src/types/api";

/**
 * E2E cho tính năng soạn bài console/stdin của admin: lời giải mẫu, grading_config, testcase sinh từ lời
 * giải mẫu và console thử. API soạn bài được mock ở đây; lời giải mẫu "chạy" = in gấp đôi số đọc được.
 */

const problem: ProblemFull = {
  id: 77, slug: "mini-bank-console", title: "Mini Bank Console", difficulty: "MEDIUM", tags: ["OOP"],
  description: "Read commands from stdin.", timeLimitMs: 2000, memoryLimitMb: 256, status: "DRAFT", type: "OOP_GRINDING",
  starterTemplates: JSON.stringify([{ path: "Main.java", content: "public class Main {}\n" }]),
};

interface Case { id: number; input: string; expectedOutput: string; isHidden: boolean; orderIndex: number }

/** Đọc một field text trong body multipart (FormData của trình duyệt). */
function multipartField(body: string, name: string): string | undefined {
  const m = body.match(new RegExp(`name="${name}"(?:; filename="[^"]*")?\\r\\n(?:Content-Type: [^\\r]*\\r\\n)?\\r\\n([\\s\\S]*?)\\r\\n--`));
  return m?.[1];
}

const reference = (stdin: string) => `${Number(stdin.trim()) * 2}\n`;

async function mockAuthoring(page: Page) {
  const state = {
    gradingConfig: null as string | null,
    referenceSolution: [] as { path: string; content: string }[],
    cases: [] as Case[],
    runs: [] as unknown[],
  };
  const authoring = () => ({
    problemId: problem.id, status: problem.status, problemType: problem.type, gradingConfig: state.gradingConfig,
    referenceSolution: state.referenceSolution, gradingFrozen: false, timeLimitMs: 2000, memoryLimitMb: 256,
  });
  const json = (route: Route, body: unknown, status = 200) =>
    route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });

  await page.route(`**/api/v1/admin/problems/${problem.id}/**`, async (route, request) => {
    const path = new URL(request.url()).pathname;
    const body = request.postDataJSON?.() ?? null;
    if (path.endsWith("/authoring")) return json(route, authoring());
    if (path.endsWith("/reference-solution")) {
      state.referenceSolution = body.files;
      return json(route, authoring());
    }
    if (path.endsWith("/grading-config/validate") || path.endsWith("/grading-config")) {
      const raw: string = body.gradingConfig ?? "";
      const errors = raw.includes("KLASS") ? ["rules[0]: Cannot deserialize value of type `ExpectedClassType` from String \"KLASS\""] : [];
      if (path.endsWith("/validate")) {
        return json(route, { valid: errors.length === 0, errors, ruleCount: 1, socketScenarioCount: 0, totalRuleWeight: 1 });
      }
      if (errors.length) return json(route, { status: 400, title: "Invalid Grading Configuration", detail: "Grading configuration is invalid", errors }, 400);
      state.gradingConfig = raw || null;
      return json(route, authoring());
    }
    if (path.endsWith("/reference-run")) {
      state.runs.push(body);
      if (body?.inputs?.length) {
        return json(route, {
          compiled: true, compileOutput: null, mainClass: "Main", error: null, appliedCount: 0,
          cases: body.inputs.map((i: string) => ({ testCaseId: null, orderIndex: null, hidden: null, status: "OK", stdout: reference(i), stderr: "", exitCode: 0, runtimeMs: 300, applied: false })),
        });
      }
      let applied = 0;
      const cases = state.cases.map((c) => {
        const stdout = reference(c.input);
        const same = stdout === c.expectedOutput;
        if (body?.apply && !same) {
          c.expectedOutput = stdout;
          applied++;
        }
        return { testCaseId: c.id, orderIndex: c.orderIndex, hidden: c.isHidden, status: same ? "MATCH" : "DIFF", stdout, stderr: "", exitCode: 0, runtimeMs: 310, applied: !!body?.apply && !same };
      });
      return json(route, { compiled: true, compileOutput: null, mainClass: "Main", error: null, cases, appliedCount: applied });
    }
    return json(route, { status: 404, title: "Not Found", detail: path }, 404);
  });

  await page.route(new RegExp(`/api/v1/problems/${problem.id}/test-cases(/\\d+)?$`), async (route, request) => {
    const method = request.method();
    if (method === "GET") return json(route, state.cases);
    const data = request.postData() ?? "";
    if (method === "POST") {
      const c: Case = {
        id: 900 + state.cases.length, orderIndex: state.cases.length,
        input: multipartField(data, "inputFile") ?? "", expectedOutput: multipartField(data, "outputFile") ?? "",
        isHidden: multipartField(data, "isHidden") === "true",
      };
      state.cases.push(c);
      return json(route, c, 201);
    }
    const id = Number(new URL(request.url()).pathname.split("/").pop());
    const c = state.cases.find((x) => x.id === id)!;
    if (method === "DELETE") {
      state.cases = state.cases.filter((x) => x.id !== id);
      return route.fulfill({ status: 204 });
    }
    const out = multipartField(data, "outputFile");
    if (out !== undefined) c.expectedOutput = out;
    return json(route, c);
  });
  return state;
}

test.describe("Admin: console problem authoring", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    const mock = await setupMockApi(page);
    mock.addProblem(problem);
    await setMockAuthSession(page, { username: "admin", role: "ADMIN" });
  });

  test("reference solution → grading rules → generated test cases → console", async ({ page }) => {
    const state = await mockAuthoring(page);
    const starts: unknown[] = [];
    await page.routeWebSocket(/\/api\/v1\/console\/ws/, (ws) => {
      ws.onMessage((raw) => {
        const frame = JSON.parse(String(raw));
        if (frame.type === "start") {
          starts.push(frame);
          ws.send(JSON.stringify({ type: "stdout", data: "Enter n: " }));
        }
      });
    });

    await page.goto("/admin/problems", { waitUntil: "domcontentloaded" });
    await page.getByRole("button", { name: "Mini Bank Console OOP", exact: true }).click();
    const editor = page.getByRole("dialog", { name: "Problem editor" });

    // 1. Lời giải mẫu
    await editor.getByRole("tab", { name: "Reference & console" }).click();
    await editor.getByRole("button", { name: "Start with Main.java" }).click();
    await expect(editor.getByLabel("Contents of Main.java")).toContainText("Scanner");
    await editor.getByRole("button", { name: "Save reference" }).click();
    await expect(page.getByText("Reference solution saved.")).toBeVisible();
    expect(state.referenceSolution.map((f) => f.path)).toEqual(["Main.java"]);

    // Console chạy đúng file trong editor.
    await editor.getByRole("button", { name: "Run", exact: true }).click();
    await expect(editor.getByRole("log", { name: "Console output" })).toContainText("Enter n:");
    expect((starts[0] as { files: { path: string }[] }).files.map((f) => f.path)).toEqual(["Main.java"]);

    // 2. Rule chấm: lỗi hiện theo đường dẫn, sau đó lưu được
    await editor.getByRole("tab", { name: "Grading" }).click();
    const jsonBox = editor.getByLabel(/grading_config/);
    await jsonBox.fill('{"rules":[{"targetClassName":"Account","expectedType":"KLASS"}]}');
    await editor.getByRole("button", { name: "Save rules" }).click();
    await expect(editor.getByRole("alert")).toContainText("KLASS");
    await jsonBox.fill('{"rules":[{"targetClassName":"Account","expectedType":"CLASS"}]}');
    await editor.getByRole("button", { name: "Validate" }).click();
    await expect(editor.getByText(/Valid — 1 rule/)).toBeVisible();
    await editor.getByRole("button", { name: "Save rules" }).click();
    await expect(page.getByText("Grading rules saved.")).toBeVisible();
    expect(state.gradingConfig).toContain("Account");

    // 3. Testcase: output sinh từ lời giải mẫu
    await editor.getByRole("tab", { name: "Test cases" }).click();
    await expect(editor.getByText("No test cases yet")).toBeVisible();
    await editor.getByRole("button", { name: "Add case" }).click();
    await editor.getByLabel("Input (stdin)").fill("21\n");
    await editor.getByRole("button", { name: "Fill from reference" }).click();
    await expect(editor.getByLabel("Expected output (stdout)")).toHaveValue("42\n");
    await editor.getByRole("button", { name: "Save case" }).click();
    await expect(page.getByText("Test case added.")).toBeVisible();
    expect(state.cases).toEqual([expect.objectContaining({ input: "21\n", expectedOutput: "42\n", isHidden: true })]);

    // Một case sai output ⇒ Check báo Differs, Apply sửa lại.
    state.cases.push({ id: 950, input: "5\n", expectedOutput: "11\n", isHidden: false, orderIndex: 1 });
    await editor.getByRole("tab", { name: "Grading" }).click();
    await editor.getByRole("tab", { name: "Test cases" }).click();
    await editor.getByRole("button", { name: "Check against reference" }).click();
    await expect(editor.getByText(/1\s*match · 1\s*differ · 0\s*failed/)).toBeVisible();
    await expect(editor.getByText("Differs · 310 ms")).toBeVisible();
    await editor.getByRole("button", { name: "Use reference output for 1 case" }).click();
    await expect(page.getByText("Updated 1 expected output.")).toBeVisible();
    expect(state.cases.find((c) => c.id === 950)?.expectedOutput).toBe("10\n");
  });

  test("new problems show the authoring tabs disabled until created", async ({ page }) => {
    await mockAuthoring(page);
    await page.goto("/admin/problems", { waitUntil: "domcontentloaded" });
    await page.getByRole("button", { name: /New problem/ }).click();
    const editor = page.getByRole("dialog", { name: "Problem editor" });
    await expect(editor.getByRole("tab", { name: "Test cases" })).toBeDisabled();
    await expect(editor.getByText(/become available once the problem is created/)).toBeVisible();
  });
});
