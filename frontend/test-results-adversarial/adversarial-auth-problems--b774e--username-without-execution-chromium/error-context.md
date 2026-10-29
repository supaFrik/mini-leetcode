# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: adversarial-auth-problems.spec.ts >> Adversarial Stress Testing: Auth and Problems Flows >> 1. Adversarial Registration >> neutralizes XSS script injection in username without execution
- Location: e2e\adversarial-auth-problems.spec.ts:43:9

# Error details

```
TimeoutError: page.waitForURL: Timeout 10000ms exceeded.
=========================== logs ===========================
waiting for navigation until "load"
============================================================
```

# Page snapshot

```yaml
- generic [active] [ref=e1]:
  - generic [ref=e2]:
    - banner [ref=e3]:
      - link "Trang chủ Mini LeetCode" [ref=e4] [cursor=pointer]:
        - /url: /
        - generic [ref=e5]: mini
        - generic [ref=e6]: Mini LeetCode
      - navigation [ref=e7]:
        - link "Practice" [ref=e8] [cursor=pointer]:
          - /url: /problems
        - link "Exam" [ref=e9] [cursor=pointer]:
          - /url: /exam
      - textbox "Tìm bài tập" [ref=e14]:
        - /placeholder: "Search by title, #slug, or keyword (e.g. dynamic programming)..."
      - generic [ref=e16]:
        - link "Bài đã nộp" [ref=e17] [cursor=pointer]:
          - /url: /submissions
        - button "Thông báo" [ref=e18] [cursor=pointer]
        - button "Đổi giao diện sáng/tối" [ref=e19] [cursor=pointer]
        - generic [ref=e20]:
          - generic [ref=e21]: "?"
          - generic [ref=e23]:
            - generic [ref=e24]: Guest
            - generic [ref=e25]: "#—"
          - button "Đăng xuất" [ref=e26] [cursor=pointer]
    - main [ref=e27]:
      - main [ref=e28]:
        - complementary [ref=e29]:
          - generic [ref=e30]:
            - generic [ref=e31]: mini
            - text: Mini LeetCode
          - generic [ref=e32]:
            - heading "Luyện thuật toán. Thi công bằng." [level=2] [ref=e33]: Luyện thuật toán.Thi công bằng.
            - list [ref=e34]:
              - listitem [ref=e35]: Chấm bài cô lập, kết quả trong vài giây
              - listitem [ref=e38]: Phòng thi có đồng hồ và giám sát từ máy chủ
              - listitem [ref=e42]: Bảng xếp hạng ACM/ICPC theo thời gian thực
          - paragraph [ref=e49]: © 2026 Mini LeetCode.
        - generic [ref=e51]:
          - generic [ref=e52]:
            - heading "Tạo tài khoản" [level=1] [ref=e53]
            - paragraph [ref=e54]: Tài khoản mới có vai trò học viên.
          - generic [ref=e55]:
            - text: Tên đăng nhập
            - textbox "Tên đăng nhập" [ref=e56]: <script>alert('xss_1793289734296')</script>
          - generic [ref=e57]:
            - text: Email
            - textbox "Email" [ref=e58]: xss_1793289734296@example.com
          - generic [ref=e59]:
            - text: Mật khẩu
            - generic [ref=e60]:
              - textbox "Mật khẩu" [ref=e61]: Password123!
              - button "Hiện mật khẩu" [ref=e62] [cursor=pointer]
          - button "Đăng ký" [ref=e66] [cursor=pointer]
          - button "Đã có tài khoản? Đăng nhập" [ref=e67] [cursor=pointer]
    - contentinfo [ref=e68]: © 2026 Mini LeetCode.
  - alert [ref=e69]
```

# Test source

