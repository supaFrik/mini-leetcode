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
    await expect(page.getByRole("heading", { name: "Kỳ thi" })).toBeVisible();
    await expect(page.getByText("Mini LeetCode Weekly Contest 101")).toBeVisible();

    // 2. Contest Overview
    await page.getByText("Mini LeetCode Weekly Contest 101").click();
    await page.waitForURL("/exam/101");
    await expect(page.getByRole("heading", { name: "Mini LeetCode Weekly Contest 101" })).toBeVisible();
    await expect(page.getByText(/ACM\/ICPC/)).toBeVisible();

    // 3. Problem A in contest
    const problemRow = page.getByText("Two Sum");
    await expect(problemRow).toBeVisible();
    await page.getByRole("link", { name: "Làm bài" }).first().click();

    await page.waitForURL("/exam/101/problems/1");

    // 4. ExamGate & Rules Verification
    await expect(page.getByRole("heading", { name: "Mini LeetCode Weekly Contest 101" })).toBeVisible();
    await expect(page.getByText("Quy chế phòng thi", { exact: true })).toBeVisible();
    await expect(page.getByText(/Không rời tab hoặc đổi cửa sổ/)).toBeVisible();
    await expect(page.getByText(/Không thoát chế độ toàn màn hình/)).toBeVisible();
    await expect(page.getByText(/Không dán \(paste\) code/)).toBeVisible();

    const startBtn = page.getByRole("button", { name: "Bắt đầu làm bài" });
    await expect(startBtn).toBeDisabled();

    // Accept rules agreement
    await page.getByRole("checkbox").check();
    await expect(startBtn).toBeEnabled();

    // Start exam
    await startBtn.click();

    // ExamGate clears, problem workspace is displayed
    await expect(page.getByText("Quy chế phòng thi", { exact: true })).not.toBeVisible();
    await expect(page.getByText(/Vi phạm 0\/3/)).toBeVisible();
  });

  test("enforces fullscreen mode with overlay and re-entry mechanism", async ({ page }) => {
    // Enter exam session (pre-agree via sessionStorage or via UI)
    await page.addInitScript(() => {
      sessionStorage.setItem("exam_started_101", "true");
    });
    await page.goto("/exam/101/problems/1", { waitUntil: "domcontentloaded" });

    // Ensure workspace is active
    await expect(page.getByText(/Vi phạm 0\/3/)).toBeVisible();

    // Simulate user exiting fullscreen mode
    await triggerFullscreenExit(page);

    // FullscreenOverlay displays
    await expect(page.getByText("Bạn đã thoát chế độ toàn màn hình")).toBeVisible();
    await expect(page.getByText("Vui lòng quay lại toàn màn hình để tiếp tục làm bài.")).toBeVisible();

    // Click "Vào lại toàn màn hình"
    await page.getByRole("button", { name: "Vào lại toàn màn hình" }).click();

    // Overlay clears
    await expect(page.getByText("Bạn đã thoát chế độ toàn màn hình")).not.toBeVisible();
  });

  test("triggers proctoring events: paste blocking warning toast and tab blur dialog", async ({ page }) => {
    await page.addInitScript(() => {
      sessionStorage.setItem("exam_started_101", "true");
    });
    await page.goto("/exam/101/problems/1", { waitUntil: "domcontentloaded" });
    await expect(page.getByText(/Vi phạm 0\/3/)).toBeVisible();

    // 1. Paste Prevention & Warning Toast
    await page.evaluate(() => {
      window.dispatchEvent(new Event("paste", { bubbles: true, cancelable: true }));
    });

    // Verify paste warning toast
    await expect(page.getByText("Không được phép dán code trong phòng thi")).toBeVisible();

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
    await expect(page.getByText("Cảnh báo vi phạm quy chế thi")).toBeVisible();
    await expect(page.getByText(/Bạn đã rời khỏi giao diện thi/)).toBeVisible();

    // Dismiss dialog
    await page.getByRole("button", { name: "Tôi hiểu và tiếp tục" }).click();
    await expect(page.getByText("Cảnh báo vi phạm quy chế thi")).not.toBeVisible();
  });

  test("locks editor and submission upon 3-strike disqualification", async ({ page }) => {
    await page.addInitScript(() => {
      sessionStorage.setItem("exam_started_101", "true");
    });

    // Set 3 violations directly on controller to test disqualification enforcement
    mockApi.disqualifyUser(101);

    await page.goto("/exam/101/problems/1", { waitUntil: "domcontentloaded" });

    // DisqualifiedOverlay displays
    await expect(page.getByText("Bài thi đã bị khoá")).toBeVisible();
    await expect(page.getByText(/Bài thi của bạn đã bị khóa do vi phạm quy chế thi/)).toBeVisible();
    await expect(page.getByRole("link", { name: "Về trang kỳ thi" })).toBeVisible();
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
    await expect(page.getByText("Đã hết giờ làm bài")).toBeVisible();
    await expect(page.getByText(/Hệ thống đã khoá việc nộp bài/)).toBeVisible();
    await expect(page.getByRole("link", { name: "Xem bảng xếp hạng" })).toBeVisible();
  });

  test("displays live ICPC ranking leaderboard with candidate rows, user indicator, and refresh action", async ({ page }) => {
    await page.goto("/exam/101/leaderboard", { waitUntil: "domcontentloaded" });

    // Header and contest title
    await expect(page.getByRole("heading", { name: "Mini LeetCode Weekly Contest 101" })).toBeVisible();
    await expect(page.getByText("Đang diễn ra")).toBeVisible();

    // Ranking Table Headers
    await expect(page.getByRole("columnheader", { name: "Hạng" })).toBeVisible();
    await expect(page.getByRole("columnheader", { name: "Thí sinh" })).toBeVisible();
    await expect(page.getByRole("columnheader", { name: "AC" })).toBeVisible();
    await expect(page.getByRole("columnheader", { name: "Phạt (phút)" })).toBeVisible();

    // Candidate Rows
    await expect(page.getByText("algorithm_master")).toBeVisible();
    await expect(page.getByText("speed_coder")).toBeVisible();

    // Authenticated user row with (bạn) indicator
    await expect(page.getByRole("cell", { name: /student/ })).toBeVisible();
    await expect(page.getByText("(bạn)")).toBeVisible();

    // Refresh action
    const refreshBtn = page.getByRole("button", { name: "Làm mới" });
    await expect(refreshBtn).toBeVisible();
    await refreshBtn.click();
    await expect(page.getByText(/Cập nhật/)).toBeVisible();
  });
});
