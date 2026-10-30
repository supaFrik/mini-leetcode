import { test, expect } from "@playwright/test";
import {
  setupMockApi,
  setMockAuthSession,
  installFullscreenMock,
  triggerFullscreenExit,
  type MockApiController,
} from "./fixtures/mock-api";

test.describe("Proctored Contest & Exam Journey E2E Suite", () => {
  let mockApi: MockApiController;

  test.beforeEach(async ({ page }) => {
    await installFullscreenMock(page);
    mockApi = await setupMockApi(page);
    await setMockAuthSession(page, { username: "student", role: "USER" });
  });

  test("navigates contest listing, views overview, and accepts ExamGate rules", async ({ page }) => {
    // 1. Contest Listing
    await page.goto("/exam", { waitUntil: "domcontentloaded" });
    await expect(page.getByRole("heading", { name: "Live" })).toBeVisible();
    await expect(page.getByText("Mini LeetCode Weekly Contest 101")).toBeVisible();

    // 2. Contest Overview
    await page.getByText("Mini LeetCode Weekly Contest 101").click();
    await page.waitForURL("/exam/101");
    await expect(page.getByRole("heading", { name: "Mini LeetCode Weekly Contest 101" })).toBeVisible();
    await expect(page.getByText(/ACM\/ICPC/)).toBeVisible();

    // 3. Problem A in contest
    const problemRow = page.getByText("Two Sum");
    await expect(problemRow).toBeVisible();
    await page.getByRole("link", { name: "Enter Problem" }).first().click();

    await page.waitForURL("/exam/101/problems/1");

    // 4. ExamGate & Rules Verification
    await expect(page.getByRole("heading", { name: "Mini LeetCode Weekly Contest 101" })).toBeVisible();
    await expect(page.getByText("Exam regulations", { exact: true })).toBeVisible();
    await expect(page.getByText(/Do not switch tabs or windows/)).toBeVisible();
    await expect(page.getByText(/Do not exit fullscreen mode/)).toBeVisible();
    await expect(page.getByText(/Do not paste code from external sources/)).toBeVisible();

    const startBtn = page.getByRole("button", { name: "Start Exam" });
    await expect(startBtn).toBeDisabled();

    // Accept rules agreement
    await page.getByRole("checkbox").check();
    await expect(startBtn).toBeEnabled();

    // Start exam
    await startBtn.click();

    // ExamGate clears, problem workspace is displayed
    await expect(page.getByText("Exam regulations", { exact: true })).not.toBeVisible();
    await expect(page.getByText(/Violations 0\/3/)).toBeVisible();
  });

  test("enforces fullscreen mode with overlay and re-entry mechanism", async ({ page }) => {
    // Enter exam session (pre-agree via sessionStorage or via UI)
    await page.addInitScript(() => {
      sessionStorage.setItem("exam_started_101", "true");
    });
    await page.goto("/exam/101/problems/1", { waitUntil: "domcontentloaded" });

    // Ensure workspace is active
    await expect(page.getByText(/Violations 0\/3/)).toBeVisible();

    // Simulate user exiting fullscreen mode
    await triggerFullscreenExit(page);

    // FullscreenOverlay displays
    await expect(page.getByText("Fullscreen Mode Exited")).toBeVisible();
    await expect(page.getByText("Please return to fullscreen mode to continue the exam.")).toBeVisible();

    // Click "Re-enter Fullscreen"
    await page.getByRole("button", { name: "Re-enter Fullscreen" }).click();

    // Overlay clears
    await expect(page.getByText("Fullscreen Mode Exited")).not.toBeVisible();
  });

  test("triggers proctoring events: paste blocking warning toast and tab blur dialog", async ({ page }) => {
    await page.addInitScript(() => {
      sessionStorage.setItem("exam_started_101", "true");
    });
    await page.goto("/exam/101/problems/1", { waitUntil: "domcontentloaded" });
    await expect(page.getByText(/Violations 0\/3/)).toBeVisible();

    // 1. Paste Prevention & Warning Toast
    await page.evaluate(() => {
      window.dispatchEvent(new Event("paste", { bubbles: true, cancelable: true }));
    });

    // Verify paste warning toast
    await expect(page.getByText("Pasting code is prohibited during exams.")).toBeVisible();

    // 2. Tab Blur / Visibility Change with Grace Period (> 1.5s)
    await page.evaluate(() => {
      window.dispatchEvent(new Event("blur"));
    });
    // Wait > 1.5s grace period
    await page.waitForTimeout(1600);
    await page.evaluate(() => {
      window.dispatchEvent(new Event("focus"));
    });

    // Violation warning dialog appears
    await expect(page.getByText("Exam Regulation Warning")).toBeVisible();
    await expect(page.getByText(/You have left the exam window/)).toBeVisible();

    // Dismiss dialog
    await page.getByRole("button", { name: "I understand and agree to continue" }).click();
    await expect(page.getByText("Exam Regulation Warning")).not.toBeVisible();
  });

  test("locks editor and submission upon 3-strike disqualification", async ({ page }) => {
    await page.addInitScript(() => {
      sessionStorage.setItem("exam_started_101", "true");
    });

    // Set 3 violations directly on controller to test disqualification enforcement
    mockApi.disqualifyUser(101);

    await page.goto("/exam/101/problems/1", { waitUntil: "domcontentloaded" });

    // DisqualifiedOverlay displays
    await expect(page.getByText("Exam Locked")).toBeVisible();
    await expect(page.getByText(/Your exam session has been locked due to exceeding allowed violations/)).toBeVisible();
    await expect(page.getByRole("link", { name: "Return to Contest" })).toBeVisible();
  });

  test("displays countdown timer and enforces submission locking on expiration", async ({ page }) => {
    // Set up contest where endTime has expired
    const expiredContest = {
      id: 105,
      title: "Expired Contest Test",
      startTime: new Date(Date.now() - 7200000).toISOString(),
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
      sessionStorage.setItem("exam_started_105", "true");
    });

    await page.goto("/exam/105/problems/1", { waitUntil: "domcontentloaded" });

    // TimeUpOverlay renders locking submissions
    await expect(page.getByText("Contest Time Expired")).toBeVisible();
    await expect(page.getByText(/The contest has ended. Your latest draft has been saved./)).toBeVisible();
    await expect(page.getByRole("link", { name: "View Leaderboard" })).toBeVisible();
  });

  test("displays live ICPC ranking leaderboard with candidate rows, user indicator, and refresh action", async ({ page }) => {
    await page.goto("/exam/101/leaderboard", { waitUntil: "domcontentloaded" });

    // Header and contest title
    await expect(page.getByRole("heading", { name: "Mini LeetCode Weekly Contest 101" })).toBeVisible();
    await expect(page.getByText("Live")).toBeVisible();

    // Ranking Table Headers
    await expect(page.getByRole("columnheader", { name: "Rank" })).toBeVisible();
    await expect(page.getByRole("columnheader", { name: "Participant" })).toBeVisible();
    await expect(page.getByRole("columnheader", { name: "AC" })).toBeVisible();
    await expect(page.getByRole("columnheader", { name: "Penalty (min)" })).toBeVisible();

    // Candidate Rows
    await expect(page.getByText("algorithm_master")).toBeVisible();
    await expect(page.getByText("speed_coder")).toBeVisible();

    // Authenticated user row with (you) indicator
    await expect(page.getByRole("cell", { name: /student/ })).toBeVisible();
    await expect(page.getByText("(you)")).toBeVisible();

    // Refresh action
    const refreshBtn = page.getByRole("button", { name: "Refresh" });
    await expect(refreshBtn).toBeVisible();
    await refreshBtn.click();
    await expect(page.getByText(/Updated/)).toBeVisible();
  });
});