```ts
  1   | import { test, expect } from "@playwright/test";
  2   | import {
  3   |   setupMockApi,
  4   |   setMockAuthSession,
  5   |   installFullscreenMock,
  6   | } from "./fixtures/mock-api";
  7   | 
  8   | test.describe("Adversarial Stress Testing: Auth and Problems Flows", () => {
  9   |   test.beforeEach(async ({ page }) => {
  10  |     await installFullscreenMock(page);
  11  |     await setupMockApi(page);
  12  |   });
  13  | 
  14  |   // =========================================================================
  15  |   // 1. ADVERSARIAL REGISTRATION & RFC 7807 SCHEMAS
  16  |   // =========================================================================
  17  |   test.describe("1. Adversarial Registration", () => {
  18  |     test("handles SQL injection payloads in username and email safely", async ({ page }) => {
  19  |       await page.goto("/register");
  20  |       await page.waitForLoadState("domcontentloaded");
  21  | 
  22  |       const ts = Date.now();
  23  |       const sqliUsername = `sqli_${ts}' OR '1'='1`;
  24  |       const sqliEmail = `sqli_${ts}'--@example.com`;
  25  | 
  26  |       await page.getByLabel("Tên đăng nhập").fill(sqliUsername);
  27  |       await page.getByLabel("Email").fill(sqliEmail);
  28  |       await page.getByLabel("Mật khẩu", { exact: true }).fill("Password123!");
  29  | 
  30  |       const submitBtn = page.getByRole("button", { name: "Đăng ký" });
  31  |       await expect(submitBtn).toBeEnabled();
  32  |       await Promise.all([
  33  |         page.waitForResponse((r) => r.url().includes("/api/v1/auth/register")),
  34  |         submitBtn.click(),
  35  |       ]);
  36  | 
  37  |       // Successfully creates account and transitions without SQL parsing or unhandled error
  38  |       await page.waitForURL((url) => !url.pathname.includes("/register") && !url.pathname.includes("/login"), { timeout: 10000 });
  39  |       const storedToken = await page.evaluate(() => localStorage.getItem("access_token"));
  40  |       expect(storedToken).toBeTruthy();
  41  |     });
  42  | 
  43  |     test("neutralizes XSS script injection in username without execution", async ({ page }) => {
  44  |       let alertTriggered = false;
  45  |       page.on("dialog", (dialog) => {
  46  |         alertTriggered = true;
  47  |         dialog.dismiss();
  48  |       });
  49  | 
  50  |       await page.goto("/register");
  51  |       await page.waitForLoadState("domcontentloaded");
  52  | 
  53  |       const ts = Date.now();
  54  |       const xssUsername = `<script>alert('xss_${ts}')</script>`;
  55  |       await page.getByLabel("Tên đăng nhập").fill(xssUsername);
  56  |       await page.getByLabel("Email").fill(`xss_${ts}@example.com`);
  57  |       await page.getByLabel("Mật khẩu", { exact: true }).fill("Password123!");
  58  | 
  59  |       const submitBtn = page.getByRole("button", { name: "Đăng ký" });
  60  |       await expect(submitBtn).toBeEnabled();
  61  |       await Promise.all([
  62  |         page.waitForResponse((r) => r.url().includes("/api/v1/auth/register")),
  63  |         submitBtn.click(),
  64  |       ]);
  65  | 
> 66  |       await page.waitForURL((url) => !url.pathname.includes("/register") && !url.pathname.includes("/login"), { timeout: 10000 });
      |                  ^ TimeoutError: page.waitForURL: Timeout 10000ms exceeded.
  67  | 
  68  |       // Crucial security invariant: No XSS dialog was ever executed
  69  |       expect(alertTriggered).toBe(false);
  70  | 
  71  |       // Successfully stored valid token for the escaped user
  72  |       const storedToken = await page.evaluate(() => localStorage.getItem("access_token"));
  73  |       expect(storedToken).toBeTruthy();
  74  |     });
  75  | 
  76  |     test("enforces maximum length boundary strings with RFC 7807 Bad Request", async ({ page }) => {
  77  |       await page.goto("/register");
  78  |       await page.waitForLoadState("domcontentloaded");
  79  | 
  80  |       // 455-character username violates maximum 50-character limit
  81  |       const longUsername = "user_" + "a".repeat(450);
  82  |       await page.getByLabel("Tên đăng nhập").fill(longUsername);
  83  |       await page.getByLabel("Email").fill("boundary_user@example.com");
  84  |       await page.getByLabel("Mật khẩu", { exact: true }).fill("Password123!");
  85  | 
  86  |       const submitBtn = page.getByRole("button", { name: "Đăng ký" });
  87  |       await expect(submitBtn).toBeEnabled();
  88  |       await Promise.all([
  89  |         page.waitForResponse((r) => r.url().includes("/api/v1/auth/register")),
  90  |         submitBtn.click(),
  91  |       ]);
  92  | 
  93  |       // Form remains on /register, showing RFC 7807 validation problem details
  94  |       await expect(page.getByText("Validation failed for request fields")).toBeVisible();
  95  |       await expect(page.getByText("Username must be between 3 and 50 characters")).toBeVisible();
  96  |     });
  97  | 
  98  |     test("maps multiple field-level RFC 7807 invalid_params simultaneously", async ({ page }) => {
  99  |       // Mock route to return multiple invalid_params on registration
  100 |       await page.route("**/api/v1/auth/register", async (route) => {
  101 |         await route.fulfill({
  102 |           status: 400,
  103 |           contentType: "application/problem+json",
  104 |           body: JSON.stringify({
  105 |             type: "https://api.mini-leetcode.com/errors/bad-request",
  106 |             title: "Bad Request",
  107 |             status: 400,
  108 |             detail: "Multiple fields failed validation",
  109 |             instance: "/api/v1/auth/register",
  110 |             invalid_params: {
  111 |               username: "Username contains illegal characters",
  112 |               email: "Email domain is blacklisted",
  113 |               password: "Password is too common",
  114 |             },
  115 |           }),
  116 |         });
  117 |       });
  118 | 
  119 |       await page.goto("/register");
  120 |       await page.waitForLoadState("domcontentloaded");
  121 | 
  122 |       await page.getByLabel("Tên đăng nhập").fill("bad_user_name");
  123 |       await page.getByLabel("Email").fill("blocked@spam.com");
  124 |       await page.getByLabel("Mật khẩu", { exact: true }).fill("12345678"); // valid length to bypass client check
  125 | 
  126 |       const submitBtn = page.getByRole("button", { name: "Đăng ký" });
  127 |       await expect(submitBtn).toBeEnabled();
  128 |       await Promise.all([
  129 |         page.waitForResponse((r) => r.url().includes("/api/v1/auth/register")),
  130 |         submitBtn.click(),
  131 |       ]);
  132 | 
  133 |       // Top-level RFC 7807 banner
  134 |       await expect(page.getByText("Multiple fields failed validation")).toBeVisible();
  135 | 
  136 |       // Field-level RFC 7807 alerts
  137 |       await expect(page.getByText("Username contains illegal characters")).toBeVisible();
  138 |       await expect(page.getByText("Email domain is blacklisted")).toBeVisible();
  139 |       await expect(page.getByText("Password is too common")).toBeVisible();
  140 |     });
  141 | 
  142 |     test("retains UI interactivity when backend returns RFC 7807 500 Internal Server Error", async ({ page }) => {
  143 |       await page.route("**/api/v1/auth/register", async (route) => {
  144 |         await route.fulfill({
  145 |           status: 500,
  146 |           contentType: "application/problem+json",
  147 |           body: JSON.stringify({
  148 |             type: "https://api.mini-leetcode.com/errors/internal-error",
  149 |             title: "Internal Server Error",
  150 |             status: 500,
  151 |             detail: "Database cluster synchronization timed out",
  152 |             instance: "/api/v1/auth/register",
  153 |           }),
  154 |         });
  155 |       });
  156 | 
  157 |       await page.goto("/register");
  158 |       await page.waitForLoadState("domcontentloaded");
  159 | 
  160 |       await page.getByLabel("Tên đăng nhập").fill("test_user_500");
  161 |       await page.getByLabel("Email").fill("test500@example.com");
  162 |       await page.getByLabel("Mật khẩu", { exact: true }).fill("Password123!");
  163 | 
  164 |       const submitBtn = page.getByRole("button", { name: "Đăng ký" });
  165 |       await expect(submitBtn).toBeEnabled();
  166 |       await Promise.all([
```