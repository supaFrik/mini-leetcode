# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: adversarial-exam.spec.ts >> Adversarial Stress-Testing: Proctored Exam & Workspace Flows >> 4. Countdown Timer Expiration & Submission Locking >> locks submission immediately when contest time expires
- Location: e2e\adversarial-exam.spec.ts:286:9

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: getByRole('alertdialog', { name: 'Đã hết giờ làm bài' })
Expected: visible
Timeout: 5000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" getByRole('alertdialog', { name: 'Đã hết giờ làm bài' }) with timeout 5000ms
  - waiting for getByRole('alertdialog', { name: 'Đã hết giờ làm bài' })

```

```yaml
- banner:
  - link "Trang chủ Mini LeetCode":
    - /url: /
    - text: mini Mini LeetCode
  - navigation:
    - text: Practice
    - link "Exam":
      - /url: /exam
  - link "Bài đã nộp":
    - /url: /submissions
  - button "Thông báo"
  - button "Đổi giao diện sáng/tối"
  - text: "S student #USER"
  - button "Đăng xuất"
- main:
  - heading "Expired Contest Adversarial Test" [level=1]
  - paragraph: Kết thúc lúc 23:13:42 29/10/26. Đồng hồ tính theo giờ máy chủ.
  - paragraph: Quy chế phòng thi
  - list:
    - listitem: Không rời tab hoặc đổi cửa sổ. Rời quá 1,5 giây tính là 1 lần vi phạm.
    - listitem: Không thoát chế độ toàn màn hình. Thoát quá 1,5 giây tính là 1 lần vi phạm.
    - listitem: Không dán (paste) code từ bên ngoài. Mỗi lần dán tính ngay 1 lần vi phạm.
    - listitem: Vi phạm 3 lần sẽ bị loại khỏi kỳ thi và không thể nộp bài.
    - listitem: Bài làm được tự động lưu cục bộ và đồng bộ lên máy chủ mỗi 15 giây.
  - list "Kiểm tra trước khi thi":
    - listitem: Kết nối mạng ổn định
    - listitem: Trình duyệt hỗ trợ toàn màn hình
  - checkbox "Tôi đã đọc và đồng ý tuân thủ quy chế phòng thi."
  - text: Tôi đã đọc và đồng ý tuân thủ quy chế phòng thi.
  - button "Bắt đầu làm bài" [disabled]
