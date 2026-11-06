import { test, expect, type Page, type WebSocketRoute } from "@playwright/test";
import { setupMockApi, setMockAuthSession, installFullscreenMock } from "./fixtures/mock-api";
import type { ProblemFull } from "../src/types/api";

/** E2E cho console live: terminal như tab Run của IDE, stdin gửi từng dòng qua WebSocket. */

const oopProblem: ProblemFull = {
  id: 42, slug: "shape-hierarchy", title: "Shape Hierarchy", difficulty: "MEDIUM", tags: ["OOP"],
  description: "Design classes.", timeLimitMs: 2000, memoryLimitMb: 256, status: "PUBLISHED", type: "OOP_GRINDING",
};
const algorithmicProblem: ProblemFull = {
  id: 43, slug: "double-it", title: "Double It", difficulty: "EASY", tags: ["Math"],
  description: "Read n, print 2n.", timeLimitMs: 1000, memoryLimitMb: 256, status: "PUBLISHED", type: "ALGORITHMIC",
};

type Frame = { type: string; [k: string]: unknown };

/** Giả lập server console; `onFrame` quyết định trả lời gì cho mỗi frame client gửi lên. */
async function mockConsole(page: Page, onFrame: (frame: Frame, ws: WebSocketRoute) => void) {
  const received: Frame[] = [];
  await page.routeWebSocket(/\/api\/v1\/console\/ws/, (ws) => {
    ws.onMessage((raw) => {
      const frame = JSON.parse(String(raw)) as Frame;
      received.push(frame);
      onFrame(frame, ws);
    });
  });
  return received;
}

const send = (ws: WebSocketRoute, frame: Frame) => ws.send(JSON.stringify(frame));

/** Mở bài và bấm nút Run của workspace ⇒ chương trình chạy trong tab Console. */
async function openAndRun(page: Page, slug: string) {
  await page.goto(`/problems/${slug}`, { waitUntil: "domcontentloaded" });
  await page.getByRole("button", { name: "Run", exact: true }).click();
}

test.describe("Live console", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    const mock = await setupMockApi(page);
    mock.addProblem(oopProblem);
    mock.addProblem(algorithmicProblem);
    await setMockAuthSession(page, { username: "student", role: "USER" });
  });

  test("Console tab is available for both OOP and algorithmic problems", async ({ page }) => {
    await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });
    await expect(page.getByRole("tab", { name: "Console" })).toBeVisible();
    await page.goto("/problems/double-it", { waitUntil: "domcontentloaded" });
    await expect(page.getByRole("tab", { name: "Console" })).toBeVisible();
  });

  test("Scanner program: prompt appears, typed line goes to stdin, output follows", async ({ page }) => {
    const received = await mockConsole(page, (frame, ws) => {
      if (frame.type === "start") send(ws, { type: "stdout", data: "Enter n: " });
      if (frame.type === "stdin") {
        send(ws, { type: "stdout", data: `${Number(String(frame.data).trim()) * 2}\n` });
        send(ws, { type: "exit", code: 0, elapsedMs: 1500, reason: null });
        ws.close();
      }
    });
    await openAndRun(page, "double-it");
    await expect(page.getByRole("tab", { name: "Console" })).toHaveAttribute("data-state", "active");

    const log = page.getByRole("log", { name: "Console output" });
    await expect(log).toContainText("Enter n:");
    const input = page.getByRole("textbox", { name: "Program input" });
    await expect(input).toBeEnabled();
    await input.fill("5");
    await input.press("Enter");

    await expect(log).toContainText("Enter n: 5");
    await expect(log).toContainText("10");
    await expect(log).toContainText("Process finished with exit code 0 (1.5s)");
    await expect(input).toBeHidden();

    const start = received.find((f) => f.type === "start")!;
    expect(start).toMatchObject({ problemId: 43, language: "JAVA" });
    expect((start.files as { path: string }[])[0].path).toBe("Solution.java");
    expect(received).toContainEqual({ type: "stdin", data: "5\n" });
  });

  test("Ctrl+D closes stdin and Ctrl+C stops the process", async ({ page }) => {
    const received = await mockConsole(page, (frame, ws) => {
      if (frame.type === "kill") {
        send(ws, { type: "exit", code: 137, elapsedMs: 800, reason: "killed" });
        ws.close();
      }
    });
    await openAndRun(page, "shape-hierarchy");
    const input = page.getByRole("textbox", { name: "Program input" });
    await input.fill("last line");
    await input.press("Control+d");
    await expect.poll(() => received.map((f) => f.type)).toContain("eof");
    expect(received).toContainEqual({ type: "stdin", data: "last line" });

    await input.press("Control+c");
    await expect.poll(() => received.map((f) => f.type)).toContain("kill");
    await expect(page.getByRole("log", { name: "Console output" })).toContainText("Process stopped");
    await expect(input).toBeHidden();
  });

  test("compile errors and server errors are shown in the terminal", async ({ page }) => {
    let runs = 0;
    await mockConsole(page, (frame, ws) => {
      if (frame.type !== "start") return;
      runs += 1;
      if (runs === 1) {
        send(ws, { type: "stderr", data: "Solution.java:3: error: ';' expected\n" });
        send(ws, { type: "exit", code: null, elapsedMs: 300, reason: "compile-error" });
      } else {
        send(ws, { type: "error", message: "Console is disabled during the exam" });
      }
      ws.close();
    });
    await openAndRun(page, "double-it");
    const log = page.getByRole("log", { name: "Console output" });

    await expect(log).toContainText("Solution.java:3: error: ';' expected");
    await expect(log).toContainText("Compilation failed");

    await page.getByRole("button", { name: "Run", exact: true }).click();
    await expect(log).toContainText("Console is disabled during the exam");
    await expect(log).not.toContainText("Compilation failed"); // mỗi lần Run là terminal mới
  });
});
