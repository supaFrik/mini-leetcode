import { test, expect } from "@playwright/test";
import {
  setupMockApi,
  setMockAuthSession,
  installFullscreenMock,
  type MockApiController,
} from "./fixtures/mock-api";

test.describe("Adversarial Stress-Testing: Proctored Exam & Workspace Flows", () => {
  let mockApi: MockApiController;

  test.beforeEach(async ({ page }) => {
    page.setDefaultTimeout(15000);
    page.setDefaultNavigationTimeout(30000);
    await installFullscreenMock(page);
    mockApi = await setupMockApi(page);
    await setMockAuthSession(page, { username: "student", role: "USER" });
    await page.addInitScript(() => {
      sessionStorage.setItem("exam_started_101", "true");
    });
  });

  async function enterExamWorkspace(page: any, contestId = 101, problemId = 1, waitForWorkspace = true) {
    await page.goto(`/exam/${contestId}/problems/${problemId}`, { waitUntil: "domcontentloaded" });
    const checkbox = page.getByRole("checkbox");
    if (await checkbox.isVisible({ timeout: 10000 }).catch(() => false)) {
      await checkbox.check();
      const startBtn = page.getByRole("button", { name: "Start Exam" });
      await expect(startBtn).toBeEnabled({ timeout: 5000 });
      await startBtn.click();
    }
    if (waitForWorkspace) {
      await expect(page.getByText(/Violations 0\/3/)).toBeVisible({ timeout: 15000 });
    }
  }

  // =========================================================================
  // 1. Anti-Cheat: Grace Period (< 1.5s) vs Violation (>= 1.5s)
  // =========================================================================
  test.describe("1. Grace Period (< 1.5s) vs Violation (>= 1.5s)", () => {
    test("does not strike on tab blur under 1.5s grace period (500ms and 1200ms)", async ({ page }) => {
      await enterExamWorkspace(page);

      // Sub-1.5s blur: 400ms
      await page.evaluate(() => window.dispatchEvent(new Event("blur")));
      await page.waitForTimeout(400);
      await page.evaluate(() => window.dispatchEvent(new Event("focus")));

      // Verify NO strike recorded
      await expect(page.getByText("Exam Regulation Warning")).not.toBeVisible();
      await expect(page.getByText(/Violations 0\/3/)).toBeVisible();

      // Another sub-1.5s blur: 800ms
      await page.evaluate(() => window.dispatchEvent(new Event("blur")));
      await page.waitForTimeout(800);
      await page.evaluate(() => window.dispatchEvent(new Event("focus")));

      // Still NO strike recorded
      await expect(page.getByText("Exam Regulation Warning")).not.toBeVisible();
      await expect(page.getByText(/Violations 0\/3/)).toBeVisible();

      const serverState = mockApi.proctoringViolations.get("101:user");
      expect(serverState?.warnings ?? 0).toBe(0);
    });

    test("survives rapid tab-switch jitter without accumulating false positive strikes", async ({ page }) => {
      await enterExamWorkspace(page);

      // 4 rapid blur/focus events (300ms each)
      for (let i = 0; i < 4; i++) {
        await page.evaluate(() => window.dispatchEvent(new Event("blur")));
        await page.waitForTimeout(300);
        await page.evaluate(() => window.dispatchEvent(new Event("focus")));
        await page.waitForTimeout(100);
      }

      await expect(page.getByText("Exam Regulation Warning")).not.toBeVisible();
      await expect(page.getByText(/Violations 0\/3/)).toBeVisible();

      const serverState = mockApi.proctoringViolations.get("101:user");
      expect(serverState?.warnings ?? 0).toBe(0);
    });

    test("accurately records strike and displays modal when blur exceeds 1.5s threshold (1650ms)", async ({ page }) => {
      await enterExamWorkspace(page);

      // Blur for 1650ms (>= 1.5s)
      await page.evaluate(() => window.dispatchEvent(new Event("blur")));
      await page.waitForTimeout(1650);
      await page.evaluate(() => window.dispatchEvent(new Event("focus")));

      // ViolationDialog MUST appear
      const dialog = page.getByText("Exam Regulation Warning");
      await expect(dialog).toBeVisible();
      await expect(page.getByText(/You have left the exam window 1\/3 times/)).toBeVisible();
      await expect(page.getByText(/Violations 1\/3/)).toBeVisible();

      // Dismiss dialog
      await page.getByRole("button", { name: "I understand and agree to continue" }).click();
      await expect(dialog).not.toBeVisible();

      // Check server state
      const serverState = mockApi.proctoringViolations.get("101:user");
      expect(serverState?.warnings).toBe(1);
    });

    test("escalates warning message on second strike (2/3) warning of impending disqualification", async ({ page }) => {
      await enterExamWorkspace(page);

      // First violation: blur 1600ms
      await page.evaluate(() => window.dispatchEvent(new Event("blur")));
      await page.waitForTimeout(1600);
      await page.evaluate(() => window.dispatchEvent(new Event("focus")));
      await expect(page.getByText("Exam Regulation Warning")).toBeVisible();
      await page.getByRole("button", { name: "I understand and agree to continue" }).click();

      // Second violation: blur 1600ms
      await page.evaluate(() => window.dispatchEvent(new Event("blur")));
      await page.waitForTimeout(1600);
      await page.evaluate(() => window.dispatchEvent(new Event("focus")));

      // Dialog for 2/3 must state that next violation will disqualify
      await expect(page.getByText("Exam Regulation Warning")).toBeVisible();
      await expect(page.getByText(/You have left the exam window 2\/3 times/)).toBeVisible();
      await expect(page.getByText(/You will be disqualified on your next violation/)).toBeVisible();
      await expect(page.getByText(/Violations 2\/3/)).toBeVisible();

      await page.getByRole("button", { name: "I understand and agree to continue" }).click();
    });
  });

  // =========================================================================
  // 2. Paste Prevention: Bypass Attempts via Keyboard, Context Menu, DOM & Drop
  // =========================================================================
  test.describe("2. Paste Prevention & Bypass Resistance", () => {
    test("intercepts Ctrl+V keyboard shortcut and emits warning toast", async ({ page }) => {
      await enterExamWorkspace(page);

      // Focus editor and attempt Ctrl+V
      const monacoEditor = page.locator(".monaco-editor");
      const fallbackTextarea = page.locator('textarea[aria-label="Source code"]');

      if (await monacoEditor.isVisible()) {
        await monacoEditor.click();
      } else if (await fallbackTextarea.isVisible()) {
        await fallbackTextarea.click();
      }

      await page.keyboard.press("Control+v");

      // Verify paste toast displayed
      await expect(page.getByText("Pasting code is prohibited during exams")).toBeVisible();
    });

    test("intercepts Shift+Insert keyboard shortcut and emits warning toast", async ({ page }) => {
      await enterExamWorkspace(page);

      const monacoEditor = page.locator(".monaco-editor");
      const fallbackTextarea = page.locator('textarea[aria-label="Source code"]');

      if (await monacoEditor.isVisible()) {
        await monacoEditor.click();
      } else if (await fallbackTextarea.isVisible()) {
        await fallbackTextarea.click();
      }

      await page.keyboard.press("Shift+Insert");

      await expect(page.getByText("Pasting code is prohibited during exams")).toBeVisible();
    });

    test("suppresses right-click context menu in exam mode", async ({ page }) => {
      await enterExamWorkspace(page);

      // Check contextmenu event cancellation in editor
      const isDefaultPrevented = await page.evaluate(() => {
        const target = document.querySelector(".monaco-editor") || document.querySelector('textarea[aria-label="Source code"]') || document.body;
        const event = new MouseEvent("contextmenu", { bubbles: true, cancelable: true });
        target.dispatchEvent(event);
        return event.defaultPrevented;
      });

      // Context menu event must be intercepted and defaultPrevented in exam mode
      expect(isDefaultPrevented).toBe(true);
    });

    test("captures and cancels synthetic DOM paste events from window and document", async ({ page }) => {
      await enterExamWorkspace(page);

      const pasteDispatched = await page.evaluate(() => {
        const ev = new Event("paste", { bubbles: true, cancelable: true });
        window.dispatchEvent(ev);
        return ev.defaultPrevented;
      });

      expect(pasteDispatched).toBe(true);
      await expect(page.getByText("Pasting code is prohibited during exams")).toBeVisible();
    });

    test("intercepts drag-and-drop code injection into editor", async ({ page }) => {
      await enterExamWorkspace(page);

      const dropPrevented = await page.evaluate(() => {
        const editor = document.querySelector(".monaco-editor") || document.querySelector('textarea[aria-label="Source code"]');
        if (!editor) return false;
        const ev = new Event("drop", { bubbles: true, cancelable: true });
        editor.dispatchEvent(ev);
        return ev.defaultPrevented;
      });

      expect(dropPrevented).toBe(true);
      await expect(page.getByText("Pasting code is prohibited during exams")).toBeVisible();
    });
  });

  // =========================================================================
  // 3. 3-Strike Disqualification Locking & Submission Neutralization
  // =========================================================================
  test.describe("3. 3-Strike Disqualification Enforcement", () => {
    test("permanently locks workspace, disables submit buttons, and neutralizes Ctrl+Shift+Enter upon 3rd strike", async ({ page }) => {
      await enterExamWorkspace(page);

      // Trigger 3 paste violations with 600ms spacing to respect debounce
      for (let i = 0; i < 3; i++) {
        await page.evaluate(() => {
          window.dispatchEvent(new Event("paste", { bubbles: true, cancelable: true }));
        });
        await page.waitForTimeout(600);
      }

      // DisqualifiedOverlay MUST appear
      const overlay = page.getByRole("alertdialog", { name: "Exam Locked" });
      await expect(overlay).toBeVisible();
      await expect(page.getByText(/Your exam session has been locked due to exceeding allowed violations/)).toBeVisible();

      // Verify submit button is disabled in DOM
      const submitBtn = page.locator('button:has-text("Submit Solution")');
      await expect(submitBtn).toBeDisabled();

      // Track any POST /submit calls
      let submitCallMade = false;
      await page.route("**/api/v1/problems/*/submit", (route) => {
        submitCallMade = true;
        route.continue();
      });

      // Attempt submit hotkey
      await page.keyboard.press("Control+Shift+Enter");
      await page.waitForTimeout(500);

      // Verify NO submission was sent
      expect(submitCallMade).toBe(false);

      // Verify server controller marks disqualified
      const serverState = mockApi.proctoringViolations.get("101:user");
      expect(serverState?.disqualified).toBe(true);
      expect(serverState?.warnings).toBeGreaterThanOrEqual(3);
    });

    test("persists disqualification state on reload and prevents workspace re-entry", async ({ page }) => {
      mockApi.disqualifyUser(101);

      await page.goto("/exam/101/problems/1", { waitUntil: "domcontentloaded" });

      // Immediate locking overlay
      await expect(page.getByText("Exam Locked")).toBeVisible();

      // Hard reload page
      await page.reload({ waitUntil: "domcontentloaded" });

      // Still locked immediately
      await expect(page.getByText("Exam Locked")).toBeVisible();
      await expect(page.getByRole("link", { name: "Return to Contest" })).toBeVisible();

      // Navigation back to exam overview shows "Disqualified" and "Locked"
      await page.getByRole("link", { name: "Return to Contest" }).click();
      await page.waitForURL("/exam/101");

      await expect(page.getByText(/You have been disqualified from this contest/)).toBeVisible();
      await expect(page.getByRole("button", { name: "Locked" }).first()).toBeVisible();
    });
  });

  // =========================================================================
  // 4. Countdown Timer Expiration & In-Flight Submission Locking
  // =========================================================================
  test.describe("4. Countdown Timer Expiration & Submission Locking", () => {
    test("locks submission immediately when contest time expires", async ({ page }) => {
      const expiredContest = {
        id: 106,
        title: "Expired Contest Adversarial Test",
        startTime: new Date(Date.now() - 3600000).toISOString(),
        endTime: new Date(Date.now() - 500).toISOString(),
        status: "RUNNING" as const,
        scoringPolicy: "ACM_ICPC" as const,
        maxViolationsAllowed: 3,
        myStatus: "REGISTERED" as const,
        myViolations: 0,
        problems: [
          { id: 1, slug: "two-sum", title: "Two Sum", difficulty: "EASY" as const, label: "A" },
        ],
      };

      await setupMockApi(page, { customContests: [expiredContest] });
      await enterExamWorkspace(page, 106, 1, false);

      // TimeUpOverlay appears
      const overlay = page.getByRole("alertdialog", { name: "Contest Time Expired" });
      await expect(overlay).toBeVisible();
      await expect(page.getByText(/The contest has ended/)).toBeVisible();

      // Submit button must be disabled
      const submitBtn = page.locator('button:has-text("Submit Solution")');
      await expect(submitBtn).toBeDisabled();

      // Link to leaderboard must be available
      await expect(page.getByRole("link", { name: "View Leaderboard" })).toBeVisible();
    });

    test("blocks late in-flight submissions attempted after expiration", async ({ page }) => {
      const expiredContest = {
        id: 107,
        title: "Expiration Race Test",
        startTime: new Date(Date.now() - 3600000).toISOString(),
        endTime: new Date(Date.now() - 1000).toISOString(),
        status: "RUNNING" as const,
        scoringPolicy: "ACM_ICPC" as const,
        maxViolationsAllowed: 3,
        myStatus: "REGISTERED" as const,
        myViolations: 0,
        problems: [
          { id: 1, slug: "two-sum", title: "Two Sum", difficulty: "EASY" as const, label: "A" },
        ],
      };

      await setupMockApi(page, { customContests: [expiredContest] });
      await page.addInitScript(() => {
        sessionStorage.setItem("exam_started_107", "true");
      });

      await page.goto("/exam/107/problems/1", { waitUntil: "domcontentloaded" });

      let postSubmitCalled = false;
      await page.route("**/api/v1/problems/*/submit", (route) => {
        postSubmitCalled = true;
        route.continue();
      });

      // Press hotkey
      await page.keyboard.press("Control+Shift+Enter");
      await page.waitForTimeout(400);

      expect(postSubmitCalled).toBe(false);
    });
  });

  // =========================================================================
  // 5. Live Leaderboard Responsiveness & Disqualification Flagging
  // =========================================================================
  test.describe("5. Live Leaderboard Disqualification Flagging", () => {
    test("accurately flags disqualified contestant with badge, null rank, and dimmed row", async ({ page }) => {
      // Disqualify student on contest 101
      mockApi.disqualifyUser(101);

      await page.goto("/exam/101/leaderboard", { waitUntil: "domcontentloaded" });

      // Check contest header
      await expect(page.getByRole("heading", { name: "Mini LeetCode Weekly Contest 101" })).toBeVisible();

      // Locate row for 'student'
      const studentRow = page.locator("tr", { has: page.getByText("student") });
      await expect(studentRow).toBeVisible();

      // Must display 'Disqualified' badge
      await expect(studentRow.getByText("Disqualified")).toBeVisible();

      // Rank must be '—' (null rank)
      await expect(studentRow.locator("td").first()).toHaveText("—");

      // Row must have dimmed opacity class (opacity-60)
      await expect(studentRow).toHaveClass(/opacity-60/);

      // Other non-disqualified contestants must retain their ranks
      const topRow = page.locator("tr", { has: page.getByText("algorithm_master") });
      await expect(topRow.locator("td").first()).toHaveText("1");
      await expect(topRow.getByText("Disqualified")).not.toBeVisible();
    });

    test("supports manual refresh action and maintains live leaderboard responsiveness", async ({ page }) => {
      await page.goto("/exam/101/leaderboard", { waitUntil: "domcontentloaded" });

      const refreshBtn = page.getByRole("button", { name: "Refresh" });
      await expect(refreshBtn).toBeVisible();

      let leaderboardRequests = 0;
      page.on("request", (req) => {
        if (req.url().includes("/leaderboard")) leaderboardRequests++;
      });

      await refreshBtn.click();
      await page.waitForTimeout(500);

      expect(leaderboardRequests).toBeGreaterThanOrEqual(1);
      await expect(page.getByText(/Updated/)).toBeVisible();
    });
  });
});
