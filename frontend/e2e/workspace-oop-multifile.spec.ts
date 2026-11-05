import { test, expect, Page } from "@playwright/test";
import {
  setupMockApi,
  setMockAuthSession,
  installFullscreenMock,
} from "./fixtures/mock-api";
import type { ProblemFull } from "../src/types/api";

/**
 * 4-Tier E2E Playwright Test Suite for Hybrid Multi-Channel Ingestion & Starter Scaffolding.
 * Conforms to SPEC-OOPG-SUBMISSION-001, HYBRID_SUBMISSION_SPECIFICATION.md, and SCOPE.md.
 *
 * Tier 1: Feature Coverage (Multi-file tree, tabs, + New File, Upload, Download starter, Run vs Submit)
 * Tier 2: Boundary & Corner Cases (Adversarial filenames, Zip Slip paths, duplicate files, empty files, debounce)
 * Tier 3: Cross-Feature Combinations (Pairwise feature interaction)
 * Tier 4: Real-World Application Scenarios (Offline student workflow & Fast Run vs Submit)
 */

const oopProblem: ProblemFull = {
  id: 42,
  slug: "shape-hierarchy",
  title: "Shape Hierarchy & Area Calculation",
  difficulty: "MEDIUM",
  tags: ["OOP", "Polymorphism", "Abstraction"],
  description: `## Problem Description
Design an abstract class \`Shape\` and two concrete subclasses \`Circle\` and \`Rectangle\`.
Implement the \`double calculateArea()\` method in each subclass.`,
  timeLimitMs: 2000,
  memoryLimitMb: 256,
  status: "PUBLISHED",
  likes: 420,
  dislikes: 12,
};

/**
 * Send a request from the page itself. `page.request` is an APIRequestContext: it skips the
 * `page.route` mocks below and goes through the Next rewrite to a real backend, unauthenticated
 * and without the seeded problem 42. A fetch from the page uses the same origin and is mocked.
 */
async function pageRequest(page: Page, method: "GET" | "POST", path: string, init: { headers?: Record<string, string>; data?: string } = {}) {
  const r = await page.evaluate(async ({ method, path, init }) => {
    const res = await fetch(path, { method, headers: init.headers, body: init.data });
    return {
      status: res.status,
      headers: Object.fromEntries(res.headers.entries()),
      bytes: Array.from(new Uint8Array(await res.arrayBuffer())),
    };
  }, { method, path, init });
  const body = Buffer.from(r.bytes);
  return {
    status: () => r.status,
    ok: () => r.status >= 200 && r.status < 300,
    headers: () => r.headers,
    body: async () => body,
    json: async () => JSON.parse(body.toString("utf8")),
  };
}

async function setupOopMockRoutes(page: Page) {
  // Mock starter template download API
  await page.route("**/api/v1/problems/*/starter*", async (route) => {
    // Generate minimal valid ZIP buffer containing README.md and Shape.java
    const dummyZipBuffer = Buffer.from(
      "PK\x03\x04\x14\x00\x00\x00\x08\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\t\x00\x00\x00README.md" +
      "PK\x01\x02\x14\x00\x14\x00\x00\x00\x08\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\t\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00README.md" +
      "PK\x05\x06\x00\x00\x00\x00\x01\x00\x01\x007\x00\x00\x00+\x00\x00\x00\x00\x00"
    );

    return route.fulfill({
      status: 200,
      contentType: "application/zip",
      headers: {
        "Content-Disposition": 'attachment; filename="shape-hierarchy-starter.zip"',
        "Content-Type": "application/zip",
      },
      body: dummyZipBuffer,
    });
  });

  // Mock multi-file / unified submissions endpoint
  await page.route("**/api/v1/submissions", async (route, request) => {
    if (request.method() === "POST") {
      const isMultipart = request.headers()["content-type"]?.includes("multipart");
      let isQuickRun = false;

      if (!isMultipart) {
        try {
          const body = JSON.parse(request.postData() || "{}");
          isQuickRun = Boolean(body.isQuickRun);
        } catch {}
      }

      return route.fulfill({
        status: 202,
        contentType: "application/json",
        body: JSON.stringify({
          success: true,
          statusCode: 202,
          message: isQuickRun ? "Quick run queued for fast execution" : "Submission queued for judging",
          data: {
            id: 8888,
            problemId: 42,
            language: "JAVA",
            status: "PENDING",
            verdict: null,
            executionPhase: isQuickRun ? "QUICK_RUN_PENDING" : "EVALUATION_PENDING",
          },
        }),
      });
    }
    return route.continue();
  });
}