- contentinfo: © 2026 Mini LeetCode.
- alert
```

# Test source

```ts
  207 |       });
  208 | 
  209 |       expect(dropPrevented).toBe(true);
  210 |       await expect(page.getByText("Không được phép dán code trong phòng thi")).toBeVisible();
  211 |     });
  212 |   });
  213 | 
  214 |   // =========================================================================
  215 |   // 3. 3-Strike Disqualification Locking & Submission Neutralization
  216 |   // =========================================================================
  217 |   test.describe("3. 3-Strike Disqualification Enforcement", () => {
  218 |     test("permanently locks workspace, disables submit buttons, and neutralizes Ctrl+Shift+Enter upon 3rd strike", async ({ page }) => {
  219 |       await enterExamWorkspace(page);
  220 | 
  221 |       // Trigger 3 paste violations with 600ms spacing to respect debounce
  222 |       for (let i = 0; i < 3; i++) {
  223 |         await page.evaluate(() => {
  224 |           window.dispatchEvent(new Event("paste", { bubbles: true, cancelable: true }));
  225 |         });
  226 |         await page.waitForTimeout(600);
  227 |       }
  228 | 
  229 |       // DisqualifiedOverlay MUST appear
  230 |       const overlay = page.getByRole("alertdialog", { name: "Bài thi đã bị khoá" });
  231 |       await expect(overlay).toBeVisible();
  232 |       await expect(page.getByText(/Bài thi của bạn đã bị khóa do vi phạm quy chế thi/)).toBeVisible();
  233 | 
  234 |       // Verify submit button is disabled in DOM
  235 |       const submitBtn = page.locator('button:has-text("Submit Solution")');
  236 |       await expect(submitBtn).toBeDisabled();
  237 | 
  238 |       // Track any POST /submit calls
  239 |       let submitCallMade = false;
  240 |       await page.route("**/api/v1/problems/*/submit", (route) => {
  241 |         submitCallMade = true;
  242 |         route.continue();
  243 |       });
  244 | 
  245 |       // Attempt submit hotkey
  246 |       await page.keyboard.press("Control+Shift+Enter");
  247 |       await page.waitForTimeout(500);
  248 | 
  249 |       // Verify NO submission was sent
  250 |       expect(submitCallMade).toBe(false);
  251 | 
  252 |       // Verify server controller marks disqualified
  253 |       const serverState = mockApi.proctoringViolations.get("101:user");
  254 |       expect(serverState?.disqualified).toBe(true);
  255 |       expect(serverState?.warnings).toBeGreaterThanOrEqual(3);
  256 |     });
  257 | 
  258 |     test("persists disqualification state on reload and prevents workspace re-entry", async ({ page }) => {
  259 |       mockApi.disqualifyUser(101);
  260 | 
  261 |       await page.goto("/exam/101/problems/1", { waitUntil: "domcontentloaded" });
  262 | 
  263 |       // Immediate locking overlay
  264 |       await expect(page.getByText("Bài thi đã bị khoá")).toBeVisible();
  265 | 
  266 |       // Hard reload page
  267 |       await page.reload({ waitUntil: "domcontentloaded" });
  268 | 
  269 |       // Still locked immediately
  270 |       await expect(page.getByText("Bài thi đã bị khoá")).toBeVisible();
  271 |       await expect(page.getByRole("link", { name: "Về trang kỳ thi" })).toBeVisible();
  272 | 
  273 |       // Navigation back to exam overview shows "Bị loại" and "Đã khoá"
  274 |       await page.getByRole("link", { name: "Về trang kỳ thi" }).click();
  275 |       await page.waitForURL("/exam/101");
  276 | 
  277 |       await expect(page.getByText(/Bạn đã bị loại khỏi kỳ thi này/)).toBeVisible();
  278 |       await expect(page.getByRole("button", { name: "Đã khoá" }).first()).toBeVisible();
  279 |     });
  280 |   });
  281 | 
  282 |   // =========================================================================
  283 |   // 4. Countdown Timer Expiration & In-Flight Submission Locking
  284 |   // =========================================================================
  285 |   test.describe("4. Countdown Timer Expiration & Submission Locking", () => {
  286 |     test("locks submission immediately when contest time expires", async ({ page }) => {
  287 |       const expiredContest = {
  288 |         id: 106,
  289 |         title: "Expired Contest Adversarial Test",
  290 |         startTime: new Date(Date.now() - 3600000).toISOString(),
  291 |         endTime: new Date(Date.now() - 500).toISOString(),
  292 |         status: "RUNNING" as const,
  293 |         scoringPolicy: "ACM_ICPC" as const,
  294 |         maxViolationsAllowed: 3,
  295 |         myStatus: "REGISTERED" as const,
  296 |         myViolations: 0,
  297 |         problems: [
  298 |           { id: 1, slug: "two-sum", title: "Two Sum", difficulty: "EASY" as const, label: "A" },
  299 |         ],
  300 |       };
  301 | 
  302 |       await setupMockApi(page, { customContests: [expiredContest] });
  303 |       await enterExamWorkspace(page, 106, 1, false);
  304 | 
  305 |       // TimeUpOverlay appears
  306 |       const overlay = page.getByRole("alertdialog", { name: "Đã hết giờ làm bài" });
> 307 |       await expect(overlay).toBeVisible();
      |                             ^ Error: expect(locator).toBeVisible() failed
  308 |       await expect(page.getByText(/Hệ thống đã khoá việc nộp bài/)).toBeVisible();
  309 | 
  310 |       // Submit button must be disabled
  311 |       const submitBtn = page.locator('button:has-text("Submit Solution")');
  312 |       await expect(submitBtn).toBeDisabled();
  313 | 
  314 |       // Link to leaderboard must be available
  315 |       await expect(page.getByRole("link", { name: "Xem bảng xếp hạng" })).toBeVisible();
  316 |     });
  317 | 
  318 |     test("blocks late in-flight submissions attempted after expiration", async ({ page }) => {
  319 |       const expiredContest = {
  320 |         id: 107,
  321 |         title: "Expiration Race Test",
  322 |         startTime: new Date(Date.now() - 3600000).toISOString(),
  323 |         endTime: new Date(Date.now() - 1000).toISOString(),
  324 |         status: "RUNNING" as const,
  325 |         scoringPolicy: "ACM_ICPC" as const,
  326 |         maxViolationsAllowed: 3,
  327 |         myStatus: "REGISTERED" as const,
  328 |         myViolations: 0,
  329 |         problems: [
  330 |           { id: 1, slug: "two-sum", title: "Two Sum", difficulty: "EASY" as const, label: "A" },
  331 |         ],
  332 |       };
  333 | 
  334 |       await setupMockApi(page, { customContests: [expiredContest] });
  335 |       await page.addInitScript(() => {
  336 |         sessionStorage.setItem("exam_started_107", "true");
  337 |       });
  338 | 
  339 |       await page.goto("/exam/107/problems/1", { waitUntil: "domcontentloaded" });
  340 | 
  341 |       let postSubmitCalled = false;
  342 |       await page.route("**/api/v1/problems/*/submit", (route) => {
  343 |         postSubmitCalled = true;
  344 |         route.continue();
  345 |       });
  346 | 
  347 |       // Press hotkey
  348 |       await page.keyboard.press("Control+Shift+Enter");
  349 |       await page.waitForTimeout(400);
  350 | 
  351 |       expect(postSubmitCalled).toBe(false);
  352 |     });
  353 |   });
  354 | 
  355 |   // =========================================================================
  356 |   // 5. Live Leaderboard Responsiveness & Disqualification Flagging
  357 |   // =========================================================================
  358 |   test.describe("5. Live Leaderboard Disqualification Flagging", () => {
  359 |     test("accurately flags disqualified contestant with badge, null rank, and dimmed row", async ({ page }) => {
  360 |       // Disqualify student on contest 101
  361 |       mockApi.disqualifyUser(101);
  362 | 
  363 |       await page.goto("/exam/101/leaderboard", { waitUntil: "domcontentloaded" });
  364 | 
  365 |       // Check contest header
  366 |       await expect(page.getByRole("heading", { name: "Mini LeetCode Weekly Contest 101" })).toBeVisible();
  367 | 
  368 |       // Locate row for 'student'
  369 |       const studentRow = page.locator("tr", { has: page.getByText("student") });
  370 |       await expect(studentRow).toBeVisible();
  371 | 
  372 |       // Must display 'Bị loại' badge
  373 |       await expect(studentRow.getByText("Bị loại")).toBeVisible();
  374 | 
  375 |       // Rank must be '—' (null rank)
  376 |       await expect(studentRow.locator("td").first()).toHaveText("—");
  377 | 
  378 |       // Row must have dimmed opacity class (opacity-60)
  379 |       await expect(studentRow).toHaveClass(/opacity-60/);
  380 | 
  381 |       // Other non-disqualified contestants must retain their ranks
  382 |       const topRow = page.locator("tr", { has: page.getByText("algorithm_master") });
  383 |       await expect(topRow.locator("td").first()).toHaveText("1");
  384 |       await expect(topRow.getByText("Bị loại")).not.toBeVisible();
  385 |     });
  386 | 
  387 |     test("supports manual refresh action and maintains live leaderboard responsiveness", async ({ page }) => {
  388 |       await page.goto("/exam/101/leaderboard", { waitUntil: "domcontentloaded" });
  389 | 
  390 |       const refreshBtn = page.getByRole("button", { name: "Làm mới" });
  391 |       await expect(refreshBtn).toBeVisible();
  392 | 
  393 |       let leaderboardRequests = 0;
  394 |       page.on("request", (req) => {
  395 |         if (req.url().includes("/leaderboard")) leaderboardRequests++;
  396 |       });
  397 | 
  398 |       await refreshBtn.click();
  399 |       await page.waitForTimeout(500);
  400 | 
  401 |       expect(leaderboardRequests).toBeGreaterThanOrEqual(1);
  402 |       await expect(page.getByText(/Cập nhật/)).toBeVisible();
  403 |     });
  404 |   });
  405 | });
  406 | 
```