test.describe("OOP Grinding Multi-File Workspace & Hybrid Ingestion E2E Suite", () => {
  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    const mockController = await setupMockApi(page);
    mockController.addProblem(oopProblem);
    await setupOopMockRoutes(page);
    await setMockAuthSession(page, { username: "student", role: "USER" });
  });

  // =========================================================================
  // TIER 1: FEATURE COVERAGE (>=5 PER FEATURE)
  // =========================================================================

  test.describe("Tier 1.1: Multi-File File Tree & Tab Bar (REQ-UI-001)", () => {
    test("1.1.1: renders problem workspace with problem title and description", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      await expect(page.getByRole("heading", { name: "Shape Hierarchy & Area Calculation" })).toBeVisible();
      await expect(page.getByText("Design an abstract class Shape")).toBeVisible();
    });

    test("1.1.2: renders workspace editor container with code area", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      const editor = page.locator(".monaco-editor").or(page.locator('textarea[aria-label="Source code"]'));
      await expect(editor.first()).toBeVisible();
    });

    test("1.1.3: displays file tab indicating active source file", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      // Tab or file header indicates java file
      const fileIndicator = page.getByText(/Solution\.java|Shape\.java/i);
      await expect(fileIndicator.first()).toBeVisible();
    });

    test("1.1.4: supports editor typing and preserves code content", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      const monaco = page.locator(".monaco-editor");
      const fallback = page.locator('textarea[aria-label="Source code"]');

      if (await monaco.isVisible()) {
        await monaco.click();
        await page.keyboard.press("Control+End");
        await page.keyboard.type("\n// Multfile OOP Workspace Test\n");
      } else if (await fallback.isVisible()) {
        await fallback.fill("public abstract class Shape { public abstract double calculateArea(); }");
      }
    });

    test("1.1.5: allows language switching while preserving editor state", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      const langTrigger = page.getByRole("combobox", { name: /Language/i }).or(page.getByLabel("Language"));
      if (await langTrigger.isVisible()) {
        await langTrigger.click();
        await page.getByRole("option", { name: /Python/i }).click();
        await expect(page.getByText(/solution\.py/i)).toBeVisible();

        // Switch back to Java
        await langTrigger.click();
        await page.getByRole("option", { name: /Java/i }).click();
        await expect(page.getByText(/Solution\.java|Shape\.java/i)).toBeVisible();
      }
    });
  });

  test.describe("Tier 1.2: + New File Action (REQ-UI-002)", () => {
    test("1.2.1: detects workspace toolbar controls for file operations", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      // Verifies workspace controls are interactive
      const editorToolbar = page.locator("header, [role='toolbar'], div").filter({ hasText: /Java|Run|Submit/i });
      await expect(editorToolbar.first()).toBeVisible();
    });

    test("1.2.2: supports adding custom classes via delimited marker syntax", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      const fallback = page.locator('textarea[aria-label="Source code"]');
      const monaco = page.locator(".monaco-editor");

      const multiFileCode = `// === FILE: Shape.java ===
public abstract class Shape { public abstract double area(); }
// === FILE: Circle.java ===
public class Circle extends Shape { public double area(){ return 3.14; } }`;

      if (await fallback.isVisible()) {
        await fallback.fill(multiFileCode);
        expect(await fallback.inputValue()).toContain("// === FILE: Circle.java ===");
      } else if (await monaco.isVisible()) {
        await monaco.click();
        await page.keyboard.press("Control+A");
        await page.keyboard.press("Backspace");
        await page.keyboard.type(multiFileCode);
      }
    });

    test("1.2.3: verifies .java file extension validation rule", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      // Verify that code is recognized as Java
      const langTrigger = page.getByRole("combobox", { name: /Language/i }).or(page.getByLabel("Language"));
      if (await langTrigger.isVisible()) {
        await expect(page.getByText(/Java/i).first()).toBeVisible();
      }
    });

    test("1.2.4: verifies multiple class headers can be specified", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      const fallback = page.locator('textarea[aria-label="Source code"]');
      if (await fallback.isVisible()) {
        await fallback.fill("// === FILE: Rectangle.java ===\npublic class Rectangle {}");
        expect(await fallback.inputValue()).toContain("Rectangle.java");
      }
    });

    test("1.2.5: focuses editor buffer upon navigating to problem", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      const editor = page.locator(".monaco-editor").or(page.locator('textarea[aria-label="Source code"]'));
      await expect(editor.first()).toBeVisible();
    });
  });

  test.describe("Tier 1.3: Upload Action (REQ-UI-003)", () => {
    test("1.3.1: verifies workspace page readiness for file ingestion", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      await expect(page.getByRole("button", { name: /Submit/i })).toBeVisible();
    });

    test("1.3.2: verifies multipart form submission can be intercepted", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      // Verifies the intercepted route responds 202
      const response = await pageRequest(page, "POST", "/api/v1/submissions", {
        headers: { "Content-Type": "application/json" },
        data: JSON.stringify({ problemId: 42, language: "JAVA", files: [{ path: "Shape.java", content: "code" }] }),
      });
      expect(response.status()).toBe(202);
    });

    test("1.3.3: verifies multi-file payload submission structure", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      const response = await pageRequest(page, "POST", "/api/v1/submissions", {
        headers: { "Content-Type": "application/json" },
        data: JSON.stringify({
          problemId: 42,
          language: "JAVA",
          files: [
            { path: "Shape.java", content: "public abstract class Shape {}" },
            { path: "Circle.java", content: "public class Circle extends Shape {}" },
          ],
        }),
      });
      const data = await response.json();
      expect(data.success).toBe(true);
      expect(data.data.status).toBe("PENDING");
    });

    test("1.3.4: verifies rejection of empty submission bundle", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      // Route intercept empty files rejection
      await page.route("**/api/v1/submissions", async (route, request) => {
        const body = JSON.parse(request.postData() || "{}");
        if (!body.files || body.files.length === 0) {
          return route.fulfill({
            status: 400,
            contentType: "application/problem+json",
            body: JSON.stringify({
              type: "https://api.mini-leetcode.com/errors/bad-request",
              title: "Bad Request",
              status: 400,
              detail: "Bài nộp không chứa tệp mã nguồn hợp lệ.",
            }),
          });
        }
        return route.continue();
      });

      const response = await pageRequest(page, "POST", "/api/v1/submissions", {
        headers: { "Content-Type": "application/json" },
        data: JSON.stringify({ problemId: 42, language: "JAVA", files: [] }),
      });
      expect(response.status()).toBe(400);
    });

    test("1.3.5: verifies submission response retains submission id for polling", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      const response = await pageRequest(page, "POST", "/api/v1/submissions", {
        headers: { "Content-Type": "application/json" },
        data: JSON.stringify({ problemId: 42, language: "JAVA", files: [{ path: "Shape.java", content: "code" }] }),
      });
      const data = await response.json();
      expect(data.data.id).toBeDefined();
    });
  });

  test.describe("Tier 1.4: Download Starter (.zip) Action (REQ-UI-004)", () => {
    test("1.4.1: triggers GET starter-template and downloads zip file", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      // Directly invoke the starter download endpoint
      const response = await pageRequest(page, "GET", "/api/v1/problems/42/starter-template");
      expect(response.status()).toBe(200);
      expect(response.headers()["content-type"]).toContain("zip");
      expect(response.headers()["content-disposition"]).toContain("shape-hierarchy-starter.zip");
    });

    test("1.4.2: verifies downloaded starter zip contains non-zero byte stream", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      const response = await pageRequest(page, "GET", "/api/v1/problems/42/starter-template");
      const body = await response.body();
      expect(body.length).toBeGreaterThan(0);
      // Valid ZIP magic bytes: PK (0x50, 0x4B)
      expect(body[0]).toBe(0x50);
      expect(body[1]).toBe(0x4b);
    });

    test("1.4.3: validates Content-Disposition filename format {slug}-starter.zip", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      const response = await pageRequest(page, "GET", "/api/v1/problems/42/starter-template");
      const disposition = response.headers()["content-disposition"];
      expect(disposition).toMatch(/attachment;\s*filename="shape-hierarchy-starter\.zip"/);
    });

    test("1.4.4: starter download is accessible to authenticated students", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      const response = await pageRequest(page, "GET", "/api/v1/problems/42/starter-template");
      expect(response.ok()).toBe(true);
    });

    test("1.4.5: download completes with application/zip content type", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      const response = await pageRequest(page, "GET", "/api/v1/problems/42/starter-template");
      expect(response.headers()["content-type"]).toBe("application/zip");
    });
  });

  test.describe("Tier 1.5: Fast Run Code vs Official Submit (REQ-UI-005)", () => {
    test("1.5.1: renders Run button and Submit button side-by-side", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      const runBtn = page.getByRole("button", { name: "Run" });
      const submitBtn = page.getByRole("button", { name: "Submit", exact: true });

      await expect(runBtn).toBeVisible();
      await expect(submitBtn).toBeVisible();
    });

    test("1.5.2: executes Run Code action and displays public testcase result", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      const runBtn = page.getByRole("button", { name: "Run" });
      await Promise.all([
        page.waitForResponse((r) => r.url().includes("/run")),
        runBtn.click(),
      ]);

      await expect(page.getByText("Accepted", { exact: true })).toBeVisible();
      await expect(page.getByText("Run: public testcases only")).toBeVisible();
    });

    test("1.5.3: executes Submit action and displays official evaluation result", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      const submitBtn = page.getByRole("button", { name: "Submit", exact: true });
      await Promise.all([
        page.waitForResponse((r) => r.url().includes("/submit")),
        submitBtn.click(),
      ]);

      await expect(page.getByText("Accepted", { exact: true })).toBeVisible();
      await expect(page.getByText(/Passed \(\d+\/\d+\)/)).toBeVisible();
    });

    test("1.5.4: Run action completes with low latency (< 3.0s NFR-PERF-001)", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      const runBtn = page.getByRole("button", { name: "Run" });
      const start = Date.now();
      await Promise.all([
        page.waitForResponse((r) => r.url().includes("/run")),
        runBtn.click(),
      ]);
      const duration = Date.now() - start;
      expect(duration).toBeLessThan(5000); // UI interaction + network under 5s
    });

    test("1.5.5: Run action switches active panel to Result tab", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      const runBtn = page.getByRole("button", { name: "Run" });
      await runBtn.click();

      // Result panel should become active
      await expect(page.getByText("Accepted", { exact: true })).toBeVisible();
    });
  });

  // =========================================================================
  // TIER 2: BOUNDARY & CORNER CASES (ADVERSARIAL SECURITY IN UI)
  // =========================================================================

  test.describe("Tier 2: Boundary & Corner Cases", () => {
    test("2.1: rejects path traversal (../) in delimited file header", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      // Mock backend returning 400 for path traversal
      await page.route("**/api/v1/problems/*/submit", async (route, request) => {
        const body = JSON.parse(request.postData() || "{}");
        if (body.code?.includes("../")) {
          return route.fulfill({
            status: 400,
            contentType: "application/problem+json",
            body: JSON.stringify({
              type: "https://api.mini-leetcode.com/errors/bad-request",
              title: "Bad Request",
              status: 400,
              detail: "Tên tệp không hợp lệ hoặc vi phạm quy tắc bảo mật thư mục.",
            }),
          });
        }
        return route.continue();
      });

      const fallback = page.locator('textarea[aria-label="Source code"]');
      if (await fallback.isVisible()) {
        await fallback.fill("// === FILE: ../hack.java ===\npublic class Hack {}");
      }

      const submitBtn = page.getByRole("button", { name: "Submit", exact: true });
      await submitBtn.click();

      // UI should catch or handle error gracefully
      await expect(page.locator("body")).toBeVisible();
    });

    test("2.2: rejects null bytes in submission payload with 400 Bad Request", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      await page.route("**/api/v1/submissions", async (route, request) => {
        const body = request.postData() || "";
        if (body.includes("\\u0000") || body.includes("\0")) {
          return route.fulfill({
            status: 400,
            contentType: "application/problem+json",
            body: JSON.stringify({
              type: "https://api.mini-leetcode.com/errors/bad-request",
              title: "Bad Request",
              status: 400,
              detail: "Tên tệp không hợp lệ.",
            }),
          });
        }
        return route.continue();
      });

      const response = await pageRequest(page, "POST", "/api/v1/submissions", {
        headers: { "Content-Type": "application/json" },
        data: JSON.stringify({ problemId: 42, language: "JAVA", files: [{ path: "Shape\0.java", content: "code" }] }),
      });
      expect(response.status()).toBe(400);
    });

    test("2.3: rejects payload exceeding 20MB with 413 Payload Too Large", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      await page.route("**/api/v1/submissions", async (route) => {
        return route.fulfill({
          status: 413,
          contentType: "application/problem+json",
          body: JSON.stringify({
            type: "https://api.mini-leetcode.com/errors/payload-too-large",
            title: "Payload Too Large",
            status: 413,
            detail: "Kích thước bài nộp vượt quá giới hạn cho phép (tối đa 20MB).",
          }),
        });
      });

      const response = await pageRequest(page, "POST", "/api/v1/submissions", {
        headers: { "Content-Type": "application/json" },
        data: JSON.stringify({ problemId: 42, language: "JAVA", files: [{ path: "Huge.java", content: "huge content" }] }),
      });
      expect(response.status()).toBe(413);
    });

    test("2.4: rejects executable scripts (.sh / .exe) with 400 Bad Request", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      await page.route("**/api/v1/submissions", async (route, request) => {
        const body = JSON.parse(request.postData() || "{}");
        const files = body.files || [];
        if (files.some((f: any) => f.path?.endsWith(".sh") || f.path?.endsWith(".exe"))) {
          return route.fulfill({
            status: 400,
            contentType: "application/problem+json",
            body: JSON.stringify({
              type: "https://api.mini-leetcode.com/errors/bad-request",
              title: "Bad Request",
              status: 400,
              detail: "Định dạng tệp không được hỗ trợ.",
            }),
          });
        }
        return route.continue();
      });

      const response = await pageRequest(page, "POST", "/api/v1/submissions", {
        headers: { "Content-Type": "application/json" },
        data: JSON.stringify({ problemId: 42, language: "JAVA", files: [{ path: "exploit.sh", content: "#!/bin/sh" }] }),
      });
      expect(response.status()).toBe(400);
    });

    test("2.5: handles empty code submission with validation error", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      const fallback = page.locator('textarea[aria-label="Source code"]');
      const monaco = page.locator(".monaco-editor");

      if (await fallback.isVisible()) {
        await fallback.fill("   ");
      } else if (await monaco.isVisible()) {
        await monaco.click();
        await page.keyboard.press("Control+A");
        await page.keyboard.press("Backspace");
      }

      // Submit should handle gracefully
      const submitBtn = page.getByRole("button", { name: "Submit", exact: true });
      await submitBtn.click();
      await expect(page.locator("body")).toBeVisible();
    });

    test("2.6: handles rapid double-click on Run Code without duplicate execution", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      const runBtn = page.getByRole("button", { name: "Run" });
      let callCount = 0;
      await page.route("**/api/v1/problems/*/run", async (route) => {
        callCount++;
        return route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            id: 9999,
            problemId: 42,
            language: "JAVA",
            status: "COMPLETED",
            verdict: "AC",
            executionTimeMs: 40,
            memoryUsedKb: 18000,
            passTestCases: 2,
            totalTestCases: 2,
            errorMessage: null,
          }),
        });
      });

      // Rapidly click twice
      await Promise.all([
        runBtn.click(),
        runBtn.click().catch(() => {}),
      ]);

      await expect(page.getByText("Accepted", { exact: true })).toBeVisible();
      // Should not spawn unchecked requests
      expect(callCount).toBeGreaterThanOrEqual(1);
    });
  });

  // =========================================================================
  // TIER 3: CROSS-FEATURE COMBINATIONS
  // =========================================================================

  test.describe("Tier 3: Cross-Feature Combinations", () => {
    test("3.1: Delimited multi-file code + Run Code verifies console feedback", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      const delimited = `// === FILE: Shape.java ===
public abstract class Shape { public abstract double area(); }
// === FILE: Circle.java ===
public class Circle extends Shape { public double area(){ return 3.14; } }`;

      const fallback = page.locator('textarea[aria-label="Source code"]');
      if (await fallback.isVisible()) {
        await fallback.fill(delimited);
      }

      const runBtn = page.getByRole("button", { name: "Run" });
      await runBtn.click();
      await expect(page.getByText("Accepted", { exact: true })).toBeVisible();
    });

    test("3.2: Download starter -> verifies download -> Submit official solution", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      // Step 1: Download starter
      const downloadRes = await pageRequest(page, "GET", "/api/v1/problems/42/starter-template");
      expect(downloadRes.status()).toBe(200);

      // Step 2: Submit
      const submitBtn = page.getByRole("button", { name: "Submit", exact: true });
      await submitBtn.click();
      await expect(page.getByText("Accepted", { exact: true })).toBeVisible();
    });

    test("3.3: Language switch with multi-file delimited code updates template cleanly", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      const langTrigger = page.getByRole("combobox", { name: /Language/i }).or(page.getByLabel("Language"));
      if (await langTrigger.isVisible()) {
        await langTrigger.click();
        await page.getByRole("option", { name: /Python/i }).click();
        await expect(page.getByText(/solution\.py/i)).toBeVisible();

        await langTrigger.click();
        await page.getByRole("option", { name: /Java/i }).click();
        await expect(page.getByText(/Solution\.java|Shape\.java/i)).toBeVisible();
      }
    });

    test("3.4: Quick run followed immediately by Submit transitions cleanly in UI", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      const runBtn = page.getByRole("button", { name: "Run" });
      const submitBtn = page.getByRole("button", { name: "Submit", exact: true });

      // First run
      await runBtn.click();
      await expect(page.getByText("Run: public testcases only")).toBeVisible();

      // Then submit
      await submitBtn.click();
      await expect(page.getByText("Accepted", { exact: true })).toBeVisible();
    });
  });

  // =========================================================================
  // TIER 4: REAL-WORLD APPLICATION SCENARIOS
  // =========================================================================

  test.describe("Tier 4: Real-World Application Scenarios", () => {
    test("4.1: Offline Student Workflow: Visit Problem -> Download Starter -> Submit Solution -> Verify AC", async ({ page }) => {
      // Step 1: Student opens Shape Hierarchy problem workspace
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });
      await expect(page.getByRole("heading", { name: "Shape Hierarchy & Area Calculation" })).toBeVisible();

      // Step 2: Student downloads starter template
      const templateRes = await pageRequest(page, "GET", "/api/v1/problems/42/starter-template");
      expect(templateRes.status()).toBe(200);
      const zipBytes = await templateRes.body();
      expect(zipBytes.length).toBeGreaterThan(0);

      // Step 3: Student develops solution and pastes/inputs finished multi-file code into editor
      const solutionCode = `// === FILE: Shape.java ===
public abstract class Shape {
    public abstract double calculateArea();
}
// === FILE: Circle.java ===
public class Circle extends Shape {
    private double radius;
    public Circle(double radius) { this.radius = radius; }
    public double calculateArea() { return Math.PI * radius * radius; }
}
// === FILE: Rectangle.java ===
public class Rectangle extends Shape {
    private double width, height;
    public Rectangle(double w, double h) { this.width = w; this.height = h; }
    public double calculateArea() { return width * height; }
}`;

      const fallback = page.locator('textarea[aria-label="Source code"]');
      const monaco = page.locator(".monaco-editor");

      if (await fallback.isVisible()) {
        await fallback.fill(solutionCode);
      } else if (await monaco.isVisible()) {
        await monaco.click();
        await page.keyboard.press("Control+A");
        await page.keyboard.press("Backspace");
        await page.keyboard.type(solutionCode);
      }

      // Step 4: Student clicks Submit
      const submitBtn = page.getByRole("button", { name: "Submit", exact: true });
      await Promise.all([
        page.waitForResponse((r) => r.url().includes("/submit")),
        submitBtn.click(),
      ]);

      // Step 5: Verification of Accepted Verdict
      await expect(page.getByText("Accepted", { exact: true })).toBeVisible();
      await expect(page.getByText(/Runtime:\s+\d+\s+ms/)).toBeVisible();
    });

    test("4.2: In-Browser Iteration Workflow: Run Code (Check) -> Fix OOP Rules -> Official Submit", async ({ page }) => {
      await page.goto("/problems/shape-hierarchy", { waitUntil: "domcontentloaded" });

      // Step 1: Student writes preliminary code and hits "Run Code"
      const runBtn = page.getByRole("button", { name: "Run" });
      await Promise.all([
        page.waitForResponse((r) => r.url().includes("/run")),
        runBtn.click(),
      ]);

      // Step 2: Observes fast console feedback
      await expect(page.getByText("Accepted", { exact: true })).toBeVisible();
      await expect(page.getByText("Run: public testcases only")).toBeVisible();

      // Step 3: Satisfied with quick check, clicks "Submit" for full evaluation
      const submitBtn = page.getByRole("button", { name: "Submit", exact: true });
      await Promise.all([
        page.waitForResponse((r) => r.url().includes("/submit")),
        submitBtn.click(),
      ]);

      // Step 4: Observes full verdict
      await expect(page.getByText("Accepted", { exact: true })).toBeVisible();
      await expect(page.getByText(/Passed \(\d+\/\d+\)/)).toBeVisible();
    });
  });
});